package com.kraat.lostfilmnewtv.di

import android.content.Context
import com.kraat.lostfilmnewtv.data.auth.AuthRepository
import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.auth.EncryptedSessionStore
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.network.LostFilmLoginClient
import com.kraat.lostfilmnewtv.data.network.LostFilmSessionVerifier
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.parser.LostFilmDetailsParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmFavoriteSeriesParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmListParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmSearchParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmScheduleParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmSeasonEpisodesParser
import com.kraat.lostfilmnewtv.data.repository.FavoritesRepository
import com.kraat.lostfilmnewtv.data.repository.FavoritesRepositoryImpl
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepositoryImpl
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        sessionStore: EncryptedSessionStore,
        sessionVerifier: LostFilmSessionVerifier,
        loginClient: LostFilmLoginClient,
        favoritesRepository: FavoritesRepository,
    ): AuthRepositoryContract =
        AuthRepository(
            null, sessionStore, sessionVerifier, loginClient = loginClient, favoritesRepository = favoritesRepository,
        )

    @Provides
    @Singleton
    fun provideLostFilmSessionVerifier(okHttpClient: OkHttpClient): LostFilmSessionVerifier =
        LostFilmSessionVerifier(okHttpClient)

    @Provides
    @Singleton
    fun provideFavoritesRepository(
        @ApplicationContext context: Context,
        @AuthenticatedHttpClient httpClient: LostFilmHttpClient,
        releaseDao: ReleaseDao,
        sessionStore: EncryptedSessionStore,
    ): FavoritesRepository = FavoritesRepositoryImpl(
        httpClient = httpClient,
        releaseDao = releaseDao,
        cachePreferences = context.getSharedPreferences("lostfilm_favorite_cache", Context.MODE_PRIVATE),
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
        favoritesRepository = favoritesRepository,
        hasAuthenticatedSession = {
            val session = sessionStore.read()
            session != null && !sessionStore.isExpired()
        },
    )
}
