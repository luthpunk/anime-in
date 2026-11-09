package com.animein.app.data.remote

import com.animein.app.data.model.*

interface AnimeDataSource {
    suspend fun getHome(): HomeResponse
    suspend fun getOngoingAnimeList(page: Int): AnimeListResponse
    suspend fun getCompletedAnimeList(page: Int): AnimeListResponse
    suspend fun searchAnime(query: String): SearchResponse
    suspend fun getGenres(): GenreListResponse
    suspend fun getAnimeByGenre(genreId: String, genreName: String = "", page: Int): AnimeListResponse
    suspend fun getAnimeDetail(animeId: String): DetailResponse
    suspend fun getEpisodeDetail(episodeId: String): EpisodeDetailResponse
    suspend fun getMirrorStream(mirrorId: String): MirrorResponse
    suspend fun getSchedule(): ScheduleResponse
}
