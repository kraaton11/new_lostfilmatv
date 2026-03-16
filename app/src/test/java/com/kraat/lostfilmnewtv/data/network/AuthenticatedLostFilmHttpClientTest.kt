package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.auth.SessionStore
import com.kraat.lostfilmnewtv.data.model.LostFilmCookie
import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthenticatedLostFilmHttpClientTest {

    @Test
    fun fetchDetails_addsCookieHeader_whenSessionExists() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        server.start()
        try {
            val client = AuthenticatedLostFilmHttpClient(
                sessionStore = FakeSessionStore(
                    LostFilmSession(
                        cookies = listOf(
                            LostFilmCookie("lf_session", "cookie-value", ".lostfilm.today"),
                            LostFilmCookie("uid", "42", ".lostfilm.today"),
                        ),
                    ),
                ),
                okHttpClient = OkHttpClient(),
            )

            client.fetchDetails(server.url("/series/test").toString())

            val request = server.takeRequest()
            assertEquals("lf_session=cookie-value; uid=42", request.getHeader("Cookie"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fetchDetails_skipsCookieHeader_whenSessionMissing() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        server.start()
        try {
            val client = AuthenticatedLostFilmHttpClient(
                sessionStore = FakeSessionStore(null),
                okHttpClient = OkHttpClient(),
            )

            client.fetchDetails(server.url("/series/test").toString())

            val request = server.takeRequest()
            assertEquals(null, request.getHeader("Cookie"))
        } finally {
            server.shutdown()
        }
    }

    private class FakeSessionStore(
        private var session: LostFilmSession?,
    ) : SessionStore {
        override suspend fun read(): LostFilmSession? = session
        override suspend fun save(session: LostFilmSession) {
            this.session = session
        }
        override suspend fun markExpired() = Unit
        override suspend fun clear() {
            session = null
        }
        override suspend fun isExpired(): Boolean = false
    }
}
