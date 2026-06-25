package com.kraat.lostfilmnewtv.di

import com.kraat.lostfilmnewtv.data.auth.AuthRepository
import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.auth.EncryptedSessionStore
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.TmdbPosterDao
import com.kraat.lostfilmnewtv.data.network.AuthBridgeClient
import com.kraat.lostfilmnewtv.data.network.KinoPoiskClient
import com.kraat.lostfilmnewtv.data.network.LostFilmSessionVerifier
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.TmdbPosterClient
import com.kraat.lostfilmnewtv.data.parser.LostFilmDetailsParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmFavoriteSeriesParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmListParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmSearchParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmScheduleParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmSeasonEpisodesParser
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterResolver
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterResolverImpl
import com.kraat.lostfilmnewtv.data.poster.TmdbEnrichmentService
import com.kraat.lostfilmnewtv.data.poster.TmdbEnrichmentServiceImpl
import com.kraat.lostfilmnewtv.data.repository.FavoritesRepository
import com.kraat.lostfilmnewtv.data.repository.FavoritesRepositoryImpl
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepositoryImpl
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        authBridgeClient: AuthBridgeClient,
        sessionStore: EncryptedSessionStore,
        sessionVerifier: LostFilmSessionVerifier,
    ): AuthRepositoryContract = AuthRepository(authBridgeClient, sessionStore, sessionVerifier)

    @Provides
    @Singleton
    fun provideLostFilmSessionVerifier(okHttpClient: OkHttpClient): LostFilmSessionVerifier =
        LostFilmSessionVerifier(okHttpClient)

    @Provides
    @Singleton
    fun provideTmdbPosterResolver(
        tmdbClient: TmdbPosterClient,
        tmdbDao: TmdbPosterDao,
        kinoPoiskClient: KinoPoiskClient,
    ): TmdbPosterResolver = TmdbPosterResolverImpl(tmdbClient, tmdbDao, kinoPoiskClient)

    @Provides
    @Singleton
    fun provideTmdbEnrichmentService(
        tmdbResolver: TmdbPosterResolver,
        releaseDao: ReleaseDao,
    ): TmdbEnrichmentService = TmdbEnrichmentServiceImpl(tmdbResolver, releaseDao)

    @Provides
    @Singleton
    fun provideFavoritesRepository(
        @AuthenticatedHttpClient httpClient: LostFilmHttpClient,
        releaseDao: ReleaseDao,
        tmdbEnrichmentService: TmdbEnrichmentService,
        sessionStore: EncryptedSessionStore,
    ): FavoritesRepository = FavoritesRepositoryImpl(
        httpClient = httpClient,
        releaseDao = releaseDao,
        tmdbEnrichmentService = tmdbEnrichmentService,
        hasAuthenticatedSession = {
            val session = sessionStore.read()
            session != null && !sessionStore.isExpired()
        },
    )

    @Provides
    @Singleton
    fun provideLostFilmRepository(
        @AuthenticatedHttpClient httpClient: LostFilmHttpClient,
        @AnonymousHttpClient anonymousHttpClient: LostFilmHttpClient,
        releaseDao: ReleaseDao,
        tmdbResolver: TmdbPosterResolver,
        tmdbEnrichmentService: TmdbEnrichmentService,
        favoritesRepository: Lazy<FavoritesRepository>,
        sessionStore: EncryptedSessionStore,
    ): LostFilmRepository = LostFilmRepositoryImpl(
        httpClient = httpClient,
        anonymousHttpClient = anonymousHttpClient,
        releaseDao = releaseDao,
        listParser = LostFilmListParser(),
        detailsParser = LostFilmDetailsParser(),
        favoriteSeriesParser = LostFilmFavoriteSeriesParser(),
        seasonEpisodesParser = LostFilmSeasonEpisodesParser(),
        searchParser = LostFilmSearchParser(),
        scheduleParser = LostFilmScheduleParser(),
        tmdbResolver = tmdbResolver,
        tmdbEnrichmentService = tmdbEnrichmentService,
        favoritesRepository = favoritesRepository,
        hasAuthenticatedSession = {
            val session = sessionStore.read()
            session != null && !sessionStore.isExpired()
        },
    )
}
