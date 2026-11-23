package com.kraat.lostfilmnewtv.di

import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.model.AuthState
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.FavoritesRepository
import com.kraat.lostfilmnewtv.data.model.FavoriteSeriesResult
import com.kraat.lostfilmnewtv.data.repository.SeriesGuideResult
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient

/**
 * Заменяет [DataModule] в Robolectric unit-тестах.
 * Конкретные тесты настраивают фейк через @Inject + cast к [UnitTestFakeRepository].
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DataModule::class],
)
object UnitTestDataModule {

    @Provides
    @Singleton
    fun provideRepository(): LostFilmRepository = UnitTestFakeRepository()

    @Provides
    @Singleton
    fun provideFavoritesRepository(repository: LostFilmRepository): FavoritesRepository =
        repository as FavoritesRepository


    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepositoryContract = UnitTestFakeAuthRepository()
}

/**
 * Заменяет [NetworkModule] в Robolectric unit-тестах.
 * Никакого сетевого трафика — только no-op заглушки.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class],
)
object UnitTestNetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

}

class UnitTestFakeRepository : LostFilmRepository, FavoritesRepository {
    var pageState: PageState = PageState.Content(
        pageNumber = 1, items = emptyList(), hasNextPage = false, isStale = false,
    )
    var detailsResult: DetailsResult = DetailsResult.Error("", "Not configured")
    var seriesGuideResult: SeriesGuideResult = SeriesGuideResult.Error("Not configured")
    var watchedStateResult: Boolean? = null
    var markWatchedResult: Boolean? = false
    var favoriteResult: FavoriteMutationResult = FavoriteMutationResult.RequiresLogin()
    var favoriteReleasesResult: FavoriteReleasesResult = FavoriteReleasesResult.Unavailable()

    override suspend fun loadPage(pageNumber: Int) = pageState
    override suspend fun loadDetails(detailsUrl: String) = detailsResult
    override suspend fun loadSeriesGuide(detailsUrl: String) = seriesGuideResult
    override suspend fun loadWatchedState(detailsUrl: String) = watchedStateResult
    override suspend fun setEpisodeWatched(detailsUrl: String, playEpisodeId: String, targetWatched: Boolean) = markWatchedResult
    override suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean) = favoriteResult
    override fun observeFavoriteReleases(pageNumber: Int): Flow<FavoriteReleasesResult> =
        flowOf(favoriteReleasesResult)
    override suspend fun loadFavoriteSeries() = FavoriteSeriesResult.Unavailable()
    override suspend fun invalidateCache() {}
}

class UnitTestFakeAuthRepository : AuthRepositoryContract {
    private val authStateFlow = MutableStateFlow(AuthState(isAuthenticated = false, session = null))
    var authState: AuthState
        get() = authStateFlow.value
        set(value) {
            authStateFlow.value = value
        }

    override suspend fun getAuthState() = authState
    override fun observeAuthState(): Flow<AuthState> = authStateFlow
    override suspend fun logout() { authState = AuthState(isAuthenticated = false, session = null) }
}
