package com.kraat.lostfilmnewtv.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    autoStart: Boolean = false,
    onAuthComplete: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(autoStart) {
        if (autoStart && uiState is AuthUiState.Idle) {
            viewModel.startAuth()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Authenticated) {
            onAuthComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val state = uiState) {
            AuthUiState.CreatingCode -> {
                Text(
                    text = "Вход в LostFilm",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator()
                Text("Создание соединения...")
            }

            AuthUiState.Authenticated -> {
                Text("Вы вошли в аккаунт!", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    viewModel.logout()
                    onNavigateBack()
                }) {
                    Text("Выйти")
                }
            }

            is AuthUiState.WaitingForPhoneOpen,
            is AuthUiState.WaitingForPhoneLogin,
            is AuthUiState.VerifyingSession -> {
                val pairing = when (state) {
                    is AuthUiState.WaitingForPhoneOpen -> state.pairing
                    is AuthUiState.WaitingForPhoneLogin -> state.pairing
                    is AuthUiState.VerifyingSession -> state.pairing
                    else -> null
                }

                Text(
                    text = "Вход в LostFilm",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(12.dp))

                val qrBitmap = remember(pairing?.verificationUrl) {
                    pairing?.verificationUrl?.let { QrCodeGenerator.generateImageBitmap(it, 420) }
                }

                qrBitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "QR code for LostFilm login",
                        modifier = Modifier.size(200.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = "Код для входа:",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = pairing?.userCode ?: "",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 40.sp,
                    lineHeight = 44.sp,
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "1. Откройте QR на телефоне\n2. Войдите в LostFilm\n3. Вернитесь к телевизору, экран обновится сам",
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = when (state) {
                        is AuthUiState.WaitingForPhoneOpen -> "Откройте ссылку на телефоне"
                        is AuthUiState.WaitingForPhoneLogin -> "Продолжайте вход на телефоне"
                        is AuthUiState.VerifyingSession -> "Проверяем вход..."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            is AuthUiState.Expired -> {
                Text(
                    text = "Вход в LostFilm",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = viewModel::retryAuth) {
                    Text("Получить новый код")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onNavigateBack) {
                    Text("Назад")
                }
            }

            is AuthUiState.RecoverableError -> {
                Text(
                    text = "Вход в LostFilm",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = viewModel::retryAuth) {
                    Text("Получить новый код")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onNavigateBack) {
                    Text("Назад")
                }
            }

            AuthUiState.Idle -> {
                Text(
                    text = "Вход в LostFilm",
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { viewModel.startAuth() }) {
                    Text("Начать вход")
                }
            }
        }
    }
}
