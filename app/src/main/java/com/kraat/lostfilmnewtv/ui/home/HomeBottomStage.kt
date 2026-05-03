package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
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
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurfaceStrong
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentGold
import com.kraat.lostfilmnewtv.ui.theme.HomeTextMuted
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
fun HomeBottomStage(
    item: ReleaseSummary?,
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
            val episodeLabel = item?.let { r ->
                when (r.kind) {
                    ReleaseKind.SERIES -> {
                        val s = r.seasonNumber?.let { "S${it.toString().padStart(2, '0')}" }
                        val e = r.episodeNumber?.let { "E${it.toString().padStart(2, '0')}" }
                        listOfNotNull(s, e).joinToString("").takeIf { it.isNotBlank() }
                    }
                    ReleaseKind.MOVIE -> "Фильм"
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                episodeLabel?.let {
                    Text(
                        text = it,
                        color = HomeAccentGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
                item?.releaseDateRu?.takeIf { it.isNotBlank() }?.let { date ->
                    Text(
                        text = date,
                        color = HomeTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                item?.episodeTitleRu?.takeIf { it.isNotBlank() }?.let { episodeTitle ->
                    Text(
                        text = episodeTitle,
                        modifier = Modifier.weight(1f, fill = false),
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                        ),
                        color = TextPrimary.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = item?.titleRu.orEmpty(),
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 34.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
