package com.kraat.lostfilmnewtv.platform.torrserve

data class TorrServeConfig(
    val baseUrl: String = "http://127.0.0.1:8090",
    val echoPath: String = "/echo",
    val streamPath: String = "/stream",
)
