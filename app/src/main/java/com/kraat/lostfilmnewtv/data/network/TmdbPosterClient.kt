package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import com.kraat.lostfilmnewtv.data.model.TmdbEpisodeOverview
import com.kraat.lostfilmnewtv.data.model.TmdbEpisodeOverviewSource
import com.kraat.lostfilmnewtv.data.model.TmdbMediaType
import com.kraat.lostfilmnewtv.data.model.TmdbSearchResult
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private const val DEFAULT_TMDB_BASE_URL = "https://api.themoviedb.org/3"
private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/"
private const val POSTER_SIZE = "w780"
private const val BACKDROP_SIZE = "w1280"
private const val TAG = "TmdbPosterClient"
private const val TMDB_RATE_LIMIT_MS = 300L
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val OVERVIEW_MAX_ATTEMPTS = 3
private const val OVERVIEW_RETRY_BASE_DELAY_MS = 500L

open class TmdbPosterClient(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    private val bearerToken: String = "",
    private val englishToRussianTranslator: (suspend (String) -> String?)? = null,
    private val baseUrl: String = DEFAULT_TMDB_BASE_URL,
    private val imageBaseUrl: String = TMDB_IMAGE_BASE,
    /**
     * Пауза перед повтором описания после 429. В тестах передаётся заглушка,
     * чтобы не ждать реальное время.
     */
    private val overviewRetryDelayMs: (attempt: Int) -> Long = { attempt ->
        OVERVIEW_RETRY_BASE_DELAY_MS shl attempt
    },
) {
    private val normalizedImageBaseUrl: String =
        if (imageBaseUrl.endsWith("/")) imageBaseUrl else "$imageBaseUrl/"
    open suspend fun searchByTitle(
        query: String,
        year: Int?,
        type: TmdbMediaType,
        page: Int = 1,
        language: String? = null,
    ): List<TmdbSearchResult> = withContext(Dispatchers.IO) {
        val endpoint = when (type) {
            TmdbMediaType.TV -> "/search/tv"
            TmdbMediaType.MOVIE -> "/search/movie"
        }
        val yearParam = when (type) {
            TmdbMediaType.TV -> year?.let { "&first_air_date_year=$it" }.orEmpty()
            TmdbMediaType.MOVIE -> year?.let { "&release_year=$it" }.orEmpty()
        }
        val pageParam = if (page > 1) "&page=$page" else ""
        // Без языка TMDB отдаёт английские названия, и русское имя в выдаче
        // отсутствует: у корейского «хоф» title = Hope, original_title = хоф.
        val languageParam = language?.let { "&language=$it" }.orEmpty()
        val url = "${baseUrl.trimEnd('/')}$endpoint?query=${query.encodeUrl()}&include_adult=true$yearParam$pageParam$languageParam"
            .withTmdbApiKey()

        val request = Request.Builder()
            .url(url)
            .tmdbHeaders()
            .build()

        rateLimit()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("TMDB HTTP ${response.code} for $url")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty TMDB response body")

            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return@use emptyList()
            val totalResults = json.optInt("total_results", 0)
            Log.d("TmdbPosterClient", "Search '$query' returned $totalResults results")
            if (totalResults > 0 && results.length() > 0) {
                val firstItem = results.getJSONObject(0)
                Log.d("TmdbPosterClient", "First result: name='${firstItem.optString("name", firstItem.optString("title", ""))}', original='${firstItem.optString("original_name", firstItem.optString("original_title", ""))}'")
            }

            List(results.length()) { i ->
                val item = results.getJSONObject(i)
                val name = when (type) {
                    TmdbMediaType.TV -> item.optString("name", "")
                    TmdbMediaType.MOVIE -> item.optString("title", "")
                }
                val originalName = when (type) {
                    TmdbMediaType.TV -> item.optString("original_name", "")
                    TmdbMediaType.MOVIE -> item.optString("original_title", "")
                }
                val releaseYear = when (type) {
                    TmdbMediaType.TV -> item.optString("first_air_date", "")
                    TmdbMediaType.MOVIE -> item.optString("release_date", "")
                }.take(4).toIntOrNull()
                TmdbSearchResult(
                    id = item.getInt("id"),
                    name = name.ifBlank { originalName },
                    popularity = item.optDouble("popularity", 0.0),
                    originalName = originalName.ifBlank { name },
                    releaseYear = releaseYear,
                    rating = item.optTmdbRating(),
                )
            }.sortedByDescending { it.popularity }
        }
    }

    open suspend fun getPosterAndBackdrop(
        tmdbId: Int,
        type: TmdbMediaType,
    ): TmdbImageUrls? = withContext(Dispatchers.IO) {
        val endpoint = when (type) {
            TmdbMediaType.TV -> "/tv/$tmdbId/images"
            TmdbMediaType.MOVIE -> "/movie/$tmdbId/images"
        }

        val imagesBaseUrl = "${baseUrl.trimEnd('/')}$endpoint"
        val russianImages = fetchImages(imagesBaseUrl, language = "ru")
        if (russianImages.hasPosterAndBackdrop()) {
            return@withContext russianImages
        }

        val englishImages = if (russianImages.needsEnglishFallback()) {
            fetchImages(imagesBaseUrl, language = "en")
        } else {
            null
        }

        val merged = mergeImages(russianImages, englishImages)
        if (merged != null && merged.posterUrl.isNotBlank()) {
            return@withContext merged
        }
        // Even when ru/en returned a backdrop, they can have zero posters (e.g. Brothers/66515).
        // Fall back to unfiltered images to pick up posters in other languages (tl, etc.).
        val fallback = fetchImages(imagesBaseUrl, language = null)
        return@withContext mergeImages(merged, fallback) ?: fallback ?: merged
    }

    private suspend fun fetchImages(baseUrl: String, language: String?): TmdbImageUrls? {
        val url = if (language != null) {
            "$baseUrl?language=$language&include_image_language=$language,null"
        } else {
            baseUrl
        }

        val request = Request.Builder()
            .url(url.withTmdbApiKey())
            .tmdbHeaders()
            .build()

        rateLimit()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "TMDB image fetch failed: HTTP ${response.code} for $url")
                return null
            }
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val posterPath = extractBestImagePath(json.optJSONArray("posters"), preferredLanguage = language)
            val backdropPath = extractBestImagePath(json.optJSONArray("backdrops"), preferredLanguage = language)

            if (posterPath == null && backdropPath == null) return null

            return TmdbImageUrls(
                posterUrl = posterPath?.let { "$normalizedImageBaseUrl$POSTER_SIZE$it" }.orEmpty(),
                backdropUrl = backdropPath?.let { "$normalizedImageBaseUrl$BACKDROP_SIZE$it" }.orEmpty(),
            )
        }
    }

    private fun extractBestImagePath(array: JSONArray?, preferredLanguage: String?): String? {
        if (array == null || array.length() == 0) return null
        return (0 until array.length())
            .asSequence()
            .map { array.getJSONObject(it) }
            .filter { it.optString("file_path", "").isNotBlank() }
            .maxWithOrNull(
                compareBy<JSONObject> {
                    if (preferredLanguage != null && it.optString("iso_639_1", "") == preferredLanguage) 1 else 0
                }.thenBy { it.optDouble("vote_average", 0.0) }
                    .thenBy { it.optInt("vote_count", 0) }
                    .thenBy { it.optInt("width", 0) * it.optInt("height", 0) },
            )
            ?.optString("file_path", "")
    }

    private fun TmdbImageUrls?.hasPosterAndBackdrop(): Boolean {
        return this != null && posterUrl.isNotBlank() && backdropUrl.isNotBlank()
    }

    private fun TmdbImageUrls?.needsEnglishFallback(): Boolean {
        return this == null || posterUrl.isBlank() || backdropUrl.isBlank()
    }

    private fun mergeImages(primary: TmdbImageUrls?, secondary: TmdbImageUrls?): TmdbImageUrls? {
        val posterUrl = primary?.posterUrl?.ifBlank { secondary?.posterUrl.orEmpty() }
            ?: secondary?.posterUrl.orEmpty()
        val backdropUrl = primary?.backdropUrl?.ifBlank { secondary?.backdropUrl.orEmpty() }
            ?: secondary?.backdropUrl.orEmpty()

        if (posterUrl.isBlank() && backdropUrl.isBlank()) {
            return null
        }

        return TmdbImageUrls(
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
        )
    }

    open suspend fun getEpisodeOverview(
        tmdbId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
    ): TmdbEpisodeOverview? = withContext(Dispatchers.IO) {
        val episodeBaseUrl = "${baseUrl.trimEnd('/')}/tv/$tmdbId/season/$seasonNumber/episode/$episodeNumber"
        fetchOverview("$episodeBaseUrl?language=ru-RU")?.let { russianOverview ->
            TmdbEpisodeOverview(
                text = russianOverview,
                source = TmdbEpisodeOverviewSource.TMDB_RU,
            )
        } ?: fetchOverview("$episodeBaseUrl?language=en-US")?.let { englishOverview ->
            translateEnglishOverview(englishOverview)?.let { translatedOverview ->
                TmdbEpisodeOverview(
                    text = translatedOverview,
                    source = TmdbEpisodeOverviewSource.MACHINE_TRANSLATED,
                )
            } ?: TmdbEpisodeOverview(
                text = englishOverview,
                source = TmdbEpisodeOverviewSource.TMDB_EN,
            )
        }
    }

    private suspend fun translateEnglishOverview(overview: String): String? {
        val translator = englishToRussianTranslator ?: return null
        return runCatching { translator(overview) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * 429 — единственный статус, который означает «спроси позже»: ответ приходит
     * одинаковым с «описания нет», а карточка после неудачи считается полностью
     * обогащённой и больше не обогащается, то есть описание теряется надолго.
     * Поэтому 429 повторяем, а 404 и прочие — нет: там ответа действительно не
     * будет.
     */
    private suspend fun fetchOverview(url: String): String? {
        var attempt = 0
        while (true) {
            val request = Request.Builder()
                .url(url.withTmdbApiKey())
                .tmdbHeaders()
                .build()

            rateLimit()
            val shouldRetry = okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body == null) return null
                    return JSONObject(body).optString("overview", "")
                        .trim()
                        .takeIf { it.isNotBlank() }
                }
                if (response.code != HTTP_TOO_MANY_REQUESTS || attempt + 1 >= OVERVIEW_MAX_ATTEMPTS) {
                    Log.w(TAG, "TMDB overview fetch failed: HTTP ${response.code} for $url")
                    return null
                }
                true
            }

            val delayMs = overviewRetryDelayMs(attempt)
            Log.w(TAG, "TMDB overview HTTP 429 для $url, повтор через ${delayMs}мс")
            delay(delayMs)
            attempt++
        }
    }

    open suspend fun getSeriesOverviewRu(tmdbId: Int): String? = withContext(Dispatchers.IO) {
        fetchOverview("${baseUrl.trimEnd('/')}/tv/$tmdbId?language=ru-RU")
    }

    open suspend fun getMovieOverviewRu(tmdbId: Int): String? = withContext(Dispatchers.IO) {
        fetchOverview("${baseUrl.trimEnd('/')}/movie/$tmdbId?language=ru-RU")
    }

    /**
     * Fetch season-specific poster and backdrop from TMDB.
     * Returns `null` when the season has no images at all.
     */
    open suspend fun getSeasonImages(
        tmdbId: Int,
        seasonNumber: Int,
    ): TmdbImageUrls? = withContext(Dispatchers.IO) {
        val imagesBaseUrl = "${baseUrl.trimEnd('/')}/tv/$tmdbId/season/$seasonNumber/images"
        val russianImages = fetchImages(imagesBaseUrl, language = "ru")
        if (russianImages.hasPosterAndBackdrop()) {
            return@withContext russianImages
        }

        val englishImages = if (russianImages.needsEnglishFallback()) {
            fetchImages(imagesBaseUrl, language = "en")
        } else {
            null
        }

        val merged = mergeImages(russianImages, englishImages)
        if (merged != null && merged.posterUrl.isNotBlank()) {
            return@withContext merged
        }
        val fallback = fetchImages(imagesBaseUrl, language = null)
        return@withContext mergeImages(merged, fallback) ?: fallback ?: merged
    }

    /**
     * Fetch season-specific overview (description) from TMDB in Russian.
     */
    open suspend fun getSeasonOverviewRu(
        tmdbId: Int,
        seasonNumber: Int,
    ): String? = withContext(Dispatchers.IO) {
        fetchOverview("${baseUrl.trimEnd('/')}/tv/$tmdbId/season/$seasonNumber?language=ru-RU")
    }

    open suspend fun getRating(
        tmdbId: Int,
        type: TmdbMediaType,
    ): String? = withContext(Dispatchers.IO) {
        val endpoint = when (type) {
            TmdbMediaType.TV -> "/tv/$tmdbId"
            TmdbMediaType.MOVIE -> "/movie/$tmdbId"
        }
        val url = "${baseUrl.trimEnd('/')}$endpoint".withTmdbApiKey()
        val request = Request.Builder()
            .url(url)
            .tmdbHeaders()
            .build()

        rateLimit()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "TMDB rating fetch failed: HTTP ${response.code} for $endpoint")
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null
            JSONObject(body).optTmdbRating()
        }
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")

    private fun String.withTmdbApiKey(): String {
        if (apiKey.isBlank()) return this
        return toHttpUrl()
            .newBuilder()
            .addQueryParameter("api_key", apiKey)
            .build()
            .toString()
    }

    private fun Request.Builder.tmdbHeaders(): Request.Builder {
        header("Accept", "application/json")
        if (bearerToken.isNotBlank()) {
            header("Authorization", "Bearer $bearerToken")
        }
        return this
    }

    private companion object {
        private val rateLimitMutex = Mutex()
        private var lastRateLimitedCallMs = 0L

        suspend fun rateLimit() {
            rateLimitMutex.withLock {
                val now = System.currentTimeMillis()
                val elapsed = now - lastRateLimitedCallMs
                if (elapsed < TMDB_RATE_LIMIT_MS) {
                    delay(TMDB_RATE_LIMIT_MS - elapsed)
                }
                lastRateLimitedCallMs = System.currentTimeMillis()
            }
        }
    }
}

private fun JSONObject.optTmdbRating(): String? {
    val rating = optDouble("vote_average", Double.NaN)
    if (rating.isNaN() || rating <= 0.0) {
        return null
    }
    return "%.1f".format(java.util.Locale.US, rating)
}
