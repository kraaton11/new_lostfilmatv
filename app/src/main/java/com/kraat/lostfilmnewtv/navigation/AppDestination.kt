package com.kraat.lostfilmnewtv.navigation

import android.net.Uri

sealed interface AppDestination {
    val route: String

    data object Home : AppDestination {
        override val route: String = "home"
    }

    data object Search : AppDestination {
        override val route: String = "search"
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

    data object SeriesOverview : AppDestination {
        const val detailsUrlArg: String = "detailsUrl"

        override val route: String = "series-overview/{$detailsUrlArg}"

        fun createRoute(detailsUrl: String): String = "series-overview/${Uri.encode(detailsUrl)}"
    }

    data object MovieOverview : AppDestination {
        const val detailsUrlArg: String = "detailsUrl"

        override val route: String = "movie-overview/{$detailsUrlArg}"

        fun createRoute(detailsUrl: String): String = "movie-overview/${Uri.encode(detailsUrl)}"
    }

    data object Auth : AppDestination {
        const val autoStartArg: String = "autoStart"
        private const val baseRoute: String = "auth"

        override val route: String = "$baseRoute?$autoStartArg={$autoStartArg}"

        fun createRoute(autoStart: Boolean = false): String = "$baseRoute?$autoStartArg=$autoStart"
    }

    data object Settings : AppDestination {
        override val route: String = "settings"
    }
}
