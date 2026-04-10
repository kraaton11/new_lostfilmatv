package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.runtime.Composable
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary

@Composable
fun BottomInfoPanel(item: ReleaseSummary?) {
    HomeBottomStage(
        item = item,
        appVersionText = "",
        appUpdateStatusText = null,
    )
}
