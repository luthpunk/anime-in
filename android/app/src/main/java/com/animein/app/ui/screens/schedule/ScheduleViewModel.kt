package com.animein.app.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animein.app.data.model.ScheduleItem
import com.animein.app.data.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    private val _scheduleState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val scheduleState: StateFlow<ScheduleUiState> = _scheduleState

    init {
        fetchSchedule()
    }

    fun fetchSchedule() {
        viewModelScope.launch {
            _scheduleState.value = ScheduleUiState.Loading
            try {
                val response = repository.getSchedule()
                _scheduleState.value = ScheduleUiState.Success(response.scheduleList)
            } catch (e: Exception) {
                _scheduleState.value = ScheduleUiState.Error(e.localizedMessage ?: "Gagal memuat jadwal rilis")
            }
        }
    }
}

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data class Success(val schedule: List<ScheduleItem>) : ScheduleUiState
    data class Error(val message: String) : ScheduleUiState
}
