package com.kraat.lostfilmnewtv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeChannelRefreshBootstrapReceiver : BroadcastReceiver() {

    @Inject
    lateinit var homeChannelBackgroundScheduler: HomeChannelBackgroundScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        homeChannelBackgroundScheduler.syncForCurrentMode()
    }
}
