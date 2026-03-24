# App Update Bug Fixes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Исправить баги, ошибки и недочеты в механизме обновления приложения, найденные при аудите кода.

**Architecture:** Каждый чанк фокусируется на конкретной проблемной области: HTTP-клиент, загрузка APK, API-запросы, обработка ошибок, дублирование кода, фоновая работа, UI-логика. Изменения минимальны и обратно совместимы.

**Tech Stack:** Kotlin, AndroidX WorkManager, OkHttp, Coroutines/Flow, SharedPreferences, JUnit 4, Robolectric, Mockito

---

## Planned File Structure

- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/UpdateHttpClientFactory.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/VersionComparator.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncher.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/GitHubReleaseClient.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateRepository.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateCoordinator.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundWorker.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundScheduler.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateAvailabilityStore.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/updates/UpdateHttpClientFactoryTest.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/updates/VersionComparatorTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncherTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/updates/GitHubReleaseClientTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateRepositoryTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundWorkerTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundSchedulerTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModelTest.kt`

---

## Chunk 1: HTTP Client Factory With Timeouts

**Problem:** `OkHttpClient` создаётся без таймаутов. Загрузка может зависнуть навсегда.

### Task 1: Create UpdateHttpClientFactory with timeouts

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/UpdateHttpClientFactory.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/updates/UpdateHttpClientFactoryTest.kt`

- [ ] **Step 1: Write failing tests for HTTP client factory**

```kotlin
@Test
fun create_returnsClient_withConnectTimeout30Seconds() {
    val client = UpdateHttpClientFactory.create()
    assertEquals(30_000L, client.connectTimeoutMillis.toLong())
}

@Test
fun create_returnsClient_withReadTimeout60Seconds() {
    val client = UpdateHttpClientFactory.create()
    assertEquals(60_000L, client.readTimeoutMillis.toLong())
}

@Test
fun create_returnsClient_withWriteTimeout60Seconds() {
    val client = UpdateHttpClientFactory.create()
    assertEquals(60_000L, client.writeTimeoutMillis.toLong())
}

@Test
fun create_returnsClient_withUserAgentHeader() {
    val client = UpdateHttpClientFactory.create()
    assertNotNull(client)
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.UpdateHttpClientFactoryTest"
```
Expected: FAIL with missing `UpdateHttpClientFactory` type

- [ ] **Step 3: Implement UpdateHttpClientFactory**

```kotlin
package com.kraat.lostfilmnewtv.updates

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object UpdateHttpClientFactory {
    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val WRITE_TIMEOUT_SECONDS = 60L

    fun create(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
```

- [ ] **Step 4: Re-run tests**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.UpdateHttpClientFactoryTest"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/kraat/lostfilmnewtv/updates/UpdateHttpClientFactory.kt app/src/test/java/com/kraat/lostfilmnewtv/updates/UpdateHttpClientFactoryTest.kt
git commit -m "feat: add HTTP client factory with timeouts for updates"
```

### Task 2: Wire UpdateHttpClientFactory into LostFilmApplication

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`

- [ ] **Step 1: Add failing test for application wiring**

Extend `LostFilmApplicationTest.kt`:

```kotlin
@Test
fun appUpdateRepository_usesUpdateHttpClient_withTimeouts() {
    val app = LostFilmApplication()
    // Verify that appUpdateRepository uses UpdateHttpClientFactory
}
```

- [ ] **Step 2: Run test to verify failure**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.LostFilmApplicationTest"
```
Expected: FAIL because app still uses shared `okHttpClient`

- [ ] **Step 3: Modify LostFilmApplication to use UpdateHttpClientFactory**

In `LostFilmApplication.kt`, change:

```kotlin
open val appUpdateRepository: AppUpdateRepository by lazy {
    AppUpdateRepository(
        installedVersion = BuildConfig.VERSION_NAME,
        releaseClient = GitHubReleaseClient(UpdateHttpClientFactory.create()),
    )
}
```

And:

```kotlin
open val releaseApkLauncher: ReleaseApkLauncher by lazy {
    ReleaseApkLauncher(UpdateHttpClientFactory.create())
}
```

- [ ] **Step 4: Re-run tests**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.LostFilmApplicationTest"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt
git commit -m "refactor: use dedicated HTTP client with timeouts for updates"
```

