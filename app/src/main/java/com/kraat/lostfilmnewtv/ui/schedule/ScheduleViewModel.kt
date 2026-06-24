package com.kraat.lostfilmnewtv.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.ScheduleResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: LostFilmRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScheduleUiState(isLoading = true))
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

    private fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        loadJob = viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadSchedule()) {
                is ScheduleResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            schedule = result.schedule,
                            errorMessage = null,
                        )
                    }
                }
                is ScheduleResult.Error -> {
                    _uiState.update {
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
