package com.kraat.lostfilmnewtv.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.EntryPointAccessors
import com.kraat.lostfilmnewtv.di.AppGraphEntryPoint
import com.kraat.lostfilmnewtv.ui.auth.AuthScreen
import com.kraat.lostfilmnewtv.ui.auth.AuthUiState
import com.kraat.lostfilmnewtv.ui.auth.AuthViewModel
import com.kraat.lostfilmnewtv.ui.details.DetailsRoute
import com.kraat.lostfilmnewtv.ui.guide.SeriesGuideRoute
import com.kraat.lostfilmnewtv.ui.home.HomeScreen
import com.kraat.lostfilmnewtv.ui.overview.MovieOverviewRoute
import com.kraat.lostfilmnewtv.ui.overview.SeriesOverviewRoute
import com.kraat.lostfilmnewtv.ui.search.SearchRoute
import com.kraat.lostfilmnewtv.ui.home.HomeViewModel
import com.kraat.lostfilmnewtv.ui.settings.SettingsRoute
import com.kraat.lostfilmnewtv.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

private const val HOME_WATCHED_DETAILS_URL_KEY = "home.watched_details_url"
private const val HOME_WATCHED_CHANGED_STATE_KEY = "home.watched_changed_state"
private const val HOME_FAVORITE_CHANGED_DETAILS_URL_KEY = "home.favorite_changed_details_url"
private const val HOME_FAVORITE_CHANGED_STATE_KEY = "home.favorite_changed_state"

