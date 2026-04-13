package com.kraat.lostfilmnewtv.di

import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.model.AuthState
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.LostFilmSearchItem
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.PairingSession
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.SearchResultsResult
import com.kraat.lostfilmnewtv.data.repository.SeriesGuideResult
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Заменяет [DataModule] в инструментальных тестах.
 *
 * По умолчанию предоставляет пустые реализации — конкретные тесты
 * могут использовать [TestFakeRepository] и подменять поведение через его свойства.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DataModule::class],
)
object TestDataModule {

    @Provides
    @Singleton
    fun provideFakeRepository(): LostFilmRepository = TestFakeRepository()

    @Provides
    @Singleton
    fun provideFakeAuthRepository(): AuthRepositoryContract = TestFakeAuthRepository()
}

/**
 * Изменяемый фейк репозитория для тестов.
 * Свойства можно переопределять прямо в тесте через [TestFakeRepository.pageState] и т.д.
 */
class TestFakeRepository : LostFilmRepository {
    var pageState: PageState = PageState.Content(pageNumber = 1, items = emptyList(), hasNextPage = false, isStale = false)
    var detailsResult: DetailsResult = DetailsResult.Error("detailsUrl", "Not configured")
    var seriesGuideResult: SeriesGuideResult = SeriesGuideResult.Error("Not configured")
    var searchResult: SearchResultsResult = SearchResultsResult.Success(query = "", items = emptyList())
    var markEpisodeWatchedResult: Boolean = false
    var setFavoriteResult: FavoriteMutationResult = FavoriteMutationResult.RequiresLogin()
    var favoriteReleasesResult: FavoriteReleasesResult = FavoriteReleasesResult.Unavailable()

    override suspend fun loadPage(pageNumber: Int): PageState = pageState
    override suspend fun loadDetails(detailsUrl: String): DetailsResult = detailsResult
    override suspend fun loadSeriesGuide(detailsUrl: String): SeriesGuideResult = seriesGuideResult
    override suspend fun search(query: String): SearchResultsResult = when (val result = searchResult) {
        is SearchResultsResult.Success -> result.copy(query = query)
        is SearchResultsResult.Error -> result.copy(query = query)
    }
    override suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean = markEpisodeWatchedResult
    override suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult = setFavoriteResult
    override suspend fun loadFavoriteReleases(): FavoriteReleasesResult = favoriteReleasesResult
}

class TestFakeAuthRepository : AuthRepositoryContract {
    var authState: AuthState = AuthState(isAuthenticated = false, session = null)
    var pairingSession: PairingSession? = null

    override suspend fun getAuthState(): AuthState = authState
    override suspend fun startPairing(): PairingSession =
        pairingSession ?: error("startPairing not configured in TestFakeAuthRepository")
    override suspend fun pollPairingStatus(): PairingSession? = pairingSession
    override suspend fun claimAndPersistSession() =
        com.kraat.lostfilmnewtv.data.auth.AuthCompletionResult.RecoverableFailure()
    override suspend fun logout() { authState = AuthState(isAuthenticated = false, session = null) }
}
