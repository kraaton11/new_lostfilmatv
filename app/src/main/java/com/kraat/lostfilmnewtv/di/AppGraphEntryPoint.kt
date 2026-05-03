package com.kraat.lostfilmnewtv.di

import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeActionHandler
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeLinkBuilder
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.updates.ReleaseApkLauncher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppGraphEntryPoint {
    fun playbackPreferencesStore(): PlaybackPreferencesStore
    fun torrServeActionHandler(): TorrServeActionHandler
    fun torrServeLinkBuilder(): TorrServeLinkBuilder
    fun homeChannelSyncManager(): HomeChannelSyncManager
    fun releaseApkLauncher(): ReleaseApkLauncher
}
