package com.kraat.lostfilmnewtv.updates

data class SavedAppUpdate(
    val latestVersion: String,
    val apkUrl: String,
    val manuallyChecked: Boolean = false,
)
