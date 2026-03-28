package com.kraat.lostfilmnewtv.updates

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class RateLimitException(val statusCode: Int, message: String) : IOException(message)

open class GitHubReleaseClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://api.github.com",
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    open suspend fun fetchLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val url = "$baseUrl/repos/kraaton11/new_lostfilmatv/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            when (response.code) {
                403, 429 -> throw RateLimitException(
                    statusCode = response.code,
                    message = "GitHub API rate limit exceeded. HTTP ${response.code}",
                )
                !in 200..299 -> throw IOException(
                    "Failed to fetch latest release from $url: HTTP ${response.code}",
                )
            }

            val body = response.body?.string() ?: throw IOException("Empty response body for $url")
            val release = json.decodeFromString(GitHubLatestReleaseDto.serializer(), body)
            GitHubRelease(
                version = release.tagName,
                apkUrl = release.assets.firstOrNull { it.isApk() }?.browserDownloadUrl,
            )
        }
    }

    @Serializable
    private data class GitHubLatestReleaseDto(
        @SerialName("tag_name")
        val tagName: String,
        val name: String,
        val assets: List<GitHubReleaseAssetDto> = emptyList(),
    )

    @Serializable
    private data class GitHubReleaseAssetDto(
        val name: String,
        @SerialName("content_type")
        val contentType: String? = null,
        @SerialName("browser_download_url")
        val browserDownloadUrl: String,
    ) {
        fun isApk(): Boolean {
            val normalizedName = name.lowercase()
            val normalizedContentType = contentType.orEmpty().lowercase()
            return normalizedName.endsWith(".apk") ||
                "android.package-archive" in normalizedContentType
        }
    }

    private companion object {
        const val USER_AGENT = "LostFilmNewTV-Update/1.0"
    }
}

data class GitHubRelease(
    val version: String,
    val apkUrl: String?,
)
