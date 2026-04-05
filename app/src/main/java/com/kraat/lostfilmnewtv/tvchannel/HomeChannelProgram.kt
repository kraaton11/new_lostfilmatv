package com.kraat.lostfilmnewtv.tvchannel

data class HomeChannelProgram(
    val detailsUrl: String,
    val title: String,
    val description: String,
    val posterUrl: String,
    val backdropUrl: String = "",
    val internalProviderId: String,
)
