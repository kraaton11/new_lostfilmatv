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
                TmdbSearchResult(
                    id = item.getInt("id"),
                    name = name.ifBlank { item.optString("original_name", item.optString("original_title", "")) },
                    popularity = item.optDouble("popularity", 0.0),
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

        fetchImages("$TMDB_BASE_URL$endpoint", language = "ru")
            ?: fetchImages("$TMDB_BASE_URL$endpoint", language = null)
    }

    private fun fetchImages(baseUrl: String, language: String?): TmdbImageUrls? {
        val url = if (language != null) "$baseUrl?language=$language" else baseUrl

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

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
