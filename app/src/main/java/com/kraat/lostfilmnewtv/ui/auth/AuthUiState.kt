package com.kraat.lostfilmnewtv.ui.auth


sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object CreatingCode : AuthUiState
    data class EnterCredentials(
        val captchaImage: ByteArray? = null,
        val message: String? = null,
        val submitting: Boolean = false,
    ) : AuthUiState
    data class WaitingForPhoneLan(val url: String) : AuthUiState
    data object Authenticated : AuthUiState
    data class Expired(val message: String) : AuthUiState
    data class RecoverableError(val message: String) : AuthUiState
}
