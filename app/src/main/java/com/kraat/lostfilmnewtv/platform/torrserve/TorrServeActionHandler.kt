package com.kraat.lostfilmnewtv.platform.torrserve

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TorrServeActionHandler(
    private val builder: TorrServeSourceBuilder,
    private val probe: TorrServeAvailabilityChecker,
    private val launcher: TorrServeUrlLauncher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val torrentDownloader: TorrServeTorrentDownloader? = null,
) {
    suspend fun open(context: Context, rawUrl: String, title: String, poster: String): TorrServeOpenResult {
        val builtUrl = builder.build(rawUrl) ?: return TorrServeOpenResult.LaunchError
        
        val available = withContext(ioDispatcher) { probe.isAvailable() }
        if (!available) return TorrServeOpenResult.Unavailable

        val preparedUrl = try {
            withContext(ioDispatcher) {
                torrentDownloader?.prepare(context, builtUrl) ?: builtUrl
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not prepare torrent file, falling back to original url", error)
            builtUrl
        }

        val taggedTitle = if (title.isNotBlank()) "[LF] $title" else ""
        val launched = withContext(mainDispatcher) { launcher.launch(context, preparedUrl, taggedTitle, poster) }
        return if (launched) TorrServeOpenResult.Success else TorrServeOpenResult.LaunchError
    }

    private companion object {
        const val TAG = "TorrServeActionHandler"
    }
}
