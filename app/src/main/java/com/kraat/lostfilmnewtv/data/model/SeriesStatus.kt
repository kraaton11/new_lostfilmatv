package com.kraat.lostfilmnewtv.data.model

enum class SeriesStatus(val queryValue: Int) {
    All(0),
    New(1),
    Ongoing(2),
    Completed(5),
    Favorites(99),
}
