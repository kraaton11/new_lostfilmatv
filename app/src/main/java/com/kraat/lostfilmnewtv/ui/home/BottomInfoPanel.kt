package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
fun BottomInfoPanel(item: ReleaseSummary?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = item?.titleRu.orEmpty(),
            color = TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
        )

        if (item?.kind == ReleaseKind.SERIES && !item.episodeTitleRu.isNullOrBlank()) {
            Text(
                text = item.episodeTitleRu,
                color = TextPrimary.copy(alpha = 0.82f),
                fontSize = 20.sp,
            )
        }

        if (item != null) {
            Text(
                text = item.releaseDateRu,
                color = TextPrimary.copy(alpha = 0.72f),
                fontSize = 18.sp,
            )
        }
    }
}
