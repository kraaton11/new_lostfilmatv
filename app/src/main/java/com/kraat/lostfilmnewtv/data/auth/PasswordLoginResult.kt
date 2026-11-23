package com.kraat.lostfilmnewtv.data.auth

sealed interface PasswordLoginResult {
    data object Authenticated : PasswordLoginResult
    data object NeedCaptcha : PasswordLoginResult
    data object WrongCaptcha : PasswordLoginResult
    data object InvalidCredentials : PasswordLoginResult
    data object VerificationFailed : PasswordLoginResult
    data object NetworkError : PasswordLoginResult
}
