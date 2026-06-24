package com.kraat.lostfilmnewtv.di

import android.content.Context
import com.kraat.lostfilmnewtv.data.auth.EncryptedSessionStore
import com.kraat.lostfilmnewtv.data.network.AuthBridgeClient
import com.kraat.lostfilmnewtv.data.network.LostFilmConcurrencyLimits.LOSTFILM_MAX_CONCURRENT_REQUESTS
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.OkHttpLostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.TmdbPosterClient
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnonymousHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Shared, cache-less OkHttp client. Holds the connection pool / dispatcher that every other
     * client below reuses via [OkHttpClient.newBuilder] (cheap: TCP connections and the
     * thread pool are shared, only the small per-client config differs).
     *
     * IMPORTANT: this client has no HTTP cache. OkHttp's disk cache keys responses by URL only,
     * it does not take the Cookie header into account. If an authenticated (cookie-bearing)
     * client shared a cache with the anonymous client, a page fetched once for one session could
     * be served from disk to a different session — e.g. another LostFilm account, or an
     * unauthenticated request — returning stale/wrong personalized data. So instead of one
     * shared cache, each client below that actually wants caching gets its own dedicated
     * [Cache] instance.
     *
     * The dispatcher's [Dispatcher.maxRequestsPerHost] is the single, transport-level place that
     * caps how many requests this app can have in flight against lostfilm.today at once. Repository
     * code used to additionally hand-roll its own `Semaphore(4)` / `Semaphore(6)` per call site to
     * avoid flooding the site — those still exist (they bound *logical* work, e.g. how many
     * favorite series are processed concurrently), but this dispatcher limit is what actually
     * guarantees the app never exceeds [LOSTFILM_MAX_CONCURRENT_REQUESTS] simultaneous HTTP
     * requests against the site, app-wide, regardless of which code path issues them.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequestsPerHost = LOSTFILM_MAX_CONCURRENT_REQUESTS
        }
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @AnonymousHttpClient
    fun provideAnonymousLostFilmHttpClient(
        okHttpClient: OkHttpClient,
        @ApplicationContext context: Context,
    ): LostFilmHttpClient {
        // Anonymous pages (new releases, movies, series catalog) are not account-specific, so
        // caching them on disk is safe and saves a full reparse on quick repeat navigation.
        val cachedClient = okHttpClient.newBuilder()
            .cache(Cache(File(context.cacheDir, "okhttp_cache_anonymous"), 10L * 1024 * 1024))
            .build()
        return OkHttpLostFilmHttpClient(sessionStore = null, okHttpClient = cachedClient)
    }

    @Provides
    @Singleton
    @AuthenticatedHttpClient
    fun provideAuthenticatedLostFilmHttpClient(
        sessionStore: EncryptedSessionStore,
        okHttpClient: OkHttpClient,
    ): LostFilmHttpClient =
        // Deliberately uncached: every authenticated response depends on the session cookie
        // (favorites, watched marks, account pages), and OkHttp's cache key ignores cookies.
        // Reusing the anonymous cache here previously risked serving one account's cached page
        // to another session. Connection pool is still shared via okHttpClient.
        OkHttpLostFilmHttpClient(sessionStore = sessionStore, okHttpClient = okHttpClient)

    @Provides
    @Singleton
    fun provideAuthBridgeClient(okHttpClient: OkHttpClient): AuthBridgeClient =
        AuthBridgeClient(AUTH_BRIDGE_BASE_URL, okHttpClient)

    @Provides
    @Singleton
    fun provideTmdbPosterClient(
        okHttpClient: OkHttpClient,
        authBridgeClient: AuthBridgeClient,
        @ApplicationContext context: Context,
    ): TmdbPosterClient {
        // TMDB lookups are not account-specific either; give them their own small cache so they
        // don't compete for space with (or get evicted by) the LostFilm page cache.
        val cachedClient = okHttpClient.newBuilder()
            .cache(Cache(File(context.cacheDir, "okhttp_cache_tmdb"), 10L * 1024 * 1024))
            .build()
        return TmdbPosterClient(
            okHttpClient = cachedClient,
            apiKey = "",
            bearerToken = "",
            englishToRussianTranslator = authBridgeClient::translateEnglishToRussian,
            baseUrl = "$AUTH_BRIDGE_BASE_URL/api/tmdb",
        )
    }

    private const val AUTH_BRIDGE_BASE_URL = "https://auth.bazuka.pp.ua"
}
