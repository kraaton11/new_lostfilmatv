package com.kraat.lostfilmnewtv.di

import android.content.Context
import androidx.room.Room
import com.kraat.lostfilmnewtv.data.db.ALL_MIGRATIONS
import com.kraat.lostfilmnewtv.data.db.LostFilmDatabase
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.TmdbPosterDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LostFilmDatabase =
        Room.databaseBuilder(
            context,
            LostFilmDatabase::class.java,
            "lostfilm-new-tv.db",
        ).addMigrations(*ALL_MIGRATIONS).build()

    @Provides
    fun provideReleaseDao(database: LostFilmDatabase): ReleaseDao =
        database.releaseDao()

    @Provides
    fun provideTmdbPosterDao(database: LostFilmDatabase): TmdbPosterDao =
        database.tmdbPosterDao()
}
