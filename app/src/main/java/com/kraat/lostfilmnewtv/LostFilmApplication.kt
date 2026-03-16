package com.kraat.lostfilmnewtv

import android.app.Application
import androidx.room.Room
import com.kraat.lostfilmnewtv.data.auth.AuthRepository
import com.kraat.lostfilmnewtv.data.auth.EncryptedSessionStore
import com.kraat.lostfilmnewtv.data.db.LostFilmDatabase
import com.kraat.lostfilmnewtv.data.network.AuthBridgeClient
import com.kraat.lostfilmnewtv.data.network.AuthenticatedLostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.network.OkHttpLostFilmHttpClient
import com.kraat.lostfilmnewtv.data.parser.LostFilmDetailsParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmListParser
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepositoryImpl
import okhttp3.OkHttpClient

open class LostFilmApplication : Application() {
    val database: LostFilmDatabase by lazy {
        Room.databaseBuilder(
            this,
            LostFilmDatabase::class.java,
            "lostfilm-new-tv.db",
        ).fallbackToDestructiveMigration().build()
    }

    val httpClient: LostFilmHttpClient by lazy {
        OkHttpLostFilmHttpClient()
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    val authBridgeBaseUrl: String by lazy {
        "http://192.168.2.168:18015"
    }

    val authBridgeClient: AuthBridgeClient by lazy {
        AuthBridgeClient(authBridgeBaseUrl, okHttpClient)
    }

    val sessionStore: EncryptedSessionStore by lazy {
        EncryptedSessionStore(this)
    }

    val authenticatedHttpClient: LostFilmHttpClient by lazy {
        AuthenticatedLostFilmHttpClient(sessionStore = sessionStore)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(authBridgeClient, sessionStore)
    }

    open val repository: LostFilmRepository by lazy {
        LostFilmRepositoryImpl(
            httpClient = authenticatedHttpClient,
            releaseDao = database.releaseDao(),
            listParser = LostFilmListParser(),
            detailsParser = LostFilmDetailsParser(),
        )
    }
}
