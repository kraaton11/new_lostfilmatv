package com.kraat.lostfilmnewtv.data.auth

import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import com.kraat.lostfilmnewtv.data.network.LostFilmLoginClient
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
                loginClient = LostFilmLoginClient(OkHttpClient()),
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
                loginClient = LostFilmLoginClient(OkHttpClient()),
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
                loginClient = LostFilmLoginClient(OkHttpClient()),
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
                loginClient = LostFilmLoginClient(OkHttpClient()),
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
    fun passwordLogin_verifiesAndPersistsSession() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .addHeader("Set-Cookie", "lf_session=verified; Path=/")
                    .setBody("""{"result":"ok","success":true}"""),
            )
            server.enqueue(authenticatedProbeResponse())
            val sessionStore = FakeSessionStore()
            val client = OkHttpClient()
            val repository = AuthRepository(
                sessionStore = sessionStore,
                sessionVerifier = LostFilmSessionVerifier(client, server.url("/").toString()),
                loginClient = LostFilmLoginClient(client) { server.url("/").toString() },
            )

            val result = repository.loginWithPassword("tv@example.test", "password", null)

            assertEquals(PasswordLoginResult.Authenticated, result)
            assertEquals("lf_session=verified", sessionStore.session?.toCookieString())
            assertEquals("/ajaxik.users.php", server.takeRequest().path)
            assertEquals("lf_session=verified", server.takeRequest().getHeader("Cookie"))
        }
    }

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
