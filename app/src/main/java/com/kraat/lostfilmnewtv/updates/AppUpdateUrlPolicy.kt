package com.kraat.lostfilmnewtv.updates

import okhttp3.HttpUrl

internal object AppUpdateUrlPolicy {
    private const val OWNER = "kraaton11"
    private const val REPO = "new_lostfilmatv"
    private val githubDownloadPathPrefix = listOf(OWNER, REPO, "releases", "download")

    fun isAllowedReleaseApkUrl(url: HttpUrl): Boolean {
        if (url.scheme != "https" || url.host != "github.com") {
            return false
        }
        if (!url.encodedPath.endsWith(".apk", ignoreCase = true)) {
            return false
        }
        return url.pathSegments.size >= githubDownloadPathPrefix.size + 2 &&
            url.pathSegments.take(githubDownloadPathPrefix.size) == githubDownloadPathPrefix
    }
}
