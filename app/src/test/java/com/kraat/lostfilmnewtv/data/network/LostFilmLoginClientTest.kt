package com.kraat.lostfilmnewtv.data.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LostFilmLoginClientTest {
    @Test
    fun captchaAndLoginShareCookiesAndReturnTheSession() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().addHeader("Set-Cookie", "challenge=abc; Path=/"))
            server.enqueue(MockResponse().setBody("""{"result":"ok","need_captcha":true}"""))
            server.enqueue(MockResponse().setBody("captcha"))
            server.enqueue(
                MockResponse().setBody("""{"result":"ok","success":true}""")
                    .addHeader("Set-Cookie", "lf_session=session-token; Path=/"),
            )
            val client = LostFilmLoginClient(OkHttpClient()) { server.url("/").toString() }

            client.warmUp()
            assertEquals(LostFilmLoginClient.LoginResult.NeedCaptcha, client.login("tv@example.test", "p&ss", null))
            assertArrayEquals("captcha".toByteArray(), client.fetchCaptcha())
            val result = client.login("tv@example.test", "p&ss", "1234") as LostFilmLoginClient.LoginResult.Success

            assertEquals("session-token", result.session.cookies.single { it.name == "lf_session" }.value)
            assertEquals("/", server.takeRequest().path)
            val firstLogin = server.takeRequest()
            assertEquals("/ajaxik.users.php", firstLogin.path)
            assertEquals("challenge=abc", firstLogin.getHeader("Cookie"))
            assertTrue(firstLogin.body.readUtf8().contains("pass=p%2526ss"))
            val captcha = server.takeRequest()
            assertTrue(captcha.path.orEmpty().startsWith("/simple_captcha.php?"))
            assertEquals("challenge=abc", captcha.getHeader("Cookie"))
            assertTrue(server.takeRequest().body.readUtf8().contains("need_captcha=1&captcha=1234"))
        }
    }
}