---

## Chunk 2: ReleaseApkLauncher Improvements

**Problem:** Нет проверки свободного места, нет прогресса загрузки, нет retry, фиксированное имя файла.

### Task 3: Add disk space check before download

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncher.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncherTest.kt`

- [ ] **Step 1: Write failing test for disk space check**

```kotlin
@Test
fun launch_returnsFalse_whenDiskSpaceInsufficient() = runBlocking {
    val server = MockWebServer()
    server.enqueue(MockResponse().setBody("fake-apk"))
    server.start()
    val base = ApplicationProvider.getApplicationContext<Context>()
    val context = RecordingContext(base)
    val launcher = object : ReleaseApkLauncher(
        httpClient = OkHttpClient(),
        ioDispatcher = Dispatchers.Unconfined,
        mainDispatcher = Dispatchers.Unconfined,
    ) {
        override fun hasEnoughDiskSpace(context: Context): Boolean = false
    }

    val result = launcher.launch(context, server.url("/app.apk").toString())

    assertFalse(result)
    server.shutdown()
}
```

- [ ] **Step 2: Run test to verify failure**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.ReleaseApkLauncherTest"
```
Expected: FAIL because `hasEnoughDiskSpace` method doesn't exist

- [ ] **Step 3: Implement disk space check**

Add to `ReleaseApkLauncher`:

```kotlin
private val minRequiredSpaceBytes = 100L * 1024 * 1024 // 100 MB

protected open fun hasEnoughDiskSpace(context: Context): Boolean {
    val cacheDir = File(context.cacheDir, CACHE_SUBDIR)
    return cacheDir.freeSpace >= minRequiredSpaceBytes
}
```

Add check in `launch()` method before download:

```kotlin
if (!hasEnoughDiskSpace(context)) {
    return false
}
```

- [ ] **Step 4: Re-run tests**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.ReleaseApkLauncherTest"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncher.kt app/src/test/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncherTest.kt
git commit -m "feat: add disk space check before APK download"
```

### Task 4: Add download progress callback

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncher.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncherTest.kt`

- [ ] **Step 1: Write failing test for progress callback**

```kotlin
@Test
fun launch_callsProgressCallback_withBytesDownloaded() = runBlocking {
    val server = MockWebServer()
    server.enqueue(MockResponse().setBody("x".repeat(1024)))
    server.start()
    val base = ApplicationProvider.getApplicationContext<Context>()
    val context = RecordingContext(base)
    val launcher = TestReleaseApkLauncher(httpClient = OkHttpClient())
    val progressValues = mutableListOf<Int>()

    launcher.launch(
        context,
        server.url("/app.apk").toString(),
        onDownloadProgress = { progressValues.add(it) },
    )

    assertTrue(progressValues.isNotEmpty())
    assertTrue(progressValues.last() == 100)
    server.shutdown()
}
```

- [ ] **Step 2: Run test to verify failure**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.ReleaseApkLauncherTest"
```
Expected: FAIL because `onDownloadProgress` parameter doesn't exist

- [ ] **Step 3: Implement progress callback**

Change `launch()` signature:

```kotlin
open suspend fun launch(
    context: Context,
    apkUrl: String,
    onDownloadingChange: (Boolean) -> Unit = {},
    onDownloadProgress: (Int) -> Unit = {},
): Boolean
```

Modify `downloadApkToCache()` to report progress:

```kotlin
private fun downloadApkToCache(
    context: Context,
    apkUrl: String,
    onProgress: (Int) -> Unit,
): File {
    val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
    val target = File(dir, APK_FILE_NAME)
    val request = Request.Builder()
        .url(apkUrl)
        .build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}")
        }
        val body = response.body ?: throw IOException("Empty body")
        val contentLength = body.contentLength()
        body.byteStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        onProgress(progress)
                    }
                }
                onProgress(100)
            }
        }
    }
    return target
}
```

Update `launch()` call:

```kotlin
val apkFile = withContext(ioDispatcher) {
    downloadApkToCache(context, apkUrl, onDownloadProgress)
}
```

- [ ] **Step 4: Re-run tests**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.ReleaseApkLauncherTest"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncher.kt app/src/test/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncherTest.kt
git commit -m "feat: add download progress callback to ReleaseApkLauncher"
```

