package com.animein.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.animein.app.data.local.dao.AnimeDao
import com.animein.app.data.local.entity.ContinueWatchingEntity
import com.animein.app.data.local.entity.FavoriteEntity
import com.animein.app.data.local.entity.HistoryEntity

@Database(
    entities = [FavoriteEntity::class, HistoryEntity::class, ContinueWatchingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AnimeDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
}
