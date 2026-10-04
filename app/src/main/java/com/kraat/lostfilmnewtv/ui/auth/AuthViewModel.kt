package com.kraat.lostfilmnewtv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.auth.AuthCompletionResult
import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.model.PairingStatus
import com.kraat.lostfilmnewtv.tvchannel.AndroidChannelLogger
import com.kraat.lostfilmnewtv.tvchannel.ChannelLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
    private val logger: ChannelLogger = AndroidChannelLogger(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private var authJob: Job? = null

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
        authJob?.cancel()
        authJob = viewModelScope.launch(ioDispatcher) {
            _uiState.value = AuthUiState.CreatingCode
            try {
                val pairing = authRepository.startPairing()
                _uiState.value = pairing.toWaitingState()
                try {
                    startPollingLoop()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _uiState.value = AuthUiState.RecoverableError("Не удалось завершить вход. Получите новый код.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Технические детали уходят в лог: «read timed out» в интерфейсе
                // пользователю ничего не объясняет и не подсказывает, что делать.
                logger.e(TAG, "Cannot start pairing session", e)
                _uiState.value = AuthUiState.RecoverableError(
                    "Не удалось начать вход. Проверьте подключение к интернету и попробуйте ещё раз.",
                )
            } finally {
                if (authJob == currentCoroutineContext()[Job]) {
                    authJob = null
                }
            }
        }
    }

    private suspend fun startPollingLoop() {
        var waitedWithoutProgress = 0L
        while (true) {
            val pairing = authRepository.pollPairingStatus()
                ?: run {
                    _uiState.value = AuthUiState.RecoverableError("Не удалось завершить вход. Получите новый код.")
                    return
                }

            val awaitingConfirmation =
                pairing.status == PairingStatus.PENDING || pairing.status == PairingStatus.IN_PROGRESS

            // Бесконечный опрос выглядел у пользователя как вечная загрузка.
            // Отсчитываем время только пока подтверждения нет: смена статуса на
            // IN_PROGRESS означает, что пользователь что-то сделал, и отсчёт
            // начинается заново. Счёт идёт по интервалу опроса, а не по
            // системным часам, чтобы вести себя предсказуемо в тестах.
            if (awaitingConfirmation) {
                val pollIntervalMillis =
                    pairing.pollInterval.coerceAtLeast(MIN_POLL_INTERVAL_SECONDS) * 1000L
                if (waitedWithoutProgress >= POLLING_DEADLINE_MILLIS) {
                    logger.w(TAG, "Pairing not confirmed within the deadline")
                    _uiState.value = AuthUiState.RecoverableError(
                        "Не удалось подтвердить вход за $POLLING_DEADLINE_MINUTES " +
                            "${minutesWord(POLLING_DEADLINE_MINUTES)}. Проверьте подключение к " +
                            "интернету и попробуйте ещё раз.",
                    )
                    return
                }
                waitedWithoutProgress += pollIntervalMillis
            } else {
                waitedWithoutProgress = 0L
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

            delay(pairing.pollInterval.coerceAtLeast(MIN_POLL_INTERVAL_SECONDS) * 1000L)
        }
    }

    fun retryAuth() { startAuth() }

    fun cancelAuth() {
        authJob?.cancel()
        authJob = null
        viewModelScope.launch(ioDispatcher) {
            authRepository.cancelPairing()
            _uiState.value = AuthUiState.Idle
        }
    }

    fun logout() {
        authJob?.cancel()
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
        "cancelled" -> "Вход отменен."
        else -> "Не удалось завершить вход. Получите новый код."
    }

    private fun minutesWord(minutes: Int): String = when {
        minutes % 10 == 1 && minutes % 100 != 11 -> "минуту"
        minutes % 10 in 2..4 && minutes % 100 !in 12..14 -> "минуты"
        else -> "минут"
    }

    private companion object {
        const val MIN_POLL_INTERVAL_SECONDS = 2
        const val POLLING_DEADLINE_MINUTES = 10
        const val POLLING_DEADLINE_MILLIS = POLLING_DEADLINE_MINUTES * 60L * 1000L
        const val TAG = "AuthViewModel"
    }
}
