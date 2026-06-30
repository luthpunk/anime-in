package com.animein.app.ui.screens.genrelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animein.app.data.model.GenreItem
import com.animein.app.data.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenreListViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    private val _genresState = MutableStateFlow<GenresUiState>(GenresUiState.Loading)
    val genresState: StateFlow<GenresUiState> = _genresState

    init {
        fetchGenres()
    }

    fun fetchGenres() {
        viewModelScope.launch {
            _genresState.value = GenresUiState.Loading
            try {
                val response = repository.getGenres()
                _genresState.value = GenresUiState.Success(response.genreList)
            } catch (e: Exception) {
                _genresState.value = GenresUiState.Error(e.localizedMessage ?: "Gagal memuat daftar genre")
            }
        }
    }
}

sealed interface GenresUiState {
    data object Loading : GenresUiState
    data class Success(val genres: List<GenreItem>) : GenresUiState
    data class Error(val message: String) : GenresUiState
}
