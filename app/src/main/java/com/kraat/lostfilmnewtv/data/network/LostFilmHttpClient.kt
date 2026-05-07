package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.parser.BASE_URL
import com.kraat.lostfilmnewtv.data.parser.resolveUrl
import com.kraat.lostfilmnewtv.data.auth.SessionStore
import com.kraat.lostfilmnewtv.data.model.FavoriteToggleNetworkResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

interface LostFilmHttpClient {
    suspend fun fetchNewPage(pageNumber: Int): String

    suspend fun fetchMoviesPage(pageNumber: Int = 1): String = fetchDetails(moviesPageUrl(pageNumber))

    suspend fun fetchSeriesCatalogPage(pageNumber: Int = 1): String = fetchDetails(seriesCatalogPageUrl(pageNumber))

    suspend fun fetchDetails(detailsUrl: String): String

    suspend fun fetchAccountPage(path: String): String

    suspend fun fetchSeasonWatchedEpisodeMarks(refererUrl: String, serialId: String): String

    suspend fun fetchTorrentRedirect(playEpisodeId: String): String

    suspend fun fetchTorrentPage(url: String): String

    suspend fun setEpisodeWatched(
        detailsUrl: String,
        playEpisodeId: String,
        ajaxSessionToken: String,
        targetWatched: Boolean,
    ): Boolean

    suspend fun toggleFavorite(
        refererUrl: String,
        favoriteTargetId: Int,
        ajaxSessionToken: String,
    ): FavoriteToggleNetworkResult
}

/**
 * Единственная реализация [LostFilmHttpClient].
 *
 * Если [sessionStore] задан — добавляет Cookie-заголовок к каждому запросу
 * (аутентифицированный режим). Если null — работает анонимно.
 *
 * До рефакторинга были два почти идентичных класса:
 * [OkHttpLostFilmHttpClient] и [AuthenticatedLostFilmHttpClient]. Они объединены здесь.
 */
class OkHttpLostFilmHttpClient(
    private val sessionStore: SessionStore? = null,
    private val okHttpClient: OkHttpClient,
) : LostFilmHttpClient {

    override suspend fun fetchNewPage(pageNumber: Int): String = withContext(Dispatchers.IO) {
        val url = if (pageNumber <= 1) "$BASE_URL/new/" else "$BASE_URL/new/page_$pageNumber"
        executeLostFilm(url)
    }

    override suspend fun fetchMoviesPage(pageNumber: Int): String = withContext(Dispatchers.IO) {
        executeLostFilm(moviesPageUrl(pageNumber))
    }

    override suspend fun fetchSeriesCatalogPage(pageNumber: Int): String = withContext(Dispatchers.IO) {
        executeSeriesCatalogSearch(pageNumber)
    }

    override suspend fun fetchDetails(detailsUrl: String): String = withContext(Dispatchers.IO) {
        executeLostFilm(resolveUrl(detailsUrl))
    }

    override suspend fun fetchAccountPage(path: String): String = withContext(Dispatchers.IO) {
        executeLostFilm(resolveUrl(path))
    }

    override suspend fun fetchSeasonWatchedEpisodeMarks(
        refererUrl: String,
        serialId: String,
    ): String = withContext(Dispatchers.IO) {
        executeFetchSeasonWatchedEpisodeMarks(
            okHttpClient = okHttpClient,
            refererUrl = resolveUrl(refererUrl),
            serialId = serialId,
            cookieHeader = sessionStore?.read()?.toCookieString(),
        )
    }

    override suspend fun fetchTorrentRedirect(playEpisodeId: String): String = withContext(Dispatchers.IO) {
        executeLostFilm("$BASE_URL/v_search.php?a=$playEpisodeId")
    }

    override suspend fun fetchTorrentPage(url: String): String = withContext(Dispatchers.IO) {
        executePublicTorrentPage(url)
    }

    override suspend fun setEpisodeWatched(
        detailsUrl: String,
        playEpisodeId: String,
        ajaxSessionToken: String,
        targetWatched: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        executeSetEpisodeWatched(
            okHttpClient = okHttpClient,
            refererUrl = resolveUrl(detailsUrl),
            playEpisodeId = playEpisodeId,
            ajaxSessionToken = ajaxSessionToken,
            targetWatched = targetWatched,
            cookieHeader = sessionStore?.read()?.toCookieString(),
        )
    }

    override suspend fun toggleFavorite(
        refererUrl: String,
        favoriteTargetId: Int,
        ajaxSessionToken: String,
    ): FavoriteToggleNetworkResult = withContext(Dispatchers.IO) {
        executeToggleFavorite(
            okHttpClient = okHttpClient,
            refererUrl = refererUrl,
            favoriteTargetId = favoriteTargetId,
            ajaxSessionToken = ajaxSessionToken,
            cookieHeader = sessionStore?.read()?.toCookieString(),
        )
    }

    private suspend fun executeLostFilm(url: String): String {
        val parsedUrl = requireLostFilmUrl(url)
        val cookieHeader = sessionStore?.read()?.toCookieString()?.takeIf { it.isNotBlank() }
        val requestBuilder = Request.Builder()
            .url(parsedUrl)
            .header("User-Agent", USER_AGENT_PUBLIC)

        cookieHeader?.let { requestBuilder.header("Cookie", it) }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for ${parsedUrl.redactedForError()}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response body for ${parsedUrl.redactedForError()}")
            if (cookieHeader != null && lostFilmResponseLooksAnonymous(body)) {
                sessionStore?.markExpired()
            }
            return body
        }
    }

    private fun executePublicTorrentPage(url: String): String {
        val parsedUrl = requireTorrentPageUrl(url)
        val request = Request.Builder()
            .url(parsedUrl)
            .header("User-Agent", USER_AGENT_PUBLIC)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for ${parsedUrl.redactedForError()}")
            }
            return response.body?.string()
                ?: throw IOException("Empty response body for ${parsedUrl.redactedForError()}")
        }
    }

    private suspend fun executeSeriesCatalogSearch(pageNumber: Int): String {
        val offset = ((pageNumber.coerceAtLeast(1) - 1) * SERIES_CATALOG_PAGE_SIZE).coerceAtLeast(0)
        val ajaxUrl = requireLostFilmUrl("$BASE_URL/ajaxik.php")
        val cookieHeader = sessionStore?.read()?.toCookieString()?.takeIf { it.isNotBlank() }
        val requestBuilder = Request.Builder()
            .url(ajaxUrl)
            .header("User-Agent", USER_AGENT_PUBLIC)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Referer", "$BASE_URL/series/")
            .post(
                FormBody.Builder()
                    .add("act", "serial")
                    .add("type", "search")
                    .add("o", offset.toString())
                    .add("s", "3")
                    .add("t", "0")
                    .build(),
            )

        cookieHeader?.let { requestBuilder.header("Cookie", it) }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for ${ajaxUrl.redactedForError()}")
            }
            return response.body?.string()
                ?: throw IOException("Empty response body for ${ajaxUrl.redactedForError()}")
        }
    }

    companion object {
        const val USER_AGENT_PUBLIC =
            "Mozilla/5.0 (Android TV; LostFilmNewTV) AppleWebKit/537.36 Chrome/132.0.0.0 Safari/537.36"
    }
}

