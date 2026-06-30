package com.animein.app.data.remote

import com.animein.app.data.model.*
import com.animein.app.util.AnimeIdUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scraper langsung ke NontonAnimeID (https://s13.nontonanimeid.boats/)
 * Digunakan sebagai fallback jika API backend tidak tersedia.
 */
@Singleton
class OtakudesuDirectScraper @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val baseUrl = "https://s13.nontonanimeid.boats/"

    // ── Home ──────────────────────────────────────────
    suspend fun getHome(): HomeResponse = withContext(Dispatchers.IO) {
        val doc = fetchDocument(baseUrl)

        val ongoingList = mutableListOf<AnimeOngoing>()
        doc.select("#postbaru .misha_posts_wrap article.animeseries").forEach { el ->
            val anchor = el.select("a").first() ?: return@forEach
            val link = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val titleSpan = el.select("h3.title span").first()
            val title = titleSpan?.attr("data-title-default")?.ifBlank { titleSpan.text().trim() }
                ?: el.select("h3.title").text().trim()
            val thumb = el.select("img").first()?.absUrl("src") ?: ""
            val episode = el.select(".types.episodes").text().trim()
            if (title.isNotBlank()) {
                ongoingList.add(AnimeOngoing(title = title, id = id, thumb = thumb,
                    episode = episode, uploadedOn = null, dayUpdated = null, link = link))
            }
        }

        HomeResponse(
            status = "success",
            home = HomeData(ongoing = ongoingList, complete = emptyList())
        )
    }

    // ── Ongoing List ──────────────────────────────────
    suspend fun getOngoingAnimeList(page: Int): AnimeListResponse = withContext(Dispatchers.IO) {
        val url = if (page <= 1) "${baseUrl}ongoing-list/" else "${baseUrl}ongoing-list/page/$page/"
        val doc = fetchDocument(url)

        val list = doc.select(".gacha-grid a.gacha-card").mapNotNull { el ->
            val link = el.absUrl("href").ifBlank { el.attr("href") }
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val title = el.select("h3.title").text().trim()
            val thumb = el.select("img").first()?.absUrl("src") ?: ""
            val currentEp = el.select(".current-ep").text().trim()
            val totalEp = el.select(".total-ep").text().trim()
            val episode = if (totalEp.isNotBlank() && totalEp != "?") "$currentEp / $totalEp" else "$currentEp / ??"
            if (title.isBlank()) null
            else AnimeListItem(title = title, animeName = title, id = id, thumb = thumb,
                episode = episode, score = null, studio = null, releaseDate = null, genreList = null)
        }
        AnimeListResponse(status = "success", animeList = list)
    }

    // ── Complete / All Anime ──────────────────────────
    suspend fun getCompletedAnimeList(page: Int): AnimeListResponse = withContext(Dispatchers.IO) {
        val url = if (page <= 1) "${baseUrl}anime/" else "${baseUrl}anime/page/$page/"
        val doc = fetchDocument(url)

        val list = doc.select(".as-anime-grid a.as-anime-card").mapNotNull { el ->
            val link = el.absUrl("href").ifBlank { el.attr("href") }
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val titleEl = el.select("h3.as-anime-title").first()
            val title = titleEl?.attr("data-title-default")?.ifBlank { titleEl.text().trim() } ?: ""
            val thumb = el.select("img").first()?.absUrl("src") ?: ""
            if (title.isBlank()) null
            else AnimeListItem(title = title, animeName = title, id = id, thumb = thumb,
                episode = null, score = null, studio = null, releaseDate = null, genreList = null)
        }
        AnimeListResponse(status = "success", animeList = list)
    }

    // ── Search ────────────────────────────────────────
    suspend fun searchAnime(query: String): SearchResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}?s=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val doc = fetchDocument(url)

        val list = doc.select(".as-anime-grid a.as-anime-card").mapNotNull { el ->
            val link = el.absUrl("href").ifBlank { el.attr("href") }
            val id = AnimeIdUtils.normalizeAnimeId(link)
            val titleEl = el.select("h3.as-anime-title").first()
            val title = titleEl?.attr("data-title-default")?.ifBlank { titleEl.text().trim() } ?: ""
            val thumb = el.select("img").first()?.absUrl("src") ?: ""
            val score = el.select(".as-rating").text().replace("⭐", "").trim()
            if (title.isBlank()) null
            else SearchItem(title = title, id = id, thumb = thumb,
                status = null, score = score.ifBlank { null }, genreList = emptyList())
        }
        SearchResponse(status = "success", searchResults = list)
    }

    // ── Genre List ────────────────────────────────────
    suspend fun getGenres(): GenreListResponse = withContext(Dispatchers.IO) {
        val doc = fetchDocument("${baseUrl}genres/")
        val list = doc.select(".genre-grid-container a.genre-grid-card").mapNotNull { el ->
            val link = el.absUrl("href").ifBlank { el.attr("href") }
            val genreName = el.select("h3.genre-name").text().trim()
            val thumb = el.select("img").first()?.absUrl("src")
            val id = link.removePrefix("${baseUrl}genres/").trimEnd('/')
            if (genreName.isBlank()) null
            else GenreItem(genreName = genreName, id = id, link = link, imageLink = thumb)
        }
        GenreListResponse(genreList = list)
    }

    // ── Anime Detail ──────────────────────────────────
    suspend fun getAnimeDetail(animeId: String): DetailResponse = withContext(Dispatchers.IO) {
        val normalizedId = AnimeIdUtils.normalizeAnimeId(animeId)
        val url = "${baseUrl}anime/$normalizedId/"
        val doc = fetchDocument(url)

        val titleSpan = doc.select("h1.entry-title span").first()
        val title = titleSpan?.attr("data-title-default")?.ifBlank { titleSpan.text().trim() }
            ?: doc.select("h1.entry-title").text().replace("Sub Indo", "").trim()
        val thumb = doc.select(".anime-card__sidebar img").first()?.absUrl("src")
        val score = doc.select(".anime-card__score .value").text().trim()
        val type = doc.select(".anime-card__score .type").text().trim()
        val synopsis = doc.select(".synopsis-prose p").joinToString("\n") { it.text().trim() }
        val status = doc.select(".info-item.status-airing").text().trim()

        val detailItems = doc.select(".details-list li")
        var studio = ""
        var aired = ""
        var contentRating = ""
        detailItems.forEach { li ->
            val label = li.select(".detail-label").text().replace(":", "").trim().lowercase()
            val value = li.text().replace(li.select(".detail-label").text(), "").trim()
            when (label) {
                "studios" -> studio = value
                "aired" -> aired = value
                "rating" -> contentRating = value
            }
        }

        val genreList = doc.select(".anime-card__genres.in-tab a.genre-tag").map { el ->
            val genreLink = el.absUrl("href").ifBlank { el.attr("href") }
            Genre(
                genreName = el.text().trim(),
                genreId = genreLink.removePrefix("${baseUrl}genres/").trimEnd('/'),
                genreLink = genreLink
            )
        }

        val episodeList = doc.select(".episode-list-items a.episode-item").map { el ->
            val epLink = el.absUrl("href").ifBlank { el.attr("href") }
            val epId = epLink.removePrefix(baseUrl).trimEnd('/')
            EpisodeItem(
                title = el.select(".ep-title").text().trim(),
                id = epId,
                link = epLink,
                uploadedOn = el.select(".ep-date").text().trim()
            )
        }

        DetailResponse(
            animeId = normalizedId,
            title = title,
            japanese = titleSpan?.attr("data-title-jp") ?: "",
            thumb = thumb,
            score = score.ifBlank { null },
            type = type,
            synopsis = synopsis,
            status = status,
            studio = studio.ifBlank { null },
            totalEpisode = null,
            duration = null,
            releaseDate = null,
            producer = null,
            genreList = genreList,
            episodeList = episodeList,
            batchLink = null
        )
    }

    // ── Episode Detail ────────────────────────────────
    suspend fun getEpisodeDetail(episodeId: String): EpisodeDetailResponse = withContext(Dispatchers.IO) {
        val normalizedId = AnimeIdUtils.normalizeEpisodeId(episodeId)
        val url = if (normalizedId.startsWith("http")) normalizedId else "$baseUrl$normalizedId/"
        val doc = fetchDocument(url)

        val titleSpan = doc.select("h1.entry-title span").first()
        val titleBase = titleSpan?.attr("data-title-default")?.ifBlank { titleSpan.text().trim() } ?: ""
        val episodePart = doc.select("h1.entry-title").text()
            .replace(titleBase, "").replace("Sub Indo", "").trim()
        val title = "$titleBase $episodePart".trim()

        val embedUrl = doc.select("#videoku iframe, #pembed iframe").first()?.let {
            it.attr("data-src").ifBlank { it.attr("src") }
        } ?: ""

        val serverList = doc.select("ul.tabs1.player li.tab-link.serverplayer").map { el ->
            MirrorItem(
                host = el.select("span").text().trim(),
                id = "${el.attr("data-post")}|${el.attr("data-type")}|${el.attr("data-nume")}"
            )
        }

        EpisodeDetailResponse(
            id = url,
            title = title,
            linkStream = embedUrl.ifBlank { "-" },
            mirror1 = MirrorQuality("SD", serverList),
            mirror2 = null,
            mirror3 = null,
            quality = null
        )
    }

    // ── Mirror Stream ─────────────────────────────────
    suspend fun getMirrorStream(mirrorId: String): MirrorResponse = withContext(Dispatchers.IO) {
        MirrorResponse(
            title = "Mirror Stream",
            id = mirrorId,
            streamLink = "-",
            linkStream = "-"
        )
    }

    // ── Helpers ───────────────────────────────────────
    private fun fetchDocument(url: String) = Jsoup.parse(fetch(url), url)

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
                throw IllegalStateException("NontonAnimeID request failed: ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
