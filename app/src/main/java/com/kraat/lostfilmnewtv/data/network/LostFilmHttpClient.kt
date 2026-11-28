package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.model.CatalogSort
import com.kraat.lostfilmnewtv.data.model.SeriesStatus
import android.util.Log
import com.kraat.lostfilmnewtv.data.parser.BASE_URL
import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl
import com.kraat.lostfilmnewtv.data.parser.resolveUrl
import com.kraat.lostfilmnewtv.data.auth.SessionStore
import com.kraat.lostfilmnewtv.data.model.FavoriteToggleNetworkResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

private fun schedulePageUrl(baseUrl: String = BASE_URL): String = "$baseUrl/schedule/my_0/type_0"

interface LostFilmHttpClient {
    suspend fun fetchNewPage(pageNumber: Int): String

    suspend fun fetchMoviesPage(pageNumber: Int = 1, sort: CatalogSort = CatalogSort.Added): String =
        fetchDetails(moviesPageUrl(pageNumber, sort))

    suspend fun fetchSeriesCatalogPage(
        pageNumber: Int = 1,
        sort: CatalogSort = CatalogSort.Added,
        status: SeriesStatus = SeriesStatus.All,
    ): String = fetchDetails(seriesCatalogPageUrl(pageNumber, sort, status.queryValue))

    suspend fun fetchFavoriteSeriesCatalogPage(pageNumber: Int, sort: CatalogSort): String =
        fetchAccountPage(seriesCatalogPagePath(pageNumber, sort, SeriesStatus.Favorites.queryValue))

    suspend fun fetchSchedulePage(): String = fetchDetails(schedulePageUrl())

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

    private val mirrorChecker by lazy { LostFilmMirrorChecker(okHttpClient) }

    override suspend fun fetchNewPage(pageNumber: Int): String = withContext(Dispatchers.IO) {
        executeLostFilm(expectReleasePage = pageNumber <= 1) { baseUrl ->
            if (pageNumber <= 1) "$baseUrl/new/" else "$baseUrl/new/page_$pageNumber"
        }
    }

    override suspend fun fetchMoviesPage(pageNumber: Int, sort: CatalogSort): String = withContext(Dispatchers.IO) {
        executeLostFilm { moviesPageUrl(pageNumber, sort, it) }
    }

    override suspend fun fetchSeriesCatalogPage(
        pageNumber: Int,
        sort: CatalogSort,
        status: SeriesStatus,
    ): String = withContext(Dispatchers.IO) {
        executeSeriesCatalogSearch(pageNumber, sort, status.queryValue)
    }

    override suspend fun fetchFavoriteSeriesCatalogPage(pageNumber: Int, sort: CatalogSort): String =
        withContext(Dispatchers.IO) {
            executeSeriesCatalogSearch(pageNumber, sort, SeriesStatus.Favorites.queryValue)
        }

    override suspend fun fetchSchedulePage(): String = withContext(Dispatchers.IO) {
        executeLostFilm(url = ::schedulePageUrl)
    }

    override suspend fun fetchDetails(detailsUrl: String): String = withContext(Dispatchers.IO) {
        executeLostFilm { resolveUrl(detailsUrl, it) }
    }

