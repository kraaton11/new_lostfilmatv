package com.kraat.lostfilmnewtv

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import com.kraat.lostfilmnewtv.data.db.LostFilmDatabase
import javax.inject.Inject

@HiltAndroidApp
class LostFilmApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerConfiguration: Configuration
    @Inject
    lateinit var okHttpClient: dagger.Lazy<OkHttpClient>
    @Inject
    lateinit var database: dagger.Lazy<LostFilmDatabase>

    override val workManagerConfiguration: Configuration
        get() = workerConfiguration

    override fun onCreate() {
        super.onCreate()
        // Фоновый предпрогрев тяжелых синглтонов
        CoroutineScope(Dispatchers.IO).launch {
            okHttpClient.get()
            database.get()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(false)
            .build()
    }
}