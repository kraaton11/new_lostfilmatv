package com.kraat.lostfilmnewtv.data.auth

import com.kraat.lostfilmnewtv.data.model.AuthState
import com.kraat.lostfilmnewtv.data.model.PairingSession
import kotlinx.coroutines.flow.Flow

interface AuthRepositoryContract {
    suspend fun getAuthState(): AuthState
    fun observeAuthState(): Flow<AuthState>
    suspend fun startPairing(): PairingSession
    suspend fun pollPairingStatus(): PairingSession?
    suspend fun claimAndPersistSession(): AuthCompletionResult
    suspend fun logout()
}
