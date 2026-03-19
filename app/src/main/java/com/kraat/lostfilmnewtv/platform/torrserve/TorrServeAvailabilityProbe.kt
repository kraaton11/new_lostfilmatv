package com.kraat.lostfilmnewtv.platform.torrserve

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TorrServeAvailabilityProbe(
    private val context: Context,
) : TorrServeAvailabilityChecker {
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).setClassName(
            TorrServeLauncher.TORRSERVE_PACKAGE,
            TorrServeLauncher.TORRSERVE_MAIN_ACTIVITY,
        )
        context.packageManager.resolveActivity(intent, 0) != null
    }
}
