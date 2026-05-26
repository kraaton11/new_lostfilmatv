package com.kraat.lostfilmnewtv.data.network

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class ProwlarrSearchResult(
    val title: String,
    val indexer: String?,
    val sourceUrl: String,
    val sizeBytes: Long?,
    val seeders: Int?,
    val leechers: Int?,
)

fun normalizeProwlarrBaseUrl(raw: String): String? {
    val parsed = raw.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
    if (parsed.scheme != "http" && parsed.scheme != "https") return null
    return parsed.toString().trimEnd('/')
}

class ProwlarrClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): List<ProwlarrSearchResult> = withContext(Dispatchers.IO) {
        val normalizedBaseUrl = normalizeProwlarrBaseUrl(baseUrl)
            ?: throw IOException("Invalid Prowlarr URL")
        val url = "$normalizedBaseUrl/api/v1/search".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("query", query)
            ?.addQueryParameter("type", "search")
            ?.build()
            ?: throw IOException("Invalid Prowlarr search URL")
        val request = Request.Builder()
            .url(url)
            .header("X-Api-Key", apiKey)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Prowlarr search failed: HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty Prowlarr response")
            json.decodeFromString<List<ProwlarrSearchResultDto>>(body)
                .mapNotNull { it.toModel() }
        }
    }

    @Serializable
    private data class ProwlarrSearchResultDto(
        val title: String = "",
        val indexer: String? = null,
        val size: Long? = null,
        val seeders: Int? = null,
        val leechers: Int? = null,
        @SerialName("magnetUrl")
        val magnetUrl: String? = null,
        @SerialName("downloadUrl")
        val downloadUrl: String? = null,
    ) {
        fun toModel(): ProwlarrSearchResult? {
            val sourceUrl = magnetUrl?.takeIf { it.isNotBlank() }
                ?: downloadUrl?.takeIf { it.isNotBlank() }
                ?: return null
            return ProwlarrSearchResult(
                title = title,
                indexer = indexer,
                sourceUrl = sourceUrl,
                sizeBytes = size,
                seeders = seeders,
                leechers = leechers,
            )
        }
    }
}

class ProwlarrClientFactory(
    private val httpClient: OkHttpClient,
) {
    fun create(baseUrl: String, apiKey: String): ProwlarrClient =
        ProwlarrClient(httpClient = httpClient, baseUrl = baseUrl, apiKey = apiKey)
}
