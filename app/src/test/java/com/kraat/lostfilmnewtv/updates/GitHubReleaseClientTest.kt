package com.kraat.lostfilmnewtv.updates

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubReleaseClientTest {

    @Test
    fun fetchLatestRelease_returnsVersionAndFirstApkAsset() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "tag_name": "v1.2.3",
                      "name": "Release 1.2.3",
                      "assets": [
                        {
                          "name": "notes.txt",
                          "content_type": "text/plain",
                          "browser_download_url": "https://example.test/notes.txt"
                        },
                        {
                          "name": "lostfilm-tv-1.2.3.apk",
                          "content_type": "application/vnd.android.package-archive",
                          "browser_download_url": "https://example.test/lostfilm-tv-1.2.3.apk"
                        },
                        {
                          "name": "lostfilm-tv-1.2.3-universal.apk",
                          "content_type": "application/octet-stream",
                          "browser_download_url": "https://example.test/lostfilm-tv-1.2.3-universal.apk"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        server.start()
        try {
            val client = GitHubReleaseClient(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )

            val release = client.fetchLatestRelease()

            assertEquals("v1.2.3", release.version)
            assertEquals("https://example.test/lostfilm-tv-1.2.3.apk", release.apkUrl)
        } finally {
            server.shutdown()
        }
    }
}
