package com.kraat.lostfilmnewtv.ui.home

enum class HomeFeedMode(
    val storageValue: String,
) {
    AllNew("all_new"),
    Favorites("favorites"),
    Movies("movies");

    companion object {
        fun fromStorageValue(value: String?): HomeFeedMode {
            return entries.firstOrNull { it.storageValue == value } ?: AllNew
        }
    }
}
