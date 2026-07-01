package com.animein.app.data.remote

import android.util.Base64
import com.animein.app.data.model.*
import com.animein.app.util.AnimeIdUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NimegamiDirectScraper @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val BASE_URL = "https://nimegami.id/"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }

    private suspend fun fetchHtml(url: String, referer: String = BASE_URL): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", referer)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to load page: ${response.code} for URL: $url")
            }
            response.body?.string().orEmpty()
        }
    }

    private suspend fun fetchDocument(url: String, referer: String = BASE_URL): Document {
        return Jsoup.parse(fetchHtml(url, referer), url)
    }

    suspend fun getHome(): HomeResponse = withContext(Dispatchers.IO) {
        val doc = fetchDocument(BASE_URL)
        val articles = doc.select(".post-article article")
        
        val ongoingList = mutableListOf<AnimeOngoing>()
        val completedList = mutableListOf<AnimeCompleted>()

        articles.forEach { item ->
            val anchor = item.select(".thumb a").first() ?: return@forEach
            val link = anchor.absUrl("href")
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val title = anchor.attr("title").replace("Sub Indo", "").trim()
            val thumb = anchor.select("img").first()?.absUrl("src") ?: ""
            
            // Check if completed or ongoing
            val isComplete = item.select(".post-2-complete").isNotEmpty()
            
            if (isComplete) {
                completedList.add(
                    AnimeCompleted(
                        title = title,
                        id = id,
                        thumb = thumb,
                        episode = "End",
                        uploadedOn = null,
                        score = null,
                        link = link
                    )
                )
            } else {
                ongoingList.add(
                    AnimeOngoing(
                        title = title,
                        id = id,
                        thumb = thumb,
                        episode = "Update",
                        uploadedOn = null,
                        dayUpdated = null,
                        link = link
                    )
                )
            }
        }

        HomeResponse(
            status = "success",
            home = HomeData(ongoing = ongoingList, complete = completedList)
        )
    }

    suspend fun getOngoingAnimeList(page: Int): AnimeListResponse = withContext(Dispatchers.IO) {
        val url = if (page <= 1) "${BASE_URL}anime-terbaru-sub-indo/" else "${BASE_URL}anime-terbaru-sub-indo/page/$page/"
        val doc = fetchDocument(url)
        val articles = doc.select("article")
        val list = articles.mapNotNull { item ->
            val anchor = item.select(".thumb a").first() ?: return@mapNotNull null
            val link = anchor.absUrl("href")
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val title = anchor.attr("title").replace("Sub Indo", "").trim()
            val thumb = anchor.select("img").first()?.absUrl("src") ?: ""
            val episode = item.select(".eps_ongo").text().trim()
            
            AnimeListItem(
                title = title,
                animeName = title,
                id = id,
                thumb = thumb,
                episode = episode,
                score = null,
                studio = null,
                releaseDate = null,
                genreList = null
            )
        }

        AnimeListResponse(status = "success", animeList = list)
    }

    suspend fun getCompletedAnimeList(page: Int): AnimeListResponse = withContext(Dispatchers.IO) {
        // Scrapes page feed and filters only completed ones
        val url = if (page <= 1) BASE_URL else "${BASE_URL}page/$page/"
        val doc = fetchDocument(url)
        val articles = doc.select(".post-article article")
        
        val list = articles.mapNotNull { item ->
            val isComplete = item.select(".post-2-complete").isNotEmpty()
            if (!isComplete) return@mapNotNull null
            
            val anchor = item.select(".thumb a").first() ?: return@mapNotNull null
            val link = anchor.absUrl("href")
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val title = anchor.attr("title").replace("Sub Indo", "").trim()
            val thumb = anchor.select("img").first()?.absUrl("src") ?: ""
            
            AnimeListItem(
                title = title,
                animeName = title,
                id = id,
                thumb = thumb,
                episode = "Complete",
                score = null,
                studio = null,
                releaseDate = null,
                genreList = null
            )
        }

        AnimeListResponse(status = "success", animeList = list)
    }

    suspend fun searchAnime(query: String): SearchResponse = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}?s=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val doc = fetchDocument(url)
        val articles = doc.select("article")
        
        val list = articles.mapNotNull { item ->
            val anchor = item.select(".thumb a").first() ?: return@mapNotNull null
            val link = anchor.absUrl("href")
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val title = anchor.attr("title").replace("Sub Indo", "").trim()
            val thumb = anchor.select("img").first()?.absUrl("src") ?: ""
            val isComplete = item.select(".post-2-complete").isNotEmpty()
            val status = if (isComplete) "Completed" else "Ongoing"

            SearchItem(
                title = title,
                id = id,
                thumb = thumb,
                status = status,
                score = null,
                genreList = null
            )
        }

        SearchResponse(status = "success", searchResults = list)
    }

    suspend fun getGenres(): GenreListResponse = withContext(Dispatchers.Default) {
        // Static genres for Nimegami for speed and reliability
        val staticGenres = listOf(
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Fantasy", "fantasy"),
            Pair("Historical", "historical"),
            Pair("Isekai", "isekai"),
            Pair("Military", "military"),
            Pair("Performing Arts", "performing-arts"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shounen", "shounen"),
            Pair("Time Travel", "time-travel")
        )

        val list = staticGenres.map { (name, slug) ->
            GenreItem(
                genreName = name,
                id = slug,
                link = "${BASE_URL}category/$slug/",
                imageLink = null
            )
        }

        GenreListResponse(genreList = list)
    }

    suspend fun getAnimeByGenre(genreId: String, page: Int): AnimeListResponse = withContext(Dispatchers.IO) {
        val url = if (page <= 1) "${BASE_URL}category/$genreId/" else "${BASE_URL}category/$genreId/page/$page/"
        val doc = fetchDocument(url)
        val articles = doc.select("article")
        
        val list = articles.mapNotNull { item ->
            val anchor = item.select(".thumb a").first() ?: return@mapNotNull null
            val link = anchor.absUrl("href")
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val title = anchor.attr("title").replace("Sub Indo", "").trim()
            val thumb = anchor.select("img").first()?.absUrl("src") ?: ""
            
            AnimeListItem(
                title = title,
                animeName = title,
                id = id,
                thumb = thumb,
                episode = "Anime",
                score = null,
                studio = null,
                releaseDate = null,
                genreList = null
            )
        }

        AnimeListResponse(status = "success", animeList = list)
    }

    suspend fun getAnimeDetail(animeId: String): DetailResponse = withContext(Dispatchers.IO) {
        val cleanId = animeId.trim('/')
        val url = "$BASE_URL$cleanId/"
        val doc = fetchDocument(url)
        
        var title = doc.select("h1.title").text()
            .replace("Sub Indo", "")
            .replace("Subtitle Indonesia", "")
            .substringBefore(":")
            .trim()
        
        if (title.isBlank()) {
            title = doc.select("meta[property=og:title]").attr("content")
                .replace("Sub Indo", "")
                .replace("Subtitle Indonesia", "")
                .substringBefore(":")
                .trim()
        }

        val thumb = doc.select("meta[property=og:image]").attr("content")
            .ifBlank { doc.select(".stiky_post .thumb img").attr("src") }

        val synopsis = doc.select("#Sinopsis p").text().trim()

        var japanese: String? = null
        var score: String? = null
        var producer: String? = null
        var type: String? = null
        var status: String? = null
        var totalEpisode: String? = null
        var duration: String? = null
        var releaseDate: String? = null
        var studio: String? = null
        val genres = mutableListOf<Genre>()

        doc.select("tr").forEach { tr ->
            val label = tr.select("td.tablex").text().replace(":", "").trim()
            val valueEl = tr.select("td").getOrNull(1)
            val value = valueEl?.text()?.trim()
            if (value != null) {
                when (label.lowercase()) {
                    "japanese" -> japanese = value
                    "rating" -> score = value
                    "produser" -> producer = value
                    "type" -> type = value
                    "status" -> status = value
                    "episode" -> totalEpisode = value
                    "durasi" -> duration = value
                    "tanggal rilis" -> releaseDate = value
                    "studio" -> studio = value
                    "genre" -> {
                        valueEl.select("a").forEach { a ->
                            val genreName = a.text().trim()
                            val genreLink = a.absUrl("href")
                            val genreId = genreLink.removeSuffix("/").substringAfterLast("/")
                            genres.add(Genre(genreName = genreName, genreId = genreId, genreLink = genreLink))
                        }
                    }
                }
            }
        }

        val episodes = mutableListOf<EpisodeItem>()
        doc.select(".list_eps_stream li.select-eps").forEach { el ->
            val epTitle = el.attr("title").ifBlank { el.text() }
                .replace("Sub Indo", "")
                .replace("Subtitle Indonesia", "")
                .trim()
            val base64Data = el.attr("data")
            if (base64Data.isNotBlank()) {
                episodes.add(
                    EpisodeItem(
                        title = epTitle,
                        id = base64Data, // We pass Base64 data directly as the ID
                        link = base64Data,
                        uploadedOn = null
                    )
                )
            }
        }

        DetailResponse(
            thumb = thumb,
            animeId = cleanId,
            synopsis = synopsis,
            title = title,
            japanese = japanese,
            score = score,
            producer = producer,
            type = type,
            status = status,
            totalEpisode = totalEpisode,
            duration = duration,
            releaseDate = releaseDate,
            studio = studio,
            genreList = genres,
            episodeList = episodes,
            batchLink = null
        )
    }

    suspend fun getEpisodeDetail(episodeId: String): EpisodeDetailResponse = withContext(Dispatchers.IO) {
        val decodedJsonStr = String(Base64.decode(episodeId, Base64.DEFAULT), Charsets.UTF_8)
        val jsonArray = JSONArray(decodedJsonStr)
        
        var selectedStreamUrl = "-"
        val mirrorList = mutableListOf<MirrorItem>()

        // Maps base64 data to mirrors
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val format = obj.getString("format")
            val urls = obj.getJSONArray("url")
            if (urls.length() > 0) {
                val streamPageUrl = urls.getString(0)
                mirrorList.add(MirrorItem(host = "Berkasdrive - $format", id = streamPageUrl))
                
                // Select 720p or 480p as the default stream if available, otherwise fallback to first resolution
                if (selectedStreamUrl == "-" || format == "720p" || (format == "480p" && selectedStreamUrl.contains("_360p_"))) {
                    selectedStreamUrl = streamPageUrl
                }
            }
        }

        val directStreamUrl = if (selectedStreamUrl != "-") {
            resolveBerkasdriveStream(selectedStreamUrl)
        } else {
            "-"
        }

        // Divide mirrorList to match the model
        val mirror1 = if (mirrorList.size > 0) MirrorQuality("Berkasdrive", listOf(mirrorList[0])) else null
        val mirror2 = if (mirrorList.size > 1) MirrorQuality("Berkasdrive", listOf(mirrorList[1])) else null
        val mirror3 = if (mirrorList.size > 2) MirrorQuality("Berkasdrive", listOf(mirrorList[2])) else null

        EpisodeDetailResponse(
            title = "Streaming Video",
            id = episodeId,
            linkStream = directStreamUrl,
            mirror1 = mirror1,
            mirror2 = mirror2,
            mirror3 = mirror3,
            quality = null
        )
    }

    suspend fun getMirrorStream(mirrorId: String): MirrorResponse = withContext(Dispatchers.IO) {
        val directStreamUrl = resolveBerkasdriveStream(mirrorId)
        MirrorResponse(
            title = "Mirror Stream",
            id = mirrorId,
            streamLink = mirrorId,
            linkStream = directStreamUrl
        )
    }

    private suspend fun resolveBerkasdriveStream(pageUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(pageUrl, BASE_URL)
            // Parse <source src="..."> directly from HTML
            val src = doc.select("video source").attr("src").trim()
            if (src.startsWith("http")) {
                src
            } else {
                "-"
            }
        } catch (e: Exception) {
            android.util.Log.e("NimegamiScraper", "Failed to resolve Berkasdrive URL: $pageUrl", e)
            "-"
        }
    }

    suspend fun getSchedule(): ScheduleResponse = withContext(Dispatchers.Default) {
        ScheduleResponse(emptyList()) // Nimegami does not have a schedule page, return empty list
    }
}
