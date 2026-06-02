package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.auth.SessionStore
import com.kraat.lostfilmnewtv.data.model.FavoriteToggleNetworkResult
import com.kraat.lostfilmnewtv.data.model.LostFilmCookie
import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import com.kraat.lostfilmnewtv.data.parser.BASE_URL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class AuthenticatedLostFilmHttpClientTest {

    @Test
    fun fetchDetails_addsCookieHeader_whenSessionExists() = runTest {
        var capturedRequest: Request? = null
        val client = AuthenticatedLostFilmHttpClient(
            sessionStore = FakeSessionStore(
                LostFilmSession(
                    cookies = listOf(
                        LostFilmCookie("lf_session", "cookie-value", ".lostfilm.today"),
                        LostFilmCookie("uid", "42", ".lostfilm.today"),
                    ),
                ),
            ),
            okHttpClient = recordingClient { request ->
                capturedRequest = request
                "ok"
            },
        )

        client.fetchDetails("https://www.lostfilm.today/series/test")

        val request = requireNotNull(capturedRequest)
        assertEquals("lf_session=cookie-value; uid=42", request.header("Cookie"))
    }

    @Test
    fun fetchDetails_skipsCookieHeader_whenSessionMissing() = runTest {
        var capturedRequest: Request? = null
        val client = AuthenticatedLostFilmHttpClient(
            sessionStore = FakeSessionStore(null),
            okHttpClient = recordingClient { request ->
                capturedRequest = request
                "ok"
            },
        )

        client.fetchDetails("https://www.lostfilm.today/series/test")

        val request = requireNotNull(capturedRequest)
        assertEquals(null, request.header("Cookie"))
    }

    @Test
    fun fetchSchedulePage_requestsFilteredSchedulePage() = runTest {
        var capturedRequest: Request? = null
        val client = AuthenticatedLostFilmHttpClient(
            sessionStore = FakeSessionStore(null),
            okHttpClient = recordingClient { request ->
                capturedRequest = request
                "ok"
            },
        )

        client.fetchSchedulePage()

        val request = requireNotNull(capturedRequest)
        assertEquals("https://www.lostfilm.today/schedule/my_0/type_0", request.url.toString())
    }

    @Test
    fun fetchMoviesPage_requestsMoviesSearchWithLatestSort() = runTest {
        var capturedRequest: Request? = null
        val client = AuthenticatedLostFilmHttpClient(
            sessionStore = FakeSessionStore(null),
            okHttpClient = recordingClient { request ->
                capturedRequest = request
                "ok"
            },
        )

        client.fetchMoviesPage()

        val request = requireNotNull(capturedRequest)
        assertEquals("https://www.lostfilm.today/movies/?type=search&s=6&t=0", request.url.toString())
    }

    @Test
    fun fetchSeriesCatalogPage_postsSeriesSearchWithLatestSort() = runTest {
        var capturedRequest: Request? = null
        val client = AuthenticatedLostFilmHttpClient(
            sessionStore = FakeSessionStore(null),
            okHttpClient = recordingClient { request ->
                capturedRequest = request
                "[]"
            },
        )

        client.fetchSeriesCatalogPage()

        val request = requireNotNull(capturedRequest)
        assertEquals("https://www.lostfilm.today/ajaxik.php", request.url.toString())
        assertEquals("https://www.lostfilm.today/series/", request.header("Referer"))
        val requestBody = request.body
        assertNotNull(requestBody)
        val buffer = okio.Buffer()
        requestBody!!.writeTo(buffer)
        assertEquals("act=serial&type=search&o=0&s=6&t=0", buffer.readUtf8())
    }

    @Test
    fun fetchDetails_marksSessionExpired_whenCookieBackedRequestLooksAnonymous() = runTest {
        val sessionStore = FakeSessionStore(
            LostFilmSession(
                cookies = listOf(
                    LostFilmCookie("lf_session", "cookie-value", ".lostfilm.today"),
                ),
            ),
        )
        val client = AuthenticatedLostFilmHttpClient(
            sessionStore = sessionStore,
            okHttpClient = recordingClient { """<html><body><form id="lf-login-form"></form></body></html>""" },
        )

        client.fetchDetails("https://www.lostfilm.today/series/test")

        assertTrue(sessionStore.expired)
    }

    @Test
    fun fetchDetails_rejectsThirdPartyUrlBeforeSendingCookies() = runTest {
        var capturedRequest: Request? = null
        val client = AuthenticatedLostFilmHttpClient(
            sessionStore = FakeSessionStore(
                LostFilmSession(
                    cookies = listOf(
                        LostFilmCookie("lf_session", "cookie-value", ".lostfilm.today"),
                    ),
                ),
            ),
            okHttpClient = recordingClient { request ->
                capturedRequest = request
                "ok"
            },
        )

        try {
            client.fetchDetails("https://evil.example/steal")
            fail("Expected third-party URL rejection")
        } catch (_: IOException) {
            assertEquals(null, capturedRequest)
        }
    }

    @Test
    fun fetchTorrentPage_doesNotSendSessionCookieToAllowedTorrentHost() = runTest {
        var capturedRequest: Request? = null
        val client = AuthenticatedLostFilmHttpClient(
            sessionStore = FakeSessionStore(
                LostFilmSession(
                    cookies = listOf(
                        LostFilmCookie("lf_session", "cookie-value", ".lostfilm.today"),
                    ),
                ),
            ),
            okHttpClient = recordingClient { request ->
                capturedRequest = request
                "torrent page"
            },
        )

        client.fetchTorrentPage("https://n.tracktor.site/td.php?s=token")

        val request = requireNotNull(capturedRequest)
        assertEquals(null, request.header("Cookie"))
    }

    @Test
    fun markEpisodeWatched_addsAjaxHeadersAndSessionCookie() = runTest {
        var capturedRequest: Request? = null
        val client = AuthenticatedLostFilmHttpClient(
            sessionStore = FakeSessionStore(
                LostFilmSession(
                    cookies = listOf(
                        LostFilmCookie("lf_session", "cookie-value", ".lostfilm.today"),
                        LostFilmCookie("uid", "42", ".lostfilm.today"),
                    ),
                ),
            ),
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedRequest = chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"result":"on"}""".toResponseBody())
                        .build()
                }
                .build(),
        )

        val marked = client.setEpisodeWatched(
            detailsUrl = "https://www.lostfilm.today/series/Invincible/season_4/episode_1/",
            playEpisodeId = "362009013",
            ajaxSessionToken = "ajax-session-token",
            targetWatched = true,
        )

        assertEquals(true, marked)
        val request = requireNotNull(capturedRequest)
        assertEquals("lf_session=cookie-value; uid=42", request.header("Cookie"))
        assertEquals("XMLHttpRequest", request.header("X-Requested-With"))
        assertEquals(BASE_URL, request.header("Origin"))
        assertEquals(
            "https://www.lostfilm.today/series/Invincible/season_4/episode_1/",
            request.header("Referer"),
        )
        val requestBody = request.body
        assertNotNull(requestBody)
        val buffer = okio.Buffer()
        requestBody!!.writeTo(buffer)
        assertEquals(
            "act=serial&type=markepisode&val=362009013&auto=0&mode=on&session=ajax-session-token",
            buffer.readUtf8(),
        )
    }

    @Test
    fun setEpisodeWatched_offMode_addsAjaxHeadersAndSessionCookie() = runTest {
        var capturedRequest: Request? = null
        val client = AuthenticatedLostFilmHttpClient(
            sessionStore = FakeSessionStore(
                LostFilmSession(
                    cookies = listOf(
                        LostFilmCookie("lf_session", "cookie-value", ".lostfilm.today"),
                        LostFilmCookie("uid", "42", ".lostfilm.today"),
                    ),
                ),
            ),
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedRequest = chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"result":"on"}""".toResponseBody())
                        .build()
                }
                .build(),
        )

        val marked = client.setEpisodeWatched(
            detailsUrl = "https://www.lostfilm.today/series/Invincible/season_4/episode_1/",
            playEpisodeId = "362009013",
            ajaxSessionToken = "ajax-session-token",
            targetWatched = false,
        )

        assertEquals(true, marked)
        val request = requireNotNull(capturedRequest)
        val requestBody = request.body
        assertNotNull(requestBody)
        val buffer = okio.Buffer()
        requestBody!!.writeTo(buffer)
        assertEquals(
            "act=serial&type=markepisode&val=362009013&auto=0&mode=off&session=ajax-session-token",
            buffer.readUtf8(),
        )
    }

    @Test
    fun toggleFavorite_addsAjaxHeadersAndSessionCookie() = runTest {
        var capturedRequest: Request? = null
        val client = AuthenticatedLostFilmHttpClient(
            sessionStore = FakeSessionStore(
                LostFilmSession(
                    cookies = listOf(
                        LostFilmCookie("lf_session", "cookie-value", ".lostfilm.today"),
                        LostFilmCookie("uid", "42", ".lostfilm.today"),
                    ),
                ),
            ),
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedRequest = chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"result":"on"}""".toResponseBody())
                        .build()
                }
                .build(),
        )

        val result = client.toggleFavorite(
            refererUrl = "https://www.lostfilm.today/series/Invincible/",
            favoriteTargetId = 915,
            ajaxSessionToken = "ajax-session-token",
        )

        assertEquals(FavoriteToggleNetworkResult.ToggledOn, result)
        val request = requireNotNull(capturedRequest)
        assertEquals("lf_session=cookie-value; uid=42", request.header("Cookie"))
        assertEquals("XMLHttpRequest", request.header("X-Requested-With"))
        assertEquals(BASE_URL, request.header("Origin"))
        assertEquals(
            "https://www.lostfilm.today/series/Invincible/",
            request.header("Referer"),
        )
        val requestBody = request.body
        assertNotNull(requestBody)
        val buffer = okio.Buffer()
        requestBody!!.writeTo(buffer)
        assertEquals(
            "act=serial&type=follow&id=915&session=ajax-session-token",
            buffer.readUtf8(),
        )
    }

    private class FakeSessionStore(
        private var session: LostFilmSession?,
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

    private fun recordingClient(body: (Request) -> String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body(chain.request()).toResponseBody())
                    .build()
            }
            .build()
}
