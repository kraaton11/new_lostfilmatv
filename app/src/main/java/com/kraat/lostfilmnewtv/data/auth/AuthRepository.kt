package com.kraat.lostfilmnewtv.data.auth

import com.kraat.lostfilmnewtv.data.model.AuthState
import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import com.kraat.lostfilmnewtv.data.model.PairingSession
import com.kraat.lostfilmnewtv.data.model.PairingStatus
import com.kraat.lostfilmnewtv.data.network.AuthBridgeHttpException
import com.kraat.lostfilmnewtv.data.network.AuthBridgeClient
import com.kraat.lostfilmnewtv.data.network.LostFilmSessionVerifier
import java.io.IOException
import kotlinx.coroutines.delay

class AuthRepository(
    private val authBridgeClient: AuthBridgeClient,
    private val sessionStore: SessionStore,
    private val sessionVerifier: LostFilmSessionVerifier = LostFilmSessionVerifier(),
    private val verificationMaxAttempts: Int = 3,
    private val verificationRetryDelayMillis: Long = 250L,
) : AuthRepositoryContract {
    private var currentPairing: PairingSession? = null

    override suspend fun getAuthState(): AuthState {
        val session = sessionStore.read()
        return AuthState(
            isAuthenticated = session != null && !session.isExpired(),
            session = session,
        )
    }

    override suspend fun startPairing(): PairingSession {
        val pairing = authBridgeClient.createPairing()
        currentPairing = pairing
        return pairing
    }

    suspend fun getCurrentPairing(): PairingSession? = currentPairing

    override suspend fun pollPairingStatus(): PairingSession? {
        val pairing = currentPairing ?: return null
        val updated = authBridgeClient.getPairingStatus(pairing)
        currentPairing = updated
        return updated
    }

    suspend fun waitForConfirmation(timeoutSeconds: Int = 120): PairingSession? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            val pairing = pollPairingStatus() ?: return null
            when (pairing.status) {
                PairingStatus.CONFIRMED -> return pairing
                PairingStatus.EXPIRED, PairingStatus.FAILED -> return pairing
                else -> delay(pairing.pollInterval * 1000L)
            }
        }
        return currentPairing
    }

    override suspend fun claimAndPersistSession(): AuthCompletionResult {
        val pairing = currentPairing ?: return AuthCompletionResult.RecoverableFailure()

        val session = try {
            val session = authBridgeClient.claimSession(pairing)
            session
        } catch (e: AuthBridgeHttpException) {
            return if (e.isExpired()) {
                AuthCompletionResult.Expired
            } else if (e.isRetryable()) {
                AuthCompletionResult.NetworkError
            } else {
                AuthCompletionResult.RecoverableFailure()
            }
        } catch (_: IOException) {
            return AuthCompletionResult.NetworkError
        } catch (_: Exception) {
            return AuthCompletionResult.RecoverableFailure()
        }

        return when (verifyClaimedSessionWithRetries(session)) {
            VerificationResult.AUTHENTICATED -> finalizeVerifiedSession(pairing, session)
            VerificationResult.UNAUTHENTICATED -> {
                releaseClaimBestEffort(pairing)
                AuthCompletionResult.VerificationFailed
            }

            VerificationResult.NETWORK_ERROR -> {
                releaseClaimBestEffort(pairing)
                AuthCompletionResult.NetworkError
            }
        }
    }

    private suspend fun verifyClaimedSessionWithRetries(
        session: com.kraat.lostfilmnewtv.data.model.LostFilmSession,
    ): VerificationResult {
        val attempts = verificationMaxAttempts.coerceAtLeast(1)
        repeat(attempts) { attempt ->
            try {
                return if (sessionVerifier.verify(session)) {
                    VerificationResult.AUTHENTICATED
                } else {
                    VerificationResult.UNAUTHENTICATED
                }
            } catch (e: IOException) {
                if (attempt == attempts - 1) {
                    return VerificationResult.NETWORK_ERROR
                }
                delay(verificationRetryDelayMillis)
            }
        }

        return VerificationResult.NETWORK_ERROR
    }

    private suspend fun finalizeVerifiedSession(
        pairing: PairingSession,
        session: LostFilmSession,
    ): AuthCompletionResult {
        val attempts = verificationMaxAttempts.coerceAtLeast(1)
        repeat(attempts) { attempt ->
            try {
                val finalized = authBridgeClient.finalizeClaim(pairing)
                if (!finalized) {
                    return AuthCompletionResult.RecoverableFailure()
                }
                sessionStore.save(session)
                return AuthCompletionResult.Authenticated
            } catch (e: AuthBridgeHttpException) {
                if (e.isExpired()) {
                    return AuthCompletionResult.Expired
                }

                if (e.isRetryable()) {
                    if (attempt == attempts - 1) {
                        return AuthCompletionResult.NetworkError
                    }
                    delay(verificationRetryDelayMillis)
                } else {
                    return AuthCompletionResult.RecoverableFailure()
                }
            } catch (_: IOException) {
                if (attempt == attempts - 1) {
                    return AuthCompletionResult.NetworkError
                }
                delay(verificationRetryDelayMillis)
            } catch (_: Exception) {
                return AuthCompletionResult.RecoverableFailure()
            }
        }

        return AuthCompletionResult.NetworkError
    }

    private suspend fun releaseClaimBestEffort(pairing: PairingSession) {
        try {
            authBridgeClient.releaseClaim(pairing)
        } catch (_: Exception) {
            // Release is best-effort after a failed local handoff.
        }
    }

    override suspend fun logout() {
        sessionStore.clear()
        currentPairing = null
    }

    private enum class VerificationResult {
        AUTHENTICATED,
        UNAUTHENTICATED,
        NETWORK_ERROR,
    }

}
