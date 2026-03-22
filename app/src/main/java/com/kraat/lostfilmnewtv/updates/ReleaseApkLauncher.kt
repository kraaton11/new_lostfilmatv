package com.kraat.lostfilmnewtv.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Скачивает APK по HTTPS во внутренний кэш и открывает системный установщик (один тап в UI).
 */
open class ReleaseApkLauncher(
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    open suspend fun launch(
        context: Context,
        apkUrl: String,
        onDownloadingChange: (Boolean) -> Unit = {},
    ): Boolean {
        val parsed = apkUrl.toHttpUrlOrNull() ?: return false
        if (!isAllowedDirectDownloadUrl(parsed)) {
            return false
        }

        return try {
            withContext(mainDispatcher) {
                onDownloadingChange(true)
            }
            val apkFile = withContext(ioDispatcher) {
                downloadApkToCache(context, apkUrl)
            }
            withContext(mainDispatcher) {
                onDownloadingChange(false)
                startPackageInstaller(context, apkFile)
            }
        } catch (_: IOException) {
            withContext(mainDispatcher) {
                onDownloadingChange(false)
            }
            false
        } catch (_: ActivityNotFoundException) {
            withContext(mainDispatcher) {
                onDownloadingChange(false)
            }
            false
        } catch (_: SecurityException) {
            withContext(mainDispatcher) {
                onDownloadingChange(false)
            }
            false
        }
    }

    private fun downloadApkToCache(context: Context, apkUrl: String): File {
        val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
        val target = File(dir, APK_FILE_NAME)
        val request = Request.Builder()
            .url(apkUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty body")
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return target
    }

    internal fun isAllowedDirectDownloadUrl(url: HttpUrl): Boolean {
        return when (url.scheme) {
            "https" -> true
            "http" -> url.host == "127.0.0.1" || url.host.equals("localhost", ignoreCase = true)
            else -> false
        }
    }

    protected open fun startPackageInstaller(context: Context, apkFile: File): Boolean {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
        return true
    }

    private companion object {
        const val CACHE_SUBDIR = "updates"
        const val APK_FILE_NAME = "app-update.apk"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val USER_AGENT = "LostFilmNewTV-Update/1 (OkHttp)"
    }
}
