package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentGold
import com.kraat.lostfilmnewtv.ui.theme.HomeTextMuted
import com.kraat.lostfilmnewtv.ui.theme.HomeTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
fun HomeBottomStage(
    item: ReleaseSummary?,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = item,
        modifier = modifier
            .fillMaxWidth()
            .testTag("home-bottom-stage"),
        label = "homeBottomStage",
    ) { targetItem ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(78.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to HomeAccentGold.copy(alpha = 0.95f),
                            1f to HomeAccentGold.copy(alpha = 0.2f),
                        ),
                    ),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ReleaseMetaRow(targetItem)

                Text(
                    text = targetItem?.titleRu.orEmpty(),
                    color = TextPrimary,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 32.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                targetItem?.episodeTitleRu?.takeIf { it.isNotBlank() }?.let { episodeTitle ->
                    Text(
                        text = episodeTitle,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                        ),
                        color = HomeTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleaseMetaRow(item: ReleaseSummary?) {
    val episodeLabel = item?.let { release ->
        when (release.kind) {
            ReleaseKind.SERIES -> {
                val season = release.seasonNumber?.let { "S${it.toString().padStart(2, '0')}" }
                val episode = release.episodeNumber?.let { "E${it.toString().padStart(2, '0')}" }
                listOfNotNull(season, episode).joinToString("").takeIf { it.isNotBlank() }
            }

            ReleaseKind.MOVIE -> "Фильм"
        }
    }
    val releaseDate = item?.releaseDateRu?.takeIf { it.isNotBlank() }
    val availabilityLabel = item?.availabilityLabel?.takeIf { it.isNotBlank() }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        availabilityLabel?.let { label ->
            Text(
                text = label,
                color = HomeAccentGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }
        episodeLabel?.let { label ->
            Text(
                text = label,
                color = HomeAccentGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }
        releaseDate?.let { date ->
            Text(
                text = date,
                color = HomeTextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        item?.kind?.takeIf { it == ReleaseKind.SERIES }?.let { kind ->
            Text(
                text = when (kind) {
                    ReleaseKind.SERIES -> "Сериал"
                    ReleaseKind.MOVIE -> "Фильм"
                },
                color = HomeTextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
