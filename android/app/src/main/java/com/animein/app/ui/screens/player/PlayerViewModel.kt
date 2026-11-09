package com.animein.app.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animein.app.data.local.entity.ContinueWatchingEntity
import com.animein.app.data.model.EpisodeDetailResponse
import com.animein.app.data.remote.AniSkipManager
import com.animein.app.data.remote.SkipInterval
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
    val simpleCache: SimpleCache,
    private val aniSkipManager: AniSkipManager
) : ViewModel() {

    val initialEpisodeId: String = savedStateHandle.get<String>("episodeId")?.let {
        AnimeIdUtils.normalizeEpisodeId(java.net.URLDecoder.decode(it, "UTF-8"))
    } ?: ""
    val animeId: String = savedStateHandle.get<String>("animeId")?.let {
        AnimeIdUtils.normalizeAnimeId(java.net.URLDecoder.decode(it, "UTF-8"))
    } ?: ""

    private val _currentEpisodeId = MutableStateFlow(initialEpisodeId)
    val currentEpisodeId: StateFlow<String> = _currentEpisodeId

    private val _playerState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val playerState: StateFlow<PlayerUiState> = _playerState

    private val _savedProgress = MutableStateFlow(0L)
    val savedProgress: StateFlow<Long> = _savedProgress

    private val _nextEpisodeId = MutableStateFlow<String?>(null)
    val nextEpisodeId: StateFlow<String?> = _nextEpisodeId

    private val _prevEpisodeId = MutableStateFlow<String?>(null)
    val prevEpisodeId: StateFlow<String?> = _prevEpisodeId

    private val _isVideoLoading = MutableStateFlow(false)
    val isVideoLoading: StateFlow<Boolean> = _isVideoLoading

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private val _episodes = MutableStateFlow<List<com.animein.app.data.model.EpisodeItem>>(emptyList())
    val episodes: StateFlow<List<com.animein.app.data.model.EpisodeItem>> = _episodes

    private val _animeThumb = MutableStateFlow("")
    private val _animeTitle = MutableStateFlow("")
    val animeTitle: StateFlow<String> = _animeTitle
    private val _animeSynopsis = MutableStateFlow("")
    val animeSynopsis: StateFlow<String> = _animeSynopsis
    private val _currentEpisodeTitle = MutableStateFlow("")
    val currentEpisodeTitle: StateFlow<String> = _currentEpisodeTitle

    private val _introInterval = MutableStateFlow<SkipInterval?>(null)
    val introInterval: StateFlow<SkipInterval?> = _introInterval

    init {
        viewModelScope.launch {
            _currentEpisodeId.collect { epId ->
                if (epId.isNotEmpty()) {
                    _introInterval.value = null
                    fetchEpisodeDetail(epId)
                    loadSavedProgress(epId)
                    loadAdjacentEpisodes(epId)
                }
            }
        }
    }

    fun selectEpisode(newEpisodeId: String) {
        val cleanEpId = AnimeIdUtils.normalizeEpisodeId(newEpisodeId)
        if (cleanEpId == _currentEpisodeId.value) return
        _currentEpisodeId.value = cleanEpId
    }

    fun fetchEpisodeDetail(epId: String = _currentEpisodeId.value) {
        if (epId.isEmpty()) {
            _playerState.value = PlayerUiState.Error("ID Episode tidak valid")
            return
        }
        viewModelScope.launch {
            val currentState = _playerState.value
            if (currentState is PlayerUiState.Success) {
                _isVideoLoading.value = true
                val updatedData = currentState.data.copy(linkStream = null)
                _playerState.value = PlayerUiState.Success(updatedData)
            } else {
                _playerState.value = PlayerUiState.Loading
            }
            try {
                val response = repository.getEpisodeDetail(epId)
                // Selalu coba resolve vidhide terlebih dahulu
                val finalResponse = autoResolveVidhideOrFallback(response)
                _playerState.value = PlayerUiState.Success(finalResponse)
            } catch (e: Exception) {
                if (_playerState.value is PlayerUiState.Success) {
                    // stay in success but show error or stop spinner
                } else {
                    _playerState.value = PlayerUiState.Error(e.localizedMessage ?: "Gagal memuat video episode")
                }
            } finally {
                _isVideoLoading.value = false
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

    private fun loadSavedProgress(epId: String = _currentEpisodeId.value) {
        viewModelScope.launch {
            val saved = repository.getContinueWatchingForEpisode(epId)
            _savedProgress.value = saved?.progress ?: 0L
        }
    }

    fun saveProgress(progress: Long, duration: Long, animeTitle: String, episodeTitle: String, thumb: String) {
        val epId = _currentEpisodeId.value
        if (epId.isEmpty() || animeId.isEmpty()) return
        viewModelScope.launch {
            repository.insertContinueWatching(
                ContinueWatchingEntity(
                    episodeId = epId,
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
                // Do NOT set Loading state to prevent destroying Composable tree and releasing player
                try {
                    val mirrorRes = repository.getMirrorStream(animeId, mirrorId)
                    val newStream = mirrorRes.linkStream ?: mirrorRes.streamLink
                    val updated = currentState.data.copy(linkStream = newStream)
                    _playerState.value = PlayerUiState.Success(updated)
                } catch (e: Exception) {
                    // fallback
                }
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    private fun loadAdjacentEpisodes(epId: String = _currentEpisodeId.value) {
        if (animeId.isEmpty()) return
        viewModelScope.launch {
            try {
                val detail = repository.getAnimeDetail(animeId)
                _animeThumb.value = detail.thumb ?: ""
                _animeTitle.value = detail.title
                _animeSynopsis.value = detail.synopsis ?: ""
                val episodesList = detail.episodeList ?: emptyList()
                _episodes.value = episodesList

                // Find the current episode title by matching episodeId
                val currentEp = episodesList.firstOrNull {
                    AnimeIdUtils.normalizeEpisodeId(it.id) == AnimeIdUtils.normalizeEpisodeId(epId)
                }
                if (currentEp != null) {
                    _currentEpisodeTitle.value = currentEp.title
                }
                loadIntroSkipTimes(detail.title, currentEp?.title ?: epId, epId)
                val currentIndex = episodesList.indexOfFirst {
                    AnimeIdUtils.normalizeEpisodeId(it.id) == AnimeIdUtils.normalizeEpisodeId(epId)
                }
                if (currentIndex != -1 && episodesList.size > 1) {
                    val firstNum = extractEpisodeNumFromTitle(episodesList.first().title)
                    val lastNum = extractEpisodeNumFromTitle(episodesList.last().title)
                    val isAsc = firstNum < lastNum

                    val nextIndex = if (isAsc) currentIndex + 1 else currentIndex - 1
                    val prevIndex = if (isAsc) currentIndex - 1 else currentIndex + 1

                    _nextEpisodeId.value = if (nextIndex in episodesList.indices) episodesList[nextIndex].id else null
                    _prevEpisodeId.value = if (prevIndex in episodesList.indices) episodesList[prevIndex].id else null
                } else {
                    _nextEpisodeId.value = null
                    _prevEpisodeId.value = null
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun extractEpisodeNumFromTitle(title: String): Int {
        val cleaned = title
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(\\d+p\\)"), "")
            .replace("_", " ")
        val matches = Regex("\\d+").findAll(cleaned).map { it.value }.toList()
        return if (matches.isNotEmpty()) matches.last().toIntOrNull() ?: 0 else 0
    }

    private fun loadIntroSkipTimes(animeTitle: String, episodeTitle: String, episodeId: String) {
        viewModelScope.launch {
            try {
                val epNum = aniSkipManager.extractEpisodeNumber(episodeTitle, episodeId)
                val interval = aniSkipManager.getIntroSkipInterval(animeTitle, epNum)
                _introInterval.value = interval
            } catch (_: Exception) {
                _introInterval.value = null
            }
        }
    }
}

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Success(val data: EpisodeDetailResponse) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}
