package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.model.PairingSession
import com.kraat.lostfilmnewtv.data.model.PairingStatus
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AuthBridgeClientTest {

    @Test
    fun claimSession_throwsHttpExceptionWithStatusCode_whenBridgeReturnsExpired() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(410)
                .setBody("expired"),
        )
        server.start()
        try {
            val client = AuthBridgeClient(baseUrl(server), OkHttpClient())

            try {
                client.claimSession(pairing())
                fail("Expected AuthBridgeHttpException")
            } catch (error: AuthBridgeHttpException) {
                assertEquals(410, error.statusCode)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun finalizeClaim_throwsHttpExceptionWithStatusCode_whenBridgeReturnsServerError() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("temporary"),
        )
        server.start()
        try {
            val client = AuthBridgeClient(baseUrl(server), OkHttpClient())

            try {
                client.finalizeClaim(pairing())
                fail("Expected AuthBridgeHttpException")
            } catch (error: AuthBridgeHttpException) {
                assertEquals(503, error.statusCode)
            }
        } finally {
            server.shutdown()
        }
    }

    private fun baseUrl(server: MockWebServer): String = server.url("/").toString().removeSuffix("/")

    private fun pairing(): PairingSession = PairingSession(
        pairingId = "pair-123",
        pairingSecret = "secret-456",
        phoneVerifier = "phone-789",
        userCode = "ABC123",
        verificationUrl = "https://phone-789.auth.example.test/",
        status = PairingStatus.CONFIRMED,
        expiresIn = 120,
        pollInterval = 5,
    )
}