    override suspend fun fetchAccountPage(path: String): String = withContext(Dispatchers.IO) {
        executeLostFilm(publicPage = false) { resolveUrl(path, it) }
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
        executeLostFilm(publicPage = false) { "$it/v_search.php?a=$playEpisodeId" }
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

    private suspend fun executeLostFilm(
        publicPage: Boolean = true,
        expectReleasePage: Boolean = false,
        url: (String) -> String,
    ): String {
        val cookieHeader = sessionStore?.read()?.toCookieString()?.takeIf { it.isNotBlank() }
        val selection = LostFilmBaseUrl.selection
        val automatic = publicPage && cookieHeader == null && selection.automatic
        return withMirrorRetry(selection, automatic) { baseUrl ->
            val target = requireLostFilmUrl(url(baseUrl), baseUrl)
            val body = executeLostFilmRequest(target, cookieHeader)
            if (automatic && expectReleasePage && !isLostFilmReleasePage(body)) {
                throw IOException("Unexpected release page for ${target.redactedForError()}")
            }
            body
        }
    }

    private suspend fun <T> withMirrorRetry(
        selection: LostFilmBaseUrl.Selection,
        automatic: Boolean,
        request: suspend (String) -> T,
    ): T {
        try {
            return request(selection.activeUrl)
        } catch (failure: IOException) {
            if (!automatic || !failure.allowsMirrorRetry()) throw failure
            val mirror = mirrorCheckMutex.withLock {
                coroutineContext.ensureActive()
                val current = LostFilmBaseUrl.selection
                if (current !== selection) {
                    current.activeUrl.takeIf { current.revision == selection.revision }
                } else {
                    val available = LostFilmBaseUrl.failoverMirrors.firstOrNull {
                        coroutineContext.ensureActive()
                        it != selection.activeUrl && mirrorChecker.isAvailable(it)
                    }
                    coroutineContext.ensureActive()
                    available?.takeIf { LostFilmBaseUrl.switchMirror(selection, it) }
                }
            } ?: throw failure
            coroutineContext.ensureActive()
            return request(mirror)
        }
    }

    private suspend fun executeLostFilmRequest(parsedUrl: HttpUrl, cookieHeader: String?): String {
        val requestBuilder = Request.Builder()
            .url(parsedUrl)
            .header("User-Agent", USER_AGENT_PUBLIC)

        cookieHeader?.let { requestBuilder.header("Cookie", it) }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw LostFilmHttpFailure(response.code, parsedUrl)
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

    private suspend fun executeSeriesCatalogSearch(pageNumber: Int, sort: CatalogSort, status: Int): String =
        LostFilmBaseUrl.selection.let { selection ->
            val automatic = selection.automatic && sessionStore == null && status != SeriesStatus.Favorites.queryValue
            withMirrorRetry(selection, automatic) { baseUrl ->
                executeSeriesCatalogRequest(baseUrl, pageNumber, sort, status)
            }
        }

    private suspend fun executeSeriesCatalogRequest(baseUrl: String, pageNumber: Int, sort: CatalogSort, status: Int): String {
        val offset = ((pageNumber.coerceAtLeast(1) - 1) * SERIES_CATALOG_PAGE_SIZE).coerceAtLeast(0)
        val ajaxUrl = requireLostFilmUrl("$baseUrl/ajaxik.php", baseUrl)
        val cookieHeader = sessionStore?.read()?.toCookieString()?.takeIf { it.isNotBlank() }
        val requestBuilder = Request.Builder()
            .url(ajaxUrl)
            .header("User-Agent", USER_AGENT_PUBLIC)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Referer", "$baseUrl/series/")
            .post(
                FormBody.Builder()
                    .add("act", "serial")
                    .add("type", "search")
                    .add("o", offset.toString())
                    .add("s", sort.queryValue.toString())
                    .add("t", status.toString())
                    .build(),
            )

        cookieHeader?.let { requestBuilder.header("Cookie", it) }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw LostFilmHttpFailure(response.code, ajaxUrl)
            }
            return response.body?.string()
                ?: throw IOException("Empty response body for ${ajaxUrl.redactedForError()}")
        }
    }

    companion object {
        private val mirrorCheckMutex = Mutex()

        const val USER_AGENT_PUBLIC =
            "Mozilla/5.0 (Android TV; LostFilmNewTV) AppleWebKit/537.36 Chrome/132.0.0.0 Safari/537.36"
    }
}

internal const val SERIES_CATALOG_PAGE_SIZE = 20

