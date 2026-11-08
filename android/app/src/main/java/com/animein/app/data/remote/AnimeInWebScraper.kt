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
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimeInWebScraper @Inject constructor(
    private val okHttpClient: OkHttpClient
) : AnimeDataSource {

    companion object {
        private const val REMOTE_CONFIG_URL =
            "https://gist.githubusercontent.com/luthpunk/6e2772f4a5240b6f481455c0c6e497ff/raw/"
        private const val DEFAULT_BASE_URL = "https://animeinweb.com/api/proxy/"
        private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 jam

        @Volatile private var cachedDomain: String = DEFAULT_BASE_URL
        @Volatile private var lastFetchedAt: Long = 0L
    }

    private val gson = Gson()
    private val proxySecret = "animein-secure-proxy-key-123"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private suspend fun getBaseUrl(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastFetchedAt < CACHE_TTL_MS) return@withContext cachedDomain

        try {
            val req = Request.Builder()
                .url(REMOTE_CONFIG_URL)
                .header("Cache-Control", "no-cache")
                .build()
            val body = okHttpClient.newCall(req).execute().use { it.body?.string() ?: "" }
            val domain = org.json.JSONObject(body)
                .getJSONObject("animeinweb")
                .getString("baseUrl")
            if (domain.startsWith("http")) {
                cachedDomain = domain
                lastFetchedAt = now
                android.util.Log.d("AnimeInWebDomainConfig", "Domain aktif: $domain")
                return@withContext domain
            }
        } catch (e: Exception) {
            android.util.Log.w("AnimeInWebDomainConfig", "Gagal fetch remote config: ${e.message}")
        }

        DEFAULT_BASE_URL
    }

    private suspend fun makeGetRequest(path: String, queryParams: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val activeBaseUrl = getBaseUrl()
        val urlBuilder = (activeBaseUrl + path).toHttpUrlOrNull()?.newBuilder()
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

    private fun getDayName(daysOffset: Int = 0): String {
        val cal = Calendar.getInstance()
        if (daysOffset != 0) {
            cal.add(Calendar.DAY_OF_YEAR, daysOffset)
        }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "SENIN"
            Calendar.TUESDAY -> "SELASA"
            Calendar.WEDNESDAY -> "RABU"
            Calendar.THURSDAY -> "KAMIS"
            Calendar.FRIDAY -> "JUMAT"
            Calendar.SATURDAY -> "SABTU"
            Calendar.SUNDAY -> "MINGGU"
            else -> "SENIN"
        }
    }

    private fun resolveDisplayScore(item: WebMovieItem): String {
        return when {
            !item.score.isNullOrBlank() -> item.score
            !item.favorites.isNullOrBlank() && item.favorites.toIntOrNull() != null -> {
                val favs = item.favorites.toInt()
                val calc = (8.0 + (favs % 15) * 0.1).coerceIn(8.0, 9.5)
                String.format(java.util.Locale.US, "%.1f", calc)
            }
            else -> "8.4"
        }
    }

    private fun resolveCompletedEpisodeLabel(item: WebMovieItem): String {
        return when {
            !item.total_episodes.isNullOrBlank() && item.total_episodes != "0" -> "Eps ${item.total_episodes}"
            item.type.equals("MOVIE", ignoreCase = true) -> "Movie"
            item.type.equals("SPECIAL", ignoreCase = true) -> "Special"
            item.type.equals("OVA", ignoreCase = true) -> "OVA"
            item.type.equals("ONA", ignoreCase = true) -> "ONA"
            else -> "Tamat"
        }
    }

    /**
     * Mengambil daftar anime ongoing untuk:
     * - Hari ini (offset 0)
     * - 1 hari sebelumnya (offset -1)
     * - 2 hari sebelumnya (offset -2)
     */
    private suspend fun fetchThreeDaysOngoingItems(): List<Pair<WebMovieItem, String>> {
        val schedType = object : TypeToken<WebResponse<WebExploreData>>() {}.type
        val combined = mutableListOf<Pair<WebMovieItem, String>>()
        val seenIds = mutableSetOf<String>()

        for (offset in listOf(0, -1, -2)) {
            val dayName = getDayName(offset)
            try {
                val schedJson = makeGetRequest("3/2/schedule/data", mapOf("day" to dayName))
                val schedResponse = gson.fromJson<WebResponse<WebExploreData>>(schedJson, schedType)
                val movies = schedResponse?.data?.movie.orEmpty()

                val filtered = if (offset == 0) {
                    // Hari ini: utamakan yang sudah rilis ('new !!')
                    movies.filter { it.time != null && it.time.contains("new", ignoreCase = true) }
                } else {
                    // 1 hari & 2 hari sebelumnya: semua yang rilis di hari tersebut (bukan 'tunda'),
                    // dengan prioritas yang masih berlabel 'new !!' di urutan atas
                    movies
                        .filter { it.time == null || !it.time.contains("tunda", ignoreCase = true) }
                        .sortedByDescending { it.time?.contains("new", ignoreCase = true) == true }
                }

                for (item in filtered) {
                    val id = item.id
                    if (!id.isNullOrBlank() && seenIds.add(id)) {
                        val badgeLabel = when {
                            !item.total_episodes.isNullOrBlank() -> "Eps ${item.total_episodes}"
                            item.time?.contains("new", ignoreCase = true) == true -> "NEW !!"
                            else -> dayName.lowercase().replaceFirstChar { it.uppercase() }
                        }
                        combined.add(item to badgeLabel)
                    }
                }
            } catch (_: Exception) {
                // Lanjutkan ke hari berikutnya jika salah satu request gagal
            }
        }
        return combined
    }

    /**
     * Mengambil daftar anime yang benar-benar sudah tamat (status == FINISHED)
     * dari endpoint home/data (terbaru & populer) serta explore/movie (sort=views).
     */
    private suspend fun fetchFinishedMovies(page: Int = 1): List<WebMovieItem> {
        val result = mutableListOf<WebMovieItem>()
        val seenIds = mutableSetOf<String>()

        if (page <= 1) {
            try {
                val homeJson = makeGetRequest("3/2/home/data", mapOf("limit" to "60"))
                val homeType = object : TypeToken<WebResponse<WebHomeData>>() {}.type
                val homeResponse = gson.fromJson<WebResponse<WebHomeData>>(homeJson, homeType)
                val newFinished = homeResponse?.data?.new.orEmpty().filter {
                    it.status.equals("FINISHED", ignoreCase = true) ||
                        it.status.equals("COMPLETED", ignoreCase = true)
                }
                val popularFinished = homeResponse?.data?.popular.orEmpty().filter {
                    it.status.equals("FINISHED", ignoreCase = true) ||
                        it.status.equals("COMPLETED", ignoreCase = true)
                }
                for (item in (newFinished + popularFinished)) {
                    val id = item.id
                    if (!id.isNullOrBlank() && seenIds.add(id)) {
                        result.add(item)
                    }
                }
            } catch (_: Exception) {}
        }

        if (result.size < 20 || page > 1) {
            try {
                val exploreJson = makeGetRequest("3/2/explore/movie", mapOf(
                    "sort" to "views",
                    "page" to page.toString()
                ))
                val exploreType = object : TypeToken<WebResponse<WebExploreData>>() {}.type
                val exploreResponse = gson.fromJson<WebResponse<WebExploreData>>(exploreJson, exploreType)
                val exploreFinished = exploreResponse?.data?.movie.orEmpty().filter {
                    it.status.equals("FINISHED", ignoreCase = true) ||
                        it.status.equals("COMPLETED", ignoreCase = true)
                }
                for (item in exploreFinished) {
                    val id = item.id
                    if (!id.isNullOrBlank() && seenIds.add(id)) {
                        result.add(item)
                    }
                }
            } catch (_: Exception) {}
        }

        return result
    }

    override suspend fun getHome(): HomeResponse = withContext(Dispatchers.IO) {
        try {
            val todayDay = getDayName(0)
            val ongoingPairs = fetchThreeDaysOngoingItems()

            val ongoingList = ongoingPairs.take(24).map { (item, badgeLabel) ->
                AnimeOngoing(
                    title = item.title ?: "Tanpa Judul",
                    id = item.id ?: "",
                    thumb = item.image_poster ?: "",
                    episode = badgeLabel,
                    uploadedOn = item.day ?: item.aired_start ?: "",
                    dayUpdated = item.day ?: todayDay,
                    link = ""
                )
            }

            // Fetch anime yang benar-benar sudah tamat (status == FINISHED)
            val completedList = fetchFinishedMovies(page = 1).take(20).map { item ->
                AnimeCompleted(
                    title = item.title ?: "Tanpa Judul",
                    id = item.id ?: "",
                    thumb = item.image_poster ?: "",
                    episode = resolveCompletedEpisodeLabel(item),
                    uploadedOn = item.aired_start ?: item.year ?: "",
                    score = resolveDisplayScore(item),
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
            val ongoingPairs = fetchThreeDaysOngoingItems()
            if (ongoingPairs.isEmpty()) {
                return@withContext AnimeListResponse("error", emptyList())
            }

            val list = ongoingPairs.map { (item, badgeLabel) ->
                AnimeListItem(
                    title = item.title ?: "Tanpa Judul",
                    animeName = item.title ?: "Tanpa Judul",
                    id = item.id ?: "",
                    thumb = item.image_poster,
                    episode = badgeLabel,
                    score = resolveDisplayScore(item),
                    studio = item.studio,
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
            val finishedMovies = fetchFinishedMovies(page = page)
            if (finishedMovies.isEmpty()) {
                return@withContext AnimeListResponse("error", emptyList())
            }

            val list = finishedMovies.map { item ->
                AnimeListItem(
                    title = item.title ?: "Tanpa Judul",
                    animeName = item.title ?: "Tanpa Judul",
                    id = item.id ?: "",
                    thumb = item.image_poster,
                    episode = resolveCompletedEpisodeLabel(item),
                    score = resolveDisplayScore(item),
                    studio = item.studio,
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

        val totalEps = when {
            episodeList.isNotEmpty() -> "${episodeList.size}"
            !movie.total_episodes.isNullOrBlank() -> movie.total_episodes
            movie.status?.contains("COMPLETED", true) == true -> "Tamat"
            else -> "Ongoing"
        }

        val displayScore = when {
            !movie.score.isNullOrBlank() -> movie.score
            !movie.favorites.isNullOrBlank() && movie.favorites.toIntOrNull() != null -> {
                val favs = movie.favorites.toInt()
                val calc = (8.0 + (favs % 15) * 0.1).coerceIn(8.0, 9.5)
                String.format("%.1f", calc)
            }
            else -> "8.4"
        }

        val displayStudio = if (!movie.studio.isNullOrBlank()) movie.studio else "AnimeIn Studio"
        val displayProducer = if (!movie.studio.isNullOrBlank()) movie.studio else "AnimeIn Production"
        val displayJapanese = if (!movie.synonyms.isNullOrBlank()) movie.synonyms else movie.title ?: "Tanpa Judul"
        val displayType = if (!movie.type.isNullOrBlank()) movie.type else "TV"
        val displayStatus = if (!movie.status.isNullOrBlank()) movie.status else "Ongoing"
        val displayDuration = if (!movie.time.isNullOrBlank()) movie.time else "24 min/ep"
        val displayReleaseDate = if (!movie.aired_start.isNullOrBlank()) movie.aired_start else if (!movie.year.isNullOrBlank()) movie.year else "-"
        val displaySynopsis = if (!movie.synopsis.isNullOrBlank()) movie.synopsis else "Belum ada sinopsis untuk anime ini."

        DetailResponse(
            thumb = movie.image_poster,
            animeId = movie.id ?: "",
            synopsis = displaySynopsis,
            title = movie.title ?: "Tanpa Judul",
            japanese = displayJapanese,
            score = displayScore,
            producer = displayProducer,
            type = displayType,
            status = displayStatus,
            totalEpisode = totalEps,
            duration = displayDuration,
            releaseDate = displayReleaseDate,
            studio = displayStudio,
            genreList = movie.genre?.split(",")?.map { Genre(it.trim(), it.trim(), null) } ?: emptyList(),
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
            else MirrorQuality("360p", list.map { MirrorItem("${it.quality} - ${it.name ?: "Server 360p"}", it.link ?: "") })
        }
        val mirror2 = servers.filter { it.quality == "480p" || it.quality == "480" }.let { list ->
            if (list.isEmpty()) null
            else MirrorQuality("480p", list.map { MirrorItem("${it.quality} - ${it.name ?: "Server 480p"}", it.link ?: "") })
        }
        val mirror3 = servers.filter { it.quality == "720p" || it.quality == "1080p" || it.quality == "720" || it.quality == "1080" }.let { list ->
            if (list.isEmpty()) null
            else MirrorQuality("720p", list.map { MirrorItem("${it.quality} - ${it.name ?: "Server HD"}", it.link ?: "") })
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
        val studio: String?,
        val total_episodes: String?,
        val score: String?
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
