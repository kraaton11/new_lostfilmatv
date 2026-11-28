package com.kraat.lostfilmnewtv.data.network

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LostFilmMirrorCheckerTest {
    @Test
    fun acceptsOnlySuccessfulReleasePages() {
        assertTrue(checker(200, """<div class="serials-list"><div class="row"></div></div>""").isAvailable(MIRROR))
        assertFalse(checker(200, "<html>Provider block page</html>").isAvailable(MIRROR))
        assertFalse(checker(503, """<div class="serials-list"><div class="row"></div></div>""").isAvailable(MIRROR))
    }

    @Test
    fun reportsConnectionFailure() {
        val client = OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build()
        assertFalse(LostFilmMirrorChecker(client).isAvailable(MIRROR))
    }

    private fun checker(code: Int, body: String) = LostFilmMirrorChecker(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            check(request.url.encodedPath == "/new/")
            check(request.header("Cookie") == null)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("Test").body(body.toResponseBody()).build()
        }.build(),
    )

    private companion object {
        const val MIRROR = "https://www.lostfilm.download"
    }
}
