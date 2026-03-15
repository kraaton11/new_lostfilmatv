package com.kraat.lostfilmnewtv

import org.junit.Assert.assertEquals
import org.junit.Test

class AppShellSmokeTest {
    @Test
    fun applicationId_matchesPlan() {
        assertEquals("com.kraat.lostfilmnewtv", BuildConfig.APPLICATION_ID)
    }
}