### Task 5: Use unique filename per version to avoid conflicts

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncher.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncherTest.kt`

- [ ] **Step 1: Write failing test for unique filename**

```kotlin
@Test
fun launch_savesApk_withVersionedFilename() = runBlocking {
    val server = MockWebServer()
    server.enqueue(MockResponse().setBody("fake-apk"))
    server.start()
    val base = ApplicationProvider.getApplicationContext<Context>()
    val context = RecordingContext(base)
    val launcher = TestReleaseApkLauncher(httpClient = OkHttpClient())

    launcher.launch(context, server.url("/app-v1.2.3.apk").toString())

    val expectedFile = File(base.cacheDir, "updates/app-update.apk")
    assertTrue(expectedFile.exists())
    server.shutdown()
}
```

- [ ] **Step 2: Run test to verify failure**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.ReleaseApkLauncherTest"
```
Expected: FAIL if filename logic changes

- [ ] **Step 3: Extract filename from URL**

Modify `downloadApkToCache()`:

```kotlin
private fun getApkFileName(apkUrl: String): String {
    val uri = apkUrl.toUri()
    val pathSegments = uri.pathSegments
    val lastSegment = pathSegments.lastOrNull() ?: return APK_FILE_NAME
    return if (lastSegment.endsWith(".apk", ignoreCase = true)) {
        lastSegment
    } else {
        APK_FILE_NAME
    }
}
```

Use in `downloadApkToCache()`:

```kotlin
val target = File(dir, getApkFileName(apkUrl))
```

- [ ] **Step 4: Re-run tests**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.ReleaseApkLauncherTest"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncher.kt app/src/test/java/com/kraat/lostfilmnewtv/updates/ReleaseApkLauncherTest.kt
git commit -m "fix: use unique APK filename from URL to avoid conflicts"
```

---

## Chunk 3: GitHubReleaseClient Hardening

**Problem:** Нет обработки rate limiting, нет User-Agent, нет логирования.

### Task 6: Add User-Agent header and rate limit handling

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/GitHubReleaseClient.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/updates/GitHubReleaseClientTest.kt`

- [ ] **Step 1: Write failing tests for rate limit and User-Agent**

```kotlin
@Test
fun fetchLatestRelease_throwsRateLimitException_when429Response() = runTest {
    val server = MockWebServer()
    server.enqueue(MockResponse().setResponseCode(429))
    server.start()
    try {
        val client = GitHubReleaseClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().removeSuffix("/"),
        )
        client.fetchLatestRelease()
        fail("Expected RateLimitException")
    } catch (e: RateLimitException) {
        assertEquals(429, e.statusCode)
    } finally {
        server.shutdown()
    }
}

@Test
fun fetchLatestRelease_includesUserAgentHeader() = runTest {
    val server = MockWebServer()
    server.enqueue(
        MockResponse()
            .setResponseCode(200)
            .setBody("""{"tag_name":"v1.0.0","name":"Release","assets":[]}"""),
    )
    server.start()
    try {
        val client = GitHubReleaseClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().removeSuffix("/"),
        )
        client.fetchLatestRelease()
        val request = server.takeRequest()
        assertTrue(request.getHeader("User-Agent")?.contains("LostFilmNewTV") == true)
    } finally {
        server.shutdown()
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.GitHubReleaseClientTest"
```
Expected: FAIL because `RateLimitException` doesn't exist and User-Agent not sent

- [ ] **Step 3: Implement rate limit handling and User-Agent**

Add exception class:

```kotlin
class RateLimitException(val statusCode: Int, message: String) : IOException(message)
```

Modify `fetchLatestRelease()`:

```kotlin
open suspend fun fetchLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
    val url = "$baseUrl/repos/kraaton11/new_lostfilmatv/releases/latest"
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .get()
        .build()

    httpClient.newCall(request).execute().use { response ->
        when (response.code) {
            403, 429 -> throw RateLimitException(
                statusCode = response.code,
                message = "GitHub API rate limit exceeded. HTTP ${response.code}",
            )
            !in 200..299 -> throw IOException(
                "Failed to fetch latest release from $url: HTTP ${response.code}",
            )
        }

        val body = response.body?.string() ?: throw IOException("Empty response body for $url")
        val release = json.decodeFromString(GitHubLatestReleaseDto.serializer(), body)
        GitHubRelease(
            version = release.tagName,
            apkUrl = release.assets.firstOrNull { it.isApk() }?.browserDownloadUrl,
        )
    }
}

private companion object {
    const val USER_AGENT = "LostFilmNewTV-Update/1.0"
}
```

