package com.kraat.lostfilmnewtv.data.auth

sealed interface AuthCompletionResult {
    data object Authenticated : AuthCompletionResult
    data object Expired : AuthCompletionResult
    data object NetworkError : AuthCompletionResult
    data object VerificationFailed : AuthCompletionResult
    data class RecoverableFailure(val hint: String? = null) : AuthCompletionResult
}
