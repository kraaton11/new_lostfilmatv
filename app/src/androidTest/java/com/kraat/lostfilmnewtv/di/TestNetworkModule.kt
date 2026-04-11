package com.kraat.lostfilmnewtv.di

import com.kraat.lostfilmnewtv.data.poster.TmdbPosterResolver
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

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
    fun provideNoOpTmdbPosterResolver(): TmdbPosterResolver = object : TmdbPosterResolver {
        override suspend fun resolve(
            detailsUrl: String,
            titleRu: String,
            releaseDateRu: String,
            kind: ReleaseKind,
        ): TmdbImageUrls? = null
    }
}
