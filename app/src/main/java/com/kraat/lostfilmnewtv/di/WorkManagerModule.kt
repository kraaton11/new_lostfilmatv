package com.kraat.lostfilmnewtv.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.startup.Initializer
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {

    /**
     * WorkManager инициализируется вручную с HiltWorkerFactory,
     * чтобы @HiltWorker мог получать @Inject зависимости.
     * Для этого нужно отключить автоматическую инициализацию WorkManager
     * через tools:node="remove" в AndroidManifest.xml.
     */
    @Provides
    @Singleton
    fun provideWorkManagerConfiguration(workerFactory: HiltWorkerFactory): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
