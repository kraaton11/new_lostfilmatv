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
    suspend fun cancelPairing()
    suspend fun logout()

    /** Прямой вход логином/паролем, без auth-bridge. */
    suspend fun beginPasswordLogin() {}
    suspend fun loadCaptcha(): ByteArray = throw UnsupportedOperationException()
    suspend fun loginWithPassword(email: String, password: String, captcha: String?): PasswordLoginResult =
        throw UnsupportedOperationException()

    /** Вход с телефона по локальной сети: поднимает сервер на ТВ, возвращает ссылку для QR. */
    suspend fun startPhoneLogin(): String = throw UnsupportedOperationException()
    suspend fun stopPhoneLogin() {}
}
