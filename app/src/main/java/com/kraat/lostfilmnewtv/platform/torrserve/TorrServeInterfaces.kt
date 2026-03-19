package com.kraat.lostfilmnewtv.platform.torrserve

fun interface TorrServeSourceBuilder {
    fun build(rawUrl: String): String?
}

fun interface TorrServeAvailabilityChecker {
    suspend fun isAvailable(): Boolean
}

fun interface TorrServeUrlLauncher {
    suspend fun launch(context: android.content.Context, torrServeUrl: String): Boolean
}
