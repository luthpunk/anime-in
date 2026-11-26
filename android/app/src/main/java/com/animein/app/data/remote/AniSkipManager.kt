package com.animein.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SkipInterval(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val skipType: String // "op", "mixed-op", "recap"
)

@Singleton
class AniSkipManager @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()
    // Fast client with shorter timeouts so blocked/slow fallback endpoints on mobile networks never stall skip-intro lookup
    private val fastClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
    // Cache memory: cleanTitle -> malId
    private val malIdCache = ConcurrentHashMap<String, Int>()
    // Cache memory: "$malId:$episodeNumber" -> List<SkipInterval>
    private val skipIntervalsCache = ConcurrentHashMap<String, List<SkipInterval>>()

    /**
     * Resolves MyAnimeList (MAL) ID using AniList GraphQL API with smart long-title shortening
     * and Jikan (MAL) fallback.
     */
    suspend fun getMalId(animeTitle: String): Int? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanAnimeTitle(animeTitle)
        if (cleanTitle.isBlank()) return@withContext null

        malIdCache[cleanTitle]?.let { return@withContext it }

        val candidates = buildSearchCandidates(animeTitle)
        if (candidates.isEmpty()) return@withContext null

        // 1. Try ranked candidates on AniList (stop early on hit or 429 rate-limit)
        for (candidate in candidates) {
            val (anilistId, rateLimited) = queryAniListMalId(candidate)
            if (anilistId != null && anilistId > 0) {
                malIdCache[cleanTitle] = anilistId
                return@withContext anilistId
            }
            if (rateLimited) break
        }

        // 2. Fallback to Jikan v4 (MyAnimeList API) with short timeout on primary candidate
        val jikanId = queryJikanMalId(candidates.first())
        if (jikanId != null && jikanId > 0) {
            malIdCache[cleanTitle] = jikanId
            return@withContext jikanId
        }

        null
    }

    private fun queryAniListMalId(searchTitle: String): Pair<Int?, Boolean> {
        return try {
            val query = """
                query SearchAnime(${'$'}search: String) {
                  Media(search: ${'$'}search, type: ANIME) {
                    idMal
                  }
                }
            """.trimIndent()

            val jsonBody = gson.toJson(mapOf(
                "query" to query,
                "variables" to mapOf("search" to searchTitle)
            ))

            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "AuraNime/1.0")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            fastClient.newCall(request).execute().use { response ->
                if (response.code == 429) return Pair(null, true)
                if (!response.isSuccessful) return Pair(null, false)
                val bodyStr = response.body?.string().orEmpty()
                val jsonObject = gson.fromJson(bodyStr, JsonObject::class.java)
                val idMalElem = jsonObject
                    ?.getAsJsonObject("data")
                    ?.getAsJsonObject("Media")
                    ?.get("idMal")
                val id = if (idMalElem != null && !idMalElem.isJsonNull) idMalElem.asInt else null
                Pair(id, false)
            }
        } catch (_: Exception) {
            Pair(null, false)
        }
    }

    private fun queryJikanMalId(searchTitle: String): Int? {
        return try {
            val encoded = URLEncoder.encode(searchTitle, "UTF-8")
            val request = Request.Builder()
                .url("https://api.jikan.moe/v4/anime?q=$encoded&limit=1")
                .header("Accept", "application/json")
                .header("User-Agent", "AuraNime/1.0")
                .build()

            fastClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string().orEmpty()
                val jsonObject = gson.fromJson(bodyStr, JsonObject::class.java)
                val dataArray = jsonObject?.getAsJsonArray("data") ?: return null
                if (dataArray.size() == 0) return null
                val malIdElem = dataArray[0].asJsonObject.get("mal_id")
                if (malIdElem != null && !malIdElem.isJsonNull) malIdElem.asInt else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetches opening/intro skip intervals from AniSkip API.
     */
    suspend fun getIntroSkipInterval(animeTitle: String, episodeNumber: Double): SkipInterval? = withContext(Dispatchers.IO) {
        val malId = getMalId(animeTitle) ?: return@withContext null
        val cacheKey = "$malId:$episodeNumber"
        skipIntervalsCache[cacheKey]?.let { list ->
            return@withContext pickBestIntroInterval(list)
        }

        try {
            val epInt = if (episodeNumber % 1.0 == 0.0) episodeNumber.toInt().toString() else episodeNumber.toString()
            val url = "https://api.aniskip.com/v2/skip-times/$malId/$epInt?types=op&types=mixed-op&types=recap&episodeLength=0"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AuraNime/1.0")
                .header("Accept", "application/json")
                .build()

            fastClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string().orEmpty()
                val jsonObject = gson.fromJson(bodyStr, JsonObject::class.java)
                val found = jsonObject?.get("found")?.asBoolean ?: false
                if (!found) return@withContext null

                val resultsArray = jsonObject.getAsJsonArray("results") ?: return@withContext null
                val list = mutableListOf<SkipInterval>()
                for (itemElem in resultsArray) {
                    val item = itemElem.asJsonObject
                    val interval = item.getAsJsonObject("interval")
                    val startTime = interval?.get("startTime")?.asDouble ?: continue
                    val endTime = interval.get("endTime")?.asDouble ?: continue
                    val skipType = item.get("skipType")?.asString ?: "op"

                    val startMs = (startTime * 1000).toLong()
                    val endMs = (endTime * 1000).toLong()
                    if (endMs > startMs + 5000L) {
                        list.add(
                            SkipInterval(
                                startTimeMs = startMs,
                                endTimeMs = endMs,
                                skipType = skipType
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) {
                    skipIntervalsCache[cacheKey] = list
                }
                pickBestIntroInterval(list)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun pickBestIntroInterval(list: List<SkipInterval>): SkipInterval? {
        return list.firstOrNull { it.skipType == "op" }
            ?: list.firstOrNull { it.skipType == "mixed-op" }
            ?: list.firstOrNull { it.skipType == "recap" }
    }

    fun extractEpisodeNumber(episodeTitle: String, episodeId: String): Double {
        val cleanedTitle = episodeTitle
            .replace(Regex("""\[.*?]"""), " ")
            .replace(Regex("""\(\s*\d{3,4}p\s*\)""", RegexOption.IGNORE_CASE), " ")
            .trim()

        // 1. Explicit episode keyword in title (e.g. "Episode 12", "Ep. 05", "Eps 3.5", "#14")
        val keywordRegex = Regex("""(?:episode|eps?|#)[.\s_-]*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        keywordRegex.find(cleanedTitle)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }

        // 2. Pure numeric episode title (e.g. "12", "08", "12.5")
        if (cleanedTitle.matches(Regex("""^\d+(?:\.\d+)?$"""))) {
            cleanedTitle.toDoubleOrNull()?.let { return it }
        }

        // 3. Explicit episode keyword in episodeId (avoid matching opaque numeric DB IDs like "animeinweb:98765")
        keywordRegex.find(episodeId)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }

        // 4. Last reasonable standalone number in episodeTitle (excluding resolutions & years)
        val standaloneNumbers = Regex("""\b(\d+(?:\.\d+)?)\b""")
            .findAll(cleanedTitle)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { n ->
                val i = n.toInt()
                n > 0.0 && i <= 2000 && i !in setOf(360, 480, 720, 1080) && i !in 1990..2035
            }
            .toList()
        if (standaloneNumbers.isNotEmpty()) {
            return standaloneNumbers.last()
        }

        return 1.0
    }

    internal fun cleanAnimeTitle(raw: String): String {
        return raw
            .replace(Regex("""\[.*?]|【.*?】"""), " ")
            .replace(
                Regex(
                    """\(\s*(?:sub\s*indo|subtitle\s*indonesia|bd|blu-?ray|uncensored|censored|tv|batch|dual\s*audio|ongoing|tamat|end|complete|\d{3,4}p)\s*\)""",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
            .replace(Regex("""(?i)\b(?:sub\s*indo(?:nesia)?|subtitle\s*indonesia|batch|full\s*episode)\b.*"""), "")
            .replace(Regex("""(?i)\b(?:episode|eps?)\s*\d+.*"""), "")
            .replace(Regex("""(?i)\bS(\d+)\b""")) { "Season ${it.groupValues[1]}" }
            .replace(Regex("""\s*[-:|/]\s*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    internal fun buildSearchCandidates(raw: String): List<String> {
        val cleaned = cleanAnimeTitle(raw)
        if (cleaned.isBlank()) return emptyList()

        val seasonSuffix = Regex("""(?i)\b(Season\s+\d+|\d+(?:nd|rd|th)\s+Season|Part\s+\d+)\b""")
            .find(cleaned)?.value

        fun appendSeasonIfMissing(candidate: String): String {
            val trimmed = candidate.trim().trimEnd('.', ':', '-', '|', '/')
            if (seasonSuffix != null && !trimmed.contains(seasonSuffix, ignoreCase = true)) {
                return "$trimmed $seasonSuffix"
            }
            return trimmed
        }

        val noParensOrQuotes = cleaned
            .replace(Regex("""\(.*?\)"""), " ")
            .replace(Regex("""["'“”‘’]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // First clause before sentence/subtitle separators (crucial for very long light-novel titles!)
        val firstClause = Regex("""\s*(?:\.\s+|\.:\s*|:\s+|\s+-\s+|\s+\|\s+|\s+/\s+)""")
            .split(noParensOrQuotes, limit = 2)
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.length >= 8 && it.split(" ").size >= 2 }
            ?.let { appendSeasonIfMissing(it) }

        val words = noParensOrQuotes.split(" ").filter { it.isNotBlank() }
        val shortPrefix = if (words.size > 7) {
            appendSeasonIfMissing(words.take(6).joinToString(" "))
        } else null

        val ordered = mutableListOf<String>()
        if (cleaned.length > 85 && !firstClause.isNullOrBlank()) {
            // Very long title (e.g. 200-char isekai LN): try the clean first sentence FIRST to avoid AniList 404!
            ordered.add(firstClause)
            shortPrefix?.let { ordered.add(it) }
            ordered.add(cleaned)
        } else {
            ordered.add(cleaned)
            firstClause?.let { ordered.add(it) }
            if (noParensOrQuotes != cleaned && noParensOrQuotes.isNotBlank()) {
                ordered.add(noParensOrQuotes)
            }
            shortPrefix?.let { ordered.add(it) }
        }

        return ordered
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
    }
}