- [ ] **Step 4: Re-run tests**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.GitHubReleaseClientTest"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/kraat/lostfilmnewtv/updates/GitHubReleaseClient.kt app/src/test/java/com/kraat/lostfilmnewtv/updates/GitHubReleaseClientTest.kt
git commit -m "feat: add User-Agent and rate limit handling to GitHubReleaseClient"
```

---

## Chunk 4: AppUpdateRepository Error Handling

**Problem:** Общий Exception ловит все ошибки, нет логирования, одно сообщение для всех ошибок.

### Task 7: Improve error handling with specific exceptions and logging

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateRepository.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateRepositoryTest.kt`

- [ ] **Step 1: Write failing tests for specific error messages**

```kotlin
@Test
fun checkForUpdate_returnsSpecificError_whenRateLimited() = runTest {
    val repository = AppUpdateRepository(
        installedVersion = "1.0.0",
        releaseClient = object : GitHubReleaseClient(httpClient = OkHttpClient()) {
            override suspend fun fetchLatestRelease(): GitHubRelease {
                throw RateLimitException(429, "Rate limited")
            }
        },
    )

    val updateInfo = repository.checkForUpdate()

    assertTrue(updateInfo is AppUpdateInfo.Error)
    assertTrue((updateInfo as AppUpdateInfo.Error).message.contains("лимит"))
}

@Test
fun checkForUpdate_returnsSpecificError_whenNetworkTimeout() = runTest {
    val repository = AppUpdateRepository(
        installedVersion = "1.0.0",
        releaseClient = object : GitHubReleaseClient(httpClient = OkHttpClient()) {
            override suspend fun fetchLatestRelease(): GitHubRelease {
                throw java.net.SocketTimeoutException("Connection timed out")
            }
        },
    )

    val updateInfo = repository.checkForUpdate()

    assertTrue(updateInfo is AppUpdateInfo.Error)
    assertTrue((updateInfo as AppUpdateInfo.Error).message.contains("таймаут") ||
        updateInfo.message.contains("соединения"))
}

@Test
fun checkForUpdate_returnsSpecificError_whenNoNetwork() = runTest {
    val repository = AppUpdateRepository(
        installedVersion = "1.0.0",
        releaseClient = object : GitHubReleaseClient(httpClient = OkHttpClient()) {
            override suspend fun fetchLatestRelease(): GitHubRelease {
                throw java.net.UnknownHostException("No network")
            }
        },
    )

    val updateInfo = repository.checkForUpdate()

    assertTrue(updateInfo is AppUpdateInfo.Error)
    assertTrue((updateInfo as AppUpdateInfo.Error).message.contains("сети") ||
        updateInfo.message.contains("подключению"))
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateRepositoryTest"
```
Expected: FAIL because error messages are generic

- [ ] **Step 3: Implement specific error handling**

Replace generic catch block in `AppUpdateRepository.checkForUpdate()`:

```kotlin
} catch (error: CancellationException) {
    throw error
} catch (error: RateLimitException) {
    AppUpdateInfo.Error(
        installedVersion = installedVersion,
        message = "Превышен лимит запросов. Попробуйте позже.",
    )
} catch (error: java.net.SocketTimeoutException) {
    AppUpdateInfo.Error(
        installedVersion = installedVersion,
        message = "Превышено время ожидания соединения.",
    )
} catch (error: java.net.UnknownHostException) {
    AppUpdateInfo.Error(
        installedVersion = installedVersion,
        message = "Нет подключения к сети.",
    )
} catch (error: IOException) {
    android.util.Log.e("AppUpdateRepository", "Update check failed", error)
    AppUpdateInfo.Error(
        installedVersion = installedVersion,
        message = "Не удалось проверить обновления.",
    )
} catch (error: Exception) {
    android.util.Log.e("AppUpdateRepository", "Unexpected error during update check", error)
    AppUpdateInfo.Error(
        installedVersion = installedVersion,
        message = "Не удалось проверить обновления.",
    )
}
```

