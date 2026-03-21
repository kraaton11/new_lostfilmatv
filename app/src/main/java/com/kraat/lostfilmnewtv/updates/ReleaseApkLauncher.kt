package com.kraat.lostfilmnewtv.updates

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReleaseApkLauncher(
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    suspend fun launch(context: Context, apkUrl: String): Boolean = withContext(mainDispatcher) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
