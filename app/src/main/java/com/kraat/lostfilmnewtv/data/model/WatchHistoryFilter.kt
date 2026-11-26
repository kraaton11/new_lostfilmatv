package com.kraat.lostfilmnewtv.data.model

enum class WatchHistoryFilter(val type: Int) {
    All(0),
    Favorites(1),
    Ongoing(2),
    Completed(3),
}
