package com.kraat.lostfilmnewtv.data.auth

import com.kraat.lostfilmnewtv.data.model.AuthState
import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import com.kraat.lostfilmnewtv.data.model.PairingSession
import com.kraat.lostfilmnewtv.data.model.PairingStatus
import com.kraat.lostfilmnewtv.data.network.AuthBridgeHttpException
import com.kraat.lostfilmnewtv.data.network.AuthBridgeClient
import com.kraat.lostfilmnewtv.data.network.LostFilmSessionVerifier
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class AuthRepository(
    private val authBridgeClient: AuthBridgeClient,
    private val sessionStore: SessionStore,
    private val sessionVerifier: LostFilmSessionVerifier,
    private val verificationMaxAttempts: Int = 3,
    private val verificationRetryDelayMillis: Long = 250L,
) : AuthRepositoryContract {
    @Volatile
    private var currentPairing: PairingSession? = null

    override suspend fun getAuthState(): AuthState {
        val session = sessionStore.read()
            ?: return AuthState(isAuthenticated = false, session = null)
        if (sessionStore.isExpired()) {
            return AuthState(isAuthenticated = false, session = session)
        }

        return when (verifyClaimedSessionWithRetries(session)) {
            VerificationResult.AUTHENTICATED -> AuthState(
                isAuthenticated = true,
                session = session,
            )

            VerificationResult.UNAUTHENTICATED -> {
                sessionStore.markExpired()
                AuthState(
                    isAuthenticated = false,
                    session = session,
                )
            }

            VerificationResult.NETWORK_ERROR -> AuthState(
                isAuthenticated = true,
                session = session,
            )
        }
    }

    override fun observeAuthState(): Flow<AuthState> {
        return sessionStore.changes()
            .onStart { emit(Unit) }
            .map { getAuthState() }
            .distinctUntilChanged()
    }

    override suspend fun startPairing(): PairingSession {
        val pairing = authBridgeClient.createPairing()
        currentPairing = pairing
        return pairing
    }

    override suspend fun pollPairingStatus(): PairingSession? {
        val pairing = currentPairing ?: return null
        val updated = authBridgeClient.getPairingStatus(pairing)
        currentPairing = updated
        return updated
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return AuthCompletionResult.RecoverableFailure()
        }

        return when (verifyClaimedSessionWithRetries(session)) {
            VerificationResult.AUTHENTICATED -> persistVerifiedSessionAndFinalizeBestEffort(pairing, session)
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

    private suspend fun persistVerifiedSessionAndFinalizeBestEffort(
        pairing: PairingSession,
        session: LostFilmSession,
    ): AuthCompletionResult {
        try {
            sessionStore.save(session)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return AuthCompletionResult.RecoverableFailure()
        }

        val attempts = verificationMaxAttempts.coerceAtLeast(1)
        repeat(attempts) { attempt ->
            try {
                val finalized = authBridgeClient.finalizeClaim(pairing)
                if (finalized) {
                    return AuthCompletionResult.Authenticated
                }
                return AuthCompletionResult.Authenticated
            } catch (e: AuthBridgeHttpException) {
                if (e.isRetryable()) {
                    if (attempt == attempts - 1) {
                        return AuthCompletionResult.Authenticated
                    }
                    delay(verificationRetryDelayMillis)
                }
            } catch (_: IOException) {
                if (attempt == attempts - 1) {
                    return AuthCompletionResult.Authenticated
                }
                delay(verificationRetryDelayMillis)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return AuthCompletionResult.Authenticated
            }
        }

        return AuthCompletionResult.Authenticated
    }

    private suspend fun releaseClaimBestEffort(pairing: PairingSession) {
        try {
            authBridgeClient.releaseClaim(pairing)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Release is best-effort after a failed local handoff.
        }
    }

    override suspend fun cancelPairing() {
        val pairing = currentPairing ?: return
        currentPairing = null
        try {
            authBridgeClient.cancelPairing(pairing)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Cancellation from the TV should close local state even if the bridge is unreachable.
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
