package com.kraat.lostfilmnewtv.data.network

import android.util.Log
import com.kraat.lostfilmnewtv.data.model.KinoPoiskFilmDetails
import com.kraat.lostfilmnewtv.data.model.KinoPoiskSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val TAG = "KinoPoiskClient"
private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]+")

/** Типы, которые считаем фильмом. */
val KINOPOISK_FILM_TYPES = setOf("FILM")

/** Типы, которые считаем сериалом. */
val KINOPOISK_SERIES_TYPES = setOf("TV_SERIES", "MINI_SERIES")

open class KinoPoiskClient(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String,
) {
    /**
     * Ищет фильм по ключевому слову.
     *
     * Кинопоиск отдаёт до двадцати films с одинаковым русским названием: фильм,
     * одноимённый сериал, переводы и ремейки. Поэтому отбор обязателен: сначала
     * фильтруем по типу (иначе карточка фильма получала постер и описание
     * сериала), затем при равном типе предпочитаем совпадение английского
     * названия и года. Если подходящих типов нет — возвращаем null, потому что
     * лучше пусто, чем чужой фильм.
     */
    open suspend fun searchByKeyword(
        query: String,
        acceptedTypes: Set<String> = KINOPOISK_FILM_TYPES,
        expectedYear: String? = null,
        expectedNameEn: String? = null,
    ): KinoPoiskSearchResult? = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "${baseUrl.trimEnd('/')}/v2.1/films/search-by-keyword?keyword=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "KP search failed: HTTP ${response.code} for query='$query'")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val films = json.optJSONArray("films")
                if (films == null || films.length() == 0) {
                    Log.d(TAG, "KP search '$query' returned 0 results")
                    return@withContext null
                }

                Log.d(TAG, "KP search '$query' returned ${films.length()} results")

                val candidates = buildList {
                    for (i in 0 until films.length()) {
                        val film = films.getJSONObject(i)
                        if (film.optString("type", "") in acceptedTypes) {
                            add(film)
                        }
                    }
                }
                if (candidates.isEmpty()) {
                    Log.w(
                        TAG,
                        "KP search '$query': ни одного из $acceptedTypes, берём null вместо чужого фильма",
                    )
                    return@withContext null
                }

                val wantedName = expectedNameEn?.let { normalizeForCompare(it) }
                val bestMatch = candidates.firstOrNull { film ->
                    wantedName != null && normalizeForCompare(film.optString("nameEn", "")) == wantedName
                } ?: candidates.firstOrNull { film ->
                    expectedYear != null && film.optString("year", "") == expectedYear
                } ?: candidates.first()

                KinoPoiskSearchResult(
                    filmId = bestMatch.getInt("filmId"),
                    nameRu = bestMatch.optString("nameRu", "").takeIf { it.isNotBlank() },
                    nameEn = bestMatch.optString("nameEn", "").takeIf { it.isNotBlank() },
                    type = bestMatch.optString("type", "").takeIf { it.isNotBlank() },
                    year = bestMatch.optString("year", "").takeIf { it.isNotBlank() },
                    rating = bestMatch.optString("rating", "").takeIf { it.isNotBlank() && it != "null" },
                    posterUrl = bestMatch.optString("posterUrl", "").takeIf { it.isNotBlank() },
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "KP search failed for query='$query': ${e.message}")
            null
        }
    }

    private fun normalizeForCompare(value: String): String =
        value.lowercase().replace(NON_ALPHANUMERIC_REGEX, " ").trim()

    open suspend fun getFilmDetails(filmId: Int): KinoPoiskFilmDetails? = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/v2.2/films/$filmId"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "KP film details failed: HTTP ${response.code} for filmId=$filmId")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)

                KinoPoiskFilmDetails(
                    posterUrl = json.optString("posterUrl", "").takeIf { it.isNotBlank() },
                    coverUrl = json.optString("coverUrl", "").takeIf { it.isNotBlank() },
                    description = json.optString("description", "").takeIf { it.isNotBlank() },
                    shortDescription = json.optString("shortDescription", "").takeIf { it.isNotBlank() },
                    ratingKinopoisk = json.optDouble("ratingKinopoisk", Double.NaN).takeIf { !it.isNaN() },
                    ratingImdb = json.optDouble("ratingImdb", Double.NaN).takeIf { !it.isNaN() },
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "KP film details failed for filmId=$filmId: ${e.message}")
            null
        }
    }

    open suspend fun getEpisodeSynopsis(
        filmId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/v2.2/films/$filmId/seasons"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "KP seasons failed: HTTP ${response.code} for filmId=$filmId")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: return@withContext null

                for (i in 0 until items.length()) {
                    val season = items.getJSONObject(i)
                    if (season.optInt("number", -1) != seasonNumber) continue
                    val episodes = season.optJSONArray("episodes") ?: continue
                    for (j in 0 until episodes.length()) {
                        val episode = episodes.getJSONObject(j)
                        if (episode.optInt("episodeNumber", -1) == episodeNumber) {
                            val synopsis = episode.optString("synopsis", "")
                                .trim()
                                .takeIf { it.isNotBlank() }
                            if (synopsis != null) {
                                Log.d(TAG, "KP synopsis found for filmId=$filmId S${seasonNumber}E$episodeNumber")
                            }
                            return@withContext synopsis
                        }
                    }
                }
                Log.d(TAG, "KP episode not found: filmId=$filmId S${seasonNumber}E$episodeNumber")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "KP episode synopsis failed for filmId=$filmId S${seasonNumber}E$episodeNumber: ${e.message}")
            null
        }
    }
}
