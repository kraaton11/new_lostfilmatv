package com.kraat.lostfilmnewtv

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kraat.lostfilmnewtv.navigation.AppLaunchTarget
import com.kraat.lostfilmnewtv.navigation.AppNavGraph
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundScheduler
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import com.kraat.lostfilmnewtv.ui.theme.ThemeController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var homeChannelBackgroundScheduler: HomeChannelBackgroundScheduler
    @Inject lateinit var appUpdateBackgroundScheduler: AppUpdateBackgroundScheduler
    @Inject lateinit var themeController: ThemeController

    private var hasResumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideLauncherBars()
        val initialDetailsUrl = AppLaunchTarget.parseDetailsUrl(intent)
        setContent {
            val theme by themeController.theme.collectAsStateWithLifecycle()
            LostFilmTheme(theme) {
                LostFilmApp(initialDetailsUrl = initialDetailsUrl)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) {
            homeChannelBackgroundScheduler.requestImmediateRefresh()
            appUpdateBackgroundScheduler.requestImmediateRefresh()
        } else {
            hasResumedOnce = true
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideLauncherBars()
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
