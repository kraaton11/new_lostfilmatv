package com.kraat.lostfilmnewtv.playback

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class PlaybackPreferencesDataStore private constructor(
    private val store: DataStore<Preferences>,
) {
    val data = store.data

    // Existing callers read synchronously. Load and migrate once, then serve
    // the latest requested settings in memory while DataStore persists them.
    private val initial by lazy {
        runBlocking {
            data.retry(2) { failure ->
                val transient = failure is IOException && failure !is CorruptionException
                if (transient) delay(100)
                transient
            }.first()
        }
    }
    private val pending = MutableStateFlow<Preferences?>(null)
    private val writeLock = Any()

    init {
        scope.launch {
            pending.filterNotNull().collectLatest { snapshot ->
                persist(snapshot)
            }
        }
    }

    operator fun <T> get(key: Preferences.Key<T>): T? = current()[key]

    fun update(change: MutablePreferences.() -> Unit) {
        synchronized(writeLock) {
            pending.value = current().toMutablePreferences().apply(change).toPreferences()
        }
    }

    private fun current(): Preferences = pending.value ?: initial

    private suspend fun persist(snapshot: Preferences) {
        while (true) {
            try {
                store.updateData { snapshot }
                return
            } catch (failure: IOException) {
                Log.w("PlaybackPreferences", "Could not persist settings. Retrying.", failure)
                delay(1_000)
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val instances = mutableMapOf<File, PlaybackPreferencesDataStore>()

        @Synchronized
        fun get(context: Context, name: String): PlaybackPreferencesDataStore {
            val appContext = context.applicationContext
            val file = appContext.preferencesDataStoreFile(name)
            return instances.getOrPut(file) {
                PlaybackPreferencesDataStore(
                    PreferenceDataStoreFactory.create(
                        migrations = listOf(SharedPreferencesMigration(appContext, name)),
                        scope = scope,
                        produceFile = { file },
                    ),
                )
            }
        }
    }
}
