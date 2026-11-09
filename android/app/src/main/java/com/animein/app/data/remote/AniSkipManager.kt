package com.animein.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    // Cache memory: animeTitle -> malId
    private val malIdCache = ConcurrentHashMap<String, Int>()
    // Cache memory: "$malId:$episodeNumber" -> List<SkipInterval>
    private val skipIntervalsCache = ConcurrentHashMap<String, List<SkipInterval>>()

    /**
     * Resolves MyAnimeList (MAL) ID using AniList GraphQL API based on anime title.
     */
    suspend fun getMalId(animeTitle: String): Int? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanAnimeTitle(animeTitle)
        if (cleanTitle.isBlank()) return@withContext null

        malIdCache[cleanTitle]?.let { return@withContext it }

        try {
            val query = """
                query SearchAnime(${'$'}search: String) {
                  Media(search: ${'$'}search, type: ANIME) {
                    idMal
                  }
                }
            """.trimIndent()

            val jsonBody = gson.toJson(mapOf(
                "query" to query,
                "variables" to mapOf("search" to cleanTitle)
            ))

            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "AnimeIn/1.0")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string().orEmpty()
                val jsonObject = gson.fromJson(bodyStr, JsonObject::class.java)
                val idMal = jsonObject
                    .getAsJsonObject("data")
                    ?.getAsJsonObject("Media")
                    ?.get("idMal")
                    ?.asInt

                if (idMal != null && idMal > 0) {
                    malIdCache[cleanTitle] = idMal
                    idMal
                } else null
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
            return@withContext list.firstOrNull { it.skipType in listOf("op", "mixed-op", "recap") }
        }

        try {
            val epInt = if (episodeNumber % 1.0 == 0.0) episodeNumber.toInt().toString() else episodeNumber.toString()
            val url = "https://api.aniskip.com/v2/skip-times/$malId/$epInt?types=op&types=mixed-op&types=recap&episodeLength=0"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AnimeIn/1.0")
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string().orEmpty()
                val jsonObject = gson.fromJson(bodyStr, JsonObject::class.java)
                val found = jsonObject.get("found")?.asBoolean ?: false
                if (!found) return@withContext null

                val resultsArray = jsonObject.getAsJsonArray("results") ?: return@withContext null
                val list = mutableListOf<SkipInterval>()
                for (itemElem in resultsArray) {
                    val item = itemElem.asJsonObject
                    val interval = item.getAsJsonObject("interval")
                    val startTime = interval?.get("startTime")?.asDouble ?: continue
                    val endTime = interval.get("endTime")?.asDouble ?: continue
                    val skipType = item.get("skipType")?.asString ?: "op"

                    list.add(SkipInterval(
                        startTimeMs = (startTime * 1000).toLong(),
                        endTimeMs = (endTime * 1000).toLong(),
                        skipType = skipType
                    ))
                }
                skipIntervalsCache[cacheKey] = list
                list.firstOrNull { it.skipType in listOf("op", "mixed-op", "recap") }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun extractEpisodeNumber(episodeTitle: String, episodeId: String): Double {
        val combined = "$episodeTitle $episodeId"
        val regex = Regex("""(?:episode|eps?)[.\s_-]*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        val match = regex.find(combined)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
    }

    private fun cleanAnimeTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)\s*(sub\s*indo.*|subtitle\s*indonesia.*)"""), "")
            .replace(Regex("""(?i)\s*episode\s*\d+.*"""), "")
            .replace(Regex("""(?i)\s*eps?\s*\d+.*"""), "")
            .replace(Regex("""\s*-\s*$"""), "")
            .trim()
    }
}
