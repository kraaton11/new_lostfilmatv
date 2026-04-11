package com.kraat.lostfilmnewtv.navigation

import android.net.Uri

sealed interface AppDestination {
    val route: String

    data object Home : AppDestination {
        override val route: String = "home"
    }

    data object Details : AppDestination {
        const val detailsUrlArg: String = "detailsUrl"
        const val isAuthenticatedArg: String = "isAuthenticated"

        override val route: String = "details/{$detailsUrlArg}/{$isAuthenticatedArg}"

        fun createRoute(detailsUrl: String, isAuthenticated: Boolean): String =
            "details/${Uri.encode(detailsUrl)}/$isAuthenticated"
    }

    data object SeriesGuide : AppDestination {
        const val detailsUrlArg: String = "detailsUrl"

        override val route: String = "series-guide/{$detailsUrlArg}"

        fun createRoute(detailsUrl: String): String = "series-guide/${Uri.encode(detailsUrl)}"
    }

    data object Auth : AppDestination {
        override val route: String = "auth"
    }

    data object Settings : AppDestination {
        override val route: String = "settings"
    }
}
