package com.kraat.lostfilmnewtv.ui.auth

import com.kraat.lostfilmnewtv.data.auth.AuthCompletionResult
import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.model.AuthState
import com.kraat.lostfilmnewtv.data.model.PairingSession
import com.kraat.lostfilmnewtv.data.model.PairingStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun startAuth_setsWaitingForPhoneOpen_afterPairingCreated() = runTest(dispatcher) {
        val repository = FakeAuthRepository(
            startPairingResult = pairing(status = PairingStatus.PENDING),
            pollBehavior = FakePollBehavior.Suspend,
        )
        val viewModel = AuthViewModel(
            authRepository = repository,
            ioDispatcher = dispatcher,
        )

        viewModel.startAuth()
        advanceUntilIdle()

        assertEquals(AuthUiState.WaitingForPhoneOpen(pairing(status = PairingStatus.PENDING)), viewModel.uiState.value)
    }

    @Test
    fun pollingMovesFromPendingToInProgressToVerifyingBeforeSuccess() = runTest(dispatcher) {
        val completionGate = CompletableDeferred<AuthCompletionResult>()
        val repository = FakeAuthRepository(
            startPairingResult = pairing(status = PairingStatus.PENDING),
            pollResults = ArrayDeque(
                listOf(
                    pairing(status = PairingStatus.IN_PROGRESS),
                    pairing(status = PairingStatus.CONFIRMED),
                ),
            ),
            completionResult = completionGate,
        )
        val viewModel = AuthViewModel(
            authRepository = repository,
            ioDispatcher = dispatcher,
        )

        viewModel.startAuth()
        advanceUntilIdle()
        assertEquals(AuthUiState.VerifyingSession(pairing(status = PairingStatus.CONFIRMED)), viewModel.uiState.value)

        completionGate.complete(AuthCompletionResult.Authenticated)
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated, viewModel.uiState.value)
    }

    @Test
    fun expiredPairing_exposesGetNewCodeRecoveryState() = runTest(dispatcher) {
        val repository = FakeAuthRepository(
            startPairingResult = pairing(status = PairingStatus.PENDING),
            pollResults = ArrayDeque(listOf(pairing(status = PairingStatus.EXPIRED))),
        )
        val viewModel = AuthViewModel(
            authRepository = repository,
            ioDispatcher = dispatcher,
        )

        viewModel.startAuth()
        advanceUntilIdle()

        assertEquals(
            AuthUiState.Expired(
                message = "Код входа истек. Получите новый код.",
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun pollingFailure_transitionsToRecoverableError() = runTest(dispatcher) {
        val repository = FakeAuthRepository(
            startPairingResult = pairing(status = PairingStatus.PENDING),
            pollBehavior = FakePollBehavior.Throw,
        )
        val viewModel = AuthViewModel(
            authRepository = repository,
            ioDispatcher = dispatcher,
        )

        viewModel.startAuth()
        advanceUntilIdle()

        assertEquals(
            AuthUiState.RecoverableError(
                message = "Не удалось завершить вход. Получите новый код.",
            ),
            viewModel.uiState.value,
        )
    }

    private class FakeAuthRepository(
        private val authState: AuthState = AuthState(),
        private val startPairingResult: PairingSession,
        private val pollBehavior: FakePollBehavior = FakePollBehavior.Queue,
        private val pollResults: ArrayDeque<PairingSession> = ArrayDeque(),
        private val completionResult: CompletableDeferred<AuthCompletionResult> = CompletableDeferred(AuthCompletionResult.Authenticated),
    ) : AuthRepositoryContract {
        override suspend fun getAuthState(): AuthState = authState

        override suspend fun startPairing(): PairingSession = startPairingResult

        override suspend fun pollPairingStatus(): PairingSession? {
            return when (pollBehavior) {
                FakePollBehavior.Suspend -> {
                    awaitCancellation()
                }

                FakePollBehavior.Throw -> error("poll failed")

                FakePollBehavior.Queue -> pollResults.removeFirstOrNull() ?: startPairingResult
            }
        }

        override suspend fun claimAndPersistSession(): AuthCompletionResult = completionResult.await()

        override suspend fun logout() = Unit
    }

    private enum class FakePollBehavior {
        Queue,
        Suspend,
        Throw,
    }

    private fun pairing(
        status: PairingStatus,
        pollInterval: Int = 0,
    ): PairingSession = PairingSession(
        pairingId = "pair-123",
        pairingSecret = "secret-456",
        phoneVerifier = "phone-789",
        userCode = "ABC123",
        verificationUrl = "https://phone-789.auth.example.test/",
        status = status,
        expiresIn = 120,
        pollInterval = pollInterval,
    )
}
