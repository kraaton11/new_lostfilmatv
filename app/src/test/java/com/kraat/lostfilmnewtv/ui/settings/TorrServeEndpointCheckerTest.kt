package com.kraat.lostfilmnewtv.ui.settings

import kotlinx.coroutines.test.runTest
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrServeEndpointCheckerTest {
    @Test
    fun check_sendsBasicAuthAndReportsServerResponse() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setBody("MatriX"))
            val checker = OkHttpTorrServeEndpointChecker(OkHttpClient(), { false })
            val baseUrl = server.url("/").toString()

            val unauthorized = checker.check(baseUrl)
            assertTrue(unauthorized.isAuthorizationRejected)
            assertFalse(unauthorized.isEndpointReachable)
            assertEquals(null, server.takeRequest().getHeader("Authorization"))

            val connected = checker.check(baseUrl, "viewer", "test-password")
            assertTrue(connected.isEndpointReachable)
            val request = server.takeRequest()
            assertEquals("/echo", request.path)
            assertEquals(Credentials.basic("viewer", "test-password"), request.getHeader("Authorization"))
        }
    }
}
