package com.kraat.lostfilmnewtv.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Заменяет [NetworkModule] в тестах.
 * Все сетевые зависимости заменены заглушками — реальных HTTP-запросов не происходит.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class],
)
object TestNetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()
}
