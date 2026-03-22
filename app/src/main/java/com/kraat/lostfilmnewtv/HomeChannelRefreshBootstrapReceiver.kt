package com.kraat.lostfilmnewtv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HomeChannelRefreshBootstrapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val application = context.applicationContext as? LostFilmApplication ?: return
        application.homeChannelBackgroundScheduler.syncForCurrentMode()
        application.homeChannelBackgroundScheduler.requestImmediateRefresh()
    }
}
