package com.kraat.lostfilmnewtv.platform.torrserve

import android.content.ComponentName
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
        const val TORRENT_MIME_TYPE = "application/x-bittorrent"
        private const val TAG = "TorrServeLauncher"
    }

    override suspend fun launch(
        context: Context,
        torrServeUrl: String,
        title: String,
        poster: String,
    ): Boolean = withContext(mainDispatcher) {
        val uri = Uri.parse(torrServeUrl)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(TORRSERVE_PACKAGE, TORRSERVE_PLAY_ACTIVITY)
            `package` = TORRSERVE_PACKAGE
            if (uri.scheme.equals("content", ignoreCase = true)) {
                Log.d(TAG, "Launching TorrServe with torrent content uri")
                setDataAndType(uri, TORRENT_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "torrent", uri)
            } else {
                Log.d(TAG, "Launching TorrServe with ${uri.scheme} uri")
                data = uri
            }
            putExtra("title", title)
            putExtra("poster", poster)
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
