package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.model.TmdbMediaType
import com.kraat.lostfilmnewtv.data.model.TmdbEpisodeOverviewSource
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TmdbPosterClientTest {
    @Test
    fun searchByTitle_usesConfiguredProxyBaseUrl_withoutTmdbSecret() = runTest {
        var requestedUrl = ""
        var authorizationHeader: String? = "unexpected"
        val client = TmdbPosterClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    requestedUrl = chain.request().url.toString()
                    authorizationHeader = chain.request().header("Authorization")
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"results": [], "total_results": 0}""".toResponseBody())
                        .build()
                })
                .build(),
            apiKey = "",
            bearerToken = "",
            baseUrl = "https://auth.example.test/api/tmdb",
        )

        client.searchByTitle("Новобранец", year = 2026, type = TmdbMediaType.TV)

        assertTrue(requestedUrl.startsWith("https://auth.example.test/api/tmdb/search/tv?"))
        assertFalse(requestedUrl.contains("api_key="))
        assertEquals(null, authorizationHeader)
    }

    @Test
    fun searchByTitle_usesApiKeyAsQueryParameter_notBearerHeader() = runTest {
        var requestedUrl = ""
        var authorizationHeader: String? = "unexpected"
        val client = TmdbPosterClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    requestedUrl = chain.request().url.toString()
                    authorizationHeader = chain.request().header("Authorization")
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"results": [], "total_results": 0}""".toResponseBody())
                        .build()
                })
                .build(),
            apiKey = "v3-key",
        )

        client.searchByTitle("Новобранец", year = 2026, type = TmdbMediaType.TV)

        assertTrue(requestedUrl.contains("api_key=v3-key"))
        assertEquals(null, authorizationHeader)
    }

    @Test
    fun searchByTitle_usesBearerTokenWhenProvided() = runTest {
        var authorizationHeader: String? = null
        val client = TmdbPosterClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    authorizationHeader = chain.request().header("Authorization")
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"results": [], "total_results": 0}""".toResponseBody())
                        .build()
                })
                .build(),
            apiKey = "",
            bearerToken = "v4-token",
        )

        client.searchByTitle("Новобранец", year = 2026, type = TmdbMediaType.TV)

        assertEquals("Bearer v4-token", authorizationHeader)
    }

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
        assertEquals("https://image.tmdb.org/t/p/w1280/en-backdrop.jpg", result.backdropUrl)
        assertTrue(requestedUrls.any { it.contains("language=ru") && it.contains("include_image_language=ru,null") })
        assertTrue(requestedUrls.any { it.contains("language=en") && it.contains("include_image_language=en,null") })
    }

    @Test
    fun getPosterAndBackdrop_usesEnglishPoster_whenRussianPosterMissing() = runTest {
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
                              "posters": [],
                              "backdrops": [{"file_path": "/ru-backdrop.jpg"}]
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
        assertEquals("https://image.tmdb.org/t/p/w780/en-poster.jpg", result.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/ru-backdrop.jpg", result.backdropUrl)
        assertTrue(requestedUrls.any { it.contains("language=en") && it.contains("include_image_language=en,null") })
    }

    @Test
    fun getPosterAndBackdrop_doesNotCallEnglish_whenRussianPosterAndBackdropExist() = runTest {
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
        assertEquals("https://image.tmdb.org/t/p/w1280/ru-backdrop.jpg", result.backdropUrl)
        assertEquals(1, requestedUrls.count { it.contains("/tv/123/images") })
    }

    @Test
    fun getPosterAndBackdrop_prefersBestRatedRussianPoster() = runTest {
        val client = TmdbPosterClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val body = """
                        {
                          "posters": [
                            {
                              "file_path": "/first-ru-poster.jpg",
                              "iso_639_1": "ru",
                              "vote_average": 5.0,
                              "vote_count": 2,
                              "width": 1000,
                              "height": 1500
                            },
                            {
                              "file_path": "/best-ru-poster.jpg",
                              "iso_639_1": "ru",
                              "vote_average": 8.0,
                              "vote_count": 5,
                              "width": 1000,
                              "height": 1500
                            }
                          ],
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
        assertEquals("https://image.tmdb.org/t/p/w780/best-ru-poster.jpg", result.posterUrl)
    }

    @Test
    fun getEpisodeOverviewRu_usesEnglishFallback_whenRussianOverviewMissing() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = TmdbPosterClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val url = chain.request().url.toString()
                    requestedUrls += url
                    val body = when {
                        url.contains("language=ru-RU") -> """{"overview": ""}"""
                        url.contains("language=en-US") -> """{"overview": "English episode overview."}"""
                        else -> """{"overview": ""}"""
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

        val result = client.getEpisodeOverview(tmdbId = 123, seasonNumber = 2, episodeNumber = 8)

        assertEquals("English episode overview.", result?.text)
        assertEquals(TmdbEpisodeOverviewSource.TMDB_EN, result?.source)
        assertTrue(requestedUrls.any { it.contains("/tv/123/season/2/episode/8") && it.contains("language=ru-RU") })
        assertTrue(requestedUrls.any { it.contains("/tv/123/season/2/episode/8") && it.contains("language=en-US") })
    }

    @Test
    fun getEpisodeOverviewRu_doesNotCallEnglish_whenRussianOverviewExists() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = TmdbPosterClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    requestedUrls += chain.request().url.toString()

                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"overview": "Русское описание серии."}""".toResponseBody())
                        .build()
                })
                .build(),
            apiKey = "test",
        )

        val result = client.getEpisodeOverview(tmdbId = 123, seasonNumber = 2, episodeNumber = 8)

        assertEquals("Русское описание серии.", result?.text)
        assertEquals(TmdbEpisodeOverviewSource.TMDB_RU, result?.source)
        assertEquals(1, requestedUrls.count { it.contains("/tv/123/season/2/episode/8") })
        assertTrue(requestedUrls.single().contains("language=ru-RU"))
    }

    @Test
    fun getEpisodeOverviewRu_translatesEnglishFallback_whenTranslatorIsAvailable() = runTest {
        val client = TmdbPosterClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val url = chain.request().url.toString()
                    val body = when {
                        url.contains("language=ru-RU") -> """{"overview": ""}"""
                        url.contains("language=en-US") -> """{"overview": "English episode overview."}"""
                        else -> """{"overview": ""}"""
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
            englishToRussianTranslator = { english ->
                assertEquals("English episode overview.", english)
                "Русское описание серии."
            },
        )

        val result = client.getEpisodeOverview(tmdbId = 123, seasonNumber = 2, episodeNumber = 8)

        assertEquals("Русское описание серии.", result?.text)
        assertEquals(TmdbEpisodeOverviewSource.MACHINE_TRANSLATED, result?.source)
    }

    @Test
    fun getSeasonImages_requestsSeasonEndpoint() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = TmdbPosterClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val url = chain.request().url.toString()
                    requestedUrls += url
                    val body = """
                        {
                          "posters": [{"file_path": "/season-poster.jpg"}],
                          "backdrops": [{"file_path": "/season-backdrop.jpg"}]
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

        val result = client.getSeasonImages(tmdbId = 456, seasonNumber = 3)

        requireNotNull(result)
        assertEquals("https://image.tmdb.org/t/p/w780/season-poster.jpg", result.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/season-backdrop.jpg", result.backdropUrl)
        assertTrue(requestedUrls.any { it.contains("/tv/456/season/3/images") })
    }

    @Test
    fun getSeasonImages_returnsNull_whenNoImagesExist() = runTest {
        val client = TmdbPosterClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"posters": [], "backdrops": []}""".toResponseBody())
                        .build()
                })
                .build(),
            apiKey = "test",
        )

        val result = client.getSeasonImages(tmdbId = 456, seasonNumber = 3)

        assertEquals(null, result)
    }

    @Test
    fun getSeasonOverviewRu_returnsRussianSeasonOverview() = runTest {
        var requestedUrl = ""
        val client = TmdbPosterClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    requestedUrl = chain.request().url.toString()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"overview": "Описание третьего сезона."}""".toResponseBody())
                        .build()
                })
                .build(),
            apiKey = "test",
        )

        val result = client.getSeasonOverviewRu(tmdbId = 456, seasonNumber = 3)

        assertEquals("Описание третьего сезона.", result)
        assertTrue(requestedUrl.contains("/tv/456/season/3"))
        assertTrue(requestedUrl.contains("language=ru-RU"))
    }

    @Test
    fun getSeasonOverviewRu_returnsNull_whenOverviewIsBlank() = runTest {
        val client = TmdbPosterClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"overview": ""}""".toResponseBody())
                        .build()
                })
                .build(),
            apiKey = "test",
        )

        val result = client.getSeasonOverviewRu(tmdbId = 456, seasonNumber = 3)

        assertEquals(null, result)
    }
}
