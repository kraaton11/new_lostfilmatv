package com.kraat.lostfilmnewtv.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.model.WatchHistoryEntry
import com.kraat.lostfilmnewtv.data.model.WatchHistoryFilter
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface HistoryState {
    data object SignIn : HistoryState
    data object Loading : HistoryState
    data object Error : HistoryState
    data class Loaded(val entries: List<WatchHistoryEntry>) : HistoryState
}

@HiltViewModel
internal class HistoryViewModel @Inject constructor(
    private val repository: LostFilmRepository,
) : ViewModel() {
    private val state = MutableStateFlow<HistoryState>(HistoryState.Loading)
    val uiState = state.asStateFlow()
    private val filter = MutableStateFlow(WatchHistoryFilter.All)
    val selectedFilter = filter.asStateFlow()
    private var loadJob: Job? = null

    fun setAuthenticated(authenticated: Boolean) {
        if (!authenticated) {
            loadJob?.cancel()
            state.value = HistoryState.SignIn
        } else {
            fetch(showLoading = state.value !is HistoryState.Loaded)
        }
    }

    fun selectFilter(selected: WatchHistoryFilter) {
        if (selected == filter.value) return
        filter.value = selected
        load()
    }

    fun load() = fetch(showLoading = true)

    private fun fetch(showLoading: Boolean) {
        loadJob?.cancel()
        if (showLoading) state.value = HistoryState.Loading
        val requestedFilter = filter.value
        loadJob = viewModelScope.launch {
            val result = try {
                HistoryState.Loaded(repository.loadWatchHistory(requestedFilter))
            } catch (_: IOException) {
                HistoryState.Error
            }
            ensureActive()
            state.value = result
        }
    }
}
