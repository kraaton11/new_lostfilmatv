package com.kraat.lostfilmnewtv.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.ui.auth.AuthScreen
import com.kraat.lostfilmnewtv.ui.auth.AuthUiState
import com.kraat.lostfilmnewtv.ui.auth.AuthViewModel
import com.kraat.lostfilmnewtv.ui.details.DetailsRoute
import com.kraat.lostfilmnewtv.ui.home.HomeScreen
import com.kraat.lostfilmnewtv.ui.home.HomeViewModel
import com.kraat.lostfilmnewtv.ui.settings.SettingsRoute

private const val HOME_WATCHED_DETAILS_URL_KEY = "home.watched_details_url"

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as LostFilmApplication
    val authViewModel: AuthViewModel = viewModel(
        key = "app-auth",
        factory = AuthViewModel.Factory(application.authRepository),
    )
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val isAuthenticated = authState is AuthUiState.Authenticated
    var selectedPlaybackQuality by remember {
        mutableStateOf(application.playbackPreferencesStore.readDefaultQuality())
    }

    NavHost(
        navController = navController,
        startDestination = AppDestination.Home.route,
    ) {
        composable(AppDestination.Home.route) { backStackEntry ->
            val homeViewModel: HomeViewModel = viewModel(
                factory = repositoryViewModelFactory(application.repository) { repository ->
                    HomeViewModel(
                        repository = repository,
                        savedStateHandle = SavedStateHandle(),
                    )
                },
            )
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            val watchedDetailsUrl by backStackEntry.savedStateHandle
                .getStateFlow<String?>(HOME_WATCHED_DETAILS_URL_KEY, null)
                .collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                homeViewModel.onStart()
            }

            LaunchedEffect(watchedDetailsUrl) {
                watchedDetailsUrl?.let { detailsUrl ->
                    homeViewModel.onItemWatched(detailsUrl)
                    backStackEntry.savedStateHandle[HOME_WATCHED_DETAILS_URL_KEY] = null
                }
            }

            HomeScreen(
                state = state,
                onItemFocused = homeViewModel::onItemFocused,
                onOpenDetails = { detailsUrl ->
                    navController.navigate(AppDestination.Details.createRoute(detailsUrl))
                },
                onEndReached = homeViewModel::onEndReached,
                onAuthClick = {
                    if (isAuthenticated) {
                        authViewModel.logout()
                    } else {
                        navController.navigate(AppDestination.Auth.route)
                    }
                },
                onSettingsClick = {
                    navController.navigate(AppDestination.Settings.route)
                },
                isAuthenticated = isAuthenticated,
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
            DetailsRoute(
                detailsUrl = detailsUrl,
                repository = application.repository,
                isAuthenticated = isAuthenticated,
                preferredPlaybackQuality = selectedPlaybackQuality,
                actionHandler = application.torrServeActionHandler,
                linkBuilder = application.torrServeLinkBuilder,
                onMarkedWatched = { watchedDetailsUrl ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(HOME_WATCHED_DETAILS_URL_KEY, watchedDetailsUrl)
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppDestination.Settings.route) {
            SettingsRoute(
                playbackPreferencesStore = application.playbackPreferencesStore,
                appUpdateRepository = application.appUpdateRepository,
                onPlaybackQualityChanged = { selectedPlaybackQuality = it },
                openInstallApk = application.releaseApkLauncher::launch,
            )
        }
        composable(AppDestination.Auth.route) {
            AuthScreen(
                viewModel = authViewModel,
                onAuthComplete = {
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
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
