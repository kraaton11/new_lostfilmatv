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
    onAuthComplete: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
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
        Text(
            text = "Вход в LostFilm",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        when {
            uiState.isLoading -> {
                CircularProgressIndicator()
                Text("Создание соединения...")
            }

            uiState.isAuthenticated -> {
                Text("Вы вошли в аккаунт!", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    viewModel.logout()
                    onNavigateBack()
                }) {
                    Text("Выйти")
                }
            }

            uiState.pairing != null -> {
                val qrBitmap = remember(uiState.pairing?.verificationUrl) {
                    uiState.pairing?.verificationUrl?.let { QrCodeGenerator.generateImageBitmap(it, 420) }
                }

                qrBitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "QR code for LostFilm login",
                        modifier = Modifier.size(220.dp),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Text(
                    text = "Код для входа:",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = uiState.pairing?.userCode ?: "",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 56.sp,
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Инструкция:",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "1. Откройте ссылку на другом устройстве\n2. Войдите в LostFilm\n3. Нажмите 'Готово'\n4. Статус обновится автоматически",
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Ссылка для входа:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = uiState.pairing?.verificationUrl ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (uiState.isPolling) {
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator()
                    Text("Ожидание подтверждения...")
                }
            }

            else -> {
                Button(onClick = { viewModel.startAuth() }) {
                    Text("Начать вход")
                }
            }
        }

        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = { viewModel.clearError() }) {
                Text("Повторить")
            }
        }
    }
}
