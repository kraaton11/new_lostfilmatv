package com.kraat.lostfilmnewtv.data.auth

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLoginServerTest {
    @Test
    fun servesPhoneFormAndAcceptsLoginOnlyWithCurrentQrToken() {
        val server = PhoneLoginServer(
            login = { email, password, _ ->
                assertEquals("tv@example.test", email)
                assertEquals("p&ss", password)
                PasswordLoginResult.Authenticated
            },
            captcha = { byteArrayOf() },
            lanAddress = { "127.0.0.1" },
        )
        val http = OkHttpClient()
        try {
            val oldUrl = server.start().toHttpUrl()
            val qrUrl = server.start().toHttpUrl()
            val staleUrl = qrUrl.newBuilder().setQueryParameter("t", oldUrl.queryParameter("t")).build()
            http.newCall(Request.Builder().url(staleUrl).build()).execute().use {
                assertEquals(401, it.code)
            }
            http.newCall(Request.Builder().url(qrUrl).build()).execute().use {
                assertEquals(200, it.code)
                assertTrue(it.body!!.string().contains("Вход в LostFilm"))
            }

            val form = FormBody.Builder()
                .add("t", checkNotNull(qrUrl.queryParameter("t")))
                .add("mail", "tv@example.test")
                .add("pass", "p&ss")
                .build()
            val loginUrl = qrUrl.resolve("/login")!!
            http.newCall(Request.Builder().url(loginUrl).post(form).build()).execute().use {
                assertEquals(200, it.code)
                assertEquals("""{"status":"ok"}""", it.body!!.string())
            }
        } finally {
            server.stop()
        }
    }
}
