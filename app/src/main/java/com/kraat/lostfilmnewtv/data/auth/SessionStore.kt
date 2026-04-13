package com.kraat.lostfilmnewtv.data.auth

import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import kotlinx.coroutines.flow.Flow

interface SessionStore {
    suspend fun read(): LostFilmSession?
    suspend fun save(session: LostFilmSession)
    suspend fun markExpired()
    suspend fun clear()
    suspend fun isExpired(): Boolean
    fun changes(): Flow<Unit>
}
