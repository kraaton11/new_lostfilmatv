package com.kraat.lostfilmnewtv.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.ui.components.PosterImage
import com.kraat.lostfilmnewtv.ui.components.ShimmerSkeletonBox
import com.kraat.lostfilmnewtv.ui.components.rememberShimmerSkeletonBrush
import com.kraat.lostfilmnewtv.ui.components.resolveArtworkForDisplay
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGold
import com.kraat.lostfilmnewtv.ui.theme.DetailsBorderDefault
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceReadable
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceSoft
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

private val overviewArtworkSize = DpSize(192.dp, 132.dp)
private val overviewReadingWidth = 820.dp

@Composable
internal fun OverviewLoadingLayout(tag: String, showSubtitle: Boolean) {
    val brush = rememberShimmerSkeletonBrush(
        label = tag,
        baseColor = DetailsSurfaceSoft,
        highlightColor = DetailsBorderDefault,
        baseAlpha = 0.56f,
        highlightAlpha = 0.66f,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, top = 22.dp, end = 48.dp, bottom = 22.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerSkeletonBox(brush, Modifier.size(overviewArtworkSize), RoundedCornerShape(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShimmerSkeletonBox(brush, Modifier.size(420.dp, 36.dp))
                if (showSubtitle) {
                    ShimmerSkeletonBox(brush, Modifier.size(240.dp, 16.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (width in listOf(84.dp, 64.dp, 100.dp)) {
                        ShimmerSkeletonBox(brush, Modifier.size(width, 26.dp), RoundedCornerShape(20.dp))
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .widthIn(max = overviewReadingWidth)
                .fillMaxWidth()
                .background(DetailsSurfaceReadable.copy(alpha = 0.86f), RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerSkeletonBox(brush, Modifier.size(96.dp, 16.dp))
            for (fraction in listOf(1f, 0.94f, 1f, 0.68f)) {
                ShimmerSkeletonBox(brush, Modifier.fillMaxWidth(fraction).height(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverviewHeader(
    title: String,
    subtitle: String?,
    artwork: String?,
    facts: List<String>,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(overviewArtworkSize).clip(shape)
                .background(DetailsSurfaceSoft)
                .border(1.dp, DetailsBorderDefault, shape),
        ) {
            PosterImage(
                model = resolveArtworkForDisplay(artwork),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() && !it.trim().equals(title.trim(), ignoreCase = true) }?.let {
                Text(it, color = DetailsTextSecondary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (facts.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    facts.forEach { fact ->
                        Text(
                            text = fact,
                            color = DetailsAccentGold,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .background(DetailsSurfaceSoft.copy(alpha = 0.88f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun OverviewReadingBlock(title: String, body: String, tag: String) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .widthIn(max = overviewReadingWidth)
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .testTag(tag)
            .background(DetailsSurfaceReadable.copy(alpha = if (focused) 0.96f else 0.86f), shape)
            .border(1.dp, if (focused) DetailsAccentGold.copy(alpha = 0.68f) else Color.Transparent, shape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (title.isNotBlank()) {
            Text(title, color = DetailsAccentGold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(body, color = TextPrimary, fontSize = 18.sp, lineHeight = 26.sp)
    }
}
