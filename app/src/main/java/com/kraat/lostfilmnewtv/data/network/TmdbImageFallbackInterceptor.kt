package com.kraat.lostfilmnewtv.data.network

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult

/**
 * Transparent fallback for Coil image loading:
 * If an image request to the auth_bridge proxy (e.g. auth.bazuka.pp.ua/api/tmdb/t/p/...)
 * fails with an error (e.g. server temporary downtime or 5xx/network error), this
 * interceptor retries the request directly against TMDB's CDN (image.tmdb.org/t/p/...).
 */
class TmdbImageFallbackInterceptor(
    private val proxyPrefix: String = AUTH_BRIDGE_TMDB_IMAGE_PREFIX,
    private val directPrefix: String = TMDB_DIRECT_IMAGE_PREFIX,
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val result = chain.proceed(request)
        if (result is ErrorResult) {
            val url = request.data as? String
            if (url != null && url.startsWith(proxyPrefix)) {
                val fallbackUrl = directPrefix + url.removePrefix(proxyPrefix)
                val fallbackRequest = request.newBuilder()
                    .data(fallbackUrl)
                    .build()
                return chain.proceed(fallbackRequest)
            }
        }
        return result
    }

    companion object {
        const val AUTH_BRIDGE_TMDB_IMAGE_PREFIX = "https://auth.bazuka.pp.ua/api/tmdb/t/p/"
        const val TMDB_DIRECT_IMAGE_PREFIX = "https://image.tmdb.org/t/p/"
    }
}
