package com.kraat.lostfilmnewtv

import org.junit.Assert.assertEquals
import org.junit.Test

class LostFilmApplicationTest {
    @Test
    fun authBridgeBaseUrl_usesProductionAuthDomain() {
        val application = LostFilmApplication()

        assertEquals("https://auth.bazuka.pp.ua", application.authBridgeBaseUrl)
    }
}