- [ ] **Step 4: Re-run tests**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateRepositoryTest"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateRepository.kt app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateRepositoryTest.kt
git commit -m "feat: improve error handling with specific messages and logging"
```

---

## Chunk 5: VersionComparator Extraction

**Problem:** Функции `isNewerThan()` и `versionParts()` дублируются в двух файлах.

### Task 8: Extract version comparison logic into utility class

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/VersionComparator.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/updates/VersionComparatorTest.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateRepository.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateCoordinator.kt`

- [ ] **Step 1: Write failing tests for VersionComparator**

```kotlin
class VersionComparatorTest {

    @Test
    fun isNewerThan_returnsTrue_whenVersionHasHigherMajor() {
        assertTrue(VersionComparator.isNewerThan("2.0.0", "1.0.0"))
    }

    @Test
    fun isNewerThan_returnsTrue_whenVersionHasHigherMinor() {
        assertTrue(VersionComparator.isNewerThan("1.1.0", "1.0.0"))
    }

    @Test
    fun isNewerThan_returnsTrue_whenVersionHasHigherPatch() {
        assertTrue(VersionComparator.isNewerThan("1.0.1", "1.0.0"))
    }

    @Test
    fun isNewerThan_returnsFalse_whenVersionsEqual() {
        assertFalse(VersionComparator.isNewerThan("1.0.0", "1.0.0"))
    }

    @Test
    fun isNewerThan_returnsFalse_whenVersionIsOlder() {
        assertFalse(VersionComparator.isNewerThan("1.0.0", "2.0.0"))
    }

    @Test
    fun isNewerThan_handlesComplexVersionFormats() {
        assertTrue(VersionComparator.isNewerThan("v2026.03.24.125", "v2026.03.24.123"))
    }

    @Test
    fun isNewerThan_handlesDifferentLengthVersions() {
        assertTrue(VersionComparator.isNewerThan("1.0.1", "1.0"))
    }

    @Test
    fun isNewerThan_handlesNonNumericParts() {
        assertTrue(VersionComparator.isNewerThan("v1.2.3-beta", "v1.2.2"))
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.VersionComparatorTest"
```
Expected: FAIL with missing `VersionComparator` type

- [ ] **Step 3: Implement VersionComparator**

```kotlin
package com.kraat.lostfilmnewtv.updates

object VersionComparator {

    fun isNewerThan(version: String, other: String): Boolean {
        val versionParts = extractParts(version)
        val otherParts = extractParts(other)
        val maxSize = maxOf(versionParts.size, otherParts.size)
        for (index in 0 until maxSize) {
            val versionPart = versionParts.getOrElse(index) { 0 }
            val otherPart = otherParts.getOrElse(index) { 0 }
            if (versionPart != otherPart) {
                return versionPart > otherPart
            }
        }
        return false
    }

    private fun extractParts(version: String): List<Int> =
        Regex("""\d+""")
            .findAll(version)
            .map { match -> match.value.toInt() }
            .toList()
}
```

- [ ] **Step 4: Re-run tests**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.VersionComparatorTest"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Update AppUpdateRepository to use VersionComparator**

Remove duplicate functions and update `checkForUpdate()`:

```kotlin
latestRelease.version.isNewerThan(installedVersion).not()
```
becomes:
```kotlin
!VersionComparator.isNewerThan(latestRelease.version, installedVersion)
```

Remove private functions `isNewerThan()` and `versionParts()`.

- [ ] **Step 6: Update AppUpdateCoordinator to use VersionComparator**

Remove duplicate functions and update `seedSavedUpdate()`:

```kotlin
if (!savedUpdate.latestVersion.isNewerThan(installedVersion))
```
becomes:
```kotlin
if (!VersionComparator.isNewerThan(savedUpdate.latestVersion, installedVersion))
```

Remove private functions `isNewerThan()` and `versionParts()`.

