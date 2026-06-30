package com.animein.app.data.remote

import com.animein.app.data.model.*
import retrofit2.http.*

interface AnimeApiService {

    @GET("api/home")
    suspend fun getHome(): HomeResponse

    @GET("api/ongoing/page/{page}")
    suspend fun getOngoingAnimeList(
        @Path("page") page: Int
    ): AnimeListResponse

    @GET("api/complete/page/{page}")
    suspend fun getCompletedAnimeList(
        @Path("page") page: Int
    ): AnimeListResponse

    @GET("api/search/{query}")
    suspend fun searchAnime(
        @Path("query") query: String
    ): SearchResponse

    @GET("api/genres")
    suspend fun getGenres(): GenreListResponse

    @GET("api/genres/{genreId}/page/{page}")
    suspend fun getAnimeByGenre(
        @Path("genreId") genreId: String,
        @Path("page") page: Int
    ): AnimeListResponse

    @GET("api/anime/{animeId}")
    suspend fun getAnimeDetail(
        @Path("animeId") animeId: String
    ): DetailResponse

    @GET("api/eps/{episodeId}")
    suspend fun getEpisodeDetail(
        @Path("episodeId") episodeId: String
    ): EpisodeDetailResponse

    @POST("api/eps/{animeId}/mirror/")
    suspend fun getMirrorStream(
        @Path("animeId") animeId: String,
        @Body body: MirrorRequest
    ): MirrorResponse

    @GET("api/schedule")
    suspend fun getSchedule(): ScheduleResponse
}

data class MirrorRequest(
    val mirrorId: String
)
