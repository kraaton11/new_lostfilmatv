package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.model.LostFilmCookie
import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LostFilmSessionVerifierTest {

    @Test
    fun verify_returnsTrue_whenProbeLooksAuthenticated() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    <html>
                      <body>
                        <a href="/my">Мой профиль</a>
                        <a href="/logout">Выйти</a>
                      </body>
                    </html>
                    """.trimIndent(),
                ),
        )
        server.start()
        try {
            val verifier = LostFilmSessionVerifier(
                probeUrl = server.url("/").toString(),
                okHttpClient = OkHttpClient(),
            )

            val verified = verifier.verify(
                LostFilmSession(
                    cookies = listOf(
                        LostFilmCookie("lf_session", "cookie-value", ".lostfilm.today"),
                        LostFilmCookie("uid", "42", ".lostfilm.today"),
                    ),
                ),
            )

            val request = server.takeRequest()
            assertEquals("lf_session=cookie-value; uid=42", request.getHeader("Cookie"))
            assertTrue(verified)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun verify_returnsFalse_whenProbeStillLooksAnonymous() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    <html>
                      <body>
                        <form id="lf-login-form"></form>
                      </body>
                    </html>
                    """.trimIndent(),
                ),
        )
        server.start()
        try {
            val verifier = LostFilmSessionVerifier(
                probeUrl = server.url("/").toString(),
                okHttpClient = OkHttpClient(),
            )

            val verified = verifier.verify(
                LostFilmSession(
                    cookies = listOf(
                        LostFilmCookie("lf_session", "cookie-value", ".lostfilm.today"),
                    ),
                ),
            )

            assertFalse(verified)
        } finally {
            server.shutdown()
        }
    }

    @Test(expected = IOException::class)
    fun verify_throwsIOException_whenProbeReturnsServerError() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("temporarily unavailable"),
        )
        server.start()
        try {
            val verifier = LostFilmSessionVerifier(
                probeUrl = server.url("/").toString(),
                okHttpClient = OkHttpClient(),
            )

            verifier.verify(
                LostFilmSession(
                    cookies = listOf(
                        LostFilmCookie("lf_session", "cookie-value", ".lostfilm.today"),
                    ),
                ),
            )
        } finally {
            server.shutdown()
        }
    }
}
