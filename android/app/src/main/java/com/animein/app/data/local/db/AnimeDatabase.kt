package com.animein.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.animein.app.data.local.dao.AnimeDao
import com.animein.app.data.local.entity.CacheEntity
import com.animein.app.data.local.entity.ContinueWatchingEntity
import com.animein.app.data.local.entity.FavoriteEntity
import com.animein.app.data.local.entity.HistoryEntity

@Database(
    entities = [FavoriteEntity::class, HistoryEntity::class, ContinueWatchingEntity::class, CacheEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AnimeDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
}
