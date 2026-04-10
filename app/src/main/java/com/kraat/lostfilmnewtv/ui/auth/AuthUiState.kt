package com.kraat.lostfilmnewtv.ui.auth

import com.kraat.lostfilmnewtv.data.model.PairingSession

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object CreatingCode : AuthUiState
    data class WaitingForPhoneOpen(val pairing: PairingSession) : AuthUiState
    data class WaitingForPhoneLogin(val pairing: PairingSession) : AuthUiState
    data class VerifyingSession(val pairing: PairingSession) : AuthUiState
    data object Authenticated : AuthUiState
    data class Expired(val message: String) : AuthUiState
    data class RecoverableError(val message: String) : AuthUiState
}
