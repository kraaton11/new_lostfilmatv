package com.kraat.lostfilmnewtv

import android.app.Application
import androidx.room.Room
import com.kraat.lostfilmnewtv.data.db.LostFilmDatabase
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.OkHttpLostFilmHttpClient
import com.kraat.lostfilmnewtv.data.parser.LostFilmDetailsParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmListParser
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepositoryImpl

open class LostFilmApplication : Application() {
    val database: LostFilmDatabase by lazy {
        Room.databaseBuilder(
            this,
            LostFilmDatabase::class.java,
            "lostfilm-new-tv.db",
        ).build()
    }

    val httpClient: LostFilmHttpClient by lazy {
        OkHttpLostFilmHttpClient()
    }

    open val repository: LostFilmRepository by lazy {
        LostFilmRepositoryImpl(
            httpClient = httpClient,
            releaseDao = database.releaseDao(),
            listParser = LostFilmListParser(),
            detailsParser = LostFilmDetailsParser(),
        )
    }
}
