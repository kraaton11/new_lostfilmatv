package com.kraat.lostfilmnewtv.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LostFilmCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/"
)

@Serializable
data class LostFilmSession(
    val cookies: List<LostFilmCookie>,
    val accountId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun isExpired(): Boolean {
        val sevenDays = 7 * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - createdAt > sevenDays
    }

    fun toCookieString(): String {
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }
}

enum class PairingStatus {
    PENDING,
    IN_PROGRESS,
    CONFIRMED,
    EXPIRED,
    FAILED
}

@Serializable
data class PairingSession(
    val pairingId: String,
    val pairingSecret: String,
    val phoneVerifier: String,
    val userCode: String,
    val verificationUrl: String,
    val status: PairingStatus,
    val expiresIn: Int,
    val pollInterval: Int = 5,
    val retryable: Boolean? = null,
    val failureReason: String? = null,
)

@Serializable
data class AuthState(
    val isAuthenticated: Boolean = false,
    val session: LostFilmSession? = null,
    val lastError: String? = null
)
