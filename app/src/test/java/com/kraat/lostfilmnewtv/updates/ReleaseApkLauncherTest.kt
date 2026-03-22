package com.kraat.lostfilmnewtv.updates

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.Mockito.mock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReleaseApkLauncherTest {

    @Test
    fun launch_downloadsApkAndStartsInstallIntent() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("fake-apk-payload"))
        server.start()
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = RecordingContext(base)
        val launcher = TestReleaseApkLauncher(
            httpClient = OkHttpClient(),
        )

        val result = launcher.launch(context, server.url("/app.apk").toString())

        assertTrue(result)
        assertEquals(Intent.ACTION_VIEW, context.startedIntent?.action)
        assertEquals("application/vnd.android.package-archive", context.startedIntent?.type)
        assertEquals("content", context.startedIntent?.data?.scheme)
        server.shutdown()
    }

    @Test
    fun launch_returnsFalse_whenHttpError() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = RecordingContext(base)
        val launcher = ReleaseApkLauncher(
            httpClient = OkHttpClient(),
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
        )

        val result = launcher.launch(context, server.url("/missing.apk").toString())

        assertFalse(result)
        server.shutdown()
    }

    @Test
    fun launch_returnsFalse_whenUrlIsNotHttps() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = RecordingContext(base)
        val launcher = ReleaseApkLauncher(
            httpClient = OkHttpClient(),
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
        )

        val result = launcher.launch(context, "http://example.test/app.apk")

        assertFalse(result)
        assertEquals(null, context.startedIntent)
    }
}

/**
 * Robolectric не подставляет meta-data FileProvider; проверяем скачивание и intent без реального FileProvider.
 */
private class TestReleaseApkLauncher(
    httpClient: OkHttpClient,
) : ReleaseApkLauncher(httpClient, Dispatchers.Unconfined, Dispatchers.Unconfined) {
    override fun startPackageInstaller(context: Context, apkFile: File): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse("content://${context.packageName}.fileprovider/updates/app-update.apk"),
                "application/vnd.android.package-archive",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
        return true
    }
}

private class RecordingContext(base: Context) : ContextWrapper(base) {
    private val packageManager = mock(PackageManager::class.java)
    var startedIntent: Intent? = null

    override fun getPackageManager(): PackageManager = packageManager

    override fun startActivity(intent: Intent?) {
        startedIntent = intent
    }
}
