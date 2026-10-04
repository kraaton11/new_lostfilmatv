package com.kraat.lostfilmnewtv.di

import com.kraat.lostfilmnewtv.tvchannel.AndroidChannelLogger
import com.kraat.lostfilmnewtv.tvchannel.ChannelLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Биндинг логгера для Hilt.
 *
 * Вызывающие классы принимают [ChannelLogger] параметром, поэтому в unit-тестах
 * подставляется заглушка, не трогающая Android-лог. Здесь же Hilt получает
 * реализацию для приложения.
 */
@Module
@InstallIn(SingletonComponent::class)
object ChannelLoggerModule {

    @Provides
    @Singleton
    fun provideChannelLogger(): ChannelLogger = AndroidChannelLogger()
}