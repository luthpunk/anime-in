package com.animein.app.data.model

import com.google.gson.annotations.SerializedName

// Home Response
data class HomeResponse(
    @SerializedName("status") val status: String,
    @SerializedName("home") val home: HomeData
)

data class HomeData(
    @SerializedName("on_going") val ongoing: List<AnimeOngoing>,
    @SerializedName("completed") val complete: List<AnimeCompleted>
)

data class AnimeOngoing(
    @SerializedName("title") val title: String,
    @SerializedName("id") val id: String,
    @SerializedName("thumb") val thumb: String,
    @SerializedName("episode") val episode: String,
    @SerializedName("uploaded_on") val uploadedOn: String?,
    @SerializedName("day_updated") val dayUpdated: String?,
    @SerializedName("link") val link: String?
)

data class AnimeCompleted(
    @SerializedName("title") val title: String,
    @SerializedName("id") val id: String,
    @SerializedName("thumb") val thumb: String,
    @SerializedName("episode") val episode: String,
    @SerializedName("uploaded_on") val uploadedOn: String?,
    @SerializedName("score") val score: String?,
    @SerializedName("link") val link: String?
)

// Listing Response (Ongoing, Completed, or Genre list)
data class AnimeListResponse(
    @SerializedName("status") val status: String,
    @SerializedName("animeList") val animeList: List<AnimeListItem>
)

data class AnimeListItem(
    @SerializedName("title") val title: String?,
    @SerializedName("anime_name") val animeName: String?, // Sometimes anime_name is returned instead of title
    @SerializedName("id") val id: String,
    @SerializedName("thumb") val thumb: String?,
    @SerializedName("episode") val episode: String?,
    @SerializedName("score") val score: String?,
    @SerializedName("studio") val studio: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("genre_list") val genreList: List<Genre>?
) {
    val displayTitle: String
        get() = title ?: animeName ?: ""
}

// Genre List Response
data class GenreListResponse(
    @SerializedName("genreList") val genreList: List<GenreItem>
)

data class GenreItem(
    @SerializedName("genre_name") val genreName: String,
    @SerializedName("id") val id: String,
    @SerializedName("link") val link: String,
    @SerializedName("image_link") val imageLink: String?
)

data class Genre(
    @SerializedName("genre_name") val genreName: String,
    @SerializedName("genre_id") val genreId: String,
    @SerializedName("genre_link") val genreLink: String?
)

// Search Response
data class SearchResponse(
    @SerializedName("status") val status: String,
    @SerializedName("search_results") val searchResults: List<SearchItem>
)

data class SearchItem(
    @SerializedName("title") val title: String = "",
    @SerializedName("id") val id: String = "",
    @SerializedName("thumb") val thumb: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("score") val score: String?,
    @SerializedName("genre_list") val genreList: List<Genre>?
)

// Detail Response
data class DetailResponse(
    @SerializedName("thumb") val thumb: String?,
    @SerializedName("anime_id") val animeId: String = "",
    @SerializedName("synopsis") val synopsis: String?,
    @SerializedName("title") val title: String = "",
    @SerializedName("japanase") val japanese: String?,
    @SerializedName("score") val score: String?,
    @SerializedName("producer") val producer: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("total_episode") val totalEpisode: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("studio") val studio: String?,
    @SerializedName("genre_list") val genreList: List<Genre>?,
    @SerializedName("episode_list") val episodeList: List<EpisodeItem>?,
    @SerializedName("batch_link") val batchLink: BatchLink?
)

data class EpisodeItem(
    @SerializedName("title") val title: String = "",
    @SerializedName("id") val id: String = "",
    @SerializedName("link") val link: String?,
    @SerializedName("uploaded_on") val uploadedOn: String?
)

data class BatchLink(
    @SerializedName("id") val id: String = "",
    @SerializedName("link") val link: String = ""
)

// Episode Detail Response
data class EpisodeDetailResponse(
    @SerializedName("title") val title: String,
    @SerializedName("id") val id: String,
    @SerializedName("link_stream") val linkStream: String?,
    @SerializedName("mirror1") val mirror1: MirrorQuality?,
    @SerializedName("mirror2") val mirror2: MirrorQuality?,
    @SerializedName("mirror3") val mirror3: MirrorQuality?,
    @SerializedName("quality") val quality: QualityOptions?,
    @SerializedName("download_links") val downloadLinks: List<DownloadLink>? = null
)

data class MirrorQuality(
    @SerializedName("quality") val quality: String,
    @SerializedName("mirrorList") val mirrorList: List<MirrorItem>
)

data class MirrorItem(
    @SerializedName("host") val host: String,
    @SerializedName("id") val id: String
)

data class MirrorResponse(
    @SerializedName("title") val title: String?,
    @SerializedName("id") val id: String?,
    @SerializedName("streamLink") val streamLink: String?,
    @SerializedName("link_stream") val linkStream: String?
)

data class QualityOptions(
    @SerializedName("low_quality") val lowQuality: QualityItem?,
    @SerializedName("medium_quality") val mediumQuality: QualityItem?,
    @SerializedName("high_quality") val highQuality: QualityItem?
)

data class QualityItem(
    @SerializedName("quality") val quality: String?,
    @SerializedName("size") val size: String?,
    @SerializedName("download_links") val downloadLinks: List<DownloadLink>?
)

data class DownloadLink(
    @SerializedName("host") val host: String,
    @SerializedName("link") val link: String
)
