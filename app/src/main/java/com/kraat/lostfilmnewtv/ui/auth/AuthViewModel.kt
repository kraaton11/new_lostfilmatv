package com.kraat.lostfilmnewtv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.auth.PhoneLoginServer
import com.kraat.lostfilmnewtv.data.auth.PasswordLoginResult
import com.kraat.lostfilmnewtv.tvchannel.AndroidChannelLogger
import com.kraat.lostfilmnewtv.tvchannel.ChannelLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var lastMode = Mode.LAN

    private enum class Mode { LAN, CREDENTIALS }

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

    /** Вход логином и паролем прямо в приложении (без телефона и сервера). */
    fun startCredentialsLogin() {
        lastMode = Mode.CREDENTIALS
        authJob?.cancel()
        authJob = viewModelScope.launch(ioDispatcher) {
            authRepository.stopPhoneLogin()
            authRepository.beginPasswordLogin()
            _uiState.value = AuthUiState.EnterCredentials()
        }
    }

    fun submitCredentials(email: String, password: String, captcha: String?) {
        val current = _uiState.value as? AuthUiState.EnterCredentials ?: return
        if (current.submitting) return
        if (email.isBlank() || password.isEmpty()) {
            _uiState.value = current.copy(message = "Введите e-mail и пароль")
            return
        }
        authJob?.cancel()
        authJob = viewModelScope.launch(ioDispatcher) {
            _uiState.value = current.copy(submitting = true, message = null)
            _uiState.value = try {
                when (authRepository.loginWithPassword(email.trim(), password, captcha?.trim()?.ifBlank { null })) {
                    PasswordLoginResult.Authenticated -> AuthUiState.Authenticated
                    PasswordLoginResult.NeedCaptcha -> AuthUiState.EnterCredentials(
                        captchaImage = loadCaptchaOrNull(),
                        message = "Введите код с картинки",
                    )
                    PasswordLoginResult.WrongCaptcha -> AuthUiState.EnterCredentials(
                        captchaImage = loadCaptchaOrNull(),
                        message = "Неверный код с картинки",
                    )
                    PasswordLoginResult.InvalidCredentials -> AuthUiState.EnterCredentials(
                        captchaImage = if (current.captchaImage != null) loadCaptchaOrNull() else null,
                        message = "Неверный e-mail или пароль",
                    )
                    PasswordLoginResult.VerificationFailed ->
                        current.copy(submitting = false, message = "Не удалось подтвердить вход")
                    PasswordLoginResult.NetworkError ->
                        current.copy(submitting = false, message = "Проблема с сетью. Попробуйте ещё раз")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "Password login failed", e)
                current.copy(submitting = false, message = "Не удалось войти. Попробуйте ещё раз")
            }
        }
    }

    fun refreshCaptcha() {
        val current = _uiState.value as? AuthUiState.EnterCredentials ?: return
        viewModelScope.launch(ioDispatcher) {
            val image = loadCaptchaOrNull() ?: return@launch
            _uiState.compareAndSet(current, current.copy(captchaImage = image))
        }
    }

    private suspend fun loadCaptchaOrNull(): ByteArray? = try {
        authRepository.loadCaptcha()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Вход с телефона по домашней сети: ТВ показывает QR с локальным адресом, сервер не нужен. */
    fun startPhoneLoginLan() {
        lastMode = Mode.LAN
        authJob?.cancel()
        authJob = viewModelScope.launch(ioDispatcher) {
            _uiState.value = AuthUiState.CreatingCode
            val result = try {
                val url = authRepository.startPhoneLogin()
                AuthUiState.WaitingForPhoneLan(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                logger.e(TAG, "Cannot start LAN login", e)
                AuthUiState.RecoverableError(
                    "Телевизор не подключён к локальной сети. Проверьте Wi-Fi или Ethernet и попробуйте снова.",
                )
            } catch (e: Exception) {
                logger.e(TAG, "Cannot start LAN login", e)
                AuthUiState.RecoverableError("Не удалось запустить вход с телефона.")
            }
            if (!_uiState.compareAndSet(AuthUiState.CreatingCode, result)) return@launch
            val waiting = result as? AuthUiState.WaitingForPhoneLan ?: return@launch
            delay(PhoneLoginServer.LOGIN_TTL_MS)
            _uiState.compareAndSet(waiting, AuthUiState.Expired("Ссылка устарела. Получите новый QR-код."))
        }
    }

    fun retryAuth() {
        when (lastMode) {
            Mode.LAN -> startPhoneLoginLan()
            Mode.CREDENTIALS -> startCredentialsLogin()
        }
    }

    fun leaveAuthScreen() {
        if (_uiState.value != AuthUiState.Authenticated) cancelAuth()
    }

    fun cancelAuth() {
        authJob?.cancel()
        authJob = null
        viewModelScope.launch(ioDispatcher) {
            authRepository.stopPhoneLogin()
            _uiState.value = AuthUiState.Idle
        }
    }

    fun logout() {
        authJob?.cancel()
        viewModelScope.launch(ioDispatcher) {
            authRepository.stopPhoneLogin()
            authRepository.logout()
            _uiState.value = AuthUiState.Idle
        }
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }
}
