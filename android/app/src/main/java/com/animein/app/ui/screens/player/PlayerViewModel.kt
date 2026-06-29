package com.animein.app.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animein.app.data.local.entity.ContinueWatchingEntity
import com.animein.app.data.model.EpisodeDetailResponse
import com.animein.app.data.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: AnimeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val episodeId: String = savedStateHandle.get<String>("episodeId")?.let {
        java.net.URLDecoder.decode(it, "UTF-8")
    } ?: ""
    val animeId: String = savedStateHandle.get<String>("animeId")?.let {
        java.net.URLDecoder.decode(it, "UTF-8")
    } ?: ""

    private val _playerState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val playerState: StateFlow<PlayerUiState> = _playerState

    private val _savedProgress = MutableStateFlow(0L)
    val savedProgress: StateFlow<Long> = _savedProgress

    init {
        fetchEpisodeDetail()
        loadSavedProgress()
    }

    fun fetchEpisodeDetail() {
        if (episodeId.isEmpty()) {
            _playerState.value = PlayerUiState.Error("ID Episode tidak valid")
            return
        }
        viewModelScope.launch {
            _playerState.value = PlayerUiState.Loading
            try {
                val response = repository.getEpisodeDetail(episodeId)
                _playerState.value = PlayerUiState.Success(response)
            } catch (e: Exception) {
                _playerState.value = PlayerUiState.Error(e.localizedMessage ?: "Gagal memuat video episode")
            }
        }
    }

    private fun loadSavedProgress() {
        viewModelScope.launch {
            val saved = repository.getContinueWatchingForEpisode(episodeId)
            _savedProgress.value = saved?.progress ?: 0L
        }
    }

    fun saveProgress(progress: Long, duration: Long, animeTitle: String, episodeTitle: String, thumb: String) {
        if (episodeId.isEmpty() || animeId.isEmpty()) return
        viewModelScope.launch {
            repository.insertContinueWatching(
                ContinueWatchingEntity(
                    episodeId = episodeId,
                    animeId = animeId,
                    animeTitle = animeTitle,
                    episodeTitle = episodeTitle,
                    thumb = thumb,
                    progress = progress,
                    duration = duration
                )
            )
        }
    }

    fun switchMirror(mirrorId: String) {
        viewModelScope.launch {
            val currentState = _playerState.value
            if (currentState is PlayerUiState.Success) {
                _playerState.value = PlayerUiState.Loading
                try {
                    val mirrorRes = repository.getMirrorStream(animeId, mirrorId)
                    val newStream = mirrorRes.linkStream ?: mirrorRes.streamLink
                    val updated = currentState.data.copy(linkStream = newStream)
                    _playerState.value = PlayerUiState.Success(updated)
                } catch (e: Exception) {
                    _playerState.value = PlayerUiState.Success(currentState.data) // Fallback to original
                }
            }
        }
    }
}

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Success(val data: EpisodeDetailResponse) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}
