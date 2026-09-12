package com.kraat.lostfilmnewtv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.kraat.lostfilmnewtv.R

@Composable
fun PosterImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
) {
    val effectiveModel = when (model) {
        is String -> model.takeIf { it.isNotBlank() }
        is ImageRequest -> {
            val data = model.data
            if (data is String && data.isBlank()) null else model
        }
        null -> null
        else -> model
    }

    if (effectiveModel == null) {
        PosterPlaceholder(modifier)
        return
    }

    SubcomposeAsyncImage(
        model = effectiveModel,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha,
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Error -> PosterPlaceholder(Modifier.fillMaxSize())
            else -> SubcomposeAsyncImageContent()
        }
    }
}

@Composable
private fun PosterPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_lf_logo),
            contentDescription = null,
            modifier = Modifier.size(48.dp).alpha(0.3f),
        )
    }
}
