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

    suspend fun fetchDetails(detailsUrl: String): String

    suspend fun fetchAccountPage(path: String): String

    suspend fun fetchSeasonWatchedEpisodeMarks(refererUrl: String, serialId: String): String

    suspend fun fetchTorrentRedirect(playEpisodeId: String): String

    suspend fun fetchTorrentPage(url: String): String

    suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String, ajaxSessionToken: String): Boolean

    suspend fun toggleFavorite(
        refererUrl: String,
        favoriteTargetId: Int,
        ajaxSessionToken: String,
    ): FavoriteToggleNetworkResult
}

class OkHttpLostFilmHttpClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) : LostFilmHttpClient {
    override suspend fun fetchNewPage(pageNumber: Int): String = withContext(Dispatchers.IO) {
        val url = if (pageNumber <= 1) {
            "$BASE_URL/new/"
        } else {
            "$BASE_URL/new/page_$pageNumber"
        }

        execute(url)
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
            cookieHeader = null,
        )
    }

    override suspend fun fetchTorrentRedirect(playEpisodeId: String): String = withContext(Dispatchers.IO) {
        execute("$BASE_URL/v_search.php?a=$playEpisodeId")
    }

    override suspend fun fetchTorrentPage(url: String): String = withContext(Dispatchers.IO) {
        execute(url)
    }

    override suspend fun markEpisodeWatched(
        detailsUrl: String,
        playEpisodeId: String,
        ajaxSessionToken: String,
    ): Boolean = withContext(Dispatchers.IO) {
        executeMarkEpisodeWatched(
            okHttpClient = okHttpClient,
            refererUrl = resolveUrl(detailsUrl),
            playEpisodeId = playEpisodeId,
            ajaxSessionToken = ajaxSessionToken,
            cookieHeader = null,
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
            cookieHeader = null,
        )
    }

    private fun execute(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }

            return response.body?.string()
                ?: throw IOException("Empty response body for $url")
        }
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Android TV; LostFilmNewTV) AppleWebKit/537.36 Chrome/132.0.0.0 Safari/537.36"
    }
}

class AuthenticatedLostFilmHttpClient(
    private val sessionStore: SessionStore,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) : LostFilmHttpClient {
    override suspend fun fetchNewPage(pageNumber: Int): String = withContext(Dispatchers.IO) {
        val url = if (pageNumber <= 1) "$BASE_URL/new/" else "$BASE_URL/new/page_$pageNumber"
        execute(url)
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
            cookieHeader = sessionStore.read()?.toCookieString(),
        )
    }

    override suspend fun fetchTorrentRedirect(playEpisodeId: String): String = withContext(Dispatchers.IO) {
        execute("$BASE_URL/v_search.php?a=$playEpisodeId")
    }

    override suspend fun fetchTorrentPage(url: String): String = withContext(Dispatchers.IO) {
        execute(url)
    }

    override suspend fun markEpisodeWatched(
        detailsUrl: String,
        playEpisodeId: String,
        ajaxSessionToken: String,
    ): Boolean = withContext(Dispatchers.IO) {
        executeMarkEpisodeWatched(
            okHttpClient = okHttpClient,
            refererUrl = resolveUrl(detailsUrl),
            playEpisodeId = playEpisodeId,
            ajaxSessionToken = ajaxSessionToken,
            cookieHeader = sessionStore.read()?.toCookieString(),
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
            cookieHeader = sessionStore.read()?.toCookieString(),
        )
    }

    private suspend fun execute(url: String): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)

        sessionStore.read()?.toCookieString()?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("Cookie", it)
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }

            return response.body?.string()
                ?: throw IOException("Empty response body for $url")
        }
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Android TV; LostFilmNewTV) AppleWebKit/537.36 Chrome/132.0.0.0 Safari/537.36"
    }
}

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
        .header("User-Agent", "Mozilla/5.0 (Android TV; LostFilmNewTV) AppleWebKit/537.36 Chrome/132.0.0.0 Safari/537.36")
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

private fun executeMarkEpisodeWatched(
    okHttpClient: OkHttpClient,
    refererUrl: String,
    playEpisodeId: String,
    ajaxSessionToken: String,
    cookieHeader: String?,
): Boolean {
    val requestBuilder = Request.Builder()
        .url("$BASE_URL/ajaxik.php")
        .header("User-Agent", "Mozilla/5.0 (Android TV; LostFilmNewTV) AppleWebKit/537.36 Chrome/132.0.0.0 Safari/537.36")
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
                .add("mode", "on")
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
        .header("User-Agent", "Mozilla/5.0 (Android TV; LostFilmNewTV) AppleWebKit/537.36 Chrome/132.0.0.0 Safari/537.36")
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
