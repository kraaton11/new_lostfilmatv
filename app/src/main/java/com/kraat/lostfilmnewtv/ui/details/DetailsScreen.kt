package com.kraat.lostfilmnewtv.ui.details

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
fun DetailsScreen(
    state: DetailsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current

    when {
        state.errorMessage != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = state.errorMessage,
                        color = TextPrimary,
                    )
                    Button(onClick = onRetry) {
                        Text("Повторить")
                    }
                }
            }
        }

        else -> {
            val details = state.details
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundPrimary)
                    .verticalScroll(rememberScrollState())
                    .padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Button(onClick = onBack) {
                    Text("Назад")
                }

                if (state.showStaleBanner) {
                    Text(
                        text = "Детали показаны из кэша",
                        color = TextPrimary.copy(alpha = 0.72f),
                    )
                }

                if (details != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        AsyncImage(
                            model = details.posterUrl,
                            contentDescription = details.titleRu,
                            modifier = Modifier
                                .size(width = 220.dp, height = 330.dp)
                                .clip(RoundedCornerShape(20.dp)),
                        )
                        Column(
                            modifier = Modifier.width(520.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = details.titleRu,
                                color = TextPrimary,
                                fontSize = 32.sp,
                            )
                            if (details.kind == ReleaseKind.SERIES && details.seasonNumber != null && details.episodeNumber != null) {
                                Text(
                                    text = "Сезон ${details.seasonNumber}, серия ${details.episodeNumber}",
                                    color = TextPrimary.copy(alpha = 0.8f),
                                    fontSize = 20.sp,
                                )
                            }
                            Text(
                                text = details.releaseDateRu,
                                color = TextPrimary.copy(alpha = 0.72f),
                                fontSize = 20.sp,
                            )
                        }
                    }
                    if (details.torrentLinks.isNotEmpty()) {
                        Text(
                            text = "Ссылки",
                            color = TextPrimary,
                            fontSize = 22.sp,
                        )
                        details.torrentLinks.forEach { link ->
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(link.url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        },
                                    )
                                },
                            ) {
                                Text(link.label)
                            }
                        }
                    }
                } else if (state.isLoading) {
                    Text(
                        text = "Загрузка деталей...",
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}
