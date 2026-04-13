package com.kraat.lostfilmnewtv.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EncryptedSessionStore(private val context: Context) : SessionStore {
    private val changesFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Инициализация отложена и защищена мьютексом, чтобы избежать I/O на главном потоке
    // и гонки при одновременных первых обращениях.
    private val initMutex = Mutex()

    @Volatile
    private var _prefs: android.content.SharedPreferences? = null

    private suspend fun prefs(): android.content.SharedPreferences {
        _prefs?.let { return it }
        return initMutex.withLock {
            _prefs ?: withContext(Dispatchers.IO) {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "lostfilm_auth_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                ).also { _prefs = it }
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun read(): LostFilmSession? = withContext(Dispatchers.IO) {
        val sessionJson = prefs().getString(KEY_SESSION, null) ?: return@withContext null
        try {
            json.decodeFromString<LostFilmSession>(sessionJson)
        } catch (e: Exception) {
            clear()
            null
        }
    }

    override suspend fun save(session: LostFilmSession) {
        withContext(Dispatchers.IO) {
            val sessionJson = json.encodeToString(session)
            prefs().edit()
                .putString(KEY_SESSION, sessionJson)
                .remove(KEY_EXPIRED)
                .apply()
            changesFlow.tryEmit(Unit)
        }
    }

    override suspend fun markExpired() {
        withContext(Dispatchers.IO) {
            prefs().edit()
                .putBoolean(KEY_EXPIRED, true)
                .apply()
            changesFlow.tryEmit(Unit)
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            prefs().edit()
                .remove(KEY_SESSION)
                .remove(KEY_EXPIRED)
                .apply()
            changesFlow.tryEmit(Unit)
        }
    }

    override suspend fun isExpired(): Boolean = withContext(Dispatchers.IO) {
        prefs().getBoolean(KEY_EXPIRED, false)
    }

    override fun changes(): Flow<Unit> = changesFlow

    companion object {
        private const val KEY_SESSION = "lostfilm_session"
        private const val KEY_EXPIRED = "session_expired"
    }
}
