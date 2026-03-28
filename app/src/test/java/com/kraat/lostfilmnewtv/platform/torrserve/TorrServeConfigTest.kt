package com.kraat.lostfilmnewtv.platform.torrserve

import org.junit.Test
import org.junit.Assert.*

class TorrServeConfigTest {
    @Test
    fun defaults_matchLocalTorrServeEndpoints() {
        val config = TorrServeConfig()
        assertEquals("http://127.0.0.1:8090", config.baseUrl)
        assertEquals("/echo", config.echoPath)
        assertEquals("/stream", config.streamPath)
    }
}
