package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LostFilmMirrorFailoverTest {
    @After
    fun restoreDefault() {
        LostFilmBaseUrl.set(LostFilmBaseUrl.DEFAULT)
        LostFilmBaseUrl.setAutomatic(false)
    }

    @Test
    fun retriesRequestedPageOnFirstAvailableMirror() {
        LostFilmBaseUrl.setAutomatic(true)
        val requests = mutableListOf<String>()
        val client = client { url ->
            requests += "${url.host}${url.encodedPath}"
            when (url.host) {
                "www.lostfilm.today" -> throw IOException("offline")
                "www.lostfilm.download" -> 503 to ""
                else -> 200 to RELEASES
            }
        }

        assertEquals(RELEASES, runBlocking { client.fetchNewPage(2) })
        assertEquals(
            listOf(
                "www.lostfilm.today/new/page_2",
                "www.lostfilm.download/new/",
                "www.lostfilm.tv/new/",
                "www.lostfilm.tv/new/page_2",
            ),
            requests,
        )
        assertEquals("https://www.lostfilm.tv", LostFilmBaseUrl.get())

        LostFilmBaseUrl.setAutomatic(false)
        assertEquals(LostFilmBaseUrl.DEFAULT, LostFilmBaseUrl.get())
    }

    @Test
    fun retriesSearchOnTheActiveMirror() {
        LostFilmBaseUrl.setAutomatic(true)
        val client = client { url ->
            if (url.host == "www.lostfilm.today") throw IOException("offline")
            if (url.encodedPath == "/new/") 200 to RELEASES else 200 to url.encodedQuery.orEmpty()
        }

        val searchUrl = "/search/?q=house%20dragon"
        assertEquals("q=house%20dragon", runBlocking { client.fetchDetails(searchUrl) })
    }

    @Test
    fun rejectsAnHtmlBlockPageWithSuccessfulStatus() {
        LostFilmBaseUrl.setAutomatic(true)
        val client = client { url ->
            200 to if (url.host == "www.lostfilm.today") "<html>Provider block</html>" else RELEASES
        }

        assertEquals(RELEASES, runBlocking { client.fetchNewPage(1) })
        assertEquals("https://www.lostfilm.download", LostFilmBaseUrl.get())
    }

    @Test
    fun leavesAddressUnchangedWhenAllMirrorsFail() {
        LostFilmBaseUrl.setAutomatic(true)
        val client = client { throw IOException("offline") }

        assertThrows(IOException::class.java) { runBlocking { client.fetchNewPage(1) } }
        assertEquals(LostFilmBaseUrl.DEFAULT, LostFilmBaseUrl.get())
    }

    @Test
    fun skipsProbesWhenDisabledOrPageIsMissing() {
        val requests = mutableListOf<String>()
        val client = client { url ->
            requests += url.host
            if (LostFilmBaseUrl.selection.automatic) 404 to "" else throw IOException("offline")
        }

        assertThrows(IOException::class.java) { runBlocking { client.fetchNewPage(2) } }
        LostFilmBaseUrl.setAutomatic(true)
        assertThrows(IOException::class.java) { runBlocking { client.fetchNewPage(2) } }
        assertEquals(listOf("www.lostfilm.today", "www.lostfilm.today"), requests)
    }

    @Test
    fun preservesManualSelectionMadeDuringProbe() {
        LostFilmBaseUrl.setAutomatic(true)
        val client = client { url ->
            if (url.host == "www.lostfilm.today") throw IOException("offline")
            LostFilmBaseUrl.set("https://example.com")
            200 to RELEASES
        }

        assertThrows(IOException::class.java) { runBlocking { client.fetchNewPage(1) } }
        assertEquals("https://example.com", LostFilmBaseUrl.get())
    }

    private fun client(respond: (HttpUrl) -> Pair<Int, String>): LostFilmHttpClient {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            check(request.header("Cookie") == null)
            val (code, body) = respond(request.url)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("Test").body(body.toResponseBody()).build()
        }.build()
        return OkHttpLostFilmHttpClient(okHttpClient = client)
    }

    private companion object {
        const val RELEASES = """<div class="serials-list"><div class="row"></div></div>"""
    }
}
