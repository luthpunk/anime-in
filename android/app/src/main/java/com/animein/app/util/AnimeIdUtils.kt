package com.animein.app.util

object AnimeIdUtils {
    fun normalizeAnimeId(rawId: String?): String {
        if (rawId.isNullOrBlank()) return ""

        val cleaned = rawId
            .trim()
            .substringBefore("?")
            .substringBefore("#")
            .removePrefix("https://nimegami.id/")
            .removePrefix("http://nimegami.id/")
            .removePrefix("https://otakudesu.blog/anime/")
            .removePrefix("http://otakudesu.blog/anime/")
            .removePrefix("https://otakudesu.cloud/anime/")
            .removePrefix("http://otakudesu.cloud/anime/")
            .removePrefix("anime/")
            .trim('/')

        if (cleaned.all { it.isDigit() }) {
            return cleaned
        }

        return cleaned
            .takeIf { it.isNotBlank() }
            ?.plus("/")
            ?: ""
    }

    fun normalizeEpisodeId(rawId: String?): String {
        if (rawId.isNullOrBlank()) return ""

        return rawId
            .trim()
            .substringBefore("?")
            .substringBefore("#")
            .removePrefix("https://nimegami.id/")
            .removePrefix("http://nimegami.id/")
            .removePrefix("https://otakudesu.blog/")
            .removePrefix("http://otakudesu.blog/")
            .removePrefix("https://otakudesu.cloud/")
            .removePrefix("http://otakudesu.cloud/")
            .trimStart('/')
    }

    /**
     * Memastikan label episode memiliki spasi yang rapi:
     * "Episode6" -> "Episode 6", "12Episode" -> "12 Episode", "Ep6" -> "Ep 6"
     */
    fun formatEpisodeLabel(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim()
            .replace(Regex("(?i)(episode|eps|ep)(\\d+)"), "$1 $2")
            .replace(Regex("(?i)(\\d+)(episode|eps|ep)"), "$1 $2")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
