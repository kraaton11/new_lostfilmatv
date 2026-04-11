package com.kraat.lostfilmnewtv.di

import com.kraat.lostfilmnewtv.BuildConfig
import com.kraat.lostfilmnewtv.data.auth.EncryptedSessionStore
import com.kraat.lostfilmnewtv.data.network.AuthBridgeClient
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.OkHttpLostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.TmdbPosterClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
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

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    @AnonymousHttpClient
    fun provideAnonymousLostFilmHttpClient(): LostFilmHttpClient =
        OkHttpLostFilmHttpClient(sessionStore = null)

    @Provides
    @Singleton
    @AuthenticatedHttpClient
    fun provideAuthenticatedLostFilmHttpClient(
        sessionStore: EncryptedSessionStore,
    ): LostFilmHttpClient = OkHttpLostFilmHttpClient(sessionStore = sessionStore)

    @Provides
    @Singleton
    fun provideAuthBridgeClient(okHttpClient: OkHttpClient): AuthBridgeClient =
        AuthBridgeClient(AUTH_BRIDGE_BASE_URL, okHttpClient)

    @Provides
    @Singleton
    fun provideTmdbPosterClient(okHttpClient: OkHttpClient): TmdbPosterClient =
        TmdbPosterClient(okHttpClient, BuildConfig.TMDB_API_KEY)

    private const val AUTH_BRIDGE_BASE_URL = "https://auth.bazuka.pp.ua"
}
