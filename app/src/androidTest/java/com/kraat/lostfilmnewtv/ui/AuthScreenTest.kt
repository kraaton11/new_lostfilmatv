package com.kraat.lostfilmnewtv.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.model.AuthState
import com.kraat.lostfilmnewtv.ui.auth.AuthScreen
import com.kraat.lostfilmnewtv.ui.auth.AuthViewModel
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class AuthScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun startSignIn_showsLocalQrCode() {
        setAuthContent()

        composeRule.onNodeWithText("Войти с телефона (по Wi-Fi)").performClick()

        composeRule.onNodeWithContentDescription("QR code for phone login").assertIsDisplayed()
        composeRule.onNodeWithText("Ссылка действует 10 минут").assertIsDisplayed()
        composeRule.onNodeWithText("Отменить").assertIsDisplayed()
    }

    @Test
    fun autoStartWithoutLan_offersRetry() {
        setAuthContent(hasLan = false, autoStart = true)

        composeRule.onNodeWithText("Получить новый код").assertIsDisplayed()
        composeRule.onNodeWithText("Назад").assertIsDisplayed()
    }

    private fun setAuthContent(hasLan: Boolean = true, autoStart: Boolean = false) {
        val viewModel = AuthViewModel(FakeAuthRepository(hasLan), Dispatchers.Main.immediate)
        composeRule.setContent {
            LostFilmTheme {
                AuthScreen(
                    viewModel = viewModel,
                    autoStart = autoStart,
                    onAuthComplete = {},
                    onNavigateBack = {},
                )
            }
        }
    }

    private class FakeAuthRepository(private val hasLan: Boolean) : AuthRepositoryContract {
        private val authState = MutableStateFlow(AuthState())

        override suspend fun getAuthState() = authState.value
        override fun observeAuthState() = authState
        override suspend fun startPairing(): Nothing = error("Unexpected bridge request")
        override suspend fun pollPairingStatus(): Nothing = error("Unexpected bridge request")
        override suspend fun claimAndPersistSession(): Nothing = error("Unexpected bridge request")
        override suspend fun cancelPairing(): Nothing = error("Unexpected bridge request")
        override suspend fun logout() = Unit

        override suspend fun startPhoneLogin(): String {
            if (!hasLan) throw IOException("No local address")
            return "http://192.168.1.2:8765/?t=local"
        }
    }
}
