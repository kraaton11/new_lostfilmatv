package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
fun SettingsScreen(
    selectedQuality: PlaybackQualityPreference,
    onQualitySelected: (PlaybackQualityPreference) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "Настройки",
            color = TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Качество по умолчанию",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            PlaybackQualityPreference.entries.forEach { quality ->
                QualityButton(
                    quality = quality,
                    isSelected = quality == selectedQuality,
                    onClick = { onQualitySelected(quality) },
                )
            }
        }
    }
}

@Composable
private fun QualityButton(
    quality: PlaybackQualityPreference,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-quality-${quality.storageValue}")
            .semantics { selected = isSelected },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFFF2C46E) else Color(0xFF16293C),
            contentColor = if (isSelected) Color(0xFF17120D) else TextPrimary,
        ),
    ) {
        Text(
            text = quality.label(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun PlaybackQualityPreference.label(): String {
    return when (this) {
        PlaybackQualityPreference.Q1080 -> "1080p"
        PlaybackQualityPreference.Q720 -> "720p"
        PlaybackQualityPreference.Q480 -> "480p / SD"
    }
}
