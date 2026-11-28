package com.kraat.lostfilmnewtv.data.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import okhttp3.CacheControl
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class LostFilmMirrorChecker @Inject constructor(okHttpClient: OkHttpClient) {
    private val client = okHttpClient.newBuilder()
        .cache(null)
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    fun isAvailable(baseUrl: String): Boolean {
        val url = "${baseUrl.trimEnd('/')}/new/".toHttpUrlOrNull() ?: return false
        val request = Request.Builder().url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("User-Agent", OkHttpLostFilmHttpClient.USER_AGENT_PUBLIC)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful && response.body?.string()?.let(::isLostFilmReleasePage) == true
            }
        } catch (_: IOException) {
            false
        }
    }
}

internal fun isLostFilmReleasePage(html: String): Boolean =
    Jsoup.parse(html).selectFirst(".serials-list .row") != null
