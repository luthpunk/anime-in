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
    // Cache memory: cleanTitle -> malId
    private val malIdCache = ConcurrentHashMap<String, Int>()
    // Cache memory: "$malId:$episodeNumber" -> List<SkipInterval>
    private val skipIntervalsCache = ConcurrentHashMap<String, List<SkipInterval>>()

    /**
     * Resolves MyAnimeList (MAL) ID using AniList GraphQL API with Jikan (MAL) fallback.
     */
    suspend fun getMalId(animeTitle: String): Int? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanAnimeTitle(animeTitle)
        if (cleanTitle.isBlank()) return@withContext null

        malIdCache[cleanTitle]?.let { return@withContext it }

        // 1. Try AniList GraphQL with cleaned title
        val anilistId = queryAniListMalId(cleanTitle)
        if (anilistId != null && anilistId > 0) {
            malIdCache[cleanTitle] = anilistId
            return@withContext anilistId
        }

        // 2. Try simplified title (strip remaining parentheticals / subtitle after colon) on AniList
        val simplifiedTitle = simplifyTitleFallback(cleanTitle)
        if (simplifiedTitle.isNotBlank() && simplifiedTitle != cleanTitle) {
            val anilistFallbackId = queryAniListMalId(simplifiedTitle)
            if (anilistFallbackId != null && anilistFallbackId > 0) {
                malIdCache[cleanTitle] = anilistFallbackId
                return@withContext anilistFallbackId
            }
        }

        // 3. Fallback to Jikan v4 (MyAnimeList API) in case AniList missed or rate-limited
        val jikanId = queryJikanMalId(cleanTitle)
            ?: simplifiedTitle.takeIf { it.isNotBlank() && it != cleanTitle }?.let { queryJikanMalId(it) }
        if (jikanId != null && jikanId > 0) {
            malIdCache[cleanTitle] = jikanId
            return@withContext jikanId
        }

        null
    }

    private fun queryAniListMalId(searchTitle: String): Int? {
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

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string().orEmpty()
                val jsonObject = gson.fromJson(bodyStr, JsonObject::class.java)
                val idMalElem = jsonObject
                    ?.getAsJsonObject("data")
                    ?.getAsJsonObject("Media")
                    ?.get("idMal")
                if (idMalElem != null && !idMalElem.isJsonNull) idMalElem.asInt else null
            }
        } catch (_: Exception) {
            null
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

            okHttpClient.newCall(request).execute().use { response ->
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

            okHttpClient.newCall(request).execute().use { response ->
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

    private fun simplifyTitleFallback(cleaned: String): String {
        return cleaned
            .replace(Regex("""\(.*?\)"""), " ")
            .substringBefore(" | ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
