package com.kraat.lostfilmnewtv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.auth.AuthRepository
import com.kraat.lostfilmnewtv.data.model.PairingSession
import com.kraat.lostfilmnewtv.data.model.PairingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val pairing: PairingSession? = null,
    val isPolling: Boolean = false,
    val isAuthenticated: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkExistingAuth()
    }

    private fun checkExistingAuth() {
        viewModelScope.launch {
            val authState = authRepository.getAuthState()
            _uiState.value = _uiState.value.copy(
                isAuthenticated = authState.isAuthenticated
            )
        }
    }

    fun startAuth() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val pairing = authRepository.startPairing()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pairing = pairing,
                    isPolling = true
                )
                startPolling()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to start auth"
                )
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            val result = authRepository.waitForConfirmation()
            if (result?.status == PairingStatus.CONFIRMED && authRepository.claimAndPersistSession()) {
                _uiState.value = _uiState.value.copy(
                    isPolling = false,
                    isAuthenticated = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isPolling = false,
                    error = result?.failureReason ?: "Pairing expired or failed"
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    class Factory(
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authRepository) as T
        }
    }
}
