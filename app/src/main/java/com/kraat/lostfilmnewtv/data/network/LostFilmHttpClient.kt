package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.parser.BASE_URL
import com.kraat.lostfilmnewtv.data.parser.resolveUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface LostFilmHttpClient {
    suspend fun fetchNewPage(pageNumber: Int): String

    suspend fun fetchDetails(detailsUrl: String): String
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
