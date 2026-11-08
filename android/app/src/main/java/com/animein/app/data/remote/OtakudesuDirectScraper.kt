package com.animein.app.data.remote

import com.animein.app.data.model.BatchLink
import com.animein.app.data.model.DetailResponse
import com.animein.app.data.model.EpisodeDetailResponse
import com.animein.app.data.model.EpisodeItem
import com.animein.app.data.model.Genre
import com.animein.app.data.model.MirrorItem
import com.animein.app.data.model.MirrorQuality
import com.animein.app.data.model.MirrorResponse
import com.animein.app.data.model.HomeResponse
import com.animein.app.data.model.AnimeListResponse
import com.animein.app.data.model.GenreListResponse
import com.animein.app.data.model.SearchResponse
import com.animein.app.data.model.ScheduleResponse
import com.animein.app.util.AnimeIdUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OtakudesuDirectScraper @Inject constructor(
    private val okHttpClient: OkHttpClient
) : AnimeDataSource {
    companion object {
        private const val REMOTE_CONFIG_URL =
            "https://gist.githubusercontent.com/luthpunk/6e2772f4a5240b6f481455c0c6e497ff/raw/"
        private const val DEFAULT_BASE_URL = "https://otakudesu.blog/"
        private val FALLBACK_DOMAINS = listOf("https://otakudesu.cloud/", "https://otakudesu.lol/")
        private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 jam
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

        @Volatile private var cachedDomain: String = DEFAULT_BASE_URL
        @Volatile private var lastFetchedAt: Long = 0L
    }

    /**
     * Ambil domain aktif dari remote config GitHub.
     * Cache 1 jam — jika cache kedaluwarsa, fetch ulang di latar belakang.
     * Fallback ke domain hardcoded jika remote config tidak dapat diakses.
     */
    private suspend fun getBaseUrl(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastFetchedAt < CACHE_TTL_MS) return@withContext cachedDomain

        try {
            val req = okhttp3.Request.Builder()
                .url(REMOTE_CONFIG_URL)
                .header("Cache-Control", "no-cache")
                .build()
            val body = okHttpClient.newCall(req).execute().use { it.body?.string() ?: "" }
            val domain = org.json.JSONObject(body)
                .getJSONObject("otakudesu")
                .getString("baseUrl")
            if (domain.startsWith("http")) {
                cachedDomain = domain
                lastFetchedAt = now
                android.util.Log.d("DomainConfig", "Domain aktif: $domain")
                return@withContext domain
            }
        } catch (e: Exception) {
            android.util.Log.w("DomainConfig", "Gagal fetch remote config: ${e.message}")
        }

        // Coba fallback domain satu per satu
        for (candidate in listOf(DEFAULT_BASE_URL) + FALLBACK_DOMAINS) {
            try {
                val req = okhttp3.Request.Builder().url(candidate).head().build()
                okHttpClient.newCall(req).execute().close()
                cachedDomain = candidate
                lastFetchedAt = now
                android.util.Log.d("DomainConfig", "Fallback domain: $candidate")
                return@withContext candidate
            } catch (_: Exception) {}
        }

        DEFAULT_BASE_URL
    }


    override suspend fun getAnimeDetail(animeId: String): DetailResponse = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val normalizedId = AnimeIdUtils.normalizeAnimeId(animeId)
        val pageUrl = "${baseUrl}anime/$normalizedId"
        val doc = fetchDocument(pageUrl)
        val detailElement = doc.select(".venser .fotoanime")
        val infoItems = detailElement.select(".infozin p span")
        val episodes = doc.select("a[href*=/episode/]")
            .mapNotNull { anchor ->
                val link = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                val id = AnimeIdUtils.normalizeEpisodeId(link)
                val title = anchor.text().trim()
                if (id.isBlank() || title.isBlank()) {
                    null
                } else {
                    EpisodeItem(
                        title = title,
                        id = id,
                        link = link,
                        uploadedOn = anchor.parent()?.parent()?.select(".zeebr")?.first()?.text()?.trim()
                    )
                }
            }
            .distinctBy { it.id }

        DetailResponse(
            thumb = detailElement.select("img").first()?.absUrl("src"),
            animeId = normalizedId,
            synopsis = doc.select(".sinopc p").joinToString("\n") { it.text().trim() },
            title = infoValue(infoItems, 0, "Judul: ").ifBlank {
                doc.select("meta[property=og:title]").first()?.attr("content")
                    ?.replace("Subtitle Indonesia | Otaku Desu", "")
                    ?.trim()
                    .orEmpty()
            },
            japanese = infoValue(infoItems, 1, "Japanese: "),
            score = infoValue(infoItems, 2, "Skor: "),
            producer = infoValue(infoItems, 3, "Produser: "),
            type = infoValue(infoItems, 4, "Tipe: "),
            status = infoValue(infoItems, 5, "Status: "),
            totalEpisode = infoValue(infoItems, 6, "Total Episode: "),
            duration = infoValue(infoItems, 7, "Durasi: "),
            releaseDate = infoValue(infoItems, 8, "Tanggal Rilis: "),
            studio = infoValue(infoItems, 9, "Studio: "),
            genreList = infoItems.getOrNull(10)?.select("a")?.map { anchor ->
                val link = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                Genre(
                    genreName = anchor.text().trim(),
                    genreId = link.removePrefix("${baseUrl}genres/"),
                    genreLink = link
                )
            },
            episodeList = episodes,
            batchLink = doc.select("a[href*=/batch/]").first()?.let { anchor ->
                val link = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                BatchLink(
                    id = link.removePrefix("${baseUrl}batch/"),
                    link = link
                )
            }
        )
    }

    override suspend fun getEpisodeDetail(episodeId: String): EpisodeDetailResponse = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val normalizedId = AnimeIdUtils.normalizeEpisodeId(episodeId)
        val pageUrl = if (normalizedId.startsWith("http")) normalizedId else "$baseUrl$normalizedId"
        val doc = fetchDocument(pageUrl)
        
        val vidhideAnchor = doc.select("ul.m360p li a, ul.m480p li a, ul.m720p li a").firstOrNull { 
            it.text().contains("vidhide", ignoreCase = true) || it.text().contains("hide", ignoreCase = true)
        }
        val vidhideContent = vidhideAnchor?.attr("data-content")?.ifBlank { vidhideAnchor.attr("href") }
        
        val iframeUrl = if (!vidhideContent.isNullOrBlank()) {
            if (vidhideContent.startsWith("eyJ")) {
                getIframeFromDataContent(vidhideContent)
            } else {
                vidhideContent
            }
        } else {
            val defaultMirrorContent = doc.select(".mirrorstream a[data-default=true]").first()?.attr("data-content")
                ?: doc.select(".mirrorstream a[data-content]").first()?.attr("data-content")
            if (!defaultMirrorContent.isNullOrBlank()) {
                getIframeFromDataContent(defaultMirrorContent)
            } else {
                doc.select("#lightsVideo #embed_holder iframe, #pembed iframe").first()
                    ?.absUrl("src")
                    ?.ifBlank { doc.select("#lightsVideo #embed_holder iframe, #pembed iframe").first()?.attr("src") }
            }
        }

        EpisodeDetailResponse(
            title = doc.select(".venutama h1").first()?.text()?.trim()
                ?: doc.select("meta[property=og:title]").first()?.attr("content")?.trim()
                ?: "Episode",
            id = pageUrl,
            linkStream = iframeUrl?.let { extractStreamUrl(it) } ?: "-",
            mirror1 = mirrorQuality(doc, "360p", "ul.m360p li"),
            mirror2 = mirrorQuality(doc, "480p", "ul.m480p li"),
            mirror3 = mirrorQuality(doc, "720p", "ul.m720p li"),
            quality = null
        )
    }

    override suspend fun getMirrorStream(mirrorId: String): MirrorResponse = withContext(Dispatchers.IO) {
        val iframeUrl = if (mirrorId.startsWith("eyJ")) {
            getIframeFromDataContent(mirrorId)
        } else {
            mirrorId
        }
        val streamUrl = iframeUrl?.let { extractStreamUrl(it) } ?: "-"
        MirrorResponse(
            title = "Mirror Stream",
            id = mirrorId,
            streamLink = iframeUrl ?: "-",
            linkStream = streamUrl
        )
    }

    override suspend fun getHome(): HomeResponse = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val doc = fetchDocument(baseUrl)
        val venz = doc.select(".venz")
        val ongoingElements = venz.getOrNull(0)?.select("ul > li")
        val completedElements = venz.getOrNull(1)?.select("ul > li")

        val allOngoingList = ongoingElements?.mapNotNull { item ->
            val anchor = item.select(".thumb > a").first() ?: return@mapNotNull null
            val link = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val title = anchor.select(".thumbz > h2").first()?.text()?.trim() ?: ""
            val thumb = anchor.select(".thumbz > img").first()?.absUrl("src") ?: ""
            val uploadedOn = item.select(".newnime").first()?.text()?.trim()
            val episode = item.select(".epz").first()?.text()?.replace(" ", "") ?: ""
            val dayUpdated = item.select(".epztipe").first()?.text()?.replace(" ", "")
            
            com.animein.app.data.model.AnimeOngoing(
                title = title,
                id = id,
                thumb = thumb,
                episode = episode,
                uploadedOn = uploadedOn,
                dayUpdated = dayUpdated,
                link = link
            )
        }.orEmpty()

        // Filter khusus rilis hari ini (0), 1 hari sebelumnya (-1), dan 2 hari sebelumnya (-2)
        val validDatePrefixes = (-2..0).flatMap { offset ->
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, offset)
            val dom = cal.get(java.util.Calendar.DAY_OF_MONTH)
            listOf(String.format("%02d ", dom), "$dom ")
        }.toSet()
        val validDayNames = (-2..0).map { offset ->
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, offset)
            when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.MONDAY -> "senin"
                java.util.Calendar.TUESDAY -> "selasa"
                java.util.Calendar.WEDNESDAY -> "rabu"
                java.util.Calendar.THURSDAY -> "kamis"
                java.util.Calendar.FRIDAY -> "jumat"
                java.util.Calendar.SATURDAY -> "sabtu"
                java.util.Calendar.SUNDAY -> "minggu"
                else -> ""
            }
        }.filter { it.isNotEmpty() }.toSet()

        val filteredOngoing = allOngoingList.filter { anime ->
            val uploaded = anime.uploadedOn?.trim().orEmpty()
            val matchesDate = validDatePrefixes.any { prefix -> uploaded.startsWith(prefix, ignoreCase = true) }
            val day = anime.dayUpdated?.trim()?.lowercase().orEmpty()
            val matchesDay = uploaded.isBlank() && validDayNames.any { day.contains(it) }
            matchesDate || matchesDay
        }
        val ongoingList = if (filteredOngoing.isNotEmpty()) filteredOngoing else allOngoingList

        val completedList = completedElements?.mapNotNull { item ->
            val anchor = item.select(".thumb > a").first() ?: return@mapNotNull null
            val link = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val title = anchor.select(".thumbz > h2").first()?.text()?.trim() ?: ""
            val thumb = anchor.select(".thumbz > img").first()?.absUrl("src") ?: ""
            val uploadedOn = item.select(".newnime").first()?.text()?.trim()
            val episode = item.select(".epz").first()?.text()?.replace(" ", "") ?: ""
            val score = item.select(".epztipe").first()?.text()?.replace(" ", "")
            
            com.animein.app.data.model.AnimeCompleted(
                title = title,
                id = id,
                thumb = thumb,
                episode = episode,
                uploadedOn = uploadedOn,
                score = score,
                link = link
            )
        }.orEmpty()

        HomeResponse(
            status = "success",
            home = com.animein.app.data.model.HomeData(
                ongoing = ongoingList,
                complete = completedList
            )
        )
    }

    override suspend fun getOngoingAnimeList(page: Int): AnimeListResponse = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val pageSegment = if (page <= 1) "" else "page/$page/"
        val pageUrl = "${baseUrl}ongoing-anime/$pageSegment"
        val doc = fetchDocument(pageUrl)
        val elements = doc.select(".venz").first()?.children()?.getOrNull(0)?.select("ul > li")
        
        val list = elements?.mapNotNull { item ->
            val anchor = item.select(".thumb > a").first() ?: return@mapNotNull null
            val link = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val title = anchor.select(".thumbz > h2").first()?.text()?.trim() ?: ""
            val thumb = anchor.select(".thumbz > img").first()?.absUrl("src") ?: ""
            val episode = item.select(".epz").first()?.text()?.replace(" ", "")
            
            com.animein.app.data.model.AnimeListItem(
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
        }.orEmpty()
        
        AnimeListResponse(
            status = "success",
            animeList = list
        )
    }

    override suspend fun getCompletedAnimeList(page: Int): AnimeListResponse = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val pageSegment = if (page <= 1) "" else "page/$page/"
        val pageUrl = "${baseUrl}complete-anime/$pageSegment"
        val doc = fetchDocument(pageUrl)
        val elements = doc.select(".venz").first()?.children()?.getOrNull(0)?.select("ul > li")
        
        val list = elements?.mapNotNull { item ->
            val anchor = item.select(".thumb > a").first() ?: return@mapNotNull null
            val link = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val title = anchor.select(".thumbz > h2").first()?.text()?.trim() ?: ""
            val thumb = anchor.select(".thumbz > img").first()?.absUrl("src") ?: ""
            val episode = item.select(".epz").first()?.text()?.replace(" ", "")
            val score = item.select(".epztipe").first()?.text()?.replace(" ", "")
            
            com.animein.app.data.model.AnimeListItem(
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
        }.orEmpty()
        
        AnimeListResponse(
            status = "success",
            animeList = list
        )
    }

    override suspend fun getGenres(): GenreListResponse = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val doc = fetchDocument("${baseUrl}genre-list/")
        val elements = doc.select(".genres li a")
        val list = elements.mapIndexed { index, el ->
            val genreName = el.text().trim()
            val href = el.attr("href")
            val id = href.replace("/genres/", "").replace("/", "")
            val link = if (href.startsWith("http")) href else "$baseUrl$href"
            
            com.animein.app.data.model.GenreItem(
                genreName = genreName,
                id = id,
                link = link,
                imageLink = null
            )
        }
        
        GenreListResponse(genreList = list)
    }

    override suspend fun searchAnime(query: String): SearchResponse = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val pageUrl = "${baseUrl}?s=${java.net.URLEncoder.encode(query, "UTF-8")}&post_type=anime"
        val doc = fetchDocument(pageUrl)
        val elements = doc.select(".page ul > li")
        
        val list = elements.mapNotNull { item ->
            val anchor = item.select("h2 > a").first() ?: return@mapNotNull null
            val link = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val title = anchor.text().trim()
            val thumb = item.select("img").first()?.absUrl("src") ?: ""
            val status = item.select(".set").getOrNull(1)?.text()?.replace("Status : ", "")?.trim()
            val score = item.select(".set").getOrNull(2)?.text()?.replace("Rating : ", "")?.trim()
            
            val genres = item.select(".set").first()?.select("a")?.map { el ->
                val genreLink = el.absUrl("href")
                val genreName = el.text().trim()
                val genreId = genreLink.replace("${baseUrl}genres/", "").replace("/", "")
                com.animein.app.data.model.Genre(
                    genreName = genreName,
                    genreId = genreId,
                    genreLink = genreLink
                )
            }.orEmpty()
            
            com.animein.app.data.model.SearchItem(
                title = title,
                id = id,
                thumb = thumb,
                status = status,
                score = score,
                genreList = genres
            )
        }
        
        SearchResponse(
            status = "success",
            searchResults = list
        )
    }

    private fun mirrorQuality(doc: Document, quality: String, selector: String): MirrorQuality {
        return MirrorQuality(
            quality = quality,
            mirrorList = doc.select(selector).mapNotNull { item ->
                val anchor = item.select("a").first() ?: return@mapNotNull null
                val host = anchor.text().trim()
                if (!host.contains("vidhide", ignoreCase = true) && !host.contains("hide", ignoreCase = true)) {
                    return@mapNotNull null
                }
                val id = anchor.attr("data-content").ifBlank { anchor.attr("href") }
                MirrorItem(
                    host = host,
                    id = id
                )
            }
        )
    }

    private suspend fun extractStreamUrl(url: String): String {
        return try {
            val html = fetch(url)
            val unpacked = html + "\n" + unpackPacker(html)
            
            // Jsoup elements parsing
            val parsedDoc = org.jsoup.Jsoup.parse(unpacked)
            val cheerioSrc = parsedDoc.select("video source").first()?.attr("src")
                ?: parsedDoc.select("video").first()?.attr("src")
                ?: parsedDoc.select("iframe").first()?.attr("src")
                
            if (!cheerioSrc.isNullOrBlank() && cheerioSrc.startsWith("http")) {
                val decoded = decodeBase64IfNeeded(cheerioSrc)
                if (decoded.startsWith("http")) {
                    return decoded
                }
            }
            
            // Search via regex
            val regexes = listOf(
                Regex("""["']?file["']?\s*:\s*["']([^"']+)["']"""),
                Regex("""["']?src["']?\s*:\s*["']([^"']+)["']"""),
                Regex("""source\s*:\s*["']([^"']+)["']""")
            )
            
            for (regex in regexes) {
                val matches = regex.findAll(unpacked)
                for (match in matches) {
                    var streamUrl = match.groupValues.getOrNull(1).orEmpty()
                    streamUrl = decodeBase64IfNeeded(streamUrl)
                    if (streamUrl.startsWith("http") && (streamUrl.contains(".mp4") || streamUrl.contains(".m3u8") || streamUrl.contains("stream") || streamUrl.contains("video"))) {
                        return streamUrl
                    }
                }
            }
            
            // Fallback search for any raw link
            val rawLinkRegex = Regex("""(https?://[^\s"'`]+?\.(?:m3u8|mp4)(?:[^\s"'`]*))""", RegexOption.IGNORE_CASE)
            val rawMatch = rawLinkRegex.find(unpacked)
            if (rawMatch != null) {
                return rawMatch.groupValues[1]
            }
            
            "-"
        } catch (e: Exception) {
            "-"
        }
    }

    private fun decodeBase64(str: String): String {
        try {
            val tbl = IntArray(256) { -1 }
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            for (i in alphabet.indices) {
                tbl[alphabet[i].code] = i
            }
            val clean = str.replace("\\s".toRegex(), "")
            val len = clean.length
            if (len == 0) return ""
            var padding = 0
            if (len > 0 && clean[len - 1] == '=') padding++
            if (len > 1 && clean[len - 2] == '=') padding++
            
            val outputLen = (len * 6 shr 3) - padding
            val out = ByteArray(outputLen)
            var outIdx = 0
            var bits = 0
            var bitCount = 0
            for (i in 0 until len) {
                val c = clean[i]
                if (c == '=') break
                val v = tbl[c.code]
                if (v >= 0) {
                    bits = bits shl 6 or v
                    bitCount += 6
                    if (bitCount >= 8) {
                        bitCount -= 8
                        out[outIdx++] = (bits shr bitCount and 0xFF).toByte()
                    }
                }
            }
            return String(out, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            return ""
        }
    }

    private fun decodeBase64IfNeeded(str: String): String {
        if (str.startsWith("aHR0cHM") || str.startsWith("aHR0cD") || str.startsWith("eyJ")) {
            val decoded = decodeBase64(str)
            if (decoded.isNotEmpty()) return decoded
        }
        return str
    }

    private fun unpackPacker(packed: String): String {
        return try {
            val packerRegex = Regex(
                """eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,\s*[rd]\s*\)\s*\{.*?return\s+p.*?\}(?:\s*\(.*?\))?\s*\(\s*'(.*?)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'(.*?)'\.split\('\|'\)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            
            val builder = StringBuilder()
            val matches = packerRegex.findAll(packed)
            for (match in matches) {
                val p = match.groupValues[1]
                val a = match.groupValues[2].toIntOrNull() ?: 10
                val c = match.groupValues[3].toIntOrNull() ?: 0
                val k = match.groupValues[4].split("|")
                
                fun base62Decode(value: Int, base: Int): String {
                    val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    if (value < base) return alphabet[value].toString()
                    return base62Decode(value / base, base) + alphabet[value % base]
                }
                
                val d = mutableMapOf<String, String>()
                for (i in 0 until c) {
                    val word = k.getOrNull(i).orEmpty()
                    if (word.isNotEmpty()) {
                        d[base62Decode(i, a)] = word
                    }
                }
                
                val unpackedBlock = Regex("""\b(\w+)\b""").replace(p) { tokenMatch ->
                    val token = tokenMatch.value
                    d[token] ?: token
                }
                builder.append("\n").append(unpackedBlock)
            }
            builder.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun fetchDocument(url: String): Document {
        return Jsoup.parse(fetch(url), url)
    }

    private suspend fun fetch(url: String): String {
        val baseUrl = getBaseUrl()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", baseUrl)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Direct Otakudesu request failed: ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun infoValue(items: List<org.jsoup.nodes.Element>, index: Int, prefix: String): String {
        return items.getOrNull(index)?.text()?.replace(prefix, "")?.trim().orEmpty()
    }

    private suspend fun postForm(url: String, params: Map<String, String>): String {
        val baseUrl = getBaseUrl()
        val formBuilder = okhttp3.FormBody.Builder()
        for ((key, value) in params) {
            formBuilder.add(key, value)
        }
        val request = Request.Builder()
            .url(url)
            .post(formBuilder.build())
            .header("User-Agent", USER_AGENT)
            .header("Referer", baseUrl)
            .header("Accept", "application/json")
            .build()
        
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("POST request failed: ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private suspend fun getIframeFromDataContent(dataContent: String): String? {
        return try {
            val baseUrl = getBaseUrl()
            val nonceJsonStr = postForm("${baseUrl}wp-admin/admin-ajax.php", mapOf(
                "action" to "aa1208d27f29ca340c92c66d1926f13f"
            ))
            val nonce = org.json.JSONObject(nonceJsonStr).optString("data")
            if (nonce.isNullOrBlank()) return null
            
            val decodedStr = decodeBase64(dataContent)
            val decodedJson = org.json.JSONObject(decodedStr)
            val id = decodedJson.optString("id")
            val i = decodedJson.optString("i")
            val q = decodedJson.optString("q")
            
            val embedJsonStr = postForm("${baseUrl}wp-admin/admin-ajax.php", mapOf(
                "action" to "2a3505c93b0035d3f455df82bf976b84",
                "id" to id,
                "i" to i,
                "q" to q,
                "nonce" to nonce
            ))
            val embedDataBase64 = org.json.JSONObject(embedJsonStr).optString("data")
            if (embedDataBase64.isNullOrBlank()) return null
            
            val embedHtml = decodeBase64(embedDataBase64)
            val doc = Jsoup.parse(embedHtml)
            doc.select("iframe").first()?.attr("src")
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getAnimeByGenre(genreId: String, page: Int): AnimeListResponse = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val url = if (page == 1) "${baseUrl}genres/$genreId/" else "${baseUrl}genres/$genreId/page/$page/"
        val doc = fetchDocument(url)
        val elements = doc.select(".page .col-md-4")
        
        val list = elements.mapNotNull { item ->
            val titleEl = item.select(".col-anime-title > a").first() ?: return@mapNotNull null
            val link = titleEl.absUrl("href")
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val title = titleEl.text().trim()
            val thumb = item.select(".col-anime-cover img").first()?.absUrl("src") ?: ""
            val studio = item.select(".col-anime-studio").text().trim()
            val eps = item.select(".col-anime-eps").text().trim()
            val scoreString = item.select(".col-anime-rating").text().trim()
            val score = scoreString.toFloatOrNull() ?: 0f
            val date = item.select(".col-anime-date").text().trim()
            
            val genres = item.select(".col-anime-genre a").map { el ->
                val genreLink = el.absUrl("href")
                val genreName = el.text().trim()
                val genreIdVal = genreLink.replace("${baseUrl}genres/", "").replace("/", "")
                com.animein.app.data.model.Genre(
                    genreName = genreName,
                    genreId = genreIdVal,
                    genreLink = genreLink
                )
            }
            
            com.animein.app.data.model.AnimeListItem(
                title = title,
                animeName = title,
                id = id,
                thumb = thumb,
                episode = eps,
                score = score.toString(),
                studio = studio,
                releaseDate = date,
                genreList = genres
            )
        }
        
        AnimeListResponse(
            status = "success",
            animeList = list
        )
    }

    override suspend fun getSchedule(): ScheduleResponse = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val doc = fetchDocument("${baseUrl}jadwal-rilis/")
        val elements = doc.select(".kgjdwl321 .kglist321")
        
        val list = elements.map { item ->
            val day = item.select("h2").text().trim()
            val animeElements = item.select("ul > li")
            val animeList = animeElements.map { li ->
                val anchor = li.select("a").first()
                val animeName = anchor?.text().orEmpty().trim()
                val link = anchor?.absUrl("href").orEmpty()
                val id = AnimeIdUtils.normalizeAnimeId(link)
                com.animein.app.data.model.ScheduleAnimeItem(
                    animeName = animeName,
                    id = id,
                    link = link
                )
            }
            com.animein.app.data.model.ScheduleItem(
                day = day,
                animeList = animeList
            )
        }
        
        ScheduleResponse(scheduleList = list)
    }


}
