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
    private var inMemoryOngoing: AnimeListResponse? = null
    private var inMemoryCompleted: AnimeListResponse? = null

    private suspend fun <T> getCachedOrFetch(
        cacheKey: String,
        expiryMillis: Long,
        forceRefresh: Boolean,
        clazz: Class<T>,
        fetcher: suspend () -> T
    ): T {
        if (!forceRefresh) {
            val cachedEntry = animeDao.getCache(cacheKey)
            if (cachedEntry != null) {
                val age = System.currentTimeMillis() - cachedEntry.lastUpdated
                if (age < expiryMillis) {
                    try {
                        return gson.fromJson(cachedEntry.jsonResponse, clazz)
                    } catch (e: Exception) {
                        // ignore and fetch
                    }
                }
            }
        }
        val result = fetcher()
        try {
            animeDao.insertCache(
                com.animein.app.data.local.entity.CacheEntity(
                    cacheKey = cacheKey,
                    jsonResponse = gson.toJson(result),
                    lastUpdated = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            // ignore cache write errors
        }
        return result
    }

    suspend fun getHome(forceRefresh: Boolean = false): HomeResponse {
        if (!forceRefresh && inMemoryHome != null) {
            return inMemoryHome!!
        }
        return try {
            val response = getCachedOrFetch(
                cacheKey = "home_data_v2",
                expiryMillis = 2 * 60 * 60 * 1000L, // 2 hours
                forceRefresh = forceRefresh,
                clazz = HomeResponse::class.java,
                fetcher = { directScraper.getHome() }
            )
            inMemoryHome = response
            response
        } catch (e: Exception) {
            inMemoryHome ?: throw e
        }
    }

    suspend fun getOngoingAnimeList(page: Int, forceRefresh: Boolean = false): AnimeListResponse {
        if (page > 1) {
            return try {
                directScraper.getOngoingAnimeList(page)
            } catch (e: Exception) {
                AnimeListResponse("error", emptyList())
            }
        }
        return try {
            val response = getCachedOrFetch(
                cacheKey = "ongoing_page_1",
                expiryMillis = 2 * 60 * 60 * 1000L, // 2 hours
                forceRefresh = forceRefresh,
                clazz = AnimeListResponse::class.java,
                fetcher = { directScraper.getOngoingAnimeList(1) }
            )
            inMemoryOngoing = response
            response
        } catch (e: Exception) {
            inMemoryOngoing ?: AnimeListResponse("error", emptyList())
        }
    }

    suspend fun getCompletedAnimeList(page: Int, forceRefresh: Boolean = false): AnimeListResponse {
        if (page > 1) {
            return try {
                directScraper.getCompletedAnimeList(page)
            } catch (e: Exception) {
                AnimeListResponse("error", emptyList())
            }
        }
        return try {
            val response = getCachedOrFetch(
                cacheKey = "completed_page_1",
                expiryMillis = 12 * 60 * 60 * 1000L, // 12 hours
                forceRefresh = forceRefresh,
                clazz = AnimeListResponse::class.java,
                fetcher = { directScraper.getCompletedAnimeList(1) }
            )
            inMemoryCompleted = response
            response
        } catch (e: Exception) {
            inMemoryCompleted ?: AnimeListResponse("error", emptyList())
        }
    }

    suspend fun searchAnime(query: String): SearchResponse {
        return try {
            directScraper.searchAnime(query)
        } catch (e: Exception) {
            SearchResponse("error", emptyList())
        }
    }

    suspend fun getGenres(forceRefresh: Boolean = false): GenreListResponse {
        return try {
            getCachedOrFetch(
                cacheKey = "genres_list",
                expiryMillis = 24 * 60 * 60 * 1000L, // 24 hours
                forceRefresh = forceRefresh,
                clazz = GenreListResponse::class.java,
                fetcher = { directScraper.getGenres() }
            )
        } catch (e: Exception) {
            GenreListResponse(emptyList())
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

    suspend fun getSchedule(forceRefresh: Boolean = false): ScheduleResponse {
        return try {
            getCachedOrFetch(
                cacheKey = "schedule_data",
                expiryMillis = 12 * 60 * 60 * 1000L, // 12 hours
                forceRefresh = forceRefresh,
                clazz = ScheduleResponse::class.java,
                fetcher = { directScraper.getSchedule() }
            )
        } catch (e: Exception) {
            ScheduleResponse(emptyList())
        }
    }
}

