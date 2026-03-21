package com.kraat.lostfilmnewtv

import android.app.Application
import androidx.room.Room
import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.auth.AuthRepository
import com.kraat.lostfilmnewtv.data.auth.EncryptedSessionStore
import com.kraat.lostfilmnewtv.data.db.LostFilmDatabase
import com.kraat.lostfilmnewtv.data.network.AuthBridgeClient
import com.kraat.lostfilmnewtv.data.network.AuthenticatedLostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.OkHttpLostFilmHttpClient
import com.kraat.lostfilmnewtv.data.parser.LostFilmDetailsParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmListParser
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
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPreferences
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.updates.AppUpdateRepository
import com.kraat.lostfilmnewtv.updates.GitHubReleaseClient
import com.kraat.lostfilmnewtv.updates.ReleaseApkLauncher
import okhttp3.OkHttpClient

open class LostFilmApplication : Application() {
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
            releaseClient = GitHubReleaseClient(okHttpClient),
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

    open val repository: LostFilmRepository by lazy {
        LostFilmRepositoryImpl(
            httpClient = authenticatedHttpClient,
            releaseDao = database.releaseDao(),
            listParser = LostFilmListParser(),
            detailsParser = LostFilmDetailsParser(),
            hasAuthenticatedSession = {
                val session = sessionStore.read()
                session != null && !session.isExpired()
            },
        )
    }

    open val playbackPreferencesStore: PlaybackPreferencesStore by lazy {
        PlaybackPreferencesStore(this)
    }

    open val homeChannelSyncManager: HomeChannelSyncManager by lazy {
        HomeChannelSyncManager(
            programSource = HomeChannelContentRepository(database.releaseDao()),
            preferences = PlaybackStoreHomeChannelPreferences(playbackPreferencesStore),
            publisher = AndroidHomeChannelPublisher(applicationContext),
        )
    }

    val torrServeConfig: TorrServeConfig by lazy { TorrServeConfig() }
    val torrServeLinkBuilder: TorrServeLinkBuilder by lazy { TorrServeLinkBuilder(torrServeConfig) }
    val torrServeAvailabilityProbe: TorrServeAvailabilityProbe by lazy { TorrServeAvailabilityProbe(applicationContext) }
    val torrServeLauncher: TorrServeLauncher by lazy { TorrServeLauncher() }
    open val releaseApkLauncher: ReleaseApkLauncher by lazy { ReleaseApkLauncher() }
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
