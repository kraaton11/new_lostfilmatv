package com.kraat.lostfilmnewtv.di

import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeActionHandler
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeLinkBuilder
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundScheduler
import com.kraat.lostfilmnewtv.updates.AppUpdateCoordinator
import com.kraat.lostfilmnewtv.updates.AppUpdateRepository
import com.kraat.lostfilmnewtv.updates.ReleaseApkLauncher

object UnitTestAppOverrides {
    var playbackPreferencesStore: PlaybackPreferencesStore? = null
    var torrServeActionHandler: TorrServeActionHandler? = null
    var torrServeLinkBuilder: TorrServeLinkBuilder? = null
    var homeChannelSyncManager: HomeChannelSyncManager? = null
    var homeChannelBackgroundScheduler: HomeChannelBackgroundScheduler? = null
    var appUpdateCoordinator: AppUpdateCoordinator? = null
    var appUpdateBackgroundScheduler: AppUpdateBackgroundScheduler? = null
    var appUpdateRepository: AppUpdateRepository? = null
    var releaseApkLauncher: ReleaseApkLauncher? = null

    fun reset() {
        playbackPreferencesStore = null
        torrServeActionHandler = null
        torrServeLinkBuilder = null
        homeChannelSyncManager = null
        homeChannelBackgroundScheduler = null
        appUpdateCoordinator = null
        appUpdateBackgroundScheduler = null
        appUpdateRepository = null
        releaseApkLauncher = null
    }
}
