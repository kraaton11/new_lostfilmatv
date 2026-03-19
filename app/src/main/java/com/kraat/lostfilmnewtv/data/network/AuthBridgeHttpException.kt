package com.kraat.lostfilmnewtv.data.network

import java.io.IOException

class AuthBridgeHttpException(
    val statusCode: Int,
    url: String,
    responseBody: String? = null,
) : IOException(buildMessage(statusCode, url, responseBody)) {

    fun isExpired(): Boolean = statusCode == 410

    fun isRetryable(): Boolean = statusCode == 429 || statusCode >= 500

    private companion object {
        fun buildMessage(statusCode: Int, url: String, responseBody: String?): String {
            val suffix = responseBody?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            return "HTTP $statusCode for $url$suffix"
        }
    }
}
