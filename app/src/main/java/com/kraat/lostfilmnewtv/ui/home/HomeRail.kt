package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.ui.components.PosterCard
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurfaceStrong
import com.kraat.lostfilmnewtv.ui.theme.HomeTextSecondary

@Composable
fun HomeRail(
    items: List<ReleaseSummary>,
    focusedItemKey: String?,
    cardFocusRequesters: Map<String, FocusRequester>,
    topActionRequester: FocusRequester?,
    isPaging: Boolean,
    onItemFocused: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onEndReached: () -> Unit,
) {
    LaunchedEffect(items, focusedItemKey) {
        if (items.isNotEmpty()) {
            val targetKey = focusedItemKey ?: items.first().detailsUrl
            val targetRequester = cardFocusRequesters[targetKey] ?: cardFocusRequesters[items.first().detailsUrl]
            withFrameNanos { }
            targetRequester?.requestFocus()
        }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(292.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        itemsIndexed(items = items, key = { _, item -> item.detailsUrl }) { index, item ->
            PosterCard(
                item = item,
                isFocused = item.detailsUrl == focusedItemKey,
                modifier = Modifier
                    .focusRequester(cardFocusRequesters.getValue(item.detailsUrl))
                    .focusProperties {
                        if (topActionRequester != null) {
                            up = topActionRequester
                        }
                    }
                    .testTag(posterTag(item.detailsUrl))
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onItemFocused(item.detailsUrl)
                            if (index == items.lastIndex) {
                                onEndReached()
                            }
                        }
                    }
                    .clickable { onOpenDetails(item.detailsUrl) },
            )
        }

        if (isPaging) {
            item {
                Surface(
                    modifier = Modifier
                        .size(width = 176.dp, height = 264.dp)
                        .focusable(false)
                        .testTag("home-paging-indicator"),
                    shape = RoundedCornerShape(20.dp),
                    color = HomePanelSurfaceStrong,
                    border = androidx.compose.foundation.BorderStroke(1.dp, HomePanelBorder),
                ) {
                    Text(
                        text = "Загрузка...",
                        color = HomeTextSecondary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

internal fun posterTag(detailsUrl: String): String = "poster:$detailsUrl"
