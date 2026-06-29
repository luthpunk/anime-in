package com.animein.app.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animein.app.data.local.entity.FavoriteEntity
import com.animein.app.data.model.DetailResponse
import com.animein.app.data.repository.AnimeRepository
import com.animein.app.util.AnimeIdUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: AnimeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val animeId: String = savedStateHandle.get<String>("animeId")?.let {
        AnimeIdUtils.normalizeAnimeId(java.net.URLDecoder.decode(it, "UTF-8"))
    } ?: ""

    private val _detailState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val detailState: StateFlow<DetailUiState> = _detailState

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    init {
        fetchDetail()
        checkFavorite()
    }

    fun fetchDetail() {
        if (animeId.isEmpty()) {
            _detailState.value = DetailUiState.Error("ID Anime tidak valid")
            return
        }
        viewModelScope.launch {
            _detailState.value = DetailUiState.Loading
            try {
                val detail = repository.getAnimeDetail(animeId)
                _detailState.value = DetailUiState.Success(detail)
            } catch (e: Exception) {
                _detailState.value = DetailUiState.Error(e.localizedMessage ?: "Gagal mengambil data detail anime")
            }
        }
    }

    private fun checkFavorite() {
        viewModelScope.launch {
            _isFavorite.value = repository.isFavorite(animeId)
        }
    }

    fun toggleFavorite(detail: DetailResponse) {
        viewModelScope.launch {
            if (_isFavorite.value) {
                repository.deleteFavorite(animeId)
                _isFavorite.value = false
            } else {
                val fav = FavoriteEntity(
                    id = animeId,
                    title = detail.title.ifBlank { "Tanpa Judul" },
                    thumb = detail.thumb ?: "",
                    score = detail.score ?: "",
                    type = detail.type ?: "",
                    status = detail.status ?: ""
                )
                repository.insertFavorite(fav)
                _isFavorite.value = true
            }
        }
    }
}

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(val data: DetailResponse) : DetailUiState
    data class Error(val message: String) : DetailUiState
}
