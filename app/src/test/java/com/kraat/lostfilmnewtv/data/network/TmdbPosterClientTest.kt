package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.model.TmdbMediaType
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TmdbPosterClientTest {
    @Test
    fun getPosterAndBackdrop_usesEnglishBackdrop_whenRussianBackdropMissing() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = TmdbPosterClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val url = chain.request().url.toString()
                    requestedUrls += url
                    val body = when {
                        url.contains("language=ru") -> {
                            """
                            {
                              "posters": [{"file_path": "/ru-poster.jpg"}],
                              "backdrops": []
                            }
                            """.trimIndent()
                        }

                        url.contains("language=en") -> {
                            """
                            {
                              "posters": [{"file_path": "/en-poster.jpg"}],
                              "backdrops": [{"file_path": "/en-backdrop.jpg"}]
                            }
                            """.trimIndent()
                        }

                        else -> "{" + "\"posters\": [], \"backdrops\": []}"
                    }

                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody())
                        .build()
                })
                .build(),
            apiKey = "test",
        )

        val result = client.getPosterAndBackdrop(123, TmdbMediaType.TV)

        requireNotNull(result)
        assertEquals("https://image.tmdb.org/t/p/w780/ru-poster.jpg", result.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/original/en-backdrop.jpg", result.backdropUrl)
        assertTrue(requestedUrls.any { it.contains("language=ru") && it.contains("include_image_language=ru,null") })
        assertTrue(requestedUrls.any { it.contains("language=en") && it.contains("include_image_language=en,null") })
    }

    @Test
    fun getPosterAndBackdrop_doesNotCallEnglish_whenRussianBackdropExists() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = TmdbPosterClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val url = chain.request().url.toString()
                    requestedUrls += url
                    val body = """
                        {
                          "posters": [{"file_path": "/ru-poster.jpg"}],
                          "backdrops": [{"file_path": "/ru-backdrop.jpg"}]
                        }
                    """.trimIndent()

                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody())
                        .build()
                })
                .build(),
            apiKey = "test",
        )

        val result = client.getPosterAndBackdrop(123, TmdbMediaType.TV)

        requireNotNull(result)
        assertEquals("https://image.tmdb.org/t/p/w780/ru-poster.jpg", result.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/original/ru-backdrop.jpg", result.backdropUrl)
        assertEquals(1, requestedUrls.count { it.contains("/tv/123/images") })
    }
}
