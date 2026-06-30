package com.animein.app.util

object AnimeIdUtils {
    private val NONTON_BASE = "https://s13.nontonanimeid.boats/"

    fun normalizeAnimeId(rawId: String?): String {
        if (rawId.isNullOrBlank()) return ""

        return rawId
            .trim()
            .substringBefore("?")
            .substringBefore("#")
            // NontonAnimeID
            .removePrefix("${NONTON_BASE}anime/")
            .removePrefix("http://s13.nontonanimeid.boats/anime/")
            // Legacy Otakudesu (agar ID lama tetap bisa di-parse jika ada di cache)
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
            // NontonAnimeID
            .removePrefix(NONTON_BASE)
            .removePrefix("http://s13.nontonanimeid.boats/")
            // Legacy Otakudesu
            .removePrefix("https://otakudesu.blog/")
            .removePrefix("http://otakudesu.blog/")
            .removePrefix("https://otakudesu.cloud/")
            .removePrefix("http://otakudesu.cloud/")
            .trimStart('/')
    }
}
