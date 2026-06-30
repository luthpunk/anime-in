package com.animein.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animein.app.data.local.entity.ContinueWatchingEntity
import com.animein.app.data.model.HomeResponse
import com.animein.app.data.model.SearchItem
import com.animein.app.data.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    private val _homeState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val homeState: StateFlow<HomeUiState> = _homeState

    val continueWatchingList: StateFlow<List<ContinueWatchingEntity>> = repository.getContinueWatchingList().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Search state inside Home ViewModel
    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        fetchHome()
        setupSearchDebounce()
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

    fun deleteContinueWatching(episodeId: String) {
        viewModelScope.launch {
            repository.deleteContinueWatching(episodeId)
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

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val data: HomeResponse) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: List<SearchItem>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}
