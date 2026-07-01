package com.animein.app.data.local.dao

import androidx.room.*
import com.animein.app.data.local.entity.CacheEntity
import com.animein.app.data.local.entity.ContinueWatchingEntity
import com.animein.app.data.local.entity.FavoriteEntity
import com.animein.app.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {
    // Favorites
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT * FROM favorites WHERE id = :id)")
    suspend fun isFavorite(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteFavorite(id: String)

    // History
    @Query("SELECT * FROM history ORDER BY viewedAt DESC LIMIT 50")
    fun getHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteHistory(id: String)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // Continue Watching
    @Query("SELECT * FROM continue_watching WHERE lastWatchedAt IN (SELECT MAX(lastWatchedAt) FROM continue_watching GROUP BY animeId) ORDER BY lastWatchedAt DESC")
    fun getContinueWatchingList(): Flow<List<ContinueWatchingEntity>>

    @Query("SELECT * FROM continue_watching WHERE animeId = :animeId ORDER BY lastWatchedAt DESC LIMIT 1")
    suspend fun getLatestContinueWatchingForAnime(animeId: String): ContinueWatchingEntity?

    @Query("SELECT * FROM continue_watching WHERE episodeId = :episodeId")
    suspend fun getContinueWatchingForEpisode(episodeId: String): ContinueWatchingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContinueWatching(continueWatching: ContinueWatchingEntity)

    @Query("DELETE FROM continue_watching WHERE episodeId = :episodeId")
    suspend fun deleteContinueWatching(episodeId: String)

    // General Cache Table
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: CacheEntity)

    @Query("SELECT * FROM cache_table WHERE cacheKey = :cacheKey")
    suspend fun getCache(cacheKey: String): CacheEntity?

    @Query("DELETE FROM cache_table WHERE cacheKey = :cacheKey")
    suspend fun deleteCache(cacheKey: String)
}

