package com.animein.app.data.remote

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class AniSkipManagerTest {

    private val manager = AniSkipManager(OkHttpClient())

    @Test
    fun cleanAnimeTitle_stripsIndonesianTagsAndNormalizesSeason() {
        assertEquals(
            "Jujutsu Kaisen Season 2",
            manager.cleanAnimeTitle("[BD] Jujutsu Kaisen S2 (Uncensored) Sub Indo")
        )
        assertEquals(
            "Sousou no Frieren",
            manager.cleanAnimeTitle("Sousou no Frieren (TV) Subtitle Indonesia")
        )
        assertEquals(
            "Solo Leveling",
            manager.cleanAnimeTitle("Solo Leveling (Ongoing) - Episode 12")
        )
    }

    @Test
    fun extractEpisodeNumber_handlesNumericAndSlugFormats() {
        assertEquals(12.0, manager.extractEpisodeNumber("12", "animeinweb:98765"), 0.001)
        assertEquals(8.0, manager.extractEpisodeNumber("08", "animeinweb:100234"), 0.001)
        assertEquals(1122.0, manager.extractEpisodeNumber("One Piece Episode 1122 (720p)", "op-ep-1122"), 0.001)
        assertEquals(5.0, manager.extractEpisodeNumber("Frieren", "frieren-episode-5-sub-indo"), 0.001)
        assertEquals(14.5, manager.extractEpisodeNumber("Ep. 14.5", "animeinweb:55555"), 0.001)
    }
}
