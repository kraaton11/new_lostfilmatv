package com.kraat.lostfilmnewtv

import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository

class DebugLostFilmApplication : LostFilmApplication() {
    override val repository: LostFilmRepository
        get() = LostFilmDebugHooks.repositoryOverride ?: super.repository
}

object LostFilmDebugHooks {
    @Volatile
    var repositoryOverride: LostFilmRepository? = null

    fun installRepositoryOverride(repository: LostFilmRepository) {
        repositoryOverride = repository
    }

    fun clearRepositoryOverride() {
        repositoryOverride = null
    }
}
