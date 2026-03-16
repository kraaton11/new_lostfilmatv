package com.kraat.lostfilmnewtv.data.auth

import com.kraat.lostfilmnewtv.data.model.LostFilmSession

interface SessionStore {
    suspend fun read(): LostFilmSession?
    suspend fun save(session: LostFilmSession)
    suspend fun markExpired()
    suspend fun clear()
    suspend fun isExpired(): Boolean
}
