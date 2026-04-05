package com.kraat.lostfilmnewtv

import android.app.Application
import androidx.work.WorkManager
import androidx.room.Room
import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.auth.AuthRepository
import com.kraat.lostfilmnewtv.data.auth.EncryptedSessionStore
import com.kraat.lostfilmnewtv.data.db.LostFilmDatabase
import com.kraat.lostfilmnewtv.data.network.AuthBridgeClient
import com.kraat.lostfilmnewtv.data.network.AuthenticatedLostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.OkHttpLostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.TmdbPosterClient
import com.kraat.lostfilmnewtv.data.parser.LostFilmDetailsParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmFavoriteSeriesParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmListParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmSeasonEpisodesParser
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterResolver
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterResolverImpl
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepositoryImpl
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeActionHandler
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeAvailabilityProbe
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeConfig
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeLauncher
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeLinkBuilder
import com.kraat.lostfilmnewtv.tvchannel.AndroidHomeChannelPublisher
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelContentRepository
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundRefreshRunner
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundRefreshRunnerProvider
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPreferences
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.updates.AppUpdateAvailabilityStore
import com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundWorkerProvider
import com.kraat.lostfilmnewtv.updates.AppUpdateCoordinator
import com.kraat.lostfilmnewtv.updates.AppUpdateRepository
import com.kraat.lostfilmnewtv.updates.GitHubReleaseClient
import com.kraat.lostfilmnewtv.updates.ReleaseApkLauncher
import com.kraat.lostfilmnewtv.updates.UpdateHttpClientFactory
import com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundScheduler as QuietAppUpdateBackgroundScheduler
import okhttp3.OkHttpClient

open class LostFilmApplication : Application(), HomeChannelBackgroundRefreshRunnerProvider, AppUpdateBackgroundWorkerProvider {
    val database: LostFilmDatabase by lazy {
        Room.databaseBuilder(
            this,
            LostFilmDatabase::class.java,
            "lostfilm-new-tv.db",
        ).fallbackToDestructiveMigration().build()
    }

    val httpClient: LostFilmHttpClient by lazy {
        OkHttpLostFilmHttpClient()
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    val authBridgeBaseUrl: String by lazy {
        "https://auth.bazuka.pp.ua"
    }

    val authBridgeClient: AuthBridgeClient by lazy {
        AuthBridgeClient(authBridgeBaseUrl, okHttpClient)
    }

    open val appUpdateRepository: AppUpdateRepository by lazy {
        AppUpdateRepository(
            installedVersion = BuildConfig.VERSION_NAME,
            releaseClient = GitHubReleaseClient(UpdateHttpClientFactory.create()),
        )
    }

    val sessionStore: EncryptedSessionStore by lazy {
        EncryptedSessionStore(this)
    }

    val authenticatedHttpClient: LostFilmHttpClient by lazy {
        AuthenticatedLostFilmHttpClient(sessionStore = sessionStore)
    }

    open val authRepository: AuthRepositoryContract by lazy {
        AuthRepository(authBridgeClient, sessionStore)
    }

    open val tmdbPosterClient: TmdbPosterClient by lazy {
        TmdbPosterClient(okHttpClient, BuildConfig.TMDB_API_KEY)
    }

    open val tmdbPosterResolver: TmdbPosterResolver by lazy {
        TmdbPosterResolverImpl(tmdbPosterClient, database.tmdbPosterDao())
    }

    open val repository: LostFilmRepository by lazy {
        LostFilmRepositoryImpl(
            httpClient = authenticatedHttpClient,
            releaseDao = database.releaseDao(),
            listParser = LostFilmListParser(),
            detailsParser = LostFilmDetailsParser(),
            favoriteSeriesParser = LostFilmFavoriteSeriesParser(),
            seasonEpisodesParser = LostFilmSeasonEpisodesParser(),
            tmdbResolver = tmdbPosterResolver,
            hasAuthenticatedSession = {
                val session = sessionStore.read()
                session != null && !session.isExpired()
            },
        )
    }

    open val playbackPreferencesStore: PlaybackPreferencesStore by lazy {
        PlaybackPreferencesStore(this)
    }

    open val appUpdateAvailabilityStore: AppUpdateAvailabilityStore by lazy {
        AppUpdateAvailabilityStore(this)
    }

    override open val appUpdateCoordinator: AppUpdateCoordinator by lazy {
        AppUpdateCoordinator(
            installedVersion = BuildConfig.VERSION_NAME,
            store = appUpdateAvailabilityStore,
            checkForUpdates = appUpdateRepository::checkForUpdate,
        )
    }

    open val homeChannelSyncManager: HomeChannelSyncManager by lazy {
        HomeChannelSyncManager(
            programSource = HomeChannelContentRepository(database.releaseDao(), tmdbPosterResolver),
            preferences = PlaybackStoreHomeChannelPreferences(playbackPreferencesStore),
            publisher = AndroidHomeChannelPublisher(applicationContext),
        )
    }

    override open val homeChannelBackgroundRefreshRunner: HomeChannelBackgroundRefreshRunner by lazy {
        HomeChannelBackgroundRefreshRunner(
            readMode = playbackPreferencesStore::readAndroidTvChannelMode,
            readSession = sessionStore::read,
            isSessionExpired = sessionStore::isExpired,
            refreshFirstPage = { repository.loadPage(pageNumber = 1) },
            syncChannel = homeChannelSyncManager::syncNow,
            readFirstPageFetchedAt = { database.releaseDao().getPageMetadata(pageNumber = 1)?.fetchedAt },
        )
    }

    open val homeChannelBackgroundScheduler: HomeChannelBackgroundScheduler by lazy {
        HomeChannelBackgroundScheduler(
            readMode = playbackPreferencesStore::readAndroidTvChannelMode,
            workManager = WorkManager.getInstance(applicationContext),
        )
    }

    open val appUpdateBackgroundScheduler: QuietAppUpdateBackgroundScheduler by lazy {
        QuietAppUpdateBackgroundScheduler(
            readMode = playbackPreferencesStore::readUpdateCheckMode,
            workManager = WorkManager.getInstance(applicationContext),
        )
    }

    val torrServeConfig: TorrServeConfig by lazy { TorrServeConfig() }
    val torrServeLinkBuilder: TorrServeLinkBuilder by lazy { TorrServeLinkBuilder(torrServeConfig) }
    val torrServeAvailabilityProbe: TorrServeAvailabilityProbe by lazy { TorrServeAvailabilityProbe(applicationContext) }
    val torrServeLauncher: TorrServeLauncher by lazy { TorrServeLauncher() }
    open val releaseApkLauncher: ReleaseApkLauncher by lazy { ReleaseApkLauncher(UpdateHttpClientFactory.create()) }
    open val torrServeActionHandler: TorrServeActionHandler by lazy {
        TorrServeActionHandler(torrServeLinkBuilder, torrServeAvailabilityProbe, torrServeLauncher)
    }
}

private class PlaybackStoreHomeChannelPreferences(
    private val store: PlaybackPreferencesStore,
) : HomeChannelPreferences {
    override fun readMode() = store.readAndroidTvChannelMode()

    override fun readChannelId() = store.readAndroidTvChannelId()

    override fun writeChannelId(channelId: Long) {
        store.writeAndroidTvChannelId(channelId)
    }

    override fun clearChannelId() {
        store.clearAndroidTvChannelId()
    }
}
