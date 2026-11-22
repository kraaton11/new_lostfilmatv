package com.kraat.lostfilmnewtv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl
import com.kraat.lostfilmnewtv.data.parser.lostFilmArtworkUrl

@Composable
internal fun resolveArtworkForDisplay(path: String?): String? =
    lostFilmArtworkUrl(path, baseUrl = LostFilmBaseUrl.url.collectAsState().value)
