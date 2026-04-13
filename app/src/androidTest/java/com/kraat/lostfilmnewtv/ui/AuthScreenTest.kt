package com.kraat.lostfilmnewtv.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kraat.lostfilmnewtv.data.auth.AuthCompletionResult
import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.model.AuthState
import com.kraat.lostfilmnewtv.data.model.PairingSession
import com.kraat.lostfilmnewtv.data.model.PairingStatus
import com.kraat.lostfilmnewtv.ui.auth.AuthScreen
import com.kraat.lostfilmnewtv.ui.auth.AuthViewModel
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class AuthScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun initialState_showsStartSignInButton() {
        composeRule.setAuthContent(
            repository = FakeAuthRepository(
                startPairingResult = pairing(PairingStatus.PENDING),
                pollBehavior = FakePollBehavior.Suspend,
            ),
        )

        composeRule.onNodeWithText("Начать вход").assertIsDisplayed()
    }

    @Test
    fun waitingState_showsApprovedStepsAfterStartingSignIn() {
        composeRule.setAuthContent(
            repository = FakeAuthRepository(
                startPairingResult = pairing(PairingStatus.PENDING),
                pollBehavior = FakePollBehavior.Suspend,
            ),
        )

        composeRule.onNodeWithText("Начать вход").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1. Откройте QR на телефоне\n2. Войдите в LostFilm\n3. Вернитесь к телевизору, экран обновится сам").assertIsDisplayed()
        composeRule.onNodeWithText("Откройте ссылку на телефоне").assertIsDisplayed()
    }

    @Test
    fun verifyingAndRecoverableErrorStates_showExpectedCopy() {
        val completionGate = CompletableDeferred<AuthCompletionResult>()
        composeRule.setAuthContent(
            repository = FakeAuthRepository(
                startPairingResult = pairing(PairingStatus.PENDING),
                pollResults = ArrayDeque(listOf(pairing(PairingStatus.CONFIRMED))),
                completionResult = completionGate,
            ),
        )

        composeRule.onNodeWithText("Начать вход").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Проверяем вход...").assertIsDisplayed()

        completionGate.complete(AuthCompletionResult.NetworkError)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Получить новый код").assertIsDisplayed()
        composeRule.onNodeWithText("Назад").assertIsDisplayed()
    }

    private fun createViewModel(repository: FakeAuthRepository): AuthViewModel {
        return AuthViewModel(
            authRepository = repository,
            ioDispatcher = Dispatchers.Main.immediate,
        )
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.setAuthContent(
        repository: FakeAuthRepository,
    ) {
        val viewModel = createViewModel(repository)
        setContent {
            LostFilmTheme {
                AuthScreen(
                    viewModel = viewModel,
                    onAuthComplete = {},
                    onNavigateBack = {},
                )
            }
        }
    }

    private class FakeAuthRepository(
        authState: AuthState = AuthState(),
        private val startPairingResult: PairingSession,
        private val pollBehavior: FakePollBehavior = FakePollBehavior.Queue,
        private val pollResults: ArrayDeque<PairingSession> = ArrayDeque(),
        private val completionResult: CompletableDeferred<AuthCompletionResult> = CompletableDeferred(AuthCompletionResult.Authenticated),
    ) : AuthRepositoryContract {
        private val authStateFlow = MutableStateFlow(authState)

        override suspend fun getAuthState(): AuthState = authStateFlow.value
        override fun observeAuthState(): Flow<AuthState> = authStateFlow

        override suspend fun startPairing(): PairingSession = startPairingResult

        override suspend fun pollPairingStatus(): PairingSession? {
            return when (pollBehavior) {
                FakePollBehavior.Suspend -> awaitCancellation()
                FakePollBehavior.Throw -> error("poll failed")
                FakePollBehavior.Queue -> pollResults.removeFirstOrNull() ?: startPairingResult
            }
        }

        override suspend fun claimAndPersistSession(): AuthCompletionResult = completionResult.await()

        override suspend fun logout() {
            authStateFlow.value = AuthState()
        }
    }

    private enum class FakePollBehavior {
        Queue,
        Suspend,
        Throw,
    }

    private companion object {
        fun pairing(
            status: PairingStatus,
            pollInterval: Int = 0,
        ): PairingSession = PairingSession(
            pairingId = "pair-123",
            pairingSecret = "secret-456",
            phoneVerifier = "phone-789",
            userCode = "ABC123",
            verificationUrl = "https://auth.example.test/pair/phone-789",
            status = status,
            expiresIn = 120,
            pollInterval = pollInterval,
        )
    }
}
