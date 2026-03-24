package com.kraat.lostfilmnewtv.updates

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class UpdateHttpClientFactoryTest {

    @Test
    fun create_returnsClient_withConnectTimeout30Seconds() {
        val client = UpdateHttpClientFactory.create()
        assertEquals(30L, TimeUnit.MILLISECONDS.toSeconds(client.connectTimeoutMillis.toLong()))
    }

    @Test
    fun create_returnsClient_withReadTimeout60Seconds() {
        val client = UpdateHttpClientFactory.create()
        assertEquals(60L, TimeUnit.MILLISECONDS.toSeconds(client.readTimeoutMillis.toLong()))
    }

    @Test
    fun create_returnsClient_withWriteTimeout60Seconds() {
        val client = UpdateHttpClientFactory.create()
        assertEquals(60L, TimeUnit.MILLISECONDS.toSeconds(client.writeTimeoutMillis.toLong()))
    }

    @Test
    fun create_returnsNonNullClient() {
        val client = UpdateHttpClientFactory.create()
        assertNotNull(client)
    }
}
