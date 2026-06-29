package com.animein.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animein.app.data.model.HomeResponse
import com.animein.app.data.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    private val _homeState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val homeState: StateFlow<HomeUiState> = _homeState

    init {
        fetchHome()
    }

    fun fetchHome(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _homeState.value = HomeUiState.Loading
            try {
                val homeData = repository.getHome(forceRefresh)
                _homeState.value = HomeUiState.Success(homeData)
            } catch (e: Exception) {
                _homeState.value = HomeUiState.Error(e.localizedMessage ?: "Gagal mengambil data anime")
            }
        }
    }
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val data: HomeResponse) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
