package com.kraat.lostfilmnewtv.di

import com.kraat.lostfilmnewtv.data.auth.AuthRepository
import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.auth.EncryptedSessionStore
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.TmdbPosterDao
import com.kraat.lostfilmnewtv.data.network.AuthBridgeClient
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.TmdbPosterClient
import com.kraat.lostfilmnewtv.data.parser.LostFilmDetailsParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmFavoriteSeriesParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmListParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmSeasonEpisodesParser
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterResolver
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterResolverImpl
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        authBridgeClient: AuthBridgeClient,
        sessionStore: EncryptedSessionStore,
    ): AuthRepositoryContract = AuthRepository(authBridgeClient, sessionStore)

    @Provides
    @Singleton
    fun provideTmdbPosterResolver(
        tmdbClient: TmdbPosterClient,
        tmdbDao: TmdbPosterDao,
    ): TmdbPosterResolver = TmdbPosterResolverImpl(tmdbClient, tmdbDao)

    @Provides
    @Singleton
    fun provideLostFilmRepository(
        @AuthenticatedHttpClient httpClient: LostFilmHttpClient,
        releaseDao: ReleaseDao,
        tmdbResolver: TmdbPosterResolver,
        sessionStore: EncryptedSessionStore,
    ): LostFilmRepository = LostFilmRepositoryImpl(
        httpClient = httpClient,
        releaseDao = releaseDao,
        listParser = LostFilmListParser(),
        detailsParser = LostFilmDetailsParser(),
        favoriteSeriesParser = LostFilmFavoriteSeriesParser(),
        seasonEpisodesParser = LostFilmSeasonEpisodesParser(),
        tmdbResolver = tmdbResolver,
        hasAuthenticatedSession = {
            val session = sessionStore.read()
            session != null && !session.isExpired()
        },
    )
}
