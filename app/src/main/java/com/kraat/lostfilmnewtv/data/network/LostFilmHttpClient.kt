package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.parser.BASE_URL
import com.kraat.lostfilmnewtv.data.parser.resolveUrl
import com.kraat.lostfilmnewtv.data.auth.SessionStore
import com.kraat.lostfilmnewtv.data.model.FavoriteToggleNetworkResult
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) : LostFilmHttpClient {

    override suspend fun fetchNewPage(pageNumber: Int): String = withContext(Dispatchers.IO) {
        val url = if (pageNumber <= 1) "$BASE_URL/new/" else "$BASE_URL/new/page_$pageNumber"
        execute(url)
    }

    override suspend fun fetchMoviesPage(pageNumber: Int): String = withContext(Dispatchers.IO) {
        execute(moviesPageUrl(pageNumber))
    }

    override suspend fun fetchSeriesCatalogPage(pageNumber: Int): String = withContext(Dispatchers.IO) {
        executeSeriesCatalogSearch(pageNumber)
    }

    override suspend fun fetchDetails(detailsUrl: String): String = withContext(Dispatchers.IO) {
        execute(resolveUrl(detailsUrl))
    }

    override suspend fun fetchAccountPage(path: String): String = withContext(Dispatchers.IO) {
        execute(resolveUrl(path))
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
        execute("$BASE_URL/v_search.php?a=$playEpisodeId")
    }

    override suspend fun fetchTorrentPage(url: String): String = withContext(Dispatchers.IO) {
        execute(url)
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

    private suspend fun execute(url: String): String {
        val cookieHeader = sessionStore?.read()?.toCookieString()?.takeIf { it.isNotBlank() }
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT_PUBLIC)

        cookieHeader?.let { requestBuilder.header("Cookie", it) }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response body for $url")
            if (cookieHeader != null && lostFilmResponseLooksAnonymous(body)) {
                sessionStore?.markExpired()
            }
            return body
        }
    }

    private suspend fun executeSeriesCatalogSearch(pageNumber: Int): String {
        val offset = ((pageNumber.coerceAtLeast(1) - 1) * SERIES_CATALOG_PAGE_SIZE).coerceAtLeast(0)
        val cookieHeader = sessionStore?.read()?.toCookieString()?.takeIf { it.isNotBlank() }
        val requestBuilder = Request.Builder()
            .url("$BASE_URL/ajaxik.php")
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
                throw IOException("HTTP ${response.code} for $BASE_URL/ajaxik.php")
            }
            return response.body?.string()
                ?: throw IOException("Empty response body for $BASE_URL/ajaxik.php")
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
    val requestBuilder = Request.Builder()
        .url("$BASE_URL/ajaxik.php")
        .header("User-Agent", OkHttpLostFilmHttpClient.USER_AGENT_PUBLIC)
        .header("Accept", "application/json, text/javascript, */*; q=0.01")
        .header("Origin", BASE_URL)
        .header("Referer", refererUrl)
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
            throw IOException("HTTP ${response.code} for $BASE_URL/ajaxik.php body=$errorBody")
        }
        return response.body?.string()
            ?: throw IOException("Empty response body for $BASE_URL/ajaxik.php")
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
    val requestBuilder = Request.Builder()
        .url("$BASE_URL/ajaxik.php")
        .header("User-Agent", OkHttpLostFilmHttpClient.USER_AGENT_PUBLIC)
        .header("Accept", "application/json, text/javascript, */*; q=0.01")
        .header("Origin", BASE_URL)
        .header("Referer", refererUrl)
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
            throw IOException("HTTP ${response.code} for $BASE_URL/ajaxik.php body=$errorBody")
        }
        val body = response.body?.string()
            ?: throw IOException("Empty response body for $BASE_URL/ajaxik.php")
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
    val requestBuilder = Request.Builder()
        .url("$BASE_URL/ajaxik.php")
        .header("User-Agent", OkHttpLostFilmHttpClient.USER_AGENT_PUBLIC)
        .header("Accept", "application/json, text/javascript, */*; q=0.01")
        .header("Origin", BASE_URL)
        .header("Referer", refererUrl)
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
            throw IOException("HTTP ${response.code} for $BASE_URL/ajaxik.php body=$errorBody")
        }
        val body = response.body?.string()
            ?: throw IOException("Empty response body for $BASE_URL/ajaxik.php")
        return when (favoriteToggleResultRegex.find(body)?.groupValues?.getOrNull(1)) {
            "on" -> FavoriteToggleNetworkResult.ToggledOn
            "off" -> FavoriteToggleNetworkResult.ToggledOff
            else -> FavoriteToggleNetworkResult.Unknown
        }
    }
}