private fun moviesPageUrl(pageNumber: Int, sort: CatalogSort, baseUrl: String = BASE_URL): String {
    val offset = ((pageNumber.coerceAtLeast(1) - 1) * 20).coerceAtLeast(0)
    return if (offset == 0) {
        "$baseUrl/movies/?type=search&s=${sort.queryValue}&t=0"
    } else {
        "$baseUrl/movies/?type=search&s=${sort.queryValue}&t=0&o=$offset"
    }
}

private fun seriesCatalogPageUrl(pageNumber: Int, sort: CatalogSort, status: Int): String =
    "$BASE_URL${seriesCatalogPagePath(pageNumber, sort, status)}"

private fun seriesCatalogPagePath(pageNumber: Int, sort: CatalogSort, status: Int): String {
    val offset = ((pageNumber.coerceAtLeast(1) - 1) * SERIES_CATALOG_PAGE_SIZE).coerceAtLeast(0)
    return if (offset == 0) {
        "/series/?type=search&s=${sort.queryValue}&t=$status"
    } else {
        "/series/?type=search&s=${sort.queryValue}&t=$status&o=$offset"
    }
}

// Псевдоним для обратной совместимости мест, где явно использовался
// AuthenticatedLostFilmHttpClient (например, в тестах).
typealias AuthenticatedLostFilmHttpClient = OkHttpLostFilmHttpClient

private val markEpisodeResultRegex = Regex(""""result"\s*:\s*"(on|off)"""")
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
        val resultValue = markEpisodeResultRegex.find(body)?.groupValues?.getOrNull(1)
        if (resultValue == null) {
            Log.w("LostFilmHttp", "markEpisode: unexpected response body=${body.take(300)}")
        }
        return if (targetWatched) resultValue == "on" else resultValue == "off"
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

private class LostFilmHttpFailure(val code: Int, url: HttpUrl) :
    IOException("HTTP $code for ${url.redactedForError()}")

private fun IOException.allowsMirrorRetry(): Boolean =
    when (this) {
        is RejectedLostFilmUrl -> false
        is LostFilmHttpFailure -> code == 403 || code == 451 || code in 500..599
        else -> true
    }

private class RejectedLostFilmUrl(message: String) : IOException(message)

private fun requireLostFilmUrl(url: String, baseUrl: String = BASE_URL): HttpUrl {
    val parsed = url.toHttpUrlOrNull() ?: throw RejectedLostFilmUrl("Invalid LostFilm URL")
    if (parsed.scheme != "https" || parsed.host !in lostFilmCookieHosts(baseUrl)) {
        throw RejectedLostFilmUrl("Rejected non-LostFilm URL: ${parsed.redactedForError()}")
    }
    return parsed
}

private fun requireTorrentPageUrl(url: String): HttpUrl {
    val parsed = url.toHttpUrlOrNull() ?: throw IOException("Invalid torrent URL")
    if (parsed.scheme != "https" || parsed.host !in torrentPageHosts()) {
        throw IOException("Rejected torrent URL: ${parsed.redactedForError()}")
    }
    return parsed
}

private fun HttpUrl.redactedForError(): String = newBuilder()
    .query(null)
    .fragment(null)
    .build()
    .toString()

private const val FALLBACK_LOSTFILM_HOST = "www.lostfilm.today"
private const val TORRENT_PAGE_HOST = "n.tracktor.site"

/**
 * Hosts allowed for LostFilm requests, derived from the currently configured base URL
 * (`BASE_URL`). Changing the host in Settings therefore also updates this allowlist.
 */
private fun lostFilmCookieHosts(baseUrl: String = BASE_URL): Set<String> {
    val host = baseUrl.toHttpUrlOrNull()?.host?.takeIf { it.isNotBlank() } ?: FALLBACK_LOSTFILM_HOST
    val bareHost = host.removePrefix("www.")
    return setOf(bareHost, "www.$bareHost")
}

private fun torrentPageHosts(): Set<String> = lostFilmCookieHosts() + TORRENT_PAGE_HOST
