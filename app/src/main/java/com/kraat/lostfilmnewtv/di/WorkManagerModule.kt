package com.kraat.lostfilmnewtv.di

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
