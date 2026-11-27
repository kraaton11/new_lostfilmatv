package com.kraat.lostfilmnewtv.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.kraat.lostfilmnewtv.R
import kotlin.math.roundToInt

@Composable
fun PosterImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
    fillLandscapeBackground: Boolean = true,
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
        when (val state = painter.state) {
            is AsyncImagePainter.State.Error -> PosterPlaceholder(Modifier.fillMaxSize())
            is AsyncImagePainter.State.Success -> {
                val size = painter.intrinsicSize
                val scale = if (size.width >= size.height) ContentScale.Fit else contentScale
                val bitmap = (state.result.drawable as? BitmapDrawable)?.bitmap
                if (fillLandscapeBackground && size.width > size.height && bitmap != null) {
                    val background = remember(bitmap) {
                        // Upscaling a tiny copy blends the poster colors on older Android TVs too.
                        val width = 12
                        val height = (width * bitmap.height.toFloat() / bitmap.width)
                            .roundToInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(bitmap, width, height, true).asImageBitmap()
                    }
                    Box(Modifier.fillMaxSize()) {
                        Image(
                            bitmap = background,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                            alpha = alpha * 0.55f,
                        )
                        this@SubcomposeAsyncImage.SubcomposeAsyncImageContent(
                            modifier = Modifier.matchParentSize(),
                            contentScale = scale,
                        )
                    }
                } else {
                    SubcomposeAsyncImageContent(contentScale = scale)
                }
            }
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
