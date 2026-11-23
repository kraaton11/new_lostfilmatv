package com.kraat.lostfilmnewtv.data.auth

sealed interface PasswordLoginResult {
    data object Authenticated : PasswordLoginResult
    data object NeedCaptcha : PasswordLoginResult
    data object WrongCaptcha : PasswordLoginResult
    data object InvalidCredentials : PasswordLoginResult
    data object VerificationFailed : PasswordLoginResult
    data object NetworkError : PasswordLoginResult
}

sealed interface AuthCompletionResult {
    data object Authenticated : AuthCompletionResult
    data object Expired : AuthCompletionResult
    data object NetworkError : AuthCompletionResult
    data object VerificationFailed : AuthCompletionResult
    data class RecoverableFailure(val hint: String? = null) : AuthCompletionResult
}
