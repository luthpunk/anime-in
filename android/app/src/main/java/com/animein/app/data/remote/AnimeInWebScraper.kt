package com.animein.app.data.remote

import com.animein.app.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimeInWebScraper @Inject constructor(
    private val okHttpClient: OkHttpClient
) : AnimeDataSource {

    private val gson = Gson()
    private val baseUrl = "https://animeinweb.com/api/proxy/"
    private val proxySecret = "animein-secure-proxy-key-123"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private suspend fun makeGetRequest(path: String, queryParams: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val urlBuilder = (baseUrl + path).toHttpUrlOrNull()?.newBuilder()
            ?: throw IOException("Invalid URL path: $path")

        queryParams.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("x-proxy-secret", proxySecret)
            .header("User-Agent", userAgent)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP error: ${response.code} for path: $path")
            }
            response.body?.string() ?: throw IOException("Empty response body")
        }
    }

    override suspend fun getHome(): HomeResponse = withContext(Dispatchers.IO) {
        try {
            val json = makeGetRequest("3/2/home/data")
            val type = object : TypeToken<WebResponse<WebHomeData>>() {}.type
            val apiResponse = gson.fromJson<WebResponse<WebHomeData>>(json, type)

            if (apiResponse?.error == true || apiResponse?.status != 200 || apiResponse?.data == null) {
                throw IOException(apiResponse?.message ?: "API error in getHome")
            }

            val data = apiResponse.data
            val ongoingSource = if (!data?.today.isNullOrEmpty()) data?.today else data?.hot
            val ongoingList = (ongoingSource ?: emptyList()).map { item ->
                AnimeOngoing(
                    title = item.title ?: "Tanpa Judul",
                    id = item.id ?: "",
                    thumb = item.image_poster ?: "",
                    episode = "Update",
                    uploadedOn = item.aired_start ?: "",
                    dayUpdated = item.day ?: "",
                    link = ""
                )
            }

            val completedList = (data?.popular ?: emptyList()).map { item ->
                AnimeCompleted(
                    title = item.title ?: "Tanpa Judul",
                    id = item.id ?: "",
                    thumb = item.image_poster ?: "",
                    episode = "Tamat",
                    uploadedOn = item.aired_start ?: "",
                    score = null,
                    link = ""
                )
            }

            HomeResponse("ok", HomeData(ongoingList, completedList))
        } catch (e: Exception) {
            HomeResponse("error", HomeData(emptyList(), emptyList()))
        }
    }

    override suspend fun getOngoingAnimeList(page: Int): AnimeListResponse = withContext(Dispatchers.IO) {
        try {
            val json = makeGetRequest("3/2/explore/movie", mapOf("sort" to "latest", "page" to page.toString()))
            val type = object : TypeToken<WebResponse<WebExploreData>>() {}.type
            val apiResponse = gson.fromJson<WebResponse<WebExploreData>>(json, type)

            if (apiResponse?.error == true || apiResponse?.status != 200 || apiResponse?.data == null) {
                return@withContext AnimeListResponse("error", emptyList())
            }

            val list = (apiResponse.data?.movie ?: emptyList()).map { item ->
                AnimeListItem(
                    title = item.title ?: "Tanpa Judul",
                    animeName = item.title ?: "Tanpa Judul",
                    id = item.id ?: "",
                    thumb = item.image_poster,
                    episode = "Update",
                    score = null,
                    studio = null,
                    releaseDate = item.aired_start,
                    genreList = item.genre?.split(",")?.map { Genre(it.trim(), it.trim(), null) }
                )
            }
            AnimeListResponse("ok", list)
        } catch (e: Exception) {
            AnimeListResponse("error", emptyList())
        }
    }

    override suspend fun getCompletedAnimeList(page: Int): AnimeListResponse = withContext(Dispatchers.IO) {
        try {
            val json = makeGetRequest("3/2/explore/movie", mapOf("sort" to "views", "page" to page.toString()))
            val type = object : TypeToken<WebResponse<WebExploreData>>() {}.type
            val apiResponse = gson.fromJson<WebResponse<WebExploreData>>(json, type)

            if (apiResponse?.error == true || apiResponse?.status != 200 || apiResponse?.data == null) {
                return@withContext AnimeListResponse("error", emptyList())
            }

            val list = (apiResponse.data?.movie ?: emptyList()).map { item ->
                AnimeListItem(
                    title = item.title ?: "Tanpa Judul",
                    animeName = item.title ?: "Tanpa Judul",
                    id = item.id ?: "",
                    thumb = item.image_poster,
                    episode = "Tamat",
                    score = null,
                    studio = null,
                    releaseDate = item.aired_start,
                    genreList = item.genre?.split(",")?.map { Genre(it.trim(), it.trim(), null) }
                )
            }
            AnimeListResponse("ok", list)
        } catch (e: Exception) {
            AnimeListResponse("error", emptyList())
        }
    }

    override suspend fun searchAnime(query: String): SearchResponse = withContext(Dispatchers.IO) {
        try {
            val json = makeGetRequest("3/2/explore/movie", mapOf("keyword" to query))
            val type = object : TypeToken<WebResponse<WebExploreData>>() {}.type
            val apiResponse = gson.fromJson<WebResponse<WebExploreData>>(json, type)

            if (apiResponse?.error == true || apiResponse?.status != 200 || apiResponse?.data == null) {
                return@withContext SearchResponse("error", emptyList())
            }

            val list = (apiResponse.data?.movie ?: emptyList()).map { item ->
                SearchItem(
                    title = item.title ?: "Tanpa Judul",
                    id = item.id ?: "",
                    thumb = item.image_poster,
                    status = item.status,
                    score = null,
                    genreList = item.genre?.split(",")?.map { Genre(it.trim(), it.trim(), null) }
                )
            }
            SearchResponse("ok", list)
        } catch (e: Exception) {
            SearchResponse("error", emptyList())
        }
    }

    override suspend fun getGenres(): GenreListResponse = withContext(Dispatchers.IO) {
        try {
            val json = makeGetRequest("3/2/explore/genre")
            val type = object : TypeToken<WebResponse<WebGenreData>>() {}.type
            val apiResponse = gson.fromJson<WebResponse<WebGenreData>>(json, type)

            if (apiResponse?.error == true || apiResponse?.status != 200 || apiResponse?.data == null) {
                return@withContext GenreListResponse(emptyList())
            }

            val list = (apiResponse.data?.genre ?: emptyList()).map { item ->
                GenreItem(
                    genreName = item.name ?: "Unknown",
                    id = item.id ?: "",
                    link = "",
                    imageLink = item.image
                )
            }
            GenreListResponse(genreList = list)
        } catch (e: Exception) {
            GenreListResponse(emptyList())
        }
    }

    override suspend fun getAnimeByGenre(genreId: String, page: Int): AnimeListResponse = withContext(Dispatchers.IO) {
        try {
            val json = makeGetRequest("3/2/explore/movie", mapOf("genreIds[]" to genreId, "page" to page.toString()))
            val type = object : TypeToken<WebResponse<WebExploreData>>() {}.type
            val apiResponse = gson.fromJson<WebResponse<WebExploreData>>(json, type)

            if (apiResponse?.error == true || apiResponse?.status != 200 || apiResponse?.data == null) {
                return@withContext AnimeListResponse("error", emptyList())
            }

            val list = (apiResponse.data?.movie ?: emptyList()).map { item ->
                AnimeListItem(
                    title = item.title ?: "Tanpa Judul",
                    animeName = item.title ?: "Tanpa Judul",
                    id = item.id ?: "",
                    thumb = item.image_poster,
                    episode = "Detail",
                    score = null,
                    studio = null,
                    releaseDate = item.aired_start,
                    genreList = item.genre?.split(",")?.map { Genre(it.trim(), it.trim(), null) }
                )
            }
            AnimeListResponse("ok", list)
        } catch (e: Exception) {
            AnimeListResponse("error", emptyList())
        }
    }

    override suspend fun getAnimeDetail(animeId: String): DetailResponse = withContext(Dispatchers.IO) {
        val json = makeGetRequest("3/2/movie/detail/$animeId")
        val type = object : TypeToken<WebResponse<WebDetailData>>() {}.type
        val apiResponse = gson.fromJson<WebResponse<WebDetailData>>(json, type)

        if (apiResponse?.error == true || apiResponse?.status != 200 || apiResponse?.data?.movie == null) {
            throw IOException(apiResponse?.message ?: "Anime not found")
        }

        val movie = apiResponse.data.movie
        val latestEpisode = apiResponse.data.episode

        val episodeList = mutableListOf<EpisodeItem>()
        if (latestEpisode?.id != null && movie.id != null) {
            try {
                val epListJson = makeGetRequest("3/2/movie/episode/${movie.id}/${latestEpisode.id}")
                val epListType = object : TypeToken<WebResponse<WebEpisodeListData>>() {}.type
                val epListResponse = gson.fromJson<WebResponse<WebEpisodeListData>>(epListJson, epListType)
                
                (epListResponse.data?.episode ?: emptyList()).forEach { ep ->
                    episodeList.add(
                        EpisodeItem(
                            title = ep.title ?: "Episode",
                            id = ep.id ?: "",
                            link = "",
                            uploadedOn = ep.key_time
                        )
                    )
                }
            } catch (e: Exception) {
                episodeList.add(
                    EpisodeItem(
                        title = latestEpisode.title ?: "Episode Terbaru",
                        id = latestEpisode.id ?: "",
                        link = "",
                        uploadedOn = latestEpisode.key_time
                    )
                )
            }
        } else if (latestEpisode != null) {
            episodeList.add(
                EpisodeItem(
                    title = latestEpisode.title ?: "Episode Terbaru",
                    id = latestEpisode.id ?: "",
                    link = "",
                    uploadedOn = latestEpisode.key_time
                )
            )
        }

        DetailResponse(
            thumb = movie.image_poster,
            animeId = movie.id ?: "",
            synopsis = movie.synopsis ?: "",
            title = movie.title ?: "Tanpa Judul",
            japanese = movie.synonyms,
            score = null,
            producer = null,
            type = movie.type,
            status = movie.status,
            totalEpisode = null,
            duration = movie.time,
            releaseDate = movie.aired_start,
            studio = movie.studio,
            genreList = movie.genre?.split(",")?.map { Genre(it.trim(), it.trim(), null) },
            episodeList = episodeList,
            batchLink = null
        )
    }

    override suspend fun getEpisodeDetail(episodeId: String): EpisodeDetailResponse = withContext(Dispatchers.IO) {
        val json = makeGetRequest("3/2/episode/streamnew/$episodeId")
        val type = object : TypeToken<WebResponse<WebStreamData>>() {}.type
        val apiResponse = gson.fromJson<WebResponse<WebStreamData>>(json, type)

        if (apiResponse?.error == true || apiResponse?.status != 200 || apiResponse?.data?.server == null) {
            throw IOException(apiResponse?.message ?: "Episode stream not found")
        }

        val servers = apiResponse.data.server
        val defaultStream = servers.firstOrNull { it.quality == "480p" || it.quality == "480" }?.link
            ?: servers.firstOrNull()?.link ?: ""

        val mirror1 = servers.filter { it.quality == "360p" || it.quality == "360" }.let { list ->
            if (list.isEmpty()) null
            else MirrorQuality("360p", list.map { MirrorItem(it.name ?: "Server 360p", it.link ?: "") })
        }
        val mirror2 = servers.filter { it.quality == "480p" || it.quality == "480" }.let { list ->
            if (list.isEmpty()) null
            else MirrorQuality("480p", list.map { MirrorItem(it.name ?: "Server 480p", it.link ?: "") })
        }
        val mirror3 = servers.filter { it.quality == "720p" || it.quality == "1080p" || it.quality == "720" || it.quality == "1080" }.let { list ->
            if (list.isEmpty()) null
            else MirrorQuality("720p", list.map { MirrorItem(it.name ?: "Server HD", it.link ?: "") })
        }

        EpisodeDetailResponse(
            title = apiResponse.data.episode?.title ?: "Episode Detail",
            id = episodeId,
            linkStream = defaultStream,
            mirror1 = mirror1,
            mirror2 = mirror2,
            mirror3 = mirror3,
            quality = null,
            downloadLinks = emptyList()
        )
    }

    override suspend fun getMirrorStream(mirrorId: String): MirrorResponse = withContext(Dispatchers.IO) {
        MirrorResponse(
            title = "Direct",
            id = mirrorId,
            streamLink = mirrorId,
            linkStream = mirrorId
        )
    }

    override suspend fun getSchedule(): ScheduleResponse = withContext(Dispatchers.IO) {
        try {
            val daysList = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")
            val scheduleItems = mutableListOf<ScheduleItem>()

            for (day in daysList) {
                try {
                    val json = makeGetRequest("3/2/schedule/data", mapOf("day" to day))
                    val type = object : TypeToken<WebResponse<WebExploreData>>() {}.type
                    val apiResponse = gson.fromJson<WebResponse<WebExploreData>>(json, type)

                    val animeList = if (apiResponse?.error == false && apiResponse.status == 200) {
                        (apiResponse.data?.movie ?: emptyList()).map { movie ->
                            ScheduleAnimeItem(
                                animeName = movie.title ?: "Tanpa Judul",
                                id = movie.id ?: "",
                                link = "",
                                thumb = movie.image_poster,
                                episode = "Update",
                                isUpdated = false
                            )
                        }
                    } else emptyList()

                    scheduleItems.add(ScheduleItem(day = day, animeList = animeList))
                } catch (e: Exception) {
                    scheduleItems.add(ScheduleItem(day = day, animeList = emptyList()))
                }
            }

            ScheduleResponse(scheduleItems)
        } catch (e: Exception) {
            ScheduleResponse(emptyList())
        }
    }

    private data class WebResponse<T>(
        val status: Int?,
        val error: Boolean?,
        val data: T?,
        val message: String?
    )

    private data class WebHomeData(
        val hot: List<WebMovieItem>?,
        val new: List<WebMovieItem>?,
        val today: List<WebMovieItem>?,
        val popular: List<WebMovieItem>?
    )

    private data class WebExploreData(
        val movie: List<WebMovieItem>?
    )

    private data class WebMovieItem(
        val id: String?,
        val title: String?,
        val synopsis: String?,
        val synonyms: String?,
        val image_poster: String?,
        val image_cover: String?,
        val type: String?,
        val year: String?,
        val day: String?,
        val status: String?,
        val views: String?,
        val favorites: String?,
        val aired_start: String?,
        val genre: String?,
        val time: String?,
        val studio: String?
    )

    private data class WebGenreData(
        val genre: List<WebGenreItem>?
    )

    private data class WebGenreItem(
        val id: String?,
        val name: String?,
        val image: String?,
        val group: String?
    )

    private data class WebDetailData(
        val movie: WebMovieItem?,
        val episode: WebEpisodeItem?,
        val season: List<Any>?
    )

    private data class WebEpisodeItem(
        val id: String?,
        val index: String?,
        val title: String?,
        val views: String?,
        val id_movie: String?,
        val key_time: String?
    )

    private data class WebEpisodeListData(
        val episode: List<WebEpisodeItem>?
    )

    private data class WebStreamData(
        val episode: WebEpisodeItem?,
        val server: List<WebServerItem>?
    )

    private data class WebServerItem(
        val id: String?,
        val link: String?,
        val quality: String?,
        val name: String?,
        val type: String?
    )
}
