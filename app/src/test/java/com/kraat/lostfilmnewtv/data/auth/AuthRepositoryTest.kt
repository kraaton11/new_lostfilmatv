package com.kraat.lostfilmnewtv.data.auth

import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import com.kraat.lostfilmnewtv.data.network.AuthBridgeClient
import com.kraat.lostfilmnewtv.data.network.LostFilmSessionVerifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    @Test
    fun getAuthState_verifiesPersistedSessionBeforeReportingAuthenticated() = runTest {
        val verifierServer = MockWebServer()
        verifierServer.enqueue(authenticatedProbeResponse())
        verifierServer.start()
        try {
            val sessionStore = FakeSessionStore(
                session = LostFilmSession(cookies = emptyList()),
                expired = false,
            )
            val repository = AuthRepository(
                authBridgeClient = AuthBridgeClient("https://auth.example.test", OkHttpClient()),
                sessionStore = sessionStore,
                sessionVerifier = LostFilmSessionVerifier(
                    probeUrl = verifierServer.url("/").toString(),
                    okHttpClient = OkHttpClient(),
                ),
                verificationMaxAttempts = 1,
                verificationRetryDelayMillis = 1L,
            )

            val authState = repository.getAuthState()

            assertTrue(authState.isAuthenticated)
            assertEquals(sessionStore.session, authState.session)
            assertFalse(sessionStore.expired)
        } finally {
            verifierServer.shutdown()
        }
    }

    @Test
    fun getAuthState_marksStoredSessionExpired_whenProbeLooksAnonymous() = runTest {
        val verifierServer = MockWebServer()
        verifierServer.enqueue(anonymousProbeResponse())
        verifierServer.start()
        try {
            val sessionStore = FakeSessionStore(
                session = LostFilmSession(cookies = emptyList()),
                expired = false,
            )
            val repository = AuthRepository(
                authBridgeClient = AuthBridgeClient("https://auth.example.test", OkHttpClient()),
                sessionStore = sessionStore,
                sessionVerifier = LostFilmSessionVerifier(
                    probeUrl = verifierServer.url("/").toString(),
                    okHttpClient = OkHttpClient(),
                ),
                verificationMaxAttempts = 1,
                verificationRetryDelayMillis = 1L,
            )

            val authState = repository.getAuthState()

            assertFalse(authState.isAuthenticated)
            assertEquals(sessionStore.session, authState.session)
            assertTrue(sessionStore.expired)
        } finally {
            verifierServer.shutdown()
        }
    }

    @Test
    fun getAuthState_keepsPersistedSessionOnVerificationNetworkError() = runTest {
        val verifierServer = MockWebServer()
        verifierServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        verifierServer.start()
        try {
            val sessionStore = FakeSessionStore(
                session = LostFilmSession(cookies = emptyList()),
                expired = false,
            )
            val repository = AuthRepository(
                authBridgeClient = AuthBridgeClient("https://auth.example.test", OkHttpClient()),
                sessionStore = sessionStore,
                sessionVerifier = LostFilmSessionVerifier(
                    probeUrl = verifierServer.url("/").toString(),
                    okHttpClient = OkHttpClient(),
                ),
                verificationMaxAttempts = 1,
                verificationRetryDelayMillis = 1L,
            )

            val authState = repository.getAuthState()

            assertTrue(authState.isAuthenticated)
            assertEquals(sessionStore.session, authState.session)
            assertFalse(sessionStore.expired)
        } finally {
            verifierServer.shutdown()
        }
    }

    @Test
    fun getAuthState_respectsPreviouslyExpiredStoreWithoutRecheckingNetwork() = runTest {
        val sessionStore = FakeSessionStore(
            session = LostFilmSession(cookies = emptyList()),
            expired = false,
        )
        val verifierServer = MockWebServer()
        verifierServer.start()
        try {
            val repository = AuthRepository(
                authBridgeClient = AuthBridgeClient("https://auth.example.test", OkHttpClient()),
                sessionStore = sessionStore,
                sessionVerifier = LostFilmSessionVerifier(
                    probeUrl = verifierServer.url("/").toString(),
                    okHttpClient = OkHttpClient(),
                ),
                verificationMaxAttempts = 1,
                verificationRetryDelayMillis = 1L,
            )
            sessionStore.markExpired()

            val authState = repository.getAuthState()

            assertFalse(authState.isAuthenticated)
            assertEquals(sessionStore.session, authState.session)
            assertEquals(0, verifierServer.requestCount)
        } finally {
            verifierServer.shutdown()
        }
    }

    @Test
    fun claimAndPersistSession_finalizes_whenVerificationSucceeds() = runTest {
        val bridgeServer = MockWebServer()
        val verifierServer = MockWebServer()
        bridgeServer.enqueue(pairingCreateResponse())
        bridgeServer.enqueue(claimResponse())
        bridgeServer.enqueue(MockResponse().setResponseCode(204))
        verifierServer.enqueue(authenticatedProbeResponse())
        bridgeServer.start()
        verifierServer.start()
        try {
            val sessionStore = FakeSessionStore()
            val repository = AuthRepository(
                authBridgeClient = AuthBridgeClient(baseUrl(bridgeServer), OkHttpClient()),
                sessionStore = sessionStore,
                sessionVerifier = LostFilmSessionVerifier(
                    probeUrl = verifierServer.url("/").toString(),
                    okHttpClient = OkHttpClient(),
                ),
                verificationMaxAttempts = 3,
                verificationRetryDelayMillis = 1L,
            )

            repository.startPairing()
            val result = repository.claimAndPersistSession()

            assertEquals(AuthCompletionResult.Authenticated, result)
            assertNotNull(sessionStore.session)
            assertEquals("/api/pairings", bridgeServer.takeRequest().path)
            assertEquals("/api/pairings/pair-123/claim", bridgeServer.takeRequest().path)
            assertEquals("/api/pairings/pair-123/finalize", bridgeServer.takeRequest().path)
        } finally {
            bridgeServer.shutdown()
            verifierServer.shutdown()
        }
    }

    @Test
    fun claimAndPersistSession_releasesAndClears_whenVerificationFails() = runTest {
        val bridgeServer = MockWebServer()
        val verifierServer = MockWebServer()
        bridgeServer.enqueue(pairingCreateResponse())
        bridgeServer.enqueue(claimResponse())
        bridgeServer.enqueue(MockResponse().setResponseCode(204))
        verifierServer.enqueue(anonymousProbeResponse())
        bridgeServer.start()
        verifierServer.start()
        try {
            val sessionStore = FakeSessionStore()
            val repository = AuthRepository(
                authBridgeClient = AuthBridgeClient(baseUrl(bridgeServer), OkHttpClient()),
                sessionStore = sessionStore,
                sessionVerifier = LostFilmSessionVerifier(
                    probeUrl = verifierServer.url("/").toString(),
                    okHttpClient = OkHttpClient(),
                ),
                verificationMaxAttempts = 3,
                verificationRetryDelayMillis = 1L,
            )

            repository.startPairing()
            val result = repository.claimAndPersistSession()

            assertEquals(AuthCompletionResult.VerificationFailed, result)
            assertNull(sessionStore.session)
            assertEquals("/api/pairings", bridgeServer.takeRequest().path)
            assertEquals("/api/pairings/pair-123/claim", bridgeServer.takeRequest().path)
            assertEquals("/api/pairings/pair-123/release", bridgeServer.takeRequest().path)
        } finally {
            bridgeServer.shutdown()
            verifierServer.shutdown()
        }
    }

    @Test
    fun claimAndPersistSession_returnsNetworkError_afterTransientProbeFailures() = runTest {
        val bridgeServer = MockWebServer()
        val verifierServer = MockWebServer()
        bridgeServer.enqueue(pairingCreateResponse())
        bridgeServer.enqueue(claimResponse())
        bridgeServer.enqueue(MockResponse().setResponseCode(204))
        verifierServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        verifierServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        verifierServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        bridgeServer.start()
        verifierServer.start()
        try {
            val sessionStore = FakeSessionStore()
            val repository = AuthRepository(
                authBridgeClient = AuthBridgeClient(baseUrl(bridgeServer), OkHttpClient()),
                sessionStore = sessionStore,
                sessionVerifier = LostFilmSessionVerifier(
                    probeUrl = verifierServer.url("/").toString(),
                    okHttpClient = OkHttpClient(),
                ),
                verificationMaxAttempts = 3,
                verificationRetryDelayMillis = 1L,
            )

            repository.startPairing()
            val result = repository.claimAndPersistSession()

            assertEquals(AuthCompletionResult.NetworkError, result)
            assertNull(sessionStore.session)
            assertEquals(3, verifierServer.requestCount)
            assertEquals("/api/pairings", bridgeServer.takeRequest().path)
            assertEquals("/api/pairings/pair-123/claim", bridgeServer.takeRequest().path)
            assertEquals("/api/pairings/pair-123/release", bridgeServer.takeRequest().path)
        } finally {
            bridgeServer.shutdown()
            verifierServer.shutdown()
        }
    }

    @Test
    fun claimAndPersistSession_returnsExpired_whenClaimHasExpired() = runTest {
        val bridgeServer = MockWebServer()
        bridgeServer.enqueue(pairingCreateResponse())
        bridgeServer.enqueue(
            MockResponse()
                .setResponseCode(410)
                .setBody("expired"),
        )
        bridgeServer.start()
        try {
            val sessionStore = FakeSessionStore()
            val repository = AuthRepository(
                authBridgeClient = AuthBridgeClient(baseUrl(bridgeServer), OkHttpClient()),
                sessionStore = sessionStore,
                sessionVerifier = LostFilmSessionVerifier(
                    probeUrl = "https://unused.example.test/",
                    okHttpClient = OkHttpClient(),
                ),
                verificationMaxAttempts = 3,
                verificationRetryDelayMillis = 1L,
            )

            repository.startPairing()
            val result = repository.claimAndPersistSession()

            assertEquals(AuthCompletionResult.Expired, result)
            assertNull(sessionStore.session)
            assertEquals("/api/pairings", bridgeServer.takeRequest().path)
            assertEquals("/api/pairings/pair-123/claim", bridgeServer.takeRequest().path)
            assertEquals(2, bridgeServer.requestCount)
        } finally {
            bridgeServer.shutdown()
        }
    }

    @Test
    fun claimAndPersistSession_returnsNetworkError_whenFinalizeFails_withoutRelease() = runTest {
        val bridgeServer = MockWebServer()
        val verifierServer = MockWebServer()
        bridgeServer.enqueue(pairingCreateResponse())
        bridgeServer.enqueue(claimResponse())
        bridgeServer.enqueue(MockResponse().setResponseCode(503).setBody("temporary"))
        bridgeServer.enqueue(MockResponse().setResponseCode(503).setBody("temporary"))
        bridgeServer.enqueue(MockResponse().setResponseCode(503).setBody("temporary"))
        verifierServer.enqueue(authenticatedProbeResponse())
        bridgeServer.start()
        verifierServer.start()
        try {
            val sessionStore = FakeSessionStore()
            val repository = AuthRepository(
                authBridgeClient = AuthBridgeClient(baseUrl(bridgeServer), OkHttpClient()),
                sessionStore = sessionStore,
                sessionVerifier = LostFilmSessionVerifier(
                    probeUrl = verifierServer.url("/").toString(),
                    okHttpClient = OkHttpClient(),
                ),
                verificationMaxAttempts = 3,
                verificationRetryDelayMillis = 1L,
            )

            repository.startPairing()
            val result = repository.claimAndPersistSession()

            assertEquals(AuthCompletionResult.NetworkError, result)
            assertNull(sessionStore.session)
            assertEquals("/api/pairings", bridgeServer.takeRequest().path)
            assertEquals("/api/pairings/pair-123/claim", bridgeServer.takeRequest().path)
            assertEquals("/api/pairings/pair-123/finalize", bridgeServer.takeRequest().path)
            assertEquals("/api/pairings/pair-123/finalize", bridgeServer.takeRequest().path)
            assertEquals("/api/pairings/pair-123/finalize", bridgeServer.takeRequest().path)
            assertEquals(5, bridgeServer.requestCount)
        } finally {
            bridgeServer.shutdown()
            verifierServer.shutdown()
        }
    }

    @Test
    fun claimAndPersistSession_returnsRecoverableFailure_whenFinalizeDoesNotComplete() = runTest {
        val bridgeServer = MockWebServer()
        val verifierServer = MockWebServer()
        bridgeServer.enqueue(pairingCreateResponse())
        bridgeServer.enqueue(claimResponse())
        bridgeServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        verifierServer.enqueue(authenticatedProbeResponse())
        bridgeServer.start()
        verifierServer.start()
        try {
            val sessionStore = FakeSessionStore()
            val repository = AuthRepository(
                authBridgeClient = AuthBridgeClient(baseUrl(bridgeServer), OkHttpClient()),
                sessionStore = sessionStore,
                sessionVerifier = LostFilmSessionVerifier(
                    probeUrl = verifierServer.url("/").toString(),
                    okHttpClient = OkHttpClient(),
                ),
                verificationMaxAttempts = 3,
                verificationRetryDelayMillis = 1L,
            )

            repository.startPairing()
            val result = repository.claimAndPersistSession()

            assertEquals(AuthCompletionResult.RecoverableFailure(), result)
            assertNull(sessionStore.session)
            assertEquals("/api/pairings", bridgeServer.takeRequest().path)
            assertEquals("/api/pairings/pair-123/claim", bridgeServer.takeRequest().path)
            assertEquals("/api/pairings/pair-123/finalize", bridgeServer.takeRequest().path)
            assertEquals(3, bridgeServer.requestCount)
        } finally {
            bridgeServer.shutdown()
            verifierServer.shutdown()
        }
    }

    private fun baseUrl(server: MockWebServer): String = server.url("/").toString().removeSuffix("/")

    private fun pairingCreateResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody(
            """
            {
              "pairingId": "pair-123",
              "pairingSecret": "secret-456",
              "phoneVerifier": "phone-789",
              "userCode": "ABC123",
              "verificationUrl": "https://phone-789.auth.example.test/",
              "status": "pending",
              "expiresIn": 120,
              "pollInterval": 5
            }
            """.trimIndent(),
        )

    private fun claimResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody(
            """
            {
              "cookies": [
                { "name": "lf_session", "value": "cookie-value", "domain": ".lostfilm.today", "path": "/" },
                { "name": "uid", "value": "42", "domain": ".lostfilm.today", "path": "/" }
              ],
              "accountId": "42"
            }
            """.trimIndent(),
        )

    private fun authenticatedProbeResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody(
            """
            <html>
              <body>
                <a href="/logout">Выйти</a>
              </body>
            </html>
            """.trimIndent(),
        )

    private fun anonymousProbeResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody("""<html><body><form id="lf-login-form"></form></body></html>""")

    private class FakeSessionStore(
        var session: LostFilmSession? = null,
        var expired: Boolean = false,
    ) : SessionStore {
        private val changesFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        override suspend fun read(): LostFilmSession? = session

        override suspend fun save(session: LostFilmSession) {
            this.session = session
            expired = false
            changesFlow.tryEmit(Unit)
        }

        override suspend fun markExpired() {
            expired = true
            changesFlow.tryEmit(Unit)
        }

        override suspend fun clear() {
            session = null
            expired = false
            changesFlow.tryEmit(Unit)
        }

        override suspend fun isExpired(): Boolean = expired
        override fun changes(): Flow<Unit> = changesFlow
    }
}
