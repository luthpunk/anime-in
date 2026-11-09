package com.animein.app.ui.screens.genre

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

@HiltViewModel
class GenreViewModel @Inject constructor(
    private val repository: AnimeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val genreId: String = savedStateHandle.get<String>("genreId")?.let {
        java.net.URLDecoder.decode(it, "UTF-8")
    } ?: ""
    val genreName: String = savedStateHandle.get<String>("genreName")?.let {
        java.net.URLDecoder.decode(it, "UTF-8")
    } ?: "Genre"

    private val _genreState = MutableStateFlow<GenreUiState>(GenreUiState.Loading)
    val genreState: StateFlow<GenreUiState> = _genreState

    private var currentPage = 1

    init {
        fetchAnimeByGenre()
    }

    fun fetchAnimeByGenre() {
        if (genreId.isEmpty() && (genreName.isEmpty() || genreName == "Genre")) {
            _genreState.value = GenreUiState.Error("ID Genre tidak valid")
            return
        }
        viewModelScope.launch {
            _genreState.value = GenreUiState.Loading
            try {
                val response = repository.getAnimeByGenre(genreId, genreName, currentPage)
                _genreState.value = GenreUiState.Success(response.animeList)
            } catch (e: Exception) {
                _genreState.value = GenreUiState.Error(e.localizedMessage ?: "Gagal memuat anime untuk genre ini")
            }
        }
    }
}

sealed interface GenreUiState {
    data object Loading : GenreUiState
    data class Success(val list: List<AnimeListItem>) : GenreUiState
    data class Error(val message: String) : GenreUiState
}
