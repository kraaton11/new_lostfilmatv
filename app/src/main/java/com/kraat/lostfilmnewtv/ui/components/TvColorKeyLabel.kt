package com.kraat.lostfilmnewtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
internal fun TvColorKeyLabel(label: String, color: Color, selected: Boolean) {
    Row(
        modifier = Modifier
            .border(1.dp, if (selected) color else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(50)))
        Text(
            text = if (selected) "$label ✓" else label,
            color = if (selected) TextPrimary else DetailsTextSecondary,
            fontSize = 13.sp,
        )
    }
}
