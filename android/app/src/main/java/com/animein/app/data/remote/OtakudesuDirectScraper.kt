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
        val iframeUrl = doc.select("#lightsVideo #embed_holder iframe, #pembed iframe").first()
            ?.absUrl("src")
            ?.ifBlank { doc.select("#lightsVideo #embed_holder iframe, #pembed iframe").first()?.attr("src") }

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
                MirrorItem(
                    host = anchor.text().trim(),
                    id = anchor.attr("href")
                )
            }
        )
    }

    private fun extractStreamUrl(url: String): String {
        return try {
            val html = fetch(url)
            Regex("""["']file["']\s*:\s*["']([^"']+)["']""")
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?: "-"
        } catch (e: Exception) {
            "-"
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

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
