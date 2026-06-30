package com.animein.app.util

object AnimeIdUtils {
    fun normalizeAnimeId(rawId: String?): String {
        if (rawId.isNullOrBlank()) return ""

        return rawId
            .trim()
            .substringBefore("?")
            .substringBefore("#")
            .removePrefix("https://otakudesu.blog/anime/")
            .removePrefix("http://otakudesu.blog/anime/")
            .removePrefix("https://otakudesu.cloud/anime/")
            .removePrefix("http://otakudesu.cloud/anime/")
            .removePrefix("anime/")
            .trim('/')
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
            .removePrefix("https://otakudesu.blog/")
            .removePrefix("http://otakudesu.blog/")
            .removePrefix("https://otakudesu.cloud/")
            .removePrefix("http://otakudesu.cloud/")
            .trimStart('/')
    }
}
