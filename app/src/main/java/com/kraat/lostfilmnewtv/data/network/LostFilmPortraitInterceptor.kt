package com.kraat.lostfilmnewtv.data.network

import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.kraat.lostfilmnewtv.data.parser.lostFilmPortraitUrl

internal class LostFilmPortraitInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val originalRequest = chain.request
        val portraitUrl = (originalRequest.data as? String)?.let { lostFilmPortraitUrl(it) }
            ?: return chain.proceed(originalRequest)

        val portraitRequest = originalRequest.newBuilder().data(portraitUrl).build()
        val result = chain.proceed(portraitRequest)
        return if (result is SuccessResult) result else chain.proceed(originalRequest)
    }
}
