package com.kraat.lostfilmnewtv.platform.torrserve

sealed interface TorrServeOpenResult {
    data object Success : TorrServeOpenResult
    data object Unavailable : TorrServeOpenResult
    data object LaunchError : TorrServeOpenResult
}
