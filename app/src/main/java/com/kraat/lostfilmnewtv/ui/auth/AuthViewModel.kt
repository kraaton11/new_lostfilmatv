package com.kraat.lostfilmnewtv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.auth.AuthCompletionResult
import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.model.PairingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepositoryContract,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch(ioDispatcher) {
            authRepository.observeAuthState().collect { authState ->
                when {
                    authState.isAuthenticated -> _uiState.value = AuthUiState.Authenticated
                    _uiState.value is AuthUiState.Authenticated || _uiState.value is AuthUiState.Idle -> {
                        _uiState.value = AuthUiState.Idle
                    }
                }
            }
        }
    }

    fun startAuth() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.value = AuthUiState.CreatingCode
            try {
                val pairing = authRepository.startPairing()
                _uiState.value = pairing.toWaitingState()
                try {
                    startPollingLoop()
                } catch (_: Exception) {
                    _uiState.value = AuthUiState.RecoverableError("Не удалось завершить вход. Получите новый код.")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.RecoverableError(
                    message = e.message ?: "Не удалось начать вход. Получите новый код.",
                )
            }
        }
    }

    private suspend fun startPollingLoop() {
        while (true) {
            val pairing = authRepository.pollPairingStatus()
                ?: run {
                    _uiState.value = AuthUiState.RecoverableError("Не удалось завершить вход. Получите новый код.")
                    return
                }

            when (pairing.status) {
                PairingStatus.PENDING -> _uiState.value = AuthUiState.WaitingForPhoneOpen(pairing)
                PairingStatus.IN_PROGRESS -> _uiState.value = AuthUiState.WaitingForPhoneLogin(pairing)
                PairingStatus.CONFIRMED -> {
                    _uiState.value = AuthUiState.VerifyingSession(pairing)
                    _uiState.value = when (val result = authRepository.claimAndPersistSession()) {
                        AuthCompletionResult.Authenticated -> AuthUiState.Authenticated
                        AuthCompletionResult.Expired -> AuthUiState.Expired("Код входа истек. Получите новый код.")
                        AuthCompletionResult.NetworkError -> AuthUiState.RecoverableError("Проблема с сетью. Получите новый код.")
                        AuthCompletionResult.VerificationFailed -> AuthUiState.RecoverableError("Не удалось подтвердить вход. Получите новый код.")
                        is AuthCompletionResult.RecoverableFailure -> AuthUiState.RecoverableError(
                            result.hint ?: "Не удалось завершить вход. Получите новый код.",
                        )
                    }
                    return
                }
                PairingStatus.EXPIRED -> {
                    _uiState.value = AuthUiState.Expired("Код входа истек. Получите новый код.")
                    return
                }
                PairingStatus.FAILED -> {
                    _uiState.value = AuthUiState.RecoverableError(
                        pairing.failureReason?.toUserMessage() ?: "Не удалось завершить вход. Получите новый код.",
                    )
                    return
                }
            }

            delay(pairing.pollInterval.coerceAtLeast(0) * 1000L)
        }
    }

    fun retryAuth() { startAuth() }

    fun clearError() {
        if (_uiState.value is AuthUiState.RecoverableError || _uiState.value is AuthUiState.Expired) {
            _uiState.value = AuthUiState.Idle
        }
    }

    fun logout() {
        viewModelScope.launch(ioDispatcher) {
            authRepository.logout()
            _uiState.value = AuthUiState.Idle
        }
    }

    private fun com.kraat.lostfilmnewtv.data.model.PairingSession.toWaitingState(): AuthUiState =
        when (status) {
            PairingStatus.IN_PROGRESS -> AuthUiState.WaitingForPhoneLogin(this)
            else -> AuthUiState.WaitingForPhoneOpen(this)
        }

    private fun String.toUserMessage(): String = when (this) {
        "lease_expired" -> "Код входа истек. Получите новый код."
        "session_invalid" -> "Не удалось подтвердить вход. Получите новый код."
        else -> "Не удалось завершить вход. Получите новый код."
    }
}
