package com.kraat.lostfilmnewtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.kraat.lostfilmnewtv.navigation.AppNavGraph
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LostFilmTheme {
                LostFilmApp()
            }
        }
    }
}

@Composable
fun LostFilmApp() {
    AppNavGraph()
}
