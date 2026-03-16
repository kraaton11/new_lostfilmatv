package com.kraat.lostfilmnewtv.data.auth

import com.kraat.lostfilmnewtv.data.model.AuthState
import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import com.kraat.lostfilmnewtv.data.model.PairingSession
import com.kraat.lostfilmnewtv.data.model.PairingStatus
import com.kraat.lostfilmnewtv.data.network.AuthBridgeClient
import kotlinx.coroutines.delay

class AuthRepository(
    private val authBridgeClient: AuthBridgeClient,
    private val sessionStore: SessionStore,
) {
    private var currentPairing: PairingSession? = null

    suspend fun getAuthState(): AuthState {
        val session = sessionStore.read()
        return AuthState(
            isAuthenticated = session != null && !session.isExpired(),
            session = session,
        )
    }

    suspend fun startPairing(): PairingSession {
        val pairing = authBridgeClient.createPairing()
        currentPairing = pairing
        return pairing
    }

    suspend fun getCurrentPairing(): PairingSession? = currentPairing

    suspend fun pollPairingStatus(): PairingSession? {
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

    suspend fun claimAndPersistSession(): Boolean {
        val pairing = currentPairing ?: return false
        val session = authBridgeClient.claimSession(pairing)
        return try {
            sessionStore.save(session)
            val verified = verifyClaimedSession(session)
            if (verified) {
                authBridgeClient.finalizeClaim(pairing)
                true
            } else {
                sessionStore.clear()
                authBridgeClient.releaseClaim(pairing)
                false
            }
        } catch (e: Exception) {
            sessionStore.clear()
            authBridgeClient.releaseClaim(pairing)
            false
        }
    }

    private suspend fun verifyClaimedSession(session: LostFilmSession): Boolean {
        return session.cookies.any { it.name == "lf_session" && it.value.isNotBlank() }
    }

    suspend fun logout() {
        sessionStore.clear()
        currentPairing = null
    }
}
