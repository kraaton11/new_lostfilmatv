package com.kraat.lostfilmnewtv

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kraat.lostfilmnewtv.navigation.AppLaunchTarget
import com.kraat.lostfilmnewtv.navigation.AppNavGraph
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme

class MainActivity : ComponentActivity() {
    private var hasResumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideLauncherBars()
        val initialDetailsUrl = AppLaunchTarget.parseDetailsUrl(intent)
        setContent {
            LostFilmTheme {
                LostFilmApp(initialDetailsUrl = initialDetailsUrl)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) {
            (application as? LostFilmApplication)?.let { app ->
                app.homeChannelBackgroundScheduler.requestImmediateRefresh()
                app.appUpdateBackgroundScheduler.requestImmediateRefresh()
            }
        } else {
            hasResumedOnce = true
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideLauncherBars()
        }
    }

    private fun hideLauncherBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        run {
            window.decorView.systemUiVisibility = FULLSCREEN_FLAGS
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private companion object {
        @Suppress("DEPRECATION")
        const val FULLSCREEN_FLAGS =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}

@Composable
fun LostFilmApp(initialDetailsUrl: String? = null) {
    AppNavGraph(initialDetailsUrl = initialDetailsUrl)
}
