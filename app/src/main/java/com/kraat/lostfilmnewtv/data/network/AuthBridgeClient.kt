package com.kraat.lostfilmnewtv.data.network

import com.kraat.lostfilmnewtv.data.model.LostFilmCookie
import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import com.kraat.lostfilmnewtv.data.model.PairingSession
import com.kraat.lostfilmnewtv.data.model.PairingStatus
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class AuthBridgeClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createPairing(): PairingSession = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/pairings"
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.requireSuccessBody(url)
            val dto = json.decodeFromString(PairingDto.serializer(), body)
            dto.toModel()
        }
    }

    suspend fun getPairingStatus(pairing: PairingSession): PairingSession = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/pairings/${pairing.pairingId}"
        val request = Request.Builder()
            .url(url)
            .header("X-Pairing-Secret", pairing.pairingSecret)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.requireSuccessBody(url)
            val dto = json.decodeFromString(PairingStatusDto.serializer(), body)
            pairing.copy(
                status = parseStatus(dto.status),
                expiresIn = dto.expiresIn,
                retryable = dto.retryable,
                failureReason = dto.failureReason,
            )
        }
    }

    suspend fun claimSession(pairing: PairingSession): LostFilmSession = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/pairings/${pairing.pairingId}/claim"
        val request = Request.Builder()
            .url(url)
            .header("X-Pairing-Secret", pairing.pairingSecret)
            .post(FormBody.Builder().build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.requireSuccessBody(url)
            val dto = json.decodeFromString(SessionPayloadDto.serializer(), body)
            LostFilmSession(
                cookies = dto.cookies.map {
                    LostFilmCookie(
                        name = it.name,
                        value = it.value,
                        domain = it.domain,
                        path = it.path,
                    )
                },
                accountId = dto.accountId,
            )
        }
    }

    suspend fun finalizeClaim(pairing: PairingSession): Boolean = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/pairings/${pairing.pairingId}/finalize"
        val request = Request.Builder()
            .url(url)
            .header("X-Pairing-Secret", pairing.pairingSecret)
            .post(FormBody.Builder().build())
            .build()
        httpClient.newCall(request).execute().use {
            it.requireSuccess(url)
            it.code == 204
        }
    }

    suspend fun releaseClaim(pairing: PairingSession): Boolean = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/pairings/${pairing.pairingId}/release"
        val request = Request.Builder()
            .url(url)
            .header("X-Pairing-Secret", pairing.pairingSecret)
            .post(FormBody.Builder().build())
            .build()
        httpClient.newCall(request).execute().use {
            it.requireSuccess(url)
            it.code == 204
        }
    }

    private fun Response.requireSuccess(url: String) {
        if (!isSuccessful) {
            throw AuthBridgeHttpException(code, url, body?.string())
        }
    }

    private fun Response.requireSuccessBody(url: String): String {
        requireSuccess(url)
        return body?.string() ?: throw IOException("Empty response body for $url")
    }

    private fun parseStatus(status: String): PairingStatus = when (status.uppercase()) {
        "PENDING" -> PairingStatus.PENDING
        "IN_PROGRESS" -> PairingStatus.IN_PROGRESS
        "CONFIRMED" -> PairingStatus.CONFIRMED
        "EXPIRED" -> PairingStatus.EXPIRED
        "FAILED" -> PairingStatus.FAILED
        else -> PairingStatus.PENDING
    }

    @Serializable
    private data class PairingDto(
        val pairingId: String,
        val pairingSecret: String,
        val phoneVerifier: String,
        val userCode: String,
        val verificationUrl: String,
        val status: String,
        val expiresIn: Int,
        val pollInterval: Int = 5,
    ) {
        fun toModel(): PairingSession = PairingSession(
            pairingId = pairingId,
            pairingSecret = pairingSecret,
            phoneVerifier = phoneVerifier,
            userCode = userCode,
            verificationUrl = verificationUrl,
            status = when (status.uppercase()) {
                "PENDING" -> PairingStatus.PENDING
                "IN_PROGRESS" -> PairingStatus.IN_PROGRESS
                "CONFIRMED" -> PairingStatus.CONFIRMED
                "EXPIRED" -> PairingStatus.EXPIRED
                "FAILED" -> PairingStatus.FAILED
                else -> PairingStatus.PENDING
            },
            expiresIn = expiresIn,
            pollInterval = pollInterval,
        )
    }

    @Serializable
    private data class PairingStatusDto(
        val pairingId: String,
        val status: String,
        val expiresIn: Int,
        val retryable: Boolean? = null,
        val failureReason: String? = null,
    )

    @Serializable
    private data class SessionPayloadDto(
        val cookies: List<CookieDto>,
        val accountId: String? = null,
    )

    @Serializable
    private data class CookieDto(
        val name: String,
        val value: String,
        val domain: String,
        val path: String = "/",
    )
}
