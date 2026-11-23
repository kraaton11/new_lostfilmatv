package com.kraat.lostfilmnewtv.data.auth

import com.kraat.lostfilmnewtv.data.model.AuthState
import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import com.kraat.lostfilmnewtv.data.network.LostFilmLoginClient
import com.kraat.lostfilmnewtv.data.network.LostFilmSessionVerifier
import com.kraat.lostfilmnewtv.data.repository.FavoritesRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class AuthRepository(
    private val sessionStore: SessionStore,
    private val sessionVerifier: LostFilmSessionVerifier,
    private val verificationMaxAttempts: Int = 3,
    private val verificationRetryDelayMillis: Long = 250L,
    private val loginClient: LostFilmLoginClient,
    private val favoritesRepository: FavoritesRepository? = null,
) : AuthRepositoryContract {
    // Сервер для входа с телефона по локальной сети; использует тот же loginWithPassword.
    private val phoneLoginServer by lazy {
        PhoneLoginServer(
            login = { email, password, captcha -> loginWithPassword(email, password, captcha) },
            captcha = { loadCaptcha() },
        )
    }

    override suspend fun getAuthState(): AuthState {
        val session = sessionStore.read()
            ?: return AuthState(isAuthenticated = false, session = null)
        if (sessionStore.isExpired()) {
            return AuthState(isAuthenticated = false, session = session)
        }

        return when (verifySessionWithRetries(session)) {
            VerificationResult.AUTHENTICATED -> AuthState(
                isAuthenticated = true,
                session = session,
            )

            VerificationResult.UNAUTHENTICATED -> {
                sessionStore.markExpired()
                AuthState(
                    isAuthenticated = false,
                    session = session,
                )
            }

            VerificationResult.NETWORK_ERROR -> AuthState(
                isAuthenticated = true,
                session = session,
            )
        }
    }

    override fun observeAuthState(): Flow<AuthState> {
        return sessionStore.changes()
            .onStart { emit(Unit) }
            .map { getAuthState() }
            .distinctUntilChanged()
    }

    private suspend fun verifySessionWithRetries(
        session: LostFilmSession,
    ): VerificationResult {
        val attempts = verificationMaxAttempts.coerceAtLeast(1)
        repeat(attempts) { attempt ->
            try {
                return if (sessionVerifier.verify(session)) {
                    VerificationResult.AUTHENTICATED
                } else {
                    VerificationResult.UNAUTHENTICATED
                }
            } catch (e: IOException) {
                if (attempt == attempts - 1) {
                    return VerificationResult.NETWORK_ERROR
                }
                delay(verificationRetryDelayMillis)
            }
        }

        return VerificationResult.NETWORK_ERROR
    }

    override suspend fun startPhoneLogin(): String {
        beginPasswordLogin()
        return withContext(Dispatchers.IO) { phoneLoginServer.start() }
    }

    override suspend fun stopPhoneLogin() {
        phoneLoginServer.stop()
    }

    override suspend fun beginPasswordLogin() {
        loginClient.reset()
        try {
            loginClient.warmUp()
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            // Не критично: cookie придут и с самим запросом логина.
        }
    }

    override suspend fun loadCaptcha(): ByteArray =
        loginClient.fetchCaptcha()

    override suspend fun loginWithPassword(
        email: String,
        password: String,
        captcha: String?,
    ): PasswordLoginResult {
        val result = try {
            loginClient.login(email, password, captcha)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            return PasswordLoginResult.NetworkError
        }

        return when (result) {
            LostFilmLoginClient.LoginResult.NeedCaptcha -> PasswordLoginResult.NeedCaptcha
            LostFilmLoginClient.LoginResult.WrongCaptcha -> PasswordLoginResult.WrongCaptcha
            LostFilmLoginClient.LoginResult.InvalidCredentials -> PasswordLoginResult.InvalidCredentials
            is LostFilmLoginClient.LoginResult.Success -> when (verifySessionWithRetries(result.session)) {
                VerificationResult.AUTHENTICATED -> {
                    currentCoroutineContext().ensureActive()
                    changeSession { sessionStore.save(result.session) }
                    PasswordLoginResult.Authenticated
                }
                VerificationResult.UNAUTHENTICATED -> PasswordLoginResult.VerificationFailed
                VerificationResult.NETWORK_ERROR -> PasswordLoginResult.NetworkError
            }
        }
    }

    override suspend fun logout() {
        changeSession { sessionStore.clear() }
    }

    private suspend fun changeSession(change: suspend () -> Unit) {
        val favorites = favoritesRepository
        if (favorites == null) change() else favorites.changeSession(change)
    }

    private enum class VerificationResult {
        AUTHENTICATED,
        UNAUTHENTICATED,
        NETWORK_ERROR,
    }

}