private const val SERIES_CATALOG_PAGE_SIZE = 20

private fun moviesPageUrl(pageNumber: Int): String {
    val offset = ((pageNumber.coerceAtLeast(1) - 1) * 20).coerceAtLeast(0)
    return if (offset == 0) {
        "$BASE_URL/movies/?type=search&s=3&t=0"
    } else {
        "$BASE_URL/movies/?type=search&s=3&t=0&o=$offset"
    }
}

private fun seriesCatalogPageUrl(pageNumber: Int): String {
    val offset = ((pageNumber.coerceAtLeast(1) - 1) * SERIES_CATALOG_PAGE_SIZE).coerceAtLeast(0)
    return if (offset == 0) {
        "$BASE_URL/series/?type=search&s=3&t=0"
    } else {
        "$BASE_URL/series/?type=search&s=3&t=0&o=$offset"
    }
}

// Псевдоним для обратной совместимости мест, где явно использовался
// AuthenticatedLostFilmHttpClient (например, в тестах).
typealias AuthenticatedLostFilmHttpClient = OkHttpLostFilmHttpClient

private val markEpisodeSuccessRegex = Regex(""""result"\s*:\s*"on"""")
private val favoriteToggleResultRegex = Regex(""""result"\s*:\s*"(on|off)"""")

private fun executeFetchSeasonWatchedEpisodeMarks(
    okHttpClient: OkHttpClient,
    refererUrl: String,
    serialId: String,
    cookieHeader: String?,
): String {
    val ajaxUrl = requireLostFilmUrl("$BASE_URL/ajaxik.php")
    val safeRefererUrl = requireLostFilmUrl(refererUrl).toString()
    val requestBuilder = Request.Builder()
        .url(ajaxUrl)
        .header("User-Agent", OkHttpLostFilmHttpClient.USER_AGENT_PUBLIC)
        .header("Accept", "application/json, text/javascript, */*; q=0.01")
        .header("Origin", BASE_URL)
        .header("Referer", safeRefererUrl)
        .header("X-Requested-With", "XMLHttpRequest")
        .post(
            FormBody.Builder()
                .add("act", "serial")
                .add("type", "getmarks")
                .add("id", serialId)
                .build(),
        )

    cookieHeader?.takeIf { it.isNotBlank() }?.let { requestBuilder.header("Cookie", it) }

    okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            throw IOException("HTTP ${response.code} for ${ajaxUrl.redactedForError()} body=$errorBody")
        }
        return response.body?.string()
            ?: throw IOException("Empty response body for ${ajaxUrl.redactedForError()}")
    }
}

