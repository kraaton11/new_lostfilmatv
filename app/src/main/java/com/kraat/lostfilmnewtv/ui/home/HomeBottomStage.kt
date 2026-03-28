package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurfaceStrong
import com.kraat.lostfilmnewtv.ui.theme.HomeTextMuted
import com.kraat.lostfilmnewtv.ui.theme.HomeTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
fun HomeBottomStage(
    item: ReleaseSummary?,
    appVersionText: String,
    appUpdateStatusText: String?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home-bottom-stage")
            .background(HomePanelSurfaceStrong, shape)
            .border(1.dp, HomePanelBorder, shape)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item?.titleRu.orEmpty(),
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 34.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            item?.episodeTitleRu
                ?.takeIf { it.isNotBlank() }
                ?.let { episodeTitle ->
                    Text(
                        text = episodeTitle,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                        ),
                        color = TextPrimary.copy(alpha = 0.92f),
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }

        if (appVersionText.isNotBlank() || !appUpdateStatusText.isNullOrBlank()) {
            Column(
                modifier = Modifier.widthIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Сервис",
                    color = HomeTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (appVersionText.isNotBlank()) {
                    Text(
                        text = appVersionText,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                appUpdateStatusText
                    ?.takeIf { it.isNotBlank() }
                    ?.let { status ->
                        Text(
                            text = status,
                            color = HomeTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }
        }
    }
}
