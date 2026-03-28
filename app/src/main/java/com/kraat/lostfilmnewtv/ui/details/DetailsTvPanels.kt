package com.kraat.lostfilmnewtv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentBlue
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGold
import com.kraat.lostfilmnewtv.ui.theme.DetailsBorderDefault
import com.kraat.lostfilmnewtv.ui.theme.DetailsStatusError
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceReadable
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextMuted
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
internal fun DetailsStatusPanel(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val background = if (isError) Color(0xCC2A0E10) else DetailsSurfaceReadable
    val borderColor = if (isError) DetailsStatusError.copy(alpha = 0.42f) else DetailsAccentBlue.copy(alpha = 0.35f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background, shape)
            .border(1.5.dp, borderColor, shape)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = text,
            color = if (isError) TextPrimary else DetailsTextSecondary,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = DetailsAccentGold),
                modifier = Modifier.widthIn(min = 140.dp),
            ) {
                Text(
                    text = actionLabel,
                    color = Color(0xFF17120D),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun DetailsCenteredStatePanel(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)

    Column(
        modifier = Modifier
            .background(DetailsSurfaceReadable, shape)
            .border(1.dp, DetailsBorderDefault, shape)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
internal fun DetailsBottomStage(
    statusLine: String,
    supportLine: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("details-bottom-stage")
            .background(DetailsSurfaceReadable, shape)
            .border(1.dp, DetailsBorderDefault, shape)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Смотреть",
                color = DetailsTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = statusLine,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 28.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (supportLine.isNotBlank()) {
            Column(
                modifier = Modifier.widthIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Релиз",
                    color = DetailsTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = supportLine,
                    color = DetailsTextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
