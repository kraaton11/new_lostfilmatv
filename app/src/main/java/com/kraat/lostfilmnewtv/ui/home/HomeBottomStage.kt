package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = item?.titleRu.orEmpty(),
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 36.sp,
            )

            item?.episodeTitleRu
                ?.takeIf { it.isNotBlank() }
                ?.let { episodeTitle ->
                    Text(
                        text = episodeTitle,
                        color = HomeTextSecondary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

            item?.releaseDateRu
                ?.takeIf { it.isNotBlank() }
                ?.let { releaseDate ->
                    Text(
                        text = releaseDate,
                        color = HomeTextMuted,
                        fontSize = 16.sp,
                    )
                }
        }

        if (appVersionText.isNotBlank() || !appUpdateStatusText.isNullOrBlank()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Сервис",
                    color = HomeTextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (appVersionText.isNotBlank()) {
                    Text(
                        text = appVersionText,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                appUpdateStatusText
                    ?.takeIf { it.isNotBlank() }
                    ?.let { status ->
                        Text(
                            text = status,
                            color = HomeTextSecondary,
                            fontSize = 14.sp,
                        )
                    }
            }
        }
    }
}
