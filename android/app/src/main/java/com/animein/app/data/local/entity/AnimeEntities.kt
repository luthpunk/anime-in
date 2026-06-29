package com.animein.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val thumb: String,
    val score: String,
    val type: String,
    val status: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val thumb: String,
    val viewedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "continue_watching")
data class ContinueWatchingEntity(
    @PrimaryKey val episodeId: String,
    val animeId: String,
    val animeTitle: String,
    val episodeTitle: String,
    val thumb: String,
    val progress: Long,
    val duration: Long,
    val lastWatchedAt: Long = System.currentTimeMillis()
)
