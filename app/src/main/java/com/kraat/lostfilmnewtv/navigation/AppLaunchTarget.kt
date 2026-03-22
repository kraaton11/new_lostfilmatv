package com.kraat.lostfilmnewtv.navigation

import android.content.Context
import android.content.Intent
import com.kraat.lostfilmnewtv.MainActivity

object AppLaunchTarget {
    private const val EXTRA_DETAILS_URL = "app_launch_target.details_url"

    fun createDetailsIntent(
        context: Context,
        detailsUrl: String,
    ): Intent {
        return Intent(context, MainActivity::class.java).putExtra(EXTRA_DETAILS_URL, detailsUrl)
    }

    fun parseDetailsUrl(intent: Intent?): String? {
        return intent?.getStringExtra(EXTRA_DETAILS_URL)
    }
}
