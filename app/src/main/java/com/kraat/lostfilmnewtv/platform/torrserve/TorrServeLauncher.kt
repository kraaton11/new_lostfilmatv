package com.kraat.lostfilmnewtv.platform.torrserve

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TorrServeLauncher(
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : TorrServeUrlLauncher {
    companion object {
        const val TORRSERVE_PACKAGE = "ru.yourok.torrserve"
        const val TORRSERVE_MAIN_ACTIVITY = "ru.yourok.torrserve.ui.activities.main.MainActivity"
        const val TORRSERVE_PLAY_ACTIVITY = "ru.yourok.torrserve.ui.activities.play.PlayActivity"
    }

    override suspend fun launch(context: Context, torrServeUrl: String): Boolean = withContext(mainDispatcher) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(torrServeUrl)).apply {
            component = ComponentName(TORRSERVE_PACKAGE, TORRSERVE_PLAY_ACTIVITY)
            `package` = TORRSERVE_PACKAGE
        }
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
