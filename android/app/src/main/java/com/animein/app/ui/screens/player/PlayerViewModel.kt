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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import javax.inject.Inject

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: AnimeRepository,
    savedStateHandle: SavedStateHandle,
    val simpleCache: SimpleCache
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

    private val _nextEpisodeId = MutableStateFlow<String?>(null)
    val nextEpisodeId: StateFlow<String?> = _nextEpisodeId

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private val _episodes = MutableStateFlow<List<com.animein.app.data.model.EpisodeItem>>(emptyList())
    val episodes: StateFlow<List<com.animein.app.data.model.EpisodeItem>> = _episodes

    private val _animeThumb = MutableStateFlow("")

    init {
        fetchEpisodeDetail()
        loadSavedProgress()
        loadNextEpisodeId()
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
                // Selalu coba resolve vidhide terlebih dahulu
                val finalResponse = autoResolveVidhideOrFallback(response)
                _playerState.value = PlayerUiState.Success(finalResponse)
            } catch (e: Exception) {
                _playerState.value = PlayerUiState.Error(e.localizedMessage ?: "Gagal memuat video episode")
            }
        }
    }

    private suspend fun autoResolveVidhideOrFallback(response: EpisodeDetailResponse): EpisodeDetailResponse {
        val vidhideMirrors = mutableListOf<com.animein.app.data.model.MirrorItem>()
        val allMirrors = mutableListOf<com.animein.app.data.model.MirrorItem>()

        fun List<com.animein.app.data.model.MirrorItem>.filterVidhide() = this.filter { mirror ->
            mirror.host.contains("vidhide", ignoreCase = true) ||
            mirror.host.contains("hide", ignoreCase = true) ||
            mirror.id.contains("vidhide", ignoreCase = true)
        }

        // 1. Kumpulkan Vidhide dengan prioritas kualitas: 480p -> 720p -> 360p
        response.mirror2?.mirrorList?.filterVidhide()?.let { vidhideMirrors.addAll(it) }
        response.mirror3?.mirrorList?.filterVidhide()?.let { vidhideMirrors.addAll(it) }
        response.mirror1?.mirrorList?.filterVidhide()?.let { vidhideMirrors.addAll(it) }

        // Kumpulkan semua mirror (sebagai fallback umum) dengan urutan: 480p -> 720p -> 360p
        response.mirror2?.mirrorList?.let { allMirrors.addAll(it) }
        response.mirror3?.mirrorList?.let { allMirrors.addAll(it) }
        response.mirror1?.mirrorList?.let { allMirrors.addAll(it) }

        // Coba resolve vidhide terlebih dahulu
        for (mirror in vidhideMirrors) {
            val mirrorId = mirror.id
            if (!mirrorId.isNullOrBlank() && mirrorId != "-") {
                try {
                    val mirrorRes = repository.getMirrorStream(animeId, mirrorId)
                    val resolvedStream = mirrorRes.linkStream ?: mirrorRes.streamLink
                    if (!resolvedStream.isNullOrBlank() && resolvedStream != "-") {
                        return response.copy(linkStream = resolvedStream)
                    }
                } catch (e: Exception) {
                    // Coba mirror berikutnya
                }
            }
        }

        // 2. Jika tidak ada vidhide atau gagal, dan linkStream bawaan valid, gunakan linkStream bawaan
        if (!response.linkStream.isNullOrBlank() && response.linkStream != "-") {
            return response
        }

        // 3. Jika linkStream bawaan kosong/invalid, coba mirror apa saja yang ada
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
                    // Coba mirror berikutnya
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
                    thumb = thumb.ifBlank { _animeThumb.value },
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

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    private fun loadNextEpisodeId() {
        if (animeId.isEmpty()) return
        viewModelScope.launch {
            try {
                val detail = repository.getAnimeDetail(animeId)
                _animeThumb.value = detail.thumb ?: ""
                val episodes = detail.episodeList ?: emptyList()
                _episodes.value = episodes
                val currentIndex = episodes.indexOfFirst {
                    AnimeIdUtils.normalizeEpisodeId(it.id) == AnimeIdUtils.normalizeEpisodeId(episodeId)
                }
                if (currentIndex != -1) {
                    val currentEpNum = extractEpisodeNumOnly(episodeId)
                    val nextEp = episodes.firstOrNull {
                        val num = extractEpisodeNumOnly(it.id)
                        num == currentEpNum + 1
                    }
                    if (nextEp != null) {
                        _nextEpisodeId.value = nextEp.id
                    } else {
                        // Fallback: index - 1
                        if (currentIndex - 1 >= 0) {
                            _nextEpisodeId.value = episodes[currentIndex - 1].id
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun extractEpisodeNumOnly(id: String): Int {
        val matches = Regex("\\d+").findAll(id).map { it.value }.toList()
        return if (matches.isNotEmpty()) matches.last().toIntOrNull() ?: 0 else 0
    }
}

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Success(val data: EpisodeDetailResponse) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}
