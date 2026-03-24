package com.kraat.lostfilmnewtv.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.launch

private const val HOME_WATCHED_DETAILS_URL_KEY = "home.watched_details_url"
private const val HOME_FAVORITES_INVALIDATED_KEY = "home.favorites_invalidated"
private const val HOME_INSTALL_UPDATE_FAILED_MESSAGE = "Не удалось открыть обновление."
private const val HOME_DOWNLOADING_UPDATE_MESSAGE = "Скачивание обновления…"

@Composable
fun AppNavGraph(initialDetailsUrl: String? = null) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val application = context.applicationContext as LostFilmApplication
    val authViewModel: AuthViewModel = viewModel(
        key = "app-auth",
        factory = AuthViewModel.Factory(application.authRepository),
    )
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val isAuthenticated = authState is AuthUiState.Authenticated
    var selectedPlaybackQuality by remember {
        mutableStateOf(application.playbackPreferencesStore.readDefaultQuality())
    }
    var isHomeFavoritesRailEnabled by remember {
        mutableStateOf(application.playbackPreferencesStore.readHomeFavoritesRailEnabled())
    }

    LaunchedEffect(application) {
        application.homeChannelBackgroundScheduler.syncForCurrentMode()
        application.appUpdateBackgroundScheduler.syncForCurrentMode()
        application.homeChannelSyncManager.syncNow()
    }

    LaunchedEffect(initialDetailsUrl) {
        initialDetailsUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { detailsUrl ->
                navController.navigate(AppDestination.Details.createRoute(detailsUrl)) {
                    launchSingleTop = true
                }
            }
    }

    NavHost(
        navController = navController,
        startDestination = AppDestination.Home.route,
    ) {
        composable(AppDestination.Home.route) { backStackEntry ->
            val homeViewModel: HomeViewModel = viewModel(
                factory = homeViewModelFactory(
                    repository = application.repository,
                    savedStateHandle = backStackEntry.savedStateHandle,
                    onChannelContentChanged = application.homeChannelSyncManager::syncNow,
                    initialFavoritesRailVisible = isAuthenticated && isHomeFavoritesRailEnabled,
                ),
            )
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            val savedAppUpdate by application.appUpdateCoordinator.savedUpdateState.collectAsStateWithLifecycle()
            val watchedDetailsUrl by backStackEntry.savedStateHandle
                .getStateFlow<String?>(HOME_WATCHED_DETAILS_URL_KEY, null)
                .collectAsStateWithLifecycle()
            val favoritesInvalidated by backStackEntry.savedStateHandle
                .getStateFlow(HOME_FAVORITES_INVALIDATED_KEY, false)
                .collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            var homeAppUpdateStatusText by rememberSaveable { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                homeViewModel.onStart()
            }

            LaunchedEffect(isAuthenticated, isHomeFavoritesRailEnabled) {
                homeViewModel.onFavoritesRailVisibilityChanged(
                    isVisible = isAuthenticated && isHomeFavoritesRailEnabled,
                )
            }

            LaunchedEffect(savedAppUpdate) {
                if (savedAppUpdate == null) {
                    homeAppUpdateStatusText = null
                }
            }

            LaunchedEffect(watchedDetailsUrl) {
                watchedDetailsUrl?.let { detailsUrl ->
                    homeViewModel.onItemWatched(detailsUrl)
                    backStackEntry.savedStateHandle[HOME_WATCHED_DETAILS_URL_KEY] = null
                }
            }

            LaunchedEffect(favoritesInvalidated) {
                if (favoritesInvalidated) {
                    homeViewModel.onFavoriteContentInvalidated()
                    backStackEntry.savedStateHandle[HOME_FAVORITES_INVALIDATED_KEY] = false
                }
            }

            HomeScreen(
                state = state,
                onItemFocused = homeViewModel::onItemFocused,
                onOpenDetails = { detailsUrl ->
                    navController.navigate(AppDestination.Details.createRoute(detailsUrl))
                },
                onEndReached = homeViewModel::onEndReached,
                onRetry = homeViewModel::onRetry,
                onPagingRetry = homeViewModel::onPagingRetry,
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
                savedAppUpdate = savedAppUpdate,
                appUpdateStatusText = homeAppUpdateStatusText,
                onInstallUpdateClick = {
                    savedAppUpdate?.let { update ->
                        scope.launch {
                            val opened = application.releaseApkLauncher.launch(
                                context,
                                update.apkUrl,
                            ) { downloading ->
                                homeAppUpdateStatusText = if (downloading) {
                                    HOME_DOWNLOADING_UPDATE_MESSAGE
                                } else {
                                    null
                                }
                            }
                            if (!opened) {
                                homeAppUpdateStatusText = HOME_INSTALL_UPDATE_FAILED_MESSAGE
                            }
                        }
                    }
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
                onFavoriteContentChanged = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(HOME_FAVORITES_INVALIDATED_KEY, true)
                },
                onChannelContentChanged = application.homeChannelSyncManager::syncNow,
            )
        }
        composable(AppDestination.Settings.route) {
            SettingsRoute(
                playbackPreferencesStore = application.playbackPreferencesStore,
                appUpdateCoordinator = application.appUpdateCoordinator,
                onPlaybackQualityChanged = { selectedPlaybackQuality = it },
                onHomeFavoritesRailVisibilityChanged = { isHomeFavoritesRailEnabled = it },
                syncAppUpdateBackgroundSchedule = application.appUpdateBackgroundScheduler::syncForCurrentMode,
                syncAndroidTvChannelBackgroundSchedule = application.homeChannelBackgroundScheduler::syncForCurrentMode,
                syncAndroidTvChannel = application.homeChannelSyncManager::syncNow,
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

private fun homeViewModelFactory(
    repository: LostFilmRepository,
    savedStateHandle: SavedStateHandle,
    onChannelContentChanged: suspend () -> Unit,
    initialFavoritesRailVisible: Boolean,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
            if (modelClass != HomeViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(
                repository = repository,
                savedStateHandle = savedStateHandle,
                onChannelContentChanged = onChannelContentChanged,
                initialFavoritesRailVisible = initialFavoritesRailVisible,
            ) as VM
        }
    }
}
