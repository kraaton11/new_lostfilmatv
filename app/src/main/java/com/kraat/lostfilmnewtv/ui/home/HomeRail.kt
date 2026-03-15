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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.ui.components.PosterCard

@Composable
fun HomeRail(
    items: List<ReleaseSummary>,
    focusedItemKey: String?,
    isPaging: Boolean,
    onItemFocused: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onEndReached: () -> Unit,
) {
    val itemKeys = remember(items) { items.map { it.detailsUrl } }
    val focusRequesters = remember(itemKeys) { itemKeys.associateWith { FocusRequester() } }

    LaunchedEffect(items, focusedItemKey) {
        if (items.isNotEmpty()) {
            val targetKey = focusedItemKey ?: items.first().detailsUrl
            val targetRequester = focusRequesters[targetKey] ?: focusRequesters[items.first().detailsUrl]
            targetRequester?.requestFocus()
        }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(292.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(horizontal = 48.dp),
    ) {
        itemsIndexed(items = items, key = { _, item -> item.detailsUrl }) { index, item ->
            PosterCard(
                item = item,
                isFocused = item.detailsUrl == focusedItemKey,
                modifier = Modifier
                    .focusRequester(focusRequesters.getValue(item.detailsUrl))
                    .testTag("poster-$index")
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
                        .focusable(false),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        text = "Загрузка...",
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
