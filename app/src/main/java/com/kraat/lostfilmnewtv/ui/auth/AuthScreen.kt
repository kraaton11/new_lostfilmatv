package com.kraat.lostfilmnewtv.ui.auth

import android.graphics.BitmapFactory
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    autoStart: Boolean = false,
    onAuthComplete: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        onDispose { viewModel.leaveAuthScreen() }
    }

    LaunchedEffect(autoStart) {
        if (autoStart && uiState is AuthUiState.Idle) {
            viewModel.startPhoneLoginLan()
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

            is AuthUiState.WaitingForPhoneLan -> {
                val qrBitmap = remember(state.url) { QrCodeGenerator.generateImageBitmap(state.url, 360) }

                Text("Вход с телефона", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(12.dp))
                qrBitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "QR code for phone login",
                        modifier = Modifier.size(200.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text(
                    text = "1. Подключите телефон к той же Wi-Fi сети, что и ТВ\n" +
                        "2. Отсканируйте QR и войдите в LostFilm\n" +
                        "3. Вернитесь к ТВ, экран обновится сам",
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ссылка действует 10 минут",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = viewModel::startPhoneLoginLan) {
                        Text("Обновить QR-код")
                    }
                    OutlinedButton(onClick = onNavigateBack) { Text("Отменить") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = viewModel::startCredentialsLogin) {
                    Text("Ввести логин и пароль на ТВ")
                }
            }

            is AuthUiState.EnterCredentials -> {
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var captcha by remember { mutableStateOf("") }
                val passwordFocus = remember { FocusRequester() }
                val captchaFocus = remember { FocusRequester() }
                val submit = { viewModel.submitCredentials(email, password, captcha) }
                val captchaBitmap = remember(state.captchaImage) {
                    state.captchaImage?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                }

                Text("Вход в LostFilm", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail") },
                    singleLine = true,
                    enabled = !state.submitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                    modifier = Modifier.width(420.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    enabled = !state.submitting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (captchaBitmap != null) ImeAction.Next else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { captchaFocus.requestFocus() },
                        onDone = { submit() },
                    ),
                    modifier = Modifier.width(420.dp).focusRequester(passwordFocus),
                )
                if (captchaBitmap != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Image(
                        bitmap = captchaBitmap,
                        contentDescription = "Captcha",
                        modifier = Modifier.height(60.dp),
                    )
                    OutlinedTextField(
                        value = captcha,
                        onValueChange = { captcha = it },
                        label = { Text("Код с картинки") },
                        singleLine = true,
                        enabled = !state.submitting,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.width(420.dp).focusRequester(captchaFocus),
                    )
                    TextButton(onClick = viewModel::refreshCaptcha) { Text("Обновить картинку") }
                }
                state.message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { submit() }, enabled = !state.submitting) {
                    Text(if (state.submitting) "Входим..." else "Войти")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    onNavigateBack()
                }) { Text("Отмена") }
            }

            AuthUiState.Idle -> {
                Text(
                    text = "Вход в LostFilm",
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { viewModel.startPhoneLoginLan() }) {
                    Text("Войти с телефона (по Wi-Fi)")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { viewModel.startCredentialsLogin() }) {
                    Text("Ввести логин и пароль на ТВ")
                }
            }
        }
    }
}
