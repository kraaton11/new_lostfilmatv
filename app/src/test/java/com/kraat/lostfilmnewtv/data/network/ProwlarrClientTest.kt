package com.kraat.lostfilmnewtv.data.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ProwlarrClientTest {
    @Test
    fun normalizeProwlarrBaseUrl_trimsTrailingSlash() {
        assertEquals(
            "http://192.168.2.245:9696",
            normalizeProwlarrBaseUrl(" http://192.168.2.245:9696/ "),
        )
    }

    @Test
    fun normalizeProwlarrBaseUrl_rejectsBlankAndNonHttpUrls() {
        assertEquals(null, normalizeProwlarrBaseUrl(""))
        assertEquals(null, normalizeProwlarrBaseUrl("ftp://192.168.2.245:9696"))
    }

    @Test
    fun search_buildsRequestWithApiKeyAndParsesTorrentResults() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {
                        "title": "Silo S01E05 1080p",
                        "indexer": "LostFilm",
                        "magnetUrl": "magnet:?xt=urn:btih:abc",
                        "size": 1073741824,
                        "seeders": 12,
                        "leechers": 3
                      },
                      {
                        "title": "Silo S01E05 720p",
                        "indexer": "Other",
                        "downloadUrl": "http://example.test/torrent",
                        "size": 536870912,
                        "seeders": 4
                      }
                    ]
                    """.trimIndent(),
                ),
        )
        server.start()
        try {
            val client = ProwlarrClient(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString(),
                apiKey = "prowlarr-key",
            )

            val results = client.search("Silo S01E05")
            val request = server.takeRequest()

            assertEquals("/api/v1/search?query=Silo%20S01E05&type=search", request.path)
            assertEquals("prowlarr-key", request.getHeader("X-Api-Key"))
            assertEquals(
                listOf(
                    ProwlarrSearchResult(
                        title = "Silo S01E05 1080p",
                        indexer = "LostFilm",
                        sourceUrl = "magnet:?xt=urn:btih:abc",
                        sizeBytes = 1073741824,
                        seeders = 12,
                        leechers = 3,
                    ),
                    ProwlarrSearchResult(
                        title = "Silo S01E05 720p",
                        indexer = "Other",
                        sourceUrl = "http://example.test/torrent",
                        sizeBytes = 536870912,
                        seeders = 4,
                        leechers = null,
                    ),
                ),
                results,
            )
        } finally {
            server.shutdown()
        }
    }
}
