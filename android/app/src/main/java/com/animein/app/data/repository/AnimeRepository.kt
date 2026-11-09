package com.animein.app.data.repository

import com.animein.app.data.local.dao.AnimeDao
import com.animein.app.data.local.entity.ContinueWatchingEntity
import com.animein.app.data.local.entity.FavoriteEntity
import com.animein.app.data.local.entity.HistoryEntity
import com.animein.app.data.local.pref.PreferencesManager
import com.animein.app.data.model.*
import com.animein.app.data.remote.AnimeApiService
import com.animein.app.data.remote.MirrorRequest
import com.animein.app.data.remote.OtakudesuDirectScraper
import com.animein.app.data.remote.AnimeInWebScraper
import com.animein.app.data.remote.AnimeDataSource
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
    private val otakudesuScraper: OtakudesuDirectScraper,
    private val animeInWebScraper: AnimeInWebScraper
) {
    private val gson = Gson()
    private var inMemoryHome: HomeResponse? = null
    private var inMemoryOngoing: AnimeListResponse? = null
    private var inMemoryCompleted: AnimeListResponse? = null
    private var lastActiveSource: String? = null

    private suspend fun getActiveScraper(): AnimeDataSource {
        val currentSource = preferencesManager.getActiveSource()
        if (lastActiveSource != currentSource) {
            lastActiveSource = currentSource
            // Invalidate in-memory caches when switching sources
            inMemoryHome = null
            inMemoryOngoing = null
            inMemoryCompleted = null
        }
        return if (currentSource == "animeinweb") {
            animeInWebScraper
        } else {
            otakudesuScraper
        }
    }

    private suspend fun <T> getCachedOrFetch(
        cacheKey: String,
        expiryMillis: Long,
        forceRefresh: Boolean,
        clazz: Class<T>,
        fetcher: suspend () -> T
    ): T {
        val sourceKey = "${cacheKey}_${preferencesManager.getActiveSource()}"
        if (!forceRefresh) {
            val cachedEntry = animeDao.getCache(sourceKey)
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
        var shouldCache = true
        when (result) {
            is HomeResponse -> {
                if (result.home.ongoing.isEmpty() && result.home.complete.isEmpty()) {
                    shouldCache = false
                }
            }
            is AnimeListResponse -> {
                if (result.status == "error" || result.animeList.isEmpty()) {
                    shouldCache = false
                }
            }
            is ScheduleResponse -> {
                if (result.scheduleList.isEmpty()) {
                    shouldCache = false
                }
            }
            is GenreListResponse -> {
                if (result.genreList.isEmpty()) {
                    shouldCache = false
                }
            }
        }
        if (shouldCache) {
            try {
                animeDao.insertCache(
                    com.animein.app.data.local.entity.CacheEntity(
                        cacheKey = sourceKey,
                        jsonResponse = gson.toJson(result),
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                // ignore cache write errors
            }
        }
        return result
    }

    suspend fun getHome(forceRefresh: Boolean = false): HomeResponse {
        val scraper = getActiveScraper()
        if (!forceRefresh && inMemoryHome != null) {
            return inMemoryHome!!
        }
        return try {
            val response = getCachedOrFetch(
                cacheKey = "home_data_v4",
                expiryMillis = 2 * 60 * 60 * 1000L, // 2 hours
                forceRefresh = forceRefresh,
                clazz = HomeResponse::class.java,
                fetcher = { scraper.getHome() }
            )
            inMemoryHome = response
            response
        } catch (e: Exception) {
            inMemoryHome ?: throw e
        }
    }

    suspend fun getOngoingAnimeList(page: Int, forceRefresh: Boolean = false): AnimeListResponse {
        val scraper = getActiveScraper()
        if (page > 1) {
            return try {
                scraper.getOngoingAnimeList(page)
            } catch (e: Exception) {
                AnimeListResponse("error", emptyList())
            }
        }
        return try {
            val response = getCachedOrFetch(
                cacheKey = "ongoing_page_1_v4",
                expiryMillis = 2 * 60 * 60 * 1000L, // 2 hours
                forceRefresh = forceRefresh,
                clazz = AnimeListResponse::class.java,
                fetcher = { scraper.getOngoingAnimeList(1) }
            )
            inMemoryOngoing = response
            response
        } catch (e: Exception) {
            inMemoryOngoing ?: AnimeListResponse("error", emptyList())
        }
    }

    suspend fun getCompletedAnimeList(page: Int, forceRefresh: Boolean = false): AnimeListResponse {
        val scraper = getActiveScraper()
        if (page > 1) {
            return try {
                scraper.getCompletedAnimeList(page)
            } catch (e: Exception) {
                AnimeListResponse("error", emptyList())
            }
        }
        return try {
            val response = getCachedOrFetch(
                cacheKey = "completed_page_1_v4",
                expiryMillis = 12 * 60 * 60 * 1000L, // 12 hours
                forceRefresh = forceRefresh,
                clazz = AnimeListResponse::class.java,
                fetcher = { scraper.getCompletedAnimeList(1) }
            )
            inMemoryCompleted = response
            response
        } catch (e: Exception) {
            inMemoryCompleted ?: AnimeListResponse("error", emptyList())
        }
    }

    suspend fun searchAnime(query: String): SearchResponse {
        return try {
            getActiveScraper().searchAnime(query)
        } catch (e: Exception) {
            SearchResponse("error", emptyList())
        }
    }

    suspend fun getGenres(forceRefresh: Boolean = false): GenreListResponse {
        val scraper = getActiveScraper()
        return try {
            getCachedOrFetch(
                cacheKey = "genres_list",
                expiryMillis = 24 * 60 * 60 * 1000L, // 24 hours
                forceRefresh = forceRefresh,
                clazz = GenreListResponse::class.java,
                fetcher = { scraper.getGenres() }
            )
        } catch (e: Exception) {
            GenreListResponse(emptyList())
        }
    }

    suspend fun getAnimeByGenre(genreId: String, genreName: String = "", page: Int): AnimeListResponse {
        return try {
            getActiveScraper().getAnimeByGenre(genreId, genreName, page)
        } catch (e: Exception) {
            AnimeListResponse("error", emptyList())
        }
    }


    suspend fun getAnimeDetail(animeId: String): DetailResponse {
        val targetSource = if (animeId.all { it.isDigit() }) "animeinweb" else "otakudesu"
        val currentSource = preferencesManager.getActiveSource()
        if (currentSource != targetSource) {
            preferencesManager.setActiveSource(targetSource)
        }
        val scraper = getActiveScraper()
        // Determine whether to normalize ID depending on active source (AnimeInWeb has numeric IDs, Otakudesu needs normalization)
        val isAnimeInWeb = preferencesManager.getActiveSource() == "animeinweb"
        val normalizedAnimeId = if (isAnimeInWeb) animeId else AnimeIdUtils.normalizeAnimeId(animeId)
        
        val detail = scraper.getAnimeDetail(normalizedAnimeId)
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
        val targetSource = if (episodeId.all { it.isDigit() }) "episodeId".let { episodeId.all { it.isDigit() } } else false
        val cleanEpisodeId = episodeId
        val finalTargetSource = if (cleanEpisodeId.all { it.isDigit() }) "animeinweb" else "otakudesu"
        val currentSource = preferencesManager.getActiveSource()
        if (currentSource != finalTargetSource) {
            preferencesManager.setActiveSource(finalTargetSource)
        }
        val scraper = getActiveScraper()
        val isAnimeInWeb = preferencesManager.getActiveSource() == "animeinweb"
        val normalizedEpisodeId = if (isAnimeInWeb) cleanEpisodeId else AnimeIdUtils.normalizeEpisodeId(cleanEpisodeId)
        return scraper.getEpisodeDetail(normalizedEpisodeId)
    }

    suspend fun getMirrorStream(animeId: String, mirrorId: String): MirrorResponse {
        return getActiveScraper().getMirrorStream(mirrorId)
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
        val scraper = getActiveScraper()
        return try {
            val response = getCachedOrFetch(
                cacheKey = "schedule_data_v2",
                expiryMillis = 12 * 60 * 60 * 1000L, // 12 hours
                forceRefresh = forceRefresh,
                clazz = ScheduleResponse::class.java,
                fetcher = { scraper.getSchedule() }
            )
            val enrichedList = enrichScheduleThumbs(response.scheduleList)
            ScheduleResponse(enrichedList)
        } catch (e: Exception) {
            ScheduleResponse(emptyList())
        }
    }

    private suspend fun enrichScheduleThumbs(scheduleList: List<ScheduleItem>): List<ScheduleItem> {
        val hasMissingThumbs = scheduleList.any { day -> day.animeList.any { it.thumb.isNullOrBlank() } }
        if (!hasMissingThumbs) return scheduleList

        val thumbMap = mutableMapOf<String, String>()
        // 1. InMemory home cache
        inMemoryHome?.home?.ongoing?.forEach { if (!it.thumb.isNullOrBlank()) thumbMap[it.id] = it.thumb }
        inMemoryHome?.home?.complete?.forEach { if (!it.thumb.isNullOrBlank()) thumbMap[it.id] = it.thumb }
        // 2. InMemory ongoing cache
        inMemoryOngoing?.animeList?.forEach { if (!it.thumb.isNullOrBlank()) thumbMap[it.id] = it.thumb }

        // 3. Fallback to cached DB entries
        if (thumbMap.isEmpty()) {
            val source = preferencesManager.getActiveSource()
            try {
                val homeCache = animeDao.getCache("home_data_$source")
                if (homeCache != null) {
                    val homeResp = gson.fromJson(homeCache.jsonResponse, HomeResponse::class.java)
                    homeResp.home.ongoing.forEach { if (!it.thumb.isNullOrBlank()) thumbMap[it.id] = it.thumb }
                    homeResp.home.complete.forEach { if (!it.thumb.isNullOrBlank()) thumbMap[it.id] = it.thumb }
                }
            } catch (_: Exception) {}
            try {
                val ongoingCache = animeDao.getCache("ongoing_data_$source")
                if (ongoingCache != null) {
                    val ongoingResp = gson.fromJson(ongoingCache.jsonResponse, AnimeListResponse::class.java)
                    ongoingResp.animeList.forEach { if (!it.thumb.isNullOrBlank()) thumbMap[it.id] = it.thumb }
                }
            } catch (_: Exception) {}
        }

        if (thumbMap.isEmpty()) return scheduleList

        return scheduleList.map { day ->
            day.copy(
                animeList = day.animeList.map { anime ->
                    if (anime.thumb.isNullOrBlank() && thumbMap.containsKey(anime.id)) {
                        anime.copy(thumb = thumbMap[anime.id])
                    } else {
                        anime
                    }
                }
            )
        }
    }
}


