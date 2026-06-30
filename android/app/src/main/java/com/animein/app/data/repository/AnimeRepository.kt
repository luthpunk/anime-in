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

    // ── Home ─────────────────────────────────────────
    suspend fun getHome(forceRefresh: Boolean = false): HomeResponse {
        if (!forceRefresh) {
            inMemoryHome?.let { return it }
            val cachedJson = preferencesManager.getHomeCache()
            if (!cachedJson.isNullOrEmpty()) {
                try {
                    val cached = gson.fromJson(cachedJson, HomeResponse::class.java)
                    inMemoryHome = cached
                    return cached
                } catch (_: Exception) {}
            }
        }

        // Direct scraper ke NontonAnimeID dari device (lebih reliable dari Vercel)
        return try {
            val response = directScraper.getHome()
            inMemoryHome = response
            preferencesManager.saveHomeCache(gson.toJson(response))
            response
        } catch (e: Exception) {
            inMemoryHome ?: throw e
        }
    }

    // ── Ongoing List ──────────────────────────────────
    suspend fun getOngoingAnimeList(page: Int): AnimeListResponse {
        return directScraper.getOngoingAnimeList(page)
    }

    // ── Complete List ─────────────────────────────────
    suspend fun getCompletedAnimeList(page: Int): AnimeListResponse {
        return directScraper.getCompletedAnimeList(page)
    }

    // ── Search ────────────────────────────────────────
    suspend fun searchAnime(query: String): SearchResponse {
        return directScraper.searchAnime(query)
    }

    // ── Genre List ────────────────────────────────────
    suspend fun getGenres(): GenreListResponse {
        return try {
            val apiRes = apiService.getGenres()
            if (apiRes.genreList.isNullOrEmpty()) directScraper.getGenres() else apiRes
        } catch (e: Exception) {
            directScraper.getGenres()
        }
    }


    // ── Anime by Genre ────────────────────────────────
    suspend fun getAnimeByGenre(genreId: String, page: Int): AnimeListResponse {
        return try {
            apiService.getAnimeByGenre(genreId, page)
        } catch (e: Exception) {
            AnimeListResponse(status = "error", animeList = emptyList())
        }
    }

    // ── Anime Detail ──────────────────────────────────
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
        // Simpan ke history
        animeDao.insertHistory(HistoryEntity(
            id = normalizedAnimeId,
            title = detail.title.ifBlank { "Tanpa Judul" },
            thumb = detail.thumb ?: ""
        ))
        return detail
    }

    // ── Episode Detail ────────────────────────────────
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

    // ── Mirror Stream ─────────────────────────────────
    suspend fun getMirrorStream(animeId: String, mirrorId: String): MirrorResponse {
        return try {
            val apiRes = apiService.getMirrorStream(
                AnimeIdUtils.normalizeAnimeId(animeId),
                MirrorRequest(mirrorId)
            )
            if (apiRes.linkStream.isNullOrBlank() || apiRes.linkStream == "-") {
                directScraper.getMirrorStream(mirrorId)
            } else {
                apiRes
            }
        } catch (e: Exception) {
            directScraper.getMirrorStream(mirrorId)
        }
    }

    // ── Local DB Operations ───────────────────────────
    fun getFavorites(): Flow<List<FavoriteEntity>> = animeDao.getFavorites()
    suspend fun isFavorite(id: String): Boolean = animeDao.isFavorite(id)
    suspend fun insertFavorite(favorite: FavoriteEntity) = animeDao.insertFavorite(favorite)
    suspend fun deleteFavorite(id: String) = animeDao.deleteFavorite(id)
    fun getHistory(): Flow<List<HistoryEntity>> = animeDao.getHistory()
    suspend fun deleteHistory(id: String) = animeDao.deleteHistory(id)
    suspend fun clearHistory() = animeDao.clearHistory()
    fun getContinueWatchingList(): Flow<List<ContinueWatchingEntity>> = animeDao.getContinueWatchingList()
    suspend fun getLatestContinueWatchingForAnime(animeId: String): ContinueWatchingEntity? =
        animeDao.getLatestContinueWatchingForAnime(animeId)
    suspend fun getContinueWatchingForEpisode(episodeId: String): ContinueWatchingEntity? =
        animeDao.getContinueWatchingForEpisode(episodeId)
    suspend fun insertContinueWatching(continueWatching: ContinueWatchingEntity) =
        animeDao.insertContinueWatching(continueWatching)
    suspend fun deleteContinueWatching(episodeId: String) =
        animeDao.deleteContinueWatching(episodeId)
}
