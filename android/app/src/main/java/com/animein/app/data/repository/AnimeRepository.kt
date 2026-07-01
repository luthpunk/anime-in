package com.animein.app.data.repository

import com.animein.app.data.local.dao.AnimeDao
import com.animein.app.data.local.entity.ContinueWatchingEntity
import com.animein.app.data.local.entity.FavoriteEntity
import com.animein.app.data.local.entity.HistoryEntity
import com.animein.app.data.local.pref.PreferencesManager
import com.animein.app.data.model.*
import com.animein.app.data.remote.AnimeApiService
import com.animein.app.data.remote.MirrorRequest
import com.animein.app.data.remote.NimegamiDirectScraper
import com.animein.app.util.AnimeIdUtils
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimeRepository @Inject constructor(
    private val apiService: AnimeApiService,
    private val animeDao: AnimeDao,
    private val preferencesManager: PreferencesManager,
    private val directScraper: NimegamiDirectScraper
) {
    private val gson = Gson()
    private var inMemoryHome: HomeResponse? = null

    suspend fun getHome(forceRefresh: Boolean = false): HomeResponse {
        if (!forceRefresh) {
            if (inMemoryHome != null) {
                return inMemoryHome!!
            }
            val cachedJson = preferencesManager.getHomeCache()
            if (!cachedJson.isNullOrEmpty()) {
                try {
                    val cached = gson.fromJson(cachedJson, HomeResponse::class.java)
                    inMemoryHome = cached
                    return cached
                } catch (e: Exception) {
                    // Ignore parsing errors and fetch from network
                }
            }
        }

        return try {
            val response = directScraper.getHome()
            inMemoryHome = response
            preferencesManager.saveHomeCache(gson.toJson(response))
            response
        } catch (e: Exception) {
            inMemoryHome ?: throw e
        }
    }

    // In-memory caches for list pages
    private var inMemoryOngoing: AnimeListResponse? = null
    private var inMemoryCompleted: AnimeListResponse? = null

    suspend fun getOngoingAnimeList(page: Int): AnimeListResponse {
        return try {
            val result = directScraper.getOngoingAnimeList(page)
            if (page == 1) inMemoryOngoing = result
            result
        } catch (e: Exception) {
            if (page == 1) inMemoryOngoing ?: AnimeListResponse("error", emptyList())
            else AnimeListResponse("error", emptyList())
        }
    }

    suspend fun getCompletedAnimeList(page: Int): AnimeListResponse {
        return try {
            val result = directScraper.getCompletedAnimeList(page)
            if (page == 1) inMemoryCompleted = result
            result
        } catch (e: Exception) {
            if (page == 1) inMemoryCompleted ?: AnimeListResponse("error", emptyList())
            else AnimeListResponse("error", emptyList())
        }
    }

    suspend fun searchAnime(query: String): SearchResponse {
        return try {
            directScraper.searchAnime(query)
        } catch (e: Exception) {
            SearchResponse("error", emptyList())
        }
    }

    suspend fun getGenres(): GenreListResponse {
        return try {
            directScraper.getGenres()
        } catch (e: Exception) {
            GenreListResponse("error", emptyList())
        }
    }

    suspend fun getAnimeByGenre(genreId: String, page: Int): AnimeListResponse {
        return try {
            directScraper.getAnimeByGenre(genreId, page)
        } catch (e: Exception) {
            AnimeListResponse("error", emptyList())
        }
    }

    suspend fun getAnimeDetail(animeId: String): DetailResponse {
        val normalizedAnimeId = AnimeIdUtils.normalizeAnimeId(animeId)
        val detail = directScraper.getAnimeDetail(normalizedAnimeId)
        // Add to history
        val history = HistoryEntity(
            id = normalizedAnimeId,
            title = detail.title.ifBlank { "Tanpa Judul" },
            thumb = detail.thumb ?: ""
        )
        animeDao.insertHistory(history)
        return detail
    }

    suspend fun getEpisodeDetail(episodeId: String): EpisodeDetailResponse {
        val normalizedEpisodeId = AnimeIdUtils.normalizeEpisodeId(episodeId)
        return directScraper.getEpisodeDetail(normalizedEpisodeId)
    }

    suspend fun getMirrorStream(animeId: String, mirrorId: String): MirrorResponse {
        return directScraper.getMirrorStream(mirrorId)
    }

    // Local DB operations
    fun getFavorites(): Flow<List<FavoriteEntity>> = animeDao.getFavorites()

    suspend fun isFavorite(id: String): Boolean = animeDao.isFavorite(id)

    suspend fun insertFavorite(favorite: FavoriteEntity) = animeDao.insertFavorite(favorite)

    suspend fun deleteFavorite(id: String) = animeDao.deleteFavorite(id)

    fun getHistory(): Flow<List<HistoryEntity>> = animeDao.getHistory()

    suspend fun deleteHistory(id: String) = animeDao.deleteHistory(id)

    suspend fun clearHistory() = animeDao.clearHistory()

    fun getContinueWatchingList(): Flow<List<ContinueWatchingEntity>> = animeDao.getContinueWatchingList()

    suspend fun getLatestContinueWatchingForAnime(animeId: String): ContinueWatchingEntity? {
        return animeDao.getLatestContinueWatchingForAnime(animeId)
    }

    suspend fun getContinueWatchingForEpisode(episodeId: String): ContinueWatchingEntity? {
        return animeDao.getContinueWatchingForEpisode(episodeId)
    }

    suspend fun insertContinueWatching(continueWatching: ContinueWatchingEntity) {
        animeDao.insertContinueWatching(continueWatching)
    }

    suspend fun deleteContinueWatching(episodeId: String) {
        animeDao.deleteContinueWatching(episodeId)
    }

    suspend fun getSchedule(): ScheduleResponse {
        return directScraper.getSchedule()
    }
}
