package com.animein.app.ui.screens.animelist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animein.app.data.model.AnimeListItem
import com.animein.app.data.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AnimeListUiState {
    object Loading : AnimeListUiState
    data class Success(val list: List<AnimeListItem>) : AnimeListUiState
    data class Error(val message: String) : AnimeListUiState
}

@HiltViewModel
class AnimeListViewModel @Inject constructor(
    private val repository: AnimeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val listType: String = savedStateHandle["listType"] ?: "ongoing"
    val listTitle: String = savedStateHandle["listTitle"] ?: "Daftar Anime"

    private val _uiState = MutableStateFlow<AnimeListUiState>(AnimeListUiState.Loading)
    val uiState: StateFlow<AnimeListUiState> = _uiState

    init {
        fetchAnimeList()
    }

    fun fetchAnimeList(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = AnimeListUiState.Loading
            try {
                val response = if (listType == "ongoing") {
                    repository.getOngoingAnimeList(1, forceRefresh)
                } else {
                    repository.getCompletedAnimeList(1, forceRefresh)
                }
                _uiState.value = AnimeListUiState.Success(response.animeList)
            } catch (e: Exception) {
                _uiState.value = AnimeListUiState.Error(e.localizedMessage ?: "Gagal memuat daftar anime")
            }
        }
    }
}

