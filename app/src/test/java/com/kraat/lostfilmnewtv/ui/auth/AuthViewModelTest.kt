package com.kraat.lostfilmnewtv.ui.auth

import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.auth.PhoneLoginServer
import com.kraat.lostfilmnewtv.data.model.AuthState
import com.kraat.lostfilmnewtv.tvchannel.NoOpChannelLogger
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @Test
    fun phoneLogin_showsLocalUrlThenOffersNewCodeAfterExpiry() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository, StandardTestDispatcher(testScheduler), NoOpChannelLogger())

        viewModel.startPhoneLoginLan()
        runCurrent()
        assertEquals(AuthUiState.WaitingForPhoneLan(repository.url), viewModel.uiState.value)

        advanceTimeBy(PhoneLoginServer.LOGIN_TTL_MS)
        runCurrent()
        assertTrue(viewModel.uiState.value is AuthUiState.Expired)

        viewModel.retryAuth()
        runCurrent()
        assertEquals(AuthUiState.WaitingForPhoneLan(repository.url), viewModel.uiState.value)
        assertEquals(2, repository.starts)
    }

    @Test
    fun closingScreen_stopsLocalListener() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository, StandardTestDispatcher(testScheduler), NoOpChannelLogger())

        viewModel.startPhoneLoginLan()
        runCurrent()
        viewModel.leaveAuthScreen()
        runCurrent()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
        assertEquals(1, repository.stops)
    }

    @Test
    fun noLan_showsRecoverableError() = runTest {
        val repository = FakeAuthRepository(hasLan = false)
        val viewModel = AuthViewModel(repository, StandardTestDispatcher(testScheduler), NoOpChannelLogger())

        viewModel.startPhoneLoginLan()
        runCurrent()

        assertTrue(viewModel.uiState.value is AuthUiState.RecoverableError)
    }

    @Test
    fun observedSessionCompletesLoginAndExpiryReturnsToIdle() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository, StandardTestDispatcher(testScheduler), NoOpChannelLogger())
        viewModel.startPhoneLoginLan()
        runCurrent()

        repository.authState.value = AuthState(isAuthenticated = true)
        runCurrent()
        assertEquals(AuthUiState.Authenticated, viewModel.uiState.value)

        repository.authState.value = AuthState()
        runCurrent()
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    private class FakeAuthRepository(private val hasLan: Boolean = true) : AuthRepositoryContract {
        val authState = MutableStateFlow(AuthState())
        val url = "http://192.168.1.2:8765/?t=local"
        var starts = 0
        var stops = 0

        override suspend fun getAuthState() = authState.value
        override fun observeAuthState() = authState

        override suspend fun startPhoneLogin(): String {
            if (!hasLan) throw IOException("No local address")
            starts++
            return url
        }

        override suspend fun stopPhoneLogin() {
            stops++
        }

        override suspend fun logout() {
            authState.value = AuthState()
        }
    }
}
