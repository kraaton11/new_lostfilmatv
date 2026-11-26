package com.kraat.lostfilmnewtv.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.ScheduleResult
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: LostFilmRepository,
    private val preferencesStore: PlaybackPreferencesStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ScheduleUiState(isLoading = true, favoritesOnly = preferencesStore.readScheduleFavoritesOnly()),
    )
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private var started = false
    private var loadJob: Job? = null

    fun onStart() {
        if (started) return
        started = true
        load()
    }

    fun onRetry() {
        load()
    }

    fun onModeSelected(favoritesOnly: Boolean) {
        if (_uiState.value.favoritesOnly == favoritesOnly) return
        preferencesStore.writeScheduleFavoritesOnly(favoritesOnly)
        load(favoritesOnly)
    }

    private fun load(favoritesOnly: Boolean = _uiState.value.favoritesOnly) {
        loadJob?.cancel()
        _uiState.update { state ->
            state.copy(
                favoritesOnly = favoritesOnly,
                isLoading = true,
                schedule = state.schedule.takeIf { state.favoritesOnly == favoritesOnly },
                errorMessage = null,
            )
        }
        loadJob = viewModelScope.launch(ioDispatcher) {
            val result = repository.loadSchedule(favoritesOnly)
            ensureActive()
            when (result) {
                is ScheduleResult.Success -> {
                    _uiState.update {
                        if (it.favoritesOnly != favoritesOnly) return@update it
                        it.copy(
                            isLoading = false,
                            schedule = result.schedule,
                            errorMessage = null,
                        )
                    }
                }
                is ScheduleResult.Error -> {
                    _uiState.update {
                        if (it.favoritesOnly != favoritesOnly) return@update it
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }
}
