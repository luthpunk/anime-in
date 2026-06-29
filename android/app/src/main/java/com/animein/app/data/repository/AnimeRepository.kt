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
    private val directScraper: OtakudesuDirectScraper
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
            try {
                val response = apiService.getHome()
                inMemoryHome = response
                preferencesManager.saveHomeCache(gson.toJson(response))
                response
            } catch (apiEx: Exception) {
                inMemoryHome ?: throw apiEx
            }
        }
    }

    suspend fun getOngoingAnimeList(page: Int): AnimeListResponse {
        return try {
            apiService.getOngoingAnimeList(page)
        } catch (e: Exception) {
            directScraper.getOngoingAnimeList(page)
        }
    }

    suspend fun getCompletedAnimeList(page: Int): AnimeListResponse {
        return try {
            apiService.getCompletedAnimeList(page)
        } catch (e: Exception) {
            directScraper.getCompletedAnimeList(page)
        }
    }

    suspend fun searchAnime(query: String): SearchResponse {
        return try {
            val apiRes = apiService.searchAnime(query)
            if (apiRes.searchResults.isNullOrEmpty()) {
                directScraper.searchAnime(query)
            } else {
                apiRes
            }
        } catch (e: Exception) {
            directScraper.searchAnime(query)
        }
    }

    suspend fun getGenres(): GenreListResponse {
        return try {
            val apiRes = apiService.getGenres()
            if (apiRes.genreList.isNullOrEmpty()) {
                directScraper.getGenres()
            } else {
                apiRes
            }
        } catch (e: Exception) {
            directScraper.getGenres()
        }
    }

    suspend fun getAnimeByGenre(genreId: String, page: Int): AnimeListResponse {
        return apiService.getAnimeByGenre(genreId, page)
    }

    suspend fun getAnimeDetail(animeId: String): DetailResponse {
        val normalizedAnimeId = AnimeIdUtils.normalizeAnimeId(animeId)
        val detail = try {
            val apiDetail = apiService.getAnimeDetail(normalizedAnimeId)
            if (apiDetail.episodeList.isNullOrEmpty()) {
                directScraper.getAnimeDetail(normalizedAnimeId)
            } else {
                apiDetail
            }
        } catch (e: Exception) {
            directScraper.getAnimeDetail(normalizedAnimeId)
        }
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
        return try {
            val apiDetail = apiService.getEpisodeDetail(normalizedEpisodeId)
            if (apiDetail.linkStream.isNullOrBlank() || apiDetail.linkStream == "-") {
                directScraper.getEpisodeDetail(normalizedEpisodeId)
            } else {
                apiDetail
            }
        } catch (e: Exception) {
            directScraper.getEpisodeDetail(normalizedEpisodeId)
        }
    }

    suspend fun getMirrorStream(animeId: String, mirrorId: String): MirrorResponse {
        return try {
            val apiRes = apiService.getMirrorStream(AnimeIdUtils.normalizeAnimeId(animeId), MirrorRequest(mirrorId))
            if (apiRes.linkStream.isNullOrBlank() || apiRes.linkStream == "-") {
                directScraper.getMirrorStream(mirrorId)
            } else {
                apiRes
            }
        } catch (e: Exception) {
            directScraper.getMirrorStream(mirrorId)
        }
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
}
