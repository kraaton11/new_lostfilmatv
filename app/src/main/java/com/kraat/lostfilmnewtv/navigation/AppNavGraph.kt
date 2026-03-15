package com.kraat.lostfilmnewtv.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kraat.lostfilmnewtv.ui.home.HomeScreen
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppDestination.Home.route,
    ) {
        composable(AppDestination.Home.route) {
            HomeScreen(
                onOpenDetails = { detailsUrl ->
                    navController.navigate(AppDestination.Details.createRoute(detailsUrl))
                },
            )
        }
        composable(
            route = AppDestination.Details.route,
            arguments = listOf(
                navArgument(AppDestination.Details.detailsUrlArg) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            DetailsPlaceholder(
                detailsUrl = Uri.decode(
                    backStackEntry.arguments?.getString(AppDestination.Details.detailsUrlArg).orEmpty(),
                ),
            )
        }
    }
}

@Composable
private fun DetailsPlaceholder(detailsUrl: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Карточка релиза",
            color = TextPrimary,
        )
        Text(
            text = if (detailsUrl.isBlank()) "Детали появятся позже" else detailsUrl,
            color = TextPrimary.copy(alpha = 0.72f),
        )
    }
}