@Composable
fun AppNavGraph(initialDetailsUrl: String? = null) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val appContext = context.applicationContext
    val appGraphEntryPoint = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, AppGraphEntryPoint::class.java)
    }

    // AuthViewModel scoped to the NavHost — один экземпляр на весь граф
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val isAuthenticated = authState is AuthUiState.Authenticated

    LaunchedEffect(initialDetailsUrl) {
        initialDetailsUrl?.takeIf { it.isNotBlank() }?.let { url ->
            navController.navigate(AppDestination.Details.createRoute(url, isAuthenticated)) {
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = AppDestination.Home.route) {

        // ── Home ──────────────────────────────────────────────────────────────
        composable(AppDestination.Home.route) { backStackEntry ->
            val homeViewModel: HomeViewModel = hiltViewModel()
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            val savedAppUpdate by homeViewModel.savedAppUpdate.collectAsStateWithLifecycle()

            val watchedDetailsUrl by backStackEntry.savedStateHandle
                .getStateFlow<String?>(HOME_WATCHED_DETAILS_URL_KEY, null)
                .collectAsStateWithLifecycle()
            val watchedChangedState by backStackEntry.savedStateHandle
                .getStateFlow<Boolean?>(HOME_WATCHED_CHANGED_STATE_KEY, null)
                .collectAsStateWithLifecycle()
            val favoriteChangedDetailsUrl by backStackEntry.savedStateHandle
                .getStateFlow<String?>(HOME_FAVORITE_CHANGED_DETAILS_URL_KEY, null)
                .collectAsStateWithLifecycle()
            val favoriteChangedState by backStackEntry.savedStateHandle
                .getStateFlow<Boolean?>(HOME_FAVORITE_CHANGED_STATE_KEY, null)
                .collectAsStateWithLifecycle()

            LaunchedEffect(Unit) { homeViewModel.onStart() }
            LaunchedEffect(isAuthenticated) { homeViewModel.onFavoriteContentInvalidated() }

            LaunchedEffect(watchedDetailsUrl, watchedChangedState) {
                val url = watchedDetailsUrl
                val isWatched = watchedChangedState
                if (url != null && isWatched != null) {
                    homeViewModel.onItemWatchedStateChanged(url, isWatched)
                    backStackEntry.savedStateHandle[HOME_WATCHED_DETAILS_URL_KEY] = null
                    backStackEntry.savedStateHandle[HOME_WATCHED_CHANGED_STATE_KEY] = null
                }
            }
            LaunchedEffect(favoriteChangedDetailsUrl, favoriteChangedState) {
                val url = favoriteChangedDetailsUrl
                val isFav = favoriteChangedState
                if (url != null && isFav != null) {
                    homeViewModel.onFavoriteStateChanged(url, isFav)
                    backStackEntry.savedStateHandle[HOME_FAVORITE_CHANGED_DETAILS_URL_KEY] = null
                    backStackEntry.savedStateHandle[HOME_FAVORITE_CHANGED_STATE_KEY] = null
                }
            }

            HomeScreen(
                state = state,
                onItemFocused = homeViewModel::onItemFocused,
                onModeSelected = homeViewModel::onModeSelected,
                onOpenDetails = { url -> navController.navigate(AppDestination.Details.createRoute(url, isAuthenticated)) },
                onOpenSeriesOverview = { url -> navController.navigate(AppDestination.SeriesOverview.createRoute(url)) },
                onEndReached = homeViewModel::onEndReached,
                onRetry = homeViewModel::onRetry,
                onPagingRetry = homeViewModel::onPagingRetry,
                onAuthClick = {
                    if (isAuthenticated) authViewModel.logout()
                    else navController.navigate(AppDestination.Auth.createRoute())
                },
                onSearchClick = { navController.navigate(AppDestination.Search.route) },
                onSettingsClick = { navController.navigate(AppDestination.Settings.route) },
                onUpdateClick = {
                    val apkUrl = savedAppUpdate?.apkUrl
                    if (!apkUrl.isNullOrBlank()) {
                        scope.launch {
                            appGraphEntryPoint.releaseApkLauncher().launch(context, apkUrl)
                        }
                    }
                },
                isAuthenticated = isAuthenticated,
                savedAppUpdate = savedAppUpdate,
            )
        }

        // ── Details ───────────────────────────────────────────────────────────
        composable(
            route = AppDestination.Details.route,
            arguments = listOf(
                navArgument(AppDestination.Details.detailsUrlArg) { type = NavType.StringType },
                navArgument(AppDestination.Details.isAuthenticatedArg) { type = NavType.BoolType; defaultValue = true },
            ),
        ) { backStackEntry ->
            val detailsUrl = Uri.decode(
                backStackEntry.arguments?.getString(AppDestination.Details.detailsUrlArg).orEmpty(),
            )
            DetailsRoute(
                detailsUrl = detailsUrl,
                isAuthenticated = isAuthenticated,
                preferredPlaybackQuality = appGraphEntryPoint.playbackPreferencesStore().readDefaultQuality(),
                watchedMarkingMode = appGraphEntryPoint.playbackPreferencesStore().readWatchedMarkingMode(),
                actionHandler = appGraphEntryPoint.torrServeActionHandler(),
                linkBuilder = appGraphEntryPoint.torrServeLinkBuilder(),
                onWatchedStateChanged = { url, isWatched ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(HOME_WATCHED_DETAILS_URL_KEY, url)
                    navController.previousBackStackEntry?.savedStateHandle?.set(HOME_WATCHED_CHANGED_STATE_KEY, isWatched)
                },
                onFavoriteContentChanged = { url, isFav ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(HOME_FAVORITE_CHANGED_DETAILS_URL_KEY, url)
                    navController.previousBackStackEntry?.savedStateHandle?.set(HOME_FAVORITE_CHANGED_STATE_KEY, isFav)
                },
                onOpenMovieOverview = { url -> navController.navigate(AppDestination.MovieOverview.createRoute(url)) },
                onOpenSeriesOverview = { url -> navController.navigate(AppDestination.SeriesOverview.createRoute(url)) },
                onOpenSeriesGuide = { url -> navController.navigate(AppDestination.SeriesGuide.createRoute(url)) },
                onAuthClick = { navController.navigate(AppDestination.Auth.createRoute(autoStart = true)) },
                onChannelContentChanged = appGraphEntryPoint.homeChannelSyncManager()::syncNow,
            )
        }

        // ── Series Overview ───────────────────────────────────────────────────
        composable(
            route = AppDestination.SeriesOverview.route,
            arguments = listOf(
                navArgument(AppDestination.SeriesOverview.detailsUrlArg) { type = NavType.StringType },
            ),
        ) {
            SeriesOverviewRoute()
        }

        // ── Movie Overview ───────────────────────────────────────────────────
        composable(
            route = AppDestination.MovieOverview.route,
            arguments = listOf(
                navArgument(AppDestination.MovieOverview.detailsUrlArg) { type = NavType.StringType },
            ),
        ) {
            MovieOverviewRoute()
        }

        // ── Series Guide ──────────────────────────────────────────────────────
        composable(
            route = AppDestination.SeriesGuide.route,
            arguments = listOf(
                navArgument(AppDestination.SeriesGuide.detailsUrlArg) { type = NavType.StringType },
            ),
        ) {
            SeriesGuideRoute(
                onOpenDetails = { url -> navController.navigate(AppDestination.Details.createRoute(url, isAuthenticated)) },
            )
        }

        // ── Search ───────────────────────────────────────────────────────────
        composable(AppDestination.Search.route) {
            SearchRoute(
                onOpenItem = { item ->
                    when (item.kind) {
                        com.kraat.lostfilmnewtv.data.model.ReleaseKind.SERIES -> {
                            navController.navigate(AppDestination.SeriesGuide.createRoute(item.targetUrl))
                        }
                        com.kraat.lostfilmnewtv.data.model.ReleaseKind.MOVIE -> {
                            navController.navigate(AppDestination.Details.createRoute(item.targetUrl, isAuthenticated))
                        }
                    }
                },
            )
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable(AppDestination.Settings.route) { backStackEntry ->
            val homeBackStack = remember(backStackEntry) {
                navController.getBackStackEntry(AppDestination.Home.route)
            }
            val homeViewModel: HomeViewModel = hiltViewModel(homeBackStack)
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            // Передаём колбэк обратно в HomeViewModel через проп SettingsViewModel
            settingsViewModel.onHomeFavoritesRailVisibilityChanged = homeViewModel::onFavoritesRailVisibilityChanged

            SettingsRoute(
                viewModel = settingsViewModel,
                isAuthenticated = isAuthenticated,
                onAuthClick = {
                    if (isAuthenticated) authViewModel.logout()
                    else navController.navigate(AppDestination.Auth.createRoute())
                },
            )
        }

        // ── Auth ──────────────────────────────────────────────────────────────
        composable(
            route = AppDestination.Auth.route,
            arguments = listOf(
                navArgument(AppDestination.Auth.autoStartArg) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val autoStart = backStackEntry.arguments?.getBoolean(AppDestination.Auth.autoStartArg) ?: false
            AuthScreen(
                viewModel = authViewModel,
                autoStart = autoStart,
                onAuthComplete = { navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