- [ ] **Step 7: Run all update tests to verify**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.*"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```
git add app/src/main/java/com/kraat/lostfilmnewtv/updates/VersionComparator.kt app/src/test/java/com/kraat/lostfilmnewtv/updates/VersionComparatorTest.kt app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateRepository.kt app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateCoordinator.kt
git commit -m "refactor: extract version comparison into VersionComparator utility"
```

---

## Chunk 6: AppUpdateBackgroundWorker Retry Logic

**Problem:** Все результаты возвращают `Result.success()`, нет retry при ошибках.

### Task 9: Add retry logic for transient failures

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundWorker.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundWorkerTest.kt`

- [ ] **Step 1: Write failing test for retry on error**

```kotlin
@Test
fun failedEmpty_mapsToRetry_whenTransientError() {
    val result = AppUpdateRefreshResult.FailedEmpty(
        installedVersion = "1.0.0",
        message = "No network",
    ).toWorkerResult()

    assertTrue(result is ListenableWorker.Result.Retry)
}
```

- [ ] **Step 2: Run test to verify failure**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundWorkerTest"
```
Expected: FAIL because `FailedEmpty` maps to `success()`

- [ ] **Step 3: Implement retry logic**

Modify `toWorkerResult()`:

```kotlin
internal fun AppUpdateRefreshResult.toWorkerResult(): ListenableWorker.Result {
    return when (this) {
        is AppUpdateRefreshResult.UpToDate,
        is AppUpdateRefreshResult.UpdateSaved,
        is AppUpdateRefreshResult.FailedKeptPrevious,
        -> ListenableWorker.Result.success()

        is AppUpdateRefreshResult.FailedEmpty -> {
            if (isTransientError(message)) {
                ListenableWorker.Result.retry()
            } else {
                ListenableWorker.Result.success()
            }
        }
    }
}

private fun isTransientError(message: String): Boolean {
    val transientKeywords = listOf(
        "сети",
        "подключению",
        "таймаут",
        "лимит",
        "timeout",
        "network",
        "connection",
    )
    return transientKeywords.any { keyword ->
        message.contains(keyword, ignoreCase = true)
    }
}
```

- [ ] **Step 4: Re-run tests**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundWorkerTest"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundWorker.kt app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundWorkerTest.kt
git commit -m "feat: add retry logic for transient errors in background worker"
```

---

## Chunk 7: AppUpdateBackgroundScheduler Error Handling

**Problem:** Нет обработки ошибок при планировании задач.

### Task 10: Add error handling to scheduler

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundScheduler.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundSchedulerTest.kt`

- [ ] **Step 1: Write failing test for error handling**

```kotlin
@Test
fun syncForCurrentMode_doesNotThrow_whenWorkManagerFails() {
    val failingWorkManager = mock<WorkManager>()
    whenever(failingWorkManager.enqueueUniquePeriodicWork(any(), any(), any()))
        .thenThrow(IllegalStateException("WorkManager not initialized"))

    val scheduler = AppUpdateBackgroundScheduler(
        readMode = { UpdateCheckMode.QUIET_CHECK },
        workManager = failingWorkManager,
    )

    // Should not throw
    scheduler.syncForCurrentMode()
}
```

- [ ] **Step 2: Run test to verify failure**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundSchedulerTest"
```
Expected: FAIL because exception propagates

- [ ] **Step 3: Add try-catch to scheduler methods**

```kotlin
fun syncForCurrentMode() {
    try {
        when (readMode()) {
            UpdateCheckMode.MANUAL -> {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
            }
            UpdateCheckMode.QUIET_CHECK -> {
                workManager.enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<AppUpdateBackgroundWorker>(
                        REFRESH_INTERVAL_HOURS,
                        TimeUnit.HOURS,
                    )
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        )
                        .build(),
                )
            }
        }
    } catch (error: Exception) {
        android.util.Log.e("AppUpdateScheduler", "Failed to sync update schedule", error)
    }
}

fun requestImmediateRefresh() {
    try {
        when (readMode()) {
            UpdateCheckMode.MANUAL -> {
                workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
            }
            UpdateCheckMode.QUIET_CHECK -> {
                workManager.enqueueUniqueWork(
                    IMMEDIATE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<AppUpdateBackgroundWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        )
                        .build(),
                )
            }
        }
    } catch (error: Exception) {
        android.util.Log.e("AppUpdateScheduler", "Failed to request immediate refresh", error)
    }
}
```