private fun executeSetEpisodeWatched(
    okHttpClient: OkHttpClient,
    refererUrl: String,
    playEpisodeId: String,
    ajaxSessionToken: String,
    targetWatched: Boolean,
    cookieHeader: String?,
): Boolean {
    val ajaxUrl = requireLostFilmUrl("$BASE_URL/ajaxik.php")
    val safeRefererUrl = requireLostFilmUrl(refererUrl).toString()
    val requestBuilder = Request.Builder()
        .url(ajaxUrl)
        .header("User-Agent", OkHttpLostFilmHttpClient.USER_AGENT_PUBLIC)
        .header("Accept", "application/json, text/javascript, */*; q=0.01")
        .header("Origin", BASE_URL)
        .header("Referer", safeRefererUrl)
        .header("X-Requested-With", "XMLHttpRequest")
        .post(
            FormBody.Builder()
                .add("act", "serial")
                .add("type", "markepisode")
                .add("val", playEpisodeId)
                .add("auto", "0")
                .add("mode", if (targetWatched) "on" else "off")
                .add("session", ajaxSessionToken)
                .build(),
        )

    cookieHeader?.takeIf { it.isNotBlank() }?.let { requestBuilder.header("Cookie", it) }

    okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            throw IOException("HTTP ${response.code} for ${ajaxUrl.redactedForError()} body=$errorBody")
        }
        val body = response.body?.string()
            ?: throw IOException("Empty response body for ${ajaxUrl.redactedForError()}")
        return markEpisodeSuccessRegex.containsMatchIn(body)
    }
}

private fun executeToggleFavorite(
    okHttpClient: OkHttpClient,
    refererUrl: String,
    favoriteTargetId: Int,
    ajaxSessionToken: String,
    cookieHeader: String?,
): FavoriteToggleNetworkResult {
    val ajaxUrl = requireLostFilmUrl("$BASE_URL/ajaxik.php")
    val safeRefererUrl = requireLostFilmUrl(resolveUrl(refererUrl)).toString()
    val requestBuilder = Request.Builder()
        .url(ajaxUrl)
        .header("User-Agent", OkHttpLostFilmHttpClient.USER_AGENT_PUBLIC)
        .header("Accept", "application/json, text/javascript, */*; q=0.01")
        .header("Origin", BASE_URL)
        .header("Referer", safeRefererUrl)
        .header("X-Requested-With", "XMLHttpRequest")
        .post(
            FormBody.Builder()
                .add("act", "serial")
                .add("type", "follow")
                .add("id", favoriteTargetId.toString())
                .add("session", ajaxSessionToken)
                .build(),
        )

    cookieHeader?.takeIf { it.isNotBlank() }?.let { requestBuilder.header("Cookie", it) }

    okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            throw IOException("HTTP ${response.code} for ${ajaxUrl.redactedForError()} body=$errorBody")
        }
        val body = response.body?.string()
            ?: throw IOException("Empty response body for ${ajaxUrl.redactedForError()}")
        return when (favoriteToggleResultRegex.find(body)?.groupValues?.getOrNull(1)) {
            "on" -> FavoriteToggleNetworkResult.ToggledOn
            "off" -> FavoriteToggleNetworkResult.ToggledOff
            else -> FavoriteToggleNetworkResult.Unknown
        }
    }
}

private fun requireLostFilmUrl(url: String): HttpUrl {
    val parsed = url.toHttpUrlOrNull() ?: throw IOException("Invalid LostFilm URL")
    if (parsed.scheme != "https" || parsed.host !in LOSTFILM_COOKIE_HOSTS) {
        throw IOException("Rejected non-LostFilm URL: ${parsed.redactedForError()}")
    }
    return parsed
}

private fun requireTorrentPageUrl(url: String): HttpUrl {
    val parsed = url.toHttpUrlOrNull() ?: throw IOException("Invalid torrent URL")
    if (parsed.scheme != "https" || parsed.host !in TORRENT_PAGE_HOSTS) {
        throw IOException("Rejected torrent URL: ${parsed.redactedForError()}")
    }
    return parsed
}

private fun HttpUrl.redactedForError(): String = newBuilder()
    .query(null)
    .fragment(null)
    .build()
    .toString()

private val LOSTFILM_COOKIE_HOSTS = setOf("www.lostfilm.today", "lostfilm.today")
private val TORRENT_PAGE_HOSTS = LOSTFILM_COOKIE_HOSTS + setOf("n.tracktor.site")
