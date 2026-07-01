package com.animein.app.data.remote

import android.content.Context
import android.util.Base64
import com.animein.app.data.model.*
import com.animein.app.util.AnimeIdUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NimegamiDirectScraper @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val BASE_URL = "https://nimegami.id/"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
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

    /**
     * Parse info from a Nimegami article element.
     * Returns Triple(cleanTitle, episodeLabel, score)
     *
     * Title strategy:
     *   1. h2[itemprop=name] a  → clean display title (e.g. "World Is Dancing")
     *   2. img[alt]             → alt text without "Sub Indo" suffix
     *   3. anchor[title]        → full WP title (fallback, strip suffix)
     *
     * Episode label strategy:
     *   1. .eps span/div        → e.g. "Eps. 11" (current latest ep from ongoing list)
     *   2. anchor[title] suffix → extract "Episode X – Y" or "Movie" from WP title
     *
     * Status strategy:
     *   - anchor[title] ends with "(End)" AND no .eps class (list page) → Completed
     *   - title/link contains "Movie"  → Movie (Completed)
     *   - otherwise                    → Ongoing
     */
    private fun parseArticleInfo(item: org.jsoup.nodes.Element): Triple<String, String, String?> {
        val anchor = item.select("a").first()
        val anchorTitle = anchor?.attr("title") ?: ""
        val imgAlt = item.select("img").attr("alt").trim()

        // 1. Clean display title
        val h2Title = item.select("h2[itemprop=name] a, h2 a").text().trim()
        val rawTitle = when {
            h2Title.isNotBlank() -> h2Title
            imgAlt.isNotBlank() -> imgAlt
            else -> anchorTitle
        }
        val cleanTitle = rawTitle
            .replace("Sub Indo", "", ignoreCase = true)
            .replace("Subtitle Indonesia", "", ignoreCase = true)
            .trim().trimEnd(':').trim()

        // 2. Episode label
        // Priority:
        // 1. .eps class (shows current uploaded episode like "Eps. 11")
        // 2. li item containing "Episode:" from info list (e.g. <li><strong>Episode:</strong> 16</li>)
        // 3. anchorTitle suffix fallback
        val epsClass = item.select(".eps, .eps_ongo").text().trim()
        val liEpisode = item.select("li").firstOrNull { it.text().contains("Episode", ignoreCase = true) }
        val liEpisodeVal = liEpisode?.text()?.substringAfter("Episode:")?.trim() ?: ""
        
        val episodeLabel = when {
            epsClass.isNotBlank() -> epsClass
            liEpisodeVal.isNotBlank() -> "Eps. $liEpisodeVal"
            anchorTitle.contains("Movie", ignoreCase = true) -> "Movie"
            else -> {
                // Try to extract "Episode X" or "Episode X – Y" from anchor title suffix
                val epMatch = Regex(":\\s*(Episode\\s*\\d+(?:\\s*[–\\-]\\s*\\d+)?)", RegexOption.IGNORE_CASE)
                    .find(anchorTitle)
                epMatch?.groupValues?.get(1)?.trim() ?: ""
            }
        }

        // 3. Score from .rating div
        val scoreText = item.select(".rating").text()
            .replace(Regex("[^\\d.]"), "").trim()
        val score = if (scoreText.isNotBlank()) scoreText else null

        return Triple(cleanTitle, episodeLabel, score)
    }

    suspend fun getHome(): HomeResponse = withContext(Dispatchers.IO) {
        val ongoingList = mutableListOf<AnimeOngoing>()
        val completedList = mutableListOf<AnimeCompleted>()

        // 1. Fetch schedule to map days
        val animeDayMap = mutableMapOf<String, String>()
        try {
            val docSchedule = fetchDocument("${BASE_URL}jadwal-rilis/")
            docSchedule.select(".jadwalrilis .rilis2").forEach { rilisDiv ->
                val day = rilisDiv.select("h2").text().trim()
                rilisDiv.select("ul li a").forEach { a ->
                    val link = a.absUrl("href")
                    val id = AnimeIdUtils.normalizeAnimeId(link)
                    animeDayMap[id] = day
                }
            }
        } catch (e: Exception) {
            // ignore schedule fetch failure
        }

        // Calculate allowed days: today, yesterday, and day before yesterday
        val calendar = java.util.Calendar.getInstance()
        val dayNames = listOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
        val todayIdx = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1
        val todayDay = dayNames[todayIdx]
        val yesterdayDay = dayNames[(todayIdx + 6) % 7]
        val dayBeforeDay = dayNames[(todayIdx + 5) % 7]

        // 2. Fetch Ongoing List from https://nimegami.id/anime-terbaru-sub-indo/
        try {
            val docOngoing = fetchDocument("${BASE_URL}anime-terbaru-sub-indo/")
            val articlesOngoing = docOngoing.select("article")
            articlesOngoing.forEach { item ->
                if (ongoingList.size >= 10) return@forEach
                val anchor = item.select("a").first() ?: return@forEach
                val link = anchor.absUrl("href")
                val id = AnimeIdUtils.normalizeAnimeId(link)
                val thumb = item.select("img").first()?.absUrl("src") ?: ""

                val (title, episodeLabel, score) = parseArticleInfo(item)
                val isMovie = title.contains("Movie", ignoreCase = true) ||
                    anchor.attr("title").contains("Movie", ignoreCase = true)

                val displayEpisode = when {
                    isMovie -> "Movie"
                    episodeLabel.isNotBlank() -> episodeLabel
                    else -> "Ongoing"
                }

                val isUpdated = item.select(".ongoing_updated").hasClass("updated_yes")

                val animeDay = animeDayMap[id]
                if (animeDay != null) {
                    val isAllowedDay = animeDay.contains(todayDay, ignoreCase = true) ||
                        animeDay.contains(yesterdayDay, ignoreCase = true) ||
                        animeDay.contains(dayBeforeDay, ignoreCase = true)
                    
                    if (animeDay.contains(todayDay, ignoreCase = true) && !isUpdated) {
                        // Skip today's not yet updated
                        return@forEach
                    }
                    if (!isAllowedDay) {
                        // Skip if it doesn't belong to today, yesterday, or day before yesterday
                        return@forEach
                    }
                }

                ongoingList.add(
                    AnimeOngoing(
                        title = title,
                        id = id,
                        thumb = thumb,
                        episode = displayEpisode,
                        uploadedOn = null,
                        dayUpdated = null,
                        link = link,
                        isUpdated = isUpdated
                    )
                )
            }
        } catch (e: Exception) {
            // ignore ongoing fetch failure
        }

        // 3. Fetch Completed List from home page (loop pages if needed to get 10 items)
        var currentPage = 1
        val seenCompletedIds = mutableSetOf<String>()
        while (completedList.size < 10 && currentPage <= 3) {
            val url = if (currentPage == 1) BASE_URL else "${BASE_URL}page/$currentPage/"
            try {
                val docCompleted = fetchDocument(url)
                val articlesCompleted = docCompleted.select("article")
                if (articlesCompleted.isEmpty()) break

                articlesCompleted.forEach { item ->
                    if (completedList.size >= 10) return@forEach
                    val anchor = item.select("a").first() ?: return@forEach
                    val link = anchor.absUrl("href")
                    val id = AnimeIdUtils.normalizeAnimeId(link)
                    if (seenCompletedIds.contains(id)) return@forEach
                    seenCompletedIds.add(id)

                    val thumb = item.select("img").first()?.absUrl("src") ?: ""
                    val anchorTitle = anchor.attr("title")

                    val (title, episodeLabel, score) = parseArticleInfo(item)
                    val isMovie = title.contains("Movie", ignoreCase = true) ||
                        anchorTitle.contains("Movie", ignoreCase = true)

                    val hasCompleteClass = item.select(".post-2-complete").text().contains("Complete", ignoreCase = true)
                    val hasOngoingTag = item.select("a[href*=on-going]").isNotEmpty()
                    val anchorEndsWithEnd = anchorTitle.trimEnd().endsWith("(End)")
                    
                    val isComplete = when {
                        hasOngoingTag -> false
                        hasCompleteClass -> true
                        isMovie -> true
                        else -> anchorEndsWithEnd
                    }

                    if (isComplete) {
                        val displayEpisode = when {
                            isMovie -> "Movie"
                            isComplete -> "Tamat"
                            episodeLabel.isNotBlank() -> episodeLabel
                            else -> "Tamat"
                        }
                        completedList.add(
                            AnimeCompleted(
                                title = title,
                                id = id,
                                thumb = thumb,
                                episode = displayEpisode,
                                uploadedOn = null,
                                score = score,
                                link = link
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                break
            }
            currentPage++
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
            val anchor = item.select("a").first() ?: return@mapNotNull null
            val link = anchor.absUrl("href")
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val thumb = item.select("img").first()?.absUrl("src") ?: ""

            val (title, episodeLabel, score) = parseArticleInfo(item)
            val isMovie = title.contains("Movie", ignoreCase = true) ||
                anchor.attr("title").contains("Movie", ignoreCase = true)

            // Latest episode from .eps (e.g. "Eps. 11")
            val episode = when {
                isMovie -> "Movie"
                episodeLabel.isNotBlank() -> episodeLabel
                else -> "Ongoing"
            }

            AnimeListItem(
                title = title,
                animeName = title,
                id = id,
                thumb = thumb,
                episode = episode,
                score = score,
                studio = null,
                releaseDate = null,
                genreList = null
            )
        }

        AnimeListResponse(status = "success", animeList = list)
    }

    suspend fun getCompletedAnimeList(page: Int): AnimeListResponse = withContext(Dispatchers.IO) {
        val url = if (page <= 1) BASE_URL else "${BASE_URL}page/$page/"
        val doc = fetchDocument(url)
        val articles = doc.select("article")

        val list = articles.mapNotNull { item ->
            val anchor = item.select("a").first() ?: return@mapNotNull null
            val anchorTitle = anchor.attr("title")
            val link = anchor.absUrl("href")
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val thumb = item.select("img").first()?.absUrl("src") ?: ""

            val (title, episodeLabel, score) = parseArticleInfo(item)
            val isMovie = title.contains("Movie", ignoreCase = true) ||
                anchorTitle.contains("Movie", ignoreCase = true)
            val hasCompleteClass = item.select(".post-2-complete").text().contains("Complete", ignoreCase = true)
            val hasOngoingTag = item.select("a[href*=on-going]").isNotEmpty()
            val anchorEndsWithEnd = anchorTitle.trimEnd().endsWith("(End)")
            
            val isComplete = when {
                hasOngoingTag -> false
                hasCompleteClass -> true
                isMovie -> true
                else -> anchorEndsWithEnd
            }

            if (!isComplete) return@mapNotNull null

            val episode = when {
                isMovie -> "Movie"
                isComplete -> "Tamat"
                episodeLabel.isNotBlank() -> episodeLabel
                else -> "Tamat"
            }

            AnimeListItem(
                title = title,
                animeName = title,
                id = id,
                thumb = thumb,
                episode = episode,
                score = score,
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
            val anchor = item.select("a").first() ?: return@mapNotNull null
            val link = anchor.absUrl("href")
            val id = AnimeIdUtils.normalizeAnimeId(link)
            
            val titleText = item.select("h2[itemprop=name] a, h2 a").text().trim()
                .ifBlank { item.select(".title-post2, .post-2-title, .title").text().trim() }
                .ifBlank { anchor.attr("title") }
            val title = titleText.replace("Sub Indo", "").replace("Subtitle Indonesia", "").trim()
            val thumb = item.select(".thumbnail img, img").first()?.absUrl("src") ?: ""
            
            val scoreText = item.select(".rating-archive").text().trim().replace("★", "").trim()
            val score = if (scoreText.isBlank()) null else scoreText
            
            val statusText = item.select(".term_tag-a").text().trim()
            val status = if (statusText.contains("Complete", ignoreCase = true)) "Completed" else "Ongoing"

            SearchItem(
                title = title,
                id = id,
                thumb = thumb,
                status = status,
                score = score,
                genreList = null
            )
        }

        SearchResponse(status = "success", searchResults = list)
    }

    suspend fun getGenres(): GenreListResponse = withContext(Dispatchers.Default) {
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
            val anchor = item.select("a").first() ?: return@mapNotNull null
            val link = anchor.absUrl("href")
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val thumb = item.select("img").first()?.absUrl("src") ?: ""

            val (title, episodeLabel, score) = parseArticleInfo(item)
            val isMovie = title.contains("Movie", ignoreCase = true) ||
                anchor.attr("title").contains("Movie", ignoreCase = true)

            val episode = when {
                isMovie -> "Movie"
                episodeLabel.isNotBlank() -> episodeLabel
                else -> ""
            }

            AnimeListItem(
                title = title,
                animeName = title,
                id = id,
                thumb = thumb,
                episode = episode,
                score = score,
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
        
        val rawTitle = doc.select("h1.title").text().ifBlank {
            doc.select("meta[property=og:title]").attr("content")
        }

        var title = rawTitle
            .replace("Sub Indo", "", ignoreCase = true)
            .replace("Subtitle Indonesia", "", ignoreCase = true)
            .substringBefore(":")
            .trim()

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
                    "japanese", "judul alternatif" -> japanese = value
                    "rating" -> score = value
                    "produser" -> producer = value
                    "type" -> type = value
                    "status" -> status = value
                    "episode" -> totalEpisode = value
                    "durasi", "durasi per episode" -> duration = value
                    "tanggal rilis", "musim / rilis" -> releaseDate = value
                    "studio" -> studio = value
                    "genre", "kategori" -> {
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
        // Select any li.select-eps directly to bypass Jsoup HTML tree-building issues
        doc.select("li.select-eps").forEach { el ->
            val epTitle = el.attr("title").ifBlank { el.text() }
                .replace("Sub Indo", "")
                .replace("Subtitle Indonesia", "")
                .trim()
            val base64Data = el.attr("data")
            if (base64Data.isNotBlank()) {
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val safeId = Base64.encodeToString(decodedBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                episodes.add(
                    EpisodeItem(
                        title = epTitle,
                        id = safeId,
                        link = safeId,
                        uploadedOn = null
                    )
                )
            }
        }

        val html = doc.html()
        val hasOngoingKeyword = html.contains("\"On-Going\"", ignoreCase = true) || html.contains("\"Ongoing\"", ignoreCase = true)
        val hasCompleteKeyword = html.contains("\"Complete\"", ignoreCase = true) || html.contains("\"Completed\"", ignoreCase = true)

        // Heuristic status and total episode inference
        val inferredStatus = when {
            hasOngoingKeyword -> "Ongoing"
            hasCompleteKeyword -> "Completed"
            type?.contains("Movie", ignoreCase = true) == true -> "Completed"
            rawTitle.contains("(End)", ignoreCase = true) || rawTitle.contains("Complete", ignoreCase = true) || rawTitle.contains("Tamat", ignoreCase = true) -> "Completed"
            status?.isNotBlank() == true -> status
            else -> "Ongoing"
        }
        val inferredTotalEpisode = if (totalEpisode.isNullOrBlank()) episodes.size.toString() else totalEpisode

        DetailResponse(
            thumb = thumb,
            animeId = cleanId,
            synopsis = synopsis,
            title = title,
            japanese = japanese,
            score = score,
            producer = producer,
            type = type,
            status = inferredStatus,
            totalEpisode = inferredTotalEpisode,
            duration = duration,
            releaseDate = releaseDate,
            studio = studio,
            genreList = genres,
            episodeList = episodes,
            batchLink = null
        )
    }

    private fun debugLog(message: String) {
        try {
            val dir = context.getExternalFilesDir(null)
            val file = java.io.File(dir, "resolved_stream.txt")
            file.appendText("[${java.util.Date()}] $message\n")
        } catch (e: Exception) {
            // Ignore logging failures
        }
    }

    suspend fun getEpisodeDetail(episodeId: String): EpisodeDetailResponse = withContext(Dispatchers.IO) {
        debugLog("getEpisodeDetail called with episodeId: $episodeId")
        try {
            val decodedBytes = Base64.decode(episodeId, Base64.URL_SAFE)
            val decodedJsonStr = String(decodedBytes, Charsets.UTF_8)
            debugLog("decodedJsonStr: $decodedJsonStr")
            val jsonArray = JSONArray(decodedJsonStr)
            
            var selectedStreamUrl = "-"
            var currentPriority = -1
            val mirrorList = mutableListOf<MirrorItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val format = obj.getString("format").lowercase()
                val urls = obj.getJSONArray("url")
                if (urls.length() > 0) {
                    val streamPageUrl = urls.getString(0)
                    mirrorList.add(MirrorItem(host = "Berkasdrive - $format", id = streamPageUrl))
                    
                    // Priority mapping: 720p (4) > 480p (3) > 1080p (2) > 360p (1)
                    val priority = when {
                        format.contains("720p") -> 4
                        format.contains("480p") -> 3
                        format.contains("1080p") -> 2
                        format.contains("360p") -> 1
                        else -> 0
                    }
                    
                    if (priority > currentPriority) {
                        selectedStreamUrl = streamPageUrl
                        currentPriority = priority
                    }
                }
            }
            
            debugLog("selectedStreamUrl: $selectedStreamUrl")

            val directStreamUrl = if (selectedStreamUrl != "-") {
                resolveBerkasdriveStream(selectedStreamUrl)
            } else {
                "-"
            }
            
            debugLog("resolved directStreamUrl: $directStreamUrl")

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
        } catch (e: Exception) {
            debugLog("Error in getEpisodeDetail: ${e.message}")
            EpisodeDetailResponse(
                title = "Streaming Video",
                id = episodeId,
                linkStream = "-",
                mirror1 = null,
                mirror2 = null,
                mirror3 = null,
                quality = null
            )
        }
    }

    suspend fun getMirrorStream(mirrorId: String): MirrorResponse = withContext(Dispatchers.IO) {
        debugLog("getMirrorStream called with mirrorId: $mirrorId")
        val directStreamUrl = resolveBerkasdriveStream(mirrorId)
        debugLog("getMirrorStream resolved to: $directStreamUrl")
        MirrorResponse(
            title = "Mirror Stream",
            id = mirrorId,
            streamLink = mirrorId,
            linkStream = directStreamUrl
        )
    }

    private suspend fun resolveBerkasdriveStream(pageUrl: String): String = withContext(Dispatchers.IO) {
        debugLog("resolveBerkasdriveStream pageUrl: $pageUrl")
        try {
            val html = fetchHtml(pageUrl, BASE_URL)
            val doc = Jsoup.parse(html, pageUrl)
            
            // Try 1: Static source tag
            val src = doc.select("video source").attr("abs:src").trim()
            if (src.startsWith("http")) {
                val encodedSrc = src.replace(" ", "%20")
                debugLog("Try 1 Success: $encodedSrc")
                return@withContext encodedSrc
            }
            
            // Try 2: Dynamic STREAM_URL_API from JavaScript
            val apiRegex = Regex("const\\s+STREAM_URL_API\\s*=\\s*[\"']([^\"']+)[\"']")
            val match = apiRegex.find(html)
            if (match != null) {
                val apiPath = match.groupValues[1]
                val uri = java.net.URI(pageUrl)
                val hostUrl = "${uri.scheme}://${uri.host}"
                val apiUrl = if (apiPath.startsWith("http")) apiPath else "$hostUrl$apiPath"
                debugLog("Try 2 Found API URL: $apiUrl")
                
                val jsonStr = fetchHtml(apiUrl, pageUrl)
                debugLog("Try 2 API Response: $jsonStr")
                val json = JSONObject(jsonStr)
                if (json.optBoolean("ok", false)) {
                    val streamUrl = json.getString("url").replace(" ", "%20")
                    if (streamUrl.startsWith("http")) {
                        debugLog("Try 2 Success: $streamUrl")
                        return@withContext streamUrl
                    }
                }
            }
            
            debugLog("Resolution failed returning '-'")
            "-"
        } catch (e: Exception) {
            debugLog("Error in resolveBerkasdriveStream: ${e.message}")
            android.util.Log.e("NimegamiScraper", "Failed to resolve Berkasdrive URL: $pageUrl", e)
            "-"
        }
    }

    suspend fun getSchedule(): ScheduleResponse = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch ongoing details list
            val ongoingDetails = mutableMapOf<String, Triple<String, String, Boolean>>() // id -> (thumb, episode, isUpdated)
            try {
                val docOngoing = fetchDocument("${BASE_URL}anime-terbaru-sub-indo/")
                docOngoing.select("article").forEach { item ->
                    val anchor = item.select("a").first() ?: return@forEach
                    val link = anchor.absUrl("href")
                    val id = AnimeIdUtils.normalizeAnimeId(link)
                    val thumb = item.select("img").first()?.absUrl("src") ?: ""
                    
                    val (_, episodeLabel, _) = parseArticleInfo(item)
                    val displayEpisode = if (episodeLabel.isNotBlank()) episodeLabel else "Ongoing"
                    
                    val isUpdated = item.select(".ongoing_updated").hasClass("updated_yes")
                    ongoingDetails[id] = Triple(thumb, displayEpisode, isUpdated)
                }
            } catch (e: Exception) {
                // ignore ongoing list error
            }

            // 2. Fetch schedule list and merge details
            val url = "${BASE_URL}jadwal-rilis/"
            val doc = fetchDocument(url)
            val scheduleList = mutableListOf<ScheduleItem>()

            // Detect today name for sorting
            val calendar = java.util.Calendar.getInstance()
            val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            val todayName = when (dayOfWeek) {
                java.util.Calendar.MONDAY -> "Senin"
                java.util.Calendar.TUESDAY -> "Selasa"
                java.util.Calendar.WEDNESDAY -> "Rabu"
                java.util.Calendar.THURSDAY -> "Kamis"
                java.util.Calendar.FRIDAY -> "Jumat"
                java.util.Calendar.SATURDAY -> "Sabtu"
                java.util.Calendar.SUNDAY -> "Minggu"
                else -> ""
            }
            
            doc.select(".jadwalrilis .rilis2").forEach { rilisDiv ->
                val day = rilisDiv.select("h2").text().trim()
                val animeItems = mutableListOf<ScheduleAnimeItem>()
                
                rilisDiv.select("ul li a").forEach { a ->
                    val name = a.text().trim()
                    val link = a.absUrl("href")
                    val id = AnimeIdUtils.normalizeAnimeId(link)
                    
                    val detail = ongoingDetails[id]
                    val thumb = detail?.first
                    val episode = detail?.second
                    val isUpdated = detail?.third
                    
                    animeItems.add(
                        ScheduleAnimeItem(
                            animeName = name,
                            id = id,
                            link = link,
                            thumb = thumb,
                            episode = episode,
                            isUpdated = isUpdated
                        )
                    )
                }
                
                if (day.isNotBlank() && animeItems.isNotEmpty()) {
                    val isToday = day.contains(todayName, ignoreCase = true)
                    if (isToday) {
                        // Sort today's anime items: isUpdated == true first, then the rest
                        animeItems.sortWith(compareByDescending { it.isUpdated == true })
                    }
                    scheduleList.add(ScheduleItem(day = day, animeList = animeItems))
                }
            }
            
            ScheduleResponse(scheduleList)
        } catch (e: Exception) {
            ScheduleResponse(emptyList())
        }
    }
}