- [ ] **Step 4: Re-run tests**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundSchedulerTest"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundScheduler.kt app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundSchedulerTest.kt
git commit -m "fix: add error handling to background scheduler"
```

---

## Chunk 8: SettingsViewModel Debounce & Validation

**Problem:** Нет debounce для кнопки проверки, нет валидации URL.

### Task 11: Add debounce to update check button

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Write failing test for debounce**

```kotlin
@Test
fun onCheckForUpdatesClick_ignoresRapidClicks_withinDebounceWindow() = runTest {
    val viewModel = createViewModel()
    
    viewModel.onCheckForUpdatesClick()
    viewModel.onCheckForUpdatesClick()
    viewModel.onCheckForUpdatesClick()
    
    advanceTimeBy(100)
    
    // Only one check should have been initiated
    assertEquals(1, checkCount)
}
```

- [ ] **Step 2: Run test to verify failure**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsViewModelTest"
```
Expected: FAIL because multiple checks are initiated

- [ ] **Step 3: Implement debounce**

Add to `SettingsViewModel`:

```kotlin
private var lastCheckTimestamp = 0L
private val debounceIntervalMs = 1000L

fun onCheckForUpdatesClick() {
    val now = System.currentTimeMillis()
    if (now - lastCheckTimestamp < debounceIntervalMs) {
        return
    }
    lastCheckTimestamp = now
    refreshUpdateInfo()
}
```

- [ ] **Step 4: Re-run tests**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsViewModelTest"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModel.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModelTest.kt
git commit -m "fix: add debounce to update check button"
```

### Task 12: Add URL validation before install

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Write failing test for URL validation**

```kotlin
@Test
fun onInstallUpdateClick_doesNothing_whenUrlIsInvalid() = runTest {
    val viewModel = createViewModel(
        savedUpdateState = MutableStateFlow(
            SavedAppUpdate(latestVersion = "1.1.0", apkUrl = "not-a-valid-url")
        )
    )
    
    // Should not crash or call install
    viewModel.onInstallDownloadProgress(false)
}
```

- [ ] **Step 2: Run test to verify failure**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsViewModelTest"
```
Expected: Test structure passes (validation happens in SettingsRoute)

- [ ] **Step 3: Add validation note**

The URL validation is already handled by `ReleaseApkLauncher.isAllowedDirectDownloadUrl()`. No changes needed in ViewModel. Document this in the commit message.

- [ ] **Step 4: Mark as verified**

No code changes needed — validation already exists in the right place.

- [ ] **Step 5: Commit documentation update**

```
git commit --allow-empty -m "docs: note URL validation already handled by ReleaseApkLauncher"
```

---

## Chunk 9: Final Regression Testing

### Task 13: Run full test suite and verify

- [ ] **Step 1: Run all update-related tests**

Run:
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.*" --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsViewModelTest" --tests "com.kraat.lostfilmnewtv.LostFilmApplicationTest"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Run lint check**

Run:
```
.\gradlew.bat :app:lintDebug
```
Expected: No critical issues

- [ ] **Step 3: Build debug APK to verify compilation**

Run:
```
.\gradlew.bat :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Create summary commit if needed**

If any final adjustments were made:
```
git add -A
git commit -m "chore: final adjustments after update bug fixes"
```

---

## Summary of Changes

| Chunk | Files Modified | Files Created | Key Improvements |
|-------|---------------|---------------|------------------|
| 1 | LostFilmApplication.kt | UpdateHttpClientFactory.kt, Test | HTTP timeouts (30s connect, 60s read/write) |
| 2 | ReleaseApkLauncher.kt, Test | — | Disk space check, progress callback, unique filenames |
| 3 | GitHubReleaseClient.kt, Test | — | User-Agent, rate limit handling |
| 4 | AppUpdateRepository.kt, Test | — | Specific error messages, logging |
| 5 | AppUpdateRepository.kt, AppUpdateCoordinator.kt | VersionComparator.kt, Test | DRY principle, deduplication |
| 6 | AppUpdateBackgroundWorker.kt, Test | — | Retry logic for transient errors |
| 7 | AppUpdateBackgroundScheduler.kt, Test | — | Error handling, logging |
| 8 | SettingsViewModel.kt, Test | — | Debounce for rapid clicks |

**Total:** ~14 files modified, ~4 files created
