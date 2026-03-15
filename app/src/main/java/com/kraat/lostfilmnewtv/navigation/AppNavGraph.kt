package com.kraat.lostfilmnewtv.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kraat.lostfilmnewtv.LostFilmApplication
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.ui.details.DetailsScreen
import com.kraat.lostfilmnewtv.ui.details.DetailsViewModel
import com.kraat.lostfilmnewtv.ui.home.HomeScreen
import com.kraat.lostfilmnewtv.ui.home.HomeViewModel

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as LostFilmApplication

    NavHost(
        navController = navController,
        startDestination = AppDestination.Home.route,
    ) {
        composable(AppDestination.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = repositoryViewModelFactory(application.repository) { repository ->
                    HomeViewModel(
                        repository = repository,
                        savedStateHandle = SavedStateHandle(),
                    )
                },
            )
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                homeViewModel.onStart()
            }

            HomeScreen(
                state = state,
                onItemFocused = homeViewModel::onItemFocused,
                onOpenDetails = { detailsUrl ->
                    navController.navigate(AppDestination.Details.createRoute(detailsUrl))
                },
                onEndReached = homeViewModel::onEndReached,
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
            val detailsUrl = Uri.decode(
                backStackEntry.arguments?.getString(AppDestination.Details.detailsUrlArg).orEmpty(),
            )
            val detailsViewModel: DetailsViewModel = viewModel(
                key = "details:$detailsUrl",
                factory = repositoryViewModelFactory(application.repository) { repository ->
                    DetailsViewModel(
                        repository = repository,
                        savedStateHandle = SavedStateHandle(
                            mapOf(AppDestination.Details.detailsUrlArg to detailsUrl),
                        ),
                    )
                },
            )
            val state by detailsViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(detailsUrl) {
                detailsViewModel.onStart()
            }

            DetailsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRetry = detailsViewModel::onRetry,
            )
        }
    }
}

private fun <T : ViewModel> repositoryViewModelFactory(
    repository: LostFilmRepository,
    creator: (LostFilmRepository) -> T,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
            @Suppress("UNCHECKED_CAST")
            return creator(repository) as VM
        }
    }
}
