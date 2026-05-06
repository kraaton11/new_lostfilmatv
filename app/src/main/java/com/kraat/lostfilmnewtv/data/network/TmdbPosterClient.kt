package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import com.kraat.lostfilmnewtv.data.model.TmdbMediaType
import com.kraat.lostfilmnewtv.data.model.TmdbSearchResult
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/"
private const val POSTER_SIZE = "w780"
private const val BACKDROP_SIZE = "original"

open class TmdbPosterClient(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
) {
    open suspend fun searchByTitle(
        query: String,
        year: Int?,
        type: TmdbMediaType,
    ): List<TmdbSearchResult> = withContext(Dispatchers.IO) {
        val endpoint = when (type) {
            TmdbMediaType.TV -> "/search/tv"
            TmdbMediaType.MOVIE -> "/search/movie"
        }
        val yearParam = when (type) {
            TmdbMediaType.TV -> year?.let { "&first_air_date_year=$it" }.orEmpty()
            TmdbMediaType.MOVIE -> year?.let { "&release_year=$it" }.orEmpty()
        }
        val url = "$TMDB_BASE_URL$endpoint?query=${query.encodeUrl()}&include_adult=true$yearParam"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()

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

        val baseUrl = "$TMDB_BASE_URL$endpoint"
        val russianImages = fetchImages(baseUrl, language = "ru")
        if (russianImages.hasPosterAndBackdrop()) {
            return@withContext russianImages
        }

        val englishImages = if (russianImages.needsEnglishFallback()) {
            fetchImages(baseUrl, language = "en")
        } else {
            null
        }

        mergeImages(russianImages, englishImages)
            ?: fetchImages(baseUrl, language = null)
    }

    private fun fetchImages(baseUrl: String, language: String?): TmdbImageUrls? {
        val url = if (language != null) {
            "$baseUrl?language=$language&include_image_language=$language,null"
        } else {
            baseUrl
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val posterPath = extractBestImagePath(json.optJSONArray("posters"))
            val backdropPath = extractBestImagePath(json.optJSONArray("backdrops"))

            if (posterPath == null && backdropPath == null) return null

            return TmdbImageUrls(
                posterUrl = posterPath?.let { "$TMDB_IMAGE_BASE$POSTER_SIZE$it" }.orEmpty(),
                backdropUrl = backdropPath?.let { "$TMDB_IMAGE_BASE$BACKDROP_SIZE$it" }.orEmpty(),
            )
        }
    }

    private fun extractBestImagePath(array: JSONArray?): String? {
        if (array == null || array.length() == 0) return null
        for (i in 0 until array.length()) {
            val path = array.getJSONObject(i).optString("file_path", "")
            if (path.isNotBlank()) return path
        }
        return null
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

    open suspend fun getEpisodeOverviewRu(
        tmdbId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
    ): String? = withContext(Dispatchers.IO) {
        val url = "$TMDB_BASE_URL/tv/$tmdbId/season/$seasonNumber/episode/$episodeNumber?language=ru-RU"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            JSONObject(body).optString("overview", "")
                .trim()
                .takeIf { it.isNotBlank() }
        }
    }

    open suspend fun getSeriesOverviewRu(tmdbId: Int): String? = withContext(Dispatchers.IO) {
        val url = "$TMDB_BASE_URL/tv/$tmdbId?language=ru-RU"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            JSONObject(body).optString("overview", "")
                .trim()
                .takeIf { it.isNotBlank() }
        }
    }

    open suspend fun getMovieOverviewRu(tmdbId: Int): String? = withContext(Dispatchers.IO) {
        val url = "$TMDB_BASE_URL/movie/$tmdbId?language=ru-RU"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            JSONObject(body).optString("overview", "")
                .trim()
                .takeIf { it.isNotBlank() }
        }
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}

private fun JSONObject.optTmdbRating(): String? {
    val rating = optDouble("vote_average", Double.NaN)
    if (rating.isNaN() || rating <= 0.0) {
        return null
    }
    return "%.1f".format(java.util.Locale.US, rating)
}
