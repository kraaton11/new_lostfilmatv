package com.kraat.lostfilmnewtv.data.network

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.Size
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TmdbImageFallbackInterceptorTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun intercept_returnsOriginalResult_whenProxySucceeds() = runTest {
        val interceptor = TmdbImageFallbackInterceptor()
        val originalRequest = ImageRequest.Builder(context)
            .data("https://auth.bazuka.pp.ua/api/tmdb/t/p/w780/poster.jpg")
            .build()
        val successResult = SuccessResult(
            drawable = ColorDrawable(Color.RED),
            request = originalRequest,
            dataSource = coil.decode.DataSource.NETWORK,
        )

        val chain = FakeChain(originalRequest) { req ->
            successResult
        }

        val result = interceptor.intercept(chain)
        assertTrue(result is SuccessResult)
        assertEquals(1, chain.callCount)
    }

    @Test
    fun intercept_fallsBackToDirectTmdb_whenProxyFails() = runTest {
        val interceptor = TmdbImageFallbackInterceptor()
        val originalRequest = ImageRequest.Builder(context)
            .data("https://auth.bazuka.pp.ua/api/tmdb/t/p/w780/poster.jpg")
            .build()

        val requestedUrls = mutableListOf<String>()
        val chain = FakeChain(originalRequest) { req ->
            val url = req.data.toString()
            requestedUrls.add(url)
            if (url.startsWith("https://auth.bazuka.pp.ua/api/tmdb/t/p/")) {
                ErrorResult(
                    drawable = null,
                    request = req,
                    throwable = RuntimeException("502 Bad Gateway"),
                )
            } else {
                SuccessResult(
                    drawable = ColorDrawable(Color.BLUE),
                    request = req,
                    dataSource = coil.decode.DataSource.NETWORK,
                )
            }
        }

        val result = interceptor.intercept(chain)
        assertTrue(result is SuccessResult)
        assertEquals(2, chain.callCount)
        assertEquals(
            listOf(
                "https://auth.bazuka.pp.ua/api/tmdb/t/p/w780/poster.jpg",
                "https://image.tmdb.org/t/p/w780/poster.jpg",
            ),
            requestedUrls,
        )
    }

    @Test
    fun intercept_doesNotFallback_forNonProxyUrls() = runTest {
        val interceptor = TmdbImageFallbackInterceptor()
        val otherRequest = ImageRequest.Builder(context)
            .data("https://kinopoiskapiunofficial.tech/images/poster.jpg")
            .build()

        val chain = FakeChain(otherRequest) { req ->
            ErrorResult(
                drawable = null,
                request = req,
                throwable = RuntimeException("404 Not Found"),
            )
        }

        val result = interceptor.intercept(chain)
        assertTrue(result is ErrorResult)
        assertEquals(1, chain.callCount)
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private class FakeChain(
        override val request: ImageRequest,
        override val size: Size = Size.ORIGINAL,
        private val handler: suspend (ImageRequest) -> ImageResult,
    ) : Interceptor.Chain {
        var callCount = 0

        override suspend fun proceed(request: ImageRequest): ImageResult {
            callCount++
            return handler(request)
        }

        override fun withSize(size: Size): Interceptor.Chain = this

        override fun withRequest(request: ImageRequest): Interceptor.Chain =
            FakeChain(request, size, handler).also { it.callCount = this.callCount }
    }
}
