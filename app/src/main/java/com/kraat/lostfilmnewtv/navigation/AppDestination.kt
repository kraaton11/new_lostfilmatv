package com.kraat.lostfilmnewtv.navigation

import android.net.Uri

sealed interface AppDestination {
    val route: String

    data object Home : AppDestination {
        override val route: String = "home"
    }

    data object Details : AppDestination {
        const val detailsUrlArg: String = "detailsUrl"

        override val route: String = "details/{$detailsUrlArg}"

        fun createRoute(detailsUrl: String): String = "details/${Uri.encode(detailsUrl)}"
    }
}
