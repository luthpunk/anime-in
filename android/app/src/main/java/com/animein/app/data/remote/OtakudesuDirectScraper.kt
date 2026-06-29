package com.animein.app.data.remote

import com.animein.app.data.model.BatchLink
import com.animein.app.data.model.DetailResponse
import com.animein.app.data.model.EpisodeDetailResponse
import com.animein.app.data.model.EpisodeItem
import com.animein.app.data.model.Genre
import com.animein.app.data.model.MirrorItem
import com.animein.app.data.model.MirrorQuality
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
) {
    private val baseUrl = "https://otakudesu.blog/"

    suspend fun getAnimeDetail(animeId: String): DetailResponse = withContext(Dispatchers.IO) {
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

    suspend fun getEpisodeDetail(episodeId: String): EpisodeDetailResponse = withContext(Dispatchers.IO) {
        val normalizedId = AnimeIdUtils.normalizeEpisodeId(episodeId)
        val pageUrl = if (normalizedId.startsWith("http")) normalizedId else "$baseUrl$normalizedId"
        val doc = fetchDocument(pageUrl)
        
        val defaultMirrorContent = doc.select(".mirrorstream a[data-default=true]").first()?.attr("data-content")
            ?: doc.select(".mirrorstream a[data-content]").first()?.attr("data-content")
            
        val iframeUrl = if (!defaultMirrorContent.isNullOrBlank()) {
            getIframeFromDataContent(defaultMirrorContent)
        } else {
            doc.select("#lightsVideo #embed_holder iframe, #pembed iframe").first()
                ?.absUrl("src")
                ?.ifBlank { doc.select("#lightsVideo #embed_holder iframe, #pembed iframe").first()?.attr("src") }
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

    private fun mirrorQuality(doc: Document, quality: String, selector: String): MirrorQuality {
        return MirrorQuality(
            quality = quality,
            mirrorList = doc.select(selector).mapNotNull { item ->
                val anchor = item.select("a").first() ?: return@mapNotNull null
                val id = anchor.attr("data-content").ifBlank { anchor.attr("href") }
                MirrorItem(
                    host = anchor.text().trim(),
                    id = id
                )
            }
        )
    }

    private fun extractStreamUrl(url: String): String {
        return try {
            val html = fetch(url)
            val unpacked = html + "\n" + unpackPacker(html)
            
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

    private fun decodeBase64IfNeeded(str: String): String {
        if (str.startsWith("aHR0cHM") || str.startsWith("aHR0cD")) {
            try {
                val tbl = IntArray(256) { -1 }
                val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
                for (i in alphabet.indices) {
                    tbl[alphabet[i].code] = i
                }
                val clean = str.replace("\\s".toRegex(), "")
                val len = clean.length
                if (len == 0) return str
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
                // ignore
            }
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

    private fun fetchDocument(url: String): Document {
        return Jsoup.parse(fetch(url), url)
    }

    private fun fetch(url: String): String {
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

    private fun postForm(url: String, params: Map<String, String>): String {
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

    private fun getIframeFromDataContent(dataContent: String): String? {
        return try {
            val nonceJsonStr = postForm("${baseUrl}wp-admin/admin-ajax.php", mapOf(
                "action" to "aa1208d27f29ca340c92c66d1926f13f"
            ))
            val nonce = org.json.JSONObject(nonceJsonStr).optString("data")
            if (nonce.isNullOrBlank()) return null
            
            val decodedStr = decodeBase64IfNeeded(dataContent)
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
            
            val embedHtml = decodeBase64IfNeeded(embedDataBase64)
            val doc = Jsoup.parse(embedHtml)
            doc.select("iframe").first()?.attr("src")
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
