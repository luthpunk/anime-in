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

    @Test
    fun buildSearchCandidates_shortensVeryLongLightNovelTitleFirst() {
        val veryLongTitle =
            """Tsuihou sareta Cheat Fuyo Majutsushi wa Kimama na Second Life wo Ouka suru. Ore wa Buki dake ja Naku, Arayuru Mono ni "Kyouka Point" wo Fuyo Dekiru shi, Ore no Ishi de Itsudemo Kouka wo Kaijo Dekiru kedo, Nokotta Hitotachi Daijoubu?"""
        val candidates = manager.buildSearchCandidates(veryLongTitle)
        assertEquals(
            "Tsuihou sareta Cheat Fuyo Majutsushi wa Kimama na Second Life wo Ouka suru",
            candidates.first()
        )
    }

    @Test
    fun buildSearchCandidates_preservesSeasonWhenSplittingSubtitle() {
        val title = "Solo Leveling Season 2: Arise from the Shadow (Sub Indo)"
        val candidates = manager.buildSearchCandidates(title)
        assertEquals("Solo Leveling Season 2: Arise from the Shadow", candidates[0])
        assertEquals("Solo Leveling Season 2", candidates[1])
    }
}
