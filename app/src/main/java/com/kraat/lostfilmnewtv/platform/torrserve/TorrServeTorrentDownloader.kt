package com.kraat.lostfilmnewtv.platform.torrserve

import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import com.kraat.lostfilmnewtv.data.network.OkHttpLostFilmHttpClient
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

class TorrServeTorrentDownloader(
    okHttpClient: OkHttpClient,
) {
    private val downloadClient = okHttpClient.newBuilder()
        .dns(TracktorDns)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    fun prepare(context: Context, rawUrl: String): String? {
        if (!rawUrl.startsWith("http://", ignoreCase = true) &&
            !rawUrl.startsWith("https://", ignoreCase = true)
        ) {
            return rawUrl
        }

        val request = Request.Builder()
            .url(rawUrl)
            .header("User-Agent", OkHttpLostFilmHttpClient.USER_AGENT_PUBLIC)
            .header("Accept", "application/x-bittorrent,application/octet-stream,*/*")
            .header("Referer", "https://www.lostfilm.today/")
            .header("Cache-Control", "no-cache")
            .build()

        Log.d(TAG, "Downloading torrent file from ${rawUrl.redactedForLog()}")
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Torrent download failed with HTTP ${response.code}")
                throw IOException("HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Empty torrent response")
            val bytes = body.bytes()
            if (!looksLikeTorrent(bytes, body.contentType()?.toString().orEmpty())) {
                Log.w(TAG, "Torrent download did not return a torrent file")
                throw IOException("Response is not a torrent file")
            }

            val torrentsDir = File(context.cacheDir, "torrents").apply { mkdirs() }
            val torrentFile = File(torrentsDir, "${rawUrl.sha256().take(24)}.torrent")
            torrentFile.writeBytes(bytes)

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, torrentFile).toString()
            Log.d(TAG, "Prepared torrent file uri=$uri size=${bytes.size}")
            return uri
        }
    }

    private fun looksLikeTorrent(bytes: ByteArray, contentType: String): Boolean {
        if (bytes.isEmpty()) return false
        if (contentType.contains("application/x-bittorrent", ignoreCase = true)) return true
        if (contentType.contains("text/html", ignoreCase = true)) return false
        return bytes.first() == 'd'.code.toByte()
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun String.redactedForLog(): String = substringBefore("?")

    private companion object {
        const val TAG = "TorrServeDownloader"
    }
}

private object TracktorDns : Dns {
    private const val TRACKTOR_HOST = "n.tracktor.site"
    private const val TRACKTOR_DIRECT_IP = "185.85.123.23"

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.equals(TRACKTOR_HOST, ignoreCase = true)) {
            return listOf(InetAddress.getByName(TRACKTOR_DIRECT_IP)) + Dns.SYSTEM.lookup(hostname)
        }
        return Dns.SYSTEM.lookup(hostname)
    }
}
