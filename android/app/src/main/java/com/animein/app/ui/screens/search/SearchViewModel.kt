package com.animein.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animein.app.data.model.GenreItem
import com.animein.app.data.model.SearchItem
import com.animein.app.data.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    private val _genresState = MutableStateFlow<GenresUiState>(GenresUiState.Loading)
    val genresState: StateFlow<GenresUiState> = _genresState

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        fetchGenres()
        setupSearchDebounce()
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

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchState.value = SearchUiState.Idle
        }
    }

    private fun setupSearchDebounce() {
        viewModelScope.launch {
            _searchQuery
                .debounce(500)
                .filter { it.isNotBlank() }
                .distinctUntilChanged()
                .collectLatest { query ->
                    performSearch(query)
                }
        }
    }

    private suspend fun performSearch(query: String) {
        _searchState.value = SearchUiState.Loading
        try {
            val response = repository.searchAnime(query)
            _searchState.value = SearchUiState.Success(response.searchResults)
        } catch (e: Exception) {
            _searchState.value = SearchUiState.Error(e.localizedMessage ?: "Pencarian gagal")
        }
    }
}

sealed interface GenresUiState {
    data object Loading : GenresUiState
    data class Success(val genres: List<GenreItem>) : GenresUiState
    data class Error(val message: String) : GenresUiState
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: List<SearchItem>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}
