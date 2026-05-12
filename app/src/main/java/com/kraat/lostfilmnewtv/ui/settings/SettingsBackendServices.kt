package com.kraat.lostfilmnewtv.ui.settings

import android.content.Context
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.TmdbPosterDao
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeAvailabilityChecker
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class TorrServeEndpointCheck(
    val normalizedBaseUrl: String?,
    val isAppInstalled: Boolean,
    val isEndpointReachable: Boolean,
)

interface TorrServeEndpointChecker {
    suspend fun check(baseUrl: String): TorrServeEndpointCheck
}

class OkHttpTorrServeEndpointChecker(
    private val okHttpClient: OkHttpClient,
    private val availabilityChecker: TorrServeAvailabilityChecker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TorrServeEndpointChecker {
    override suspend fun check(baseUrl: String): TorrServeEndpointCheck = withContext(ioDispatcher) {
        val normalized = normalizeTorrServeBaseUrl(baseUrl)
        val appInstalled = availabilityChecker.isAvailable()
        val endpointReachable = normalized?.let(::isReachable).orFalse()
        TorrServeEndpointCheck(
            normalizedBaseUrl = normalized,
            isAppInstalled = appInstalled,
            isEndpointReachable = endpointReachable,
        )
    }

    private fun isReachable(baseUrl: String): Boolean {
        val client = okHttpClient.newBuilder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build()
        val echoUrl = baseUrl.trimEnd('/') + "/echo"
        val request = Request.Builder().url(echoUrl).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (error: IOException) {
            false
        } catch (error: IllegalArgumentException) {
            false
        }
    }
}

interface SettingsDataManager {
    suspend fun refreshFirstPage(): Boolean
    suspend fun clearReleaseCache()
    suspend fun clearPosterCache()
    suspend fun clearNetworkCache()
}

class AppSettingsDataManager(
    private val appContext: Context,
    private val releaseDao: ReleaseDao,
    private val tmdbPosterDao: TmdbPosterDao,
    private val repository: LostFilmRepository,
    private val okHttpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SettingsDataManager {
    override suspend fun refreshFirstPage(): Boolean = withContext(ioDispatcher) {
        releaseDao.deleteSummariesForPage(pageNumber = 1)
        releaseDao.deletePageMetadata(pageNumber = 1)
        repository.loadPage(pageNumber = 1) !is PageState.Error
    }

    override suspend fun clearReleaseCache() = withContext(ioDispatcher) {
        releaseDao.deleteAllCachedReleaseData()
    }

    override suspend fun clearPosterCache() = withContext(ioDispatcher) {
        tmdbPosterDao.deleteAll()
        File(appContext.cacheDir, COIL_IMAGE_CACHE_DIR).deleteRecursively()
        Unit
    }

    override suspend fun clearNetworkCache() = withContext(ioDispatcher) {
        okHttpClient.cache?.evictAll()
        Unit
    }

    private companion object {
        const val COIL_IMAGE_CACHE_DIR = "coil_image_cache"
    }
}

data class SettingsDiagnosticResult(
    val title: String,
    val value: String,
    val isOk: Boolean,
)

interface SettingsDiagnosticsRunner {
    suspend fun run(torrServeBaseUrl: String): List<SettingsDiagnosticResult>
}

class AppSettingsDiagnosticsRunner(
    private val lostFilmHttpClient: LostFilmHttpClient,
    private val torrServeEndpointChecker: TorrServeEndpointChecker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SettingsDiagnosticsRunner {
    override suspend fun run(torrServeBaseUrl: String): List<SettingsDiagnosticResult> = withContext(ioDispatcher) {
        val lostFilmOk = runCatching { lostFilmHttpClient.fetchNewPage(pageNumber = 1) }.isSuccess
        val torrServe = torrServeEndpointChecker.check(torrServeBaseUrl)
        listOf(
            SettingsDiagnosticResult(
                title = "LostFilm",
                value = if (lostFilmOk) "Доступен" else "Недоступен",
                isOk = lostFilmOk,
            ),
            SettingsDiagnosticResult(
                title = "TorrServe приложение",
                value = if (torrServe.isAppInstalled) "Установлено" else "Не найдено",
                isOk = torrServe.isAppInstalled,
            ),
            SettingsDiagnosticResult(
                title = "TorrServe HTTP",
                value = when {
                    torrServe.normalizedBaseUrl == null -> "Неверный адрес"
                    torrServe.isEndpointReachable -> "Доступен"
                    else -> "Недоступен"
                },
                isOk = torrServe.normalizedBaseUrl != null && torrServe.isEndpointReachable,
            ),
        )
    }
}

fun normalizeTorrServeBaseUrl(raw: String): String? {
    val withScheme = raw.trim()
        .takeIf { it.isNotBlank() }
        ?.let { if ("://" in it) it else "http://$it" }
        ?: return null
    val parsed = withScheme.toHttpUrlOrNull() ?: return null
    if (parsed.host.isBlank()) return null
    return parsed.newBuilder()
        .encodedPath("/")
        .query(null)
        .fragment(null)
        .build()
        .toString()
        .trimEnd('/')
}

private fun Boolean?.orFalse(): Boolean = this == true
