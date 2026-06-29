package com.animein.app.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animein.app.data.local.entity.ContinueWatchingEntity
import com.animein.app.data.model.EpisodeDetailResponse
import com.animein.app.data.repository.AnimeRepository
import com.animein.app.util.AnimeIdUtils
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
        AnimeIdUtils.normalizeEpisodeId(java.net.URLDecoder.decode(it, "UTF-8"))
    } ?: ""
    val animeId: String = savedStateHandle.get<String>("animeId")?.let {
        AnimeIdUtils.normalizeAnimeId(java.net.URLDecoder.decode(it, "UTF-8"))
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
                val finalResponse = if (response.linkStream.isNullOrBlank() || response.linkStream == "-") {
                    autoResolveMirrors(response)
                } else {
                    response
                }
                _playerState.value = PlayerUiState.Success(finalResponse)
            } catch (e: Exception) {
                _playerState.value = PlayerUiState.Error(e.localizedMessage ?: "Gagal memuat video episode")
            }
        }
    }

    private suspend fun autoResolveMirrors(response: EpisodeDetailResponse): EpisodeDetailResponse {
        val allMirrors = mutableListOf<com.animein.app.data.model.MirrorItem>()
        response.mirror2?.mirrorList?.let { allMirrors.addAll(it) } // Try 480p first
        response.mirror3?.mirrorList?.let { allMirrors.addAll(it) } // Try 720p second
        response.mirror1?.mirrorList?.let { allMirrors.addAll(it) } // Try 360p last

        for (mirror in allMirrors) {
            val mirrorId = mirror.id
            if (!mirrorId.isNullOrBlank() && mirrorId != "-") {
                try {
                    val mirrorRes = repository.getMirrorStream(animeId, mirrorId)
                    val resolvedStream = mirrorRes.linkStream ?: mirrorRes.streamLink
                    if (!resolvedStream.isNullOrBlank() && resolvedStream != "-") {
                        return response.copy(linkStream = resolvedStream)
                    }
                } catch (e: Exception) {
                    // try next mirror
                }
            }
        }
        return response
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
