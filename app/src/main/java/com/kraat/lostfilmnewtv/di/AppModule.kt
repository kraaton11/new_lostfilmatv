package com.kraat.lostfilmnewtv.di

import android.content.Context
import androidx.work.WorkManager
import com.kraat.lostfilmnewtv.BuildConfig
import com.kraat.lostfilmnewtv.data.auth.EncryptedSessionStore
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterResolver
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeActionHandler
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeAvailabilityProbe
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeConfig
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeLauncher
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeLinkBuilder
import com.kraat.lostfilmnewtv.tvchannel.AndroidHomeChannelPublisher
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundRefreshRunner
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelContentRepository
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.updates.AppUpdateAvailabilityStore
import com.kraat.lostfilmnewtv.updates.AppUpdateCoordinator
import com.kraat.lostfilmnewtv.updates.AppUpdateRepository
import com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundScheduler
import com.kraat.lostfilmnewtv.updates.GitHubReleaseClient
import com.kraat.lostfilmnewtv.updates.ReleaseApkLauncher
import com.kraat.lostfilmnewtv.updates.UpdateHttpClientFactory
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // region Session / Preferences

    @Provides
    @Singleton
    fun provideEncryptedSessionStore(@ApplicationContext context: Context): EncryptedSessionStore =
        EncryptedSessionStore(context)

    @Provides
    @Singleton
    fun providePlaybackPreferencesStore(@ApplicationContext context: Context): PlaybackPreferencesStore =
        PlaybackPreferencesStore(context)

    @Provides
    @Singleton
    fun provideAppUpdateAvailabilityStore(@ApplicationContext context: Context): AppUpdateAvailabilityStore =
        AppUpdateAvailabilityStore(context)

    // endregion

    // region WorkManager

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    // endregion

    // region TorrServe

    @Provides
    @Singleton
    fun provideTorrServeConfig(): TorrServeConfig = TorrServeConfig()

    @Provides
    @Singleton
    fun provideTorrServeLinkBuilder(config: TorrServeConfig): TorrServeLinkBuilder =
        TorrServeLinkBuilder(config)

    @Provides
    @Singleton
    fun provideTorrServeAvailabilityProbe(@ApplicationContext context: Context): TorrServeAvailabilityProbe =
        TorrServeAvailabilityProbe(context)

    @Provides
    @Singleton
    fun provideTorrServeLauncher(): TorrServeLauncher = TorrServeLauncher()

    @Provides
    @Singleton
    fun provideTorrServeActionHandler(
        linkBuilder: TorrServeLinkBuilder,
        probe: TorrServeAvailabilityProbe,
        launcher: TorrServeLauncher,
    ): TorrServeActionHandler = TorrServeActionHandler(linkBuilder, probe, launcher)

    // endregion

    // region Home Channel

    @Provides
    @Singleton
    fun provideHomeChannelContentRepository(
        releaseDao: ReleaseDao,
        tmdbResolver: TmdbPosterResolver,
    ): HomeChannelContentRepository = HomeChannelContentRepository(releaseDao, tmdbResolver)

    @Provides
    @Singleton
    fun provideHomeChannelSyncManager(
        contentRepository: HomeChannelContentRepository,
        playbackPreferencesStore: PlaybackPreferencesStore,
        @ApplicationContext context: Context,
    ): HomeChannelSyncManager = HomeChannelSyncManager(
        programSource = contentRepository,
        preferences = PlaybackStoreHomeChannelPreferences(playbackPreferencesStore),
        publisher = AndroidHomeChannelPublisher(context),
    )

    @Provides
    @Singleton
    fun provideHomeChannelBackgroundScheduler(
        playbackPreferencesStore: PlaybackPreferencesStore,
        workManager: WorkManager,
    ): HomeChannelBackgroundScheduler = HomeChannelBackgroundScheduler(
        readMode = playbackPreferencesStore::readAndroidTvChannelMode,
        workManager = workManager,
    )

    @Provides
    @Singleton
    fun provideHomeChannelBackgroundRefreshRunner(
        playbackPreferencesStore: PlaybackPreferencesStore,
        sessionStore: EncryptedSessionStore,
        repository: LostFilmRepository,
        homeChannelSyncManager: HomeChannelSyncManager,
        releaseDao: ReleaseDao,
    ): HomeChannelBackgroundRefreshRunner = HomeChannelBackgroundRefreshRunner(
        readMode = playbackPreferencesStore::readAndroidTvChannelMode,
        readSession = sessionStore::read,
        isSessionExpired = sessionStore::isExpired,
        refreshFirstPage = { repository.loadPage(pageNumber = 1) },
        syncChannel = homeChannelSyncManager::syncNow,
        readFirstPageFetchedAt = { releaseDao.getPageMetadata(pageNumber = 1)?.fetchedAt },
    )

    // endregion

    // region App Updates

    @Provides
    @Singleton
    fun provideAppUpdateRepository(): AppUpdateRepository = AppUpdateRepository(
        installedVersion = BuildConfig.VERSION_NAME,
        releaseClient = GitHubReleaseClient(UpdateHttpClientFactory.create()),
    )

    @Provides
    @Singleton
    fun provideAppUpdateCoordinator(
        store: AppUpdateAvailabilityStore,
        appUpdateRepository: AppUpdateRepository,
    ): AppUpdateCoordinator = AppUpdateCoordinator(
        installedVersion = BuildConfig.VERSION_NAME,
        store = store,
        checkForUpdates = appUpdateRepository::checkForUpdate,
    )

    @Provides
    @Singleton
    fun provideAppUpdateBackgroundScheduler(
        playbackPreferencesStore: PlaybackPreferencesStore,
        workManager: WorkManager,
    ): AppUpdateBackgroundScheduler = AppUpdateBackgroundScheduler(
        readMode = playbackPreferencesStore::readUpdateCheckMode,
        workManager = workManager,
    )

    @Provides
    @Singleton
    fun provideReleaseApkLauncher(): ReleaseApkLauncher =
        ReleaseApkLauncher(UpdateHttpClientFactory.create())

    // endregion
}
