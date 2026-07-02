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

            if (apiResponse.error || apiResponse.status != 200) {
                throw IOException(apiResponse.message ?: "API error in getHome")
            }

            val ongoingList = (apiResponse.data.hot ?: emptyList()).map { item ->
                AnimeOngoing(
                    title = item.title,
                    id = item.id,
                    thumb = item.image_poster ?: "",
                    episode = "Update",
                    uploadedOn = item.aired_start,
                    dayUpdated = item.day,
                    link = ""
                )
            }

            val completedList = (apiResponse.data.new ?: emptyList()).map { item ->
                AnimeCompleted(
                    title = item.title,
                    id = item.id,
                    thumb = item.image_poster ?: "",
                    episode = "Tamat",
                    uploadedOn = item.aired_start,
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
            // explore/movie sorted by latest returns ongoing + new items
            val json = makeGetRequest("3/2/explore/movie", mapOf("sort" to "latest", "page" to page.toString()))
            val type = object : TypeToken<WebResponse<WebExploreData>>() {}.type
            val apiResponse = gson.fromJson<WebResponse<WebExploreData>>(json, type)

            val list = (apiResponse.data.movie ?: emptyList()).map { item ->
                AnimeListItem(
                    title = item.title,
                    animeName = item.title,
                    id = item.id,
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
            // Filter completed from explore/movie by getting popular or sorted
            val json = makeGetRequest("3/2/explore/movie", mapOf("sort" to "views", "page" to page.toString()))
            val type = object : TypeToken<WebResponse<WebExploreData>>() {}.type
            val apiResponse = gson.fromJson<WebResponse<WebExploreData>>(json, type)

            val list = (apiResponse.data.movie ?: emptyList()).map { item ->
                AnimeListItem(
                    title = item.title,
                    animeName = item.title,
                    id = item.id,
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

            val list = (apiResponse.data.movie ?: emptyList()).map { item ->
                SearchItem(
                    title = item.title,
                    id = item.id,
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

            val list = (apiResponse.data.genre ?: emptyList()).map { item ->
                GenreItem(
                    genreName = item.name,
                    id = item.id,
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

            val list = (apiResponse.data.movie ?: emptyList()).map { item ->
                AnimeListItem(
                    title = item.title,
                    animeName = item.title,
                    id = item.id,
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

        if (apiResponse.error || apiResponse.status != 200 || apiResponse.data.movie == null) {
            throw IOException(apiResponse.message ?: "Anime not found")
        }

        val movie = apiResponse.data.movie
        val latestEpisode = apiResponse.data.episode

        // Fetch all episodes using the latest episode id
        val episodeList = mutableListOf<EpisodeItem>()
        if (latestEpisode != null) {
            try {
                val epListJson = makeGetRequest("3/2/movie/episode/${movie.id}/${latestEpisode.id}")
                val epListType = object : TypeToken<WebResponse<WebEpisodeListData>>() {}.type
                val epListResponse = gson.fromJson<WebResponse<WebEpisodeListData>>(epListJson, epListType)
                
                (epListResponse.data.episode ?: emptyList()).forEach { ep ->
                    episodeList.add(
                        EpisodeItem(
                            title = ep.title,
                            id = ep.id,
                            link = "",
                            uploadedOn = ep.key_time
                        )
                    )
                }
            } catch (e: Exception) {
                // Fallback to only the single latest episode if list fails
                episodeList.add(
                    EpisodeItem(
                        title = latestEpisode.title,
                        id = latestEpisode.id,
                        link = "",
                        uploadedOn = latestEpisode.key_time
                    )
                )
            }
        }

        DetailResponse(
            thumb = movie.image_poster,
            animeId = movie.id,
            synopsis = movie.synopsis,
            title = movie.title,
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

        if (apiResponse.error || apiResponse.status != 200 || apiResponse.data.server == null) {
            throw IOException(apiResponse.message ?: "Episode stream not found")
        }

        val servers = apiResponse.data.server
        val defaultStream = servers.firstOrNull { it.quality == "480p" || it.quality == "480" }?.link
            ?: servers.firstOrNull()?.link ?: ""

        val mirror1 = servers.filter { it.quality == "360p" || it.quality == "360" }.let { list ->
            if (list.isEmpty()) null
            else MirrorQuality("360p", list.map { MirrorItem(it.name, it.link) })
        }
        val mirror2 = servers.filter { it.quality == "480p" || it.quality == "480" }.let { list ->
            if (list.isEmpty()) null
            else MirrorQuality("480p", list.map { MirrorItem(it.name, it.link) })
        }
        val mirror3 = servers.filter { it.quality == "720p" || it.quality == "1080p" || it.quality == "720" || it.quality == "1080" }.let { list ->
            if (list.isEmpty()) null
            else MirrorQuality("720p", list.map { MirrorItem(it.name, it.link) })
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
        // Since mirrorId is the direct URL itself in our case, just return it
        MirrorResponse(
            title = "Direct",
            id = mirrorId,
            streamLink = mirrorId,
            linkStream = mirrorId
        )
    }

    override suspend fun getSchedule(): ScheduleResponse = withContext(Dispatchers.IO) {
        try {
            // Fetch home data which has today ongoing, and query explore sorted by latest to group
            val json = makeGetRequest("3/2/explore/movie", mapOf("sort" to "latest"))
            val type = object : TypeToken<WebResponse<WebExploreData>>() {}.type
            val apiResponse = gson.fromJson<WebResponse<WebExploreData>>(json, type)

            val daysMap = mutableMapOf<String, MutableList<ScheduleAnimeItem>>()
            val daysList = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")

            // Initialize all days
            daysList.forEach { daysMap[it] = mutableListOf() }

            (apiResponse.data.movie ?: emptyList()).forEach { movie ->
                val rawDay = movie.day?.uppercase() ?: "RANDOM"
                if (daysMap.containsKey(rawDay)) {
                    daysMap[rawDay]?.add(
                        ScheduleAnimeItem(
                            animeName = movie.title,
                            id = movie.id,
                            link = "",
                            thumb = movie.image_poster,
                            episode = "Update",
                            isUpdated = false
                        )
                    )
                }
            }

            val list = daysList.map { day ->
                ScheduleItem(
                    day = day,
                    animeList = daysMap[day] ?: emptyList()
                )
            }
            ScheduleResponse(list)
        } catch (e: Exception) {
            ScheduleResponse(emptyList())
        }
    }

    // --- Web API JSON DTOs ---

    private data class WebResponse<T>(
        val status: Int,
        val error: Boolean,
        val data: T,
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
        val id: String,
        val title: String,
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
        val id: String,
        val name: String,
        val image: String?,
        val group: String?
    )

    private data class WebDetailData(
        val movie: WebMovieItem?,
        val episode: WebEpisodeItem?,
        val season: List<Any>?
    )

    private data class WebEpisodeItem(
        val id: String,
        val index: String,
        val title: String,
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
        val id: String,
        val link: String,
        val quality: String,
        val name: String,
        val type: String
    )
}
