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
) {
    fun toCookieString(): String {
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }
}

@Serializable
data class AuthState(
    val isAuthenticated: Boolean = false,
    val session: LostFilmSession? = null
)
