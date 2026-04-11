package com.kraat.lostfilmnewtv.di

import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPreferences

internal class PlaybackStoreHomeChannelPreferences(
    private val store: PlaybackPreferencesStore,
) : HomeChannelPreferences {
    override fun readMode() = store.readAndroidTvChannelMode()
    override fun readChannelId() = store.readAndroidTvChannelId()
    override fun writeChannelId(channelId: Long) = store.writeAndroidTvChannelId(channelId)
    override fun clearChannelId() = store.clearAndroidTvChannelId()
}
