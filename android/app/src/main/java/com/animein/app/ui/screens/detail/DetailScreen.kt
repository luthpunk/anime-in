package com.animein.app.ui.screens.detail

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animein.app.data.model.DetailResponse
import com.animein.app.ui.theme.DarkSurface
import com.animein.app.ui.theme.NeonPink
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.ui.theme.TextSecondary

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBackClick: () -> Unit,
    onEpisodeClick: (String, String) -> Unit, // episodeId, animeId
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.detailState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    Scaffold(
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonPurple)
                    }
                }
                is DetailUiState.Success -> {
                    val detail = state.data
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp)
                    ) {
                        // Poster Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            AsyncImage(
                                model = detail.thumb,
                                contentDescription = detail.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.5f),
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )
                        // Back button (top-left)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                                    .statusBarsPadding()
                            ) {
                                IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Kembali",
                                        tint = Color.White
                                    )
                                }
                            }
                            // Favorite button (top-right)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .statusBarsPadding()
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleFavorite(detail) },
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorit",
                                        tint = if (isFavorite) NeonPink else Color.White
                                    )
                                }
                            }
                        }

                        // Info Details
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .offset(y = (-40).dp)
                        ) {
                            Text(
                                detail.title,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            detail.japanese?.takeIf { it.isNotBlank() }?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    it,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 2
                                )
                            }

                            // Type badge (Movie / OVA / Special / TV)
                            detail.type?.takeIf { it.isNotBlank() }?.let { animeType ->
                                Spacer(modifier = Modifier.height(8.dp))
                                val (badgeColor, badgeEmoji) = when {
                                    animeType.contains("Movie", ignoreCase = true) -> Pair(Color(0xFFFFD700), "🎬")
                                    animeType.contains("OVA", ignoreCase = true) -> Pair(Color(0xFFAB47BC), "💿")
                                    animeType.contains("Special", ignoreCase = true) -> Pair(Color(0xFF26A69A), "⭐")
                                    animeType.contains("ONA", ignoreCase = true) -> Pair(Color(0xFFEF5350), "📡")
                                    else -> Pair(NeonPurple, "📺")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "$badgeEmoji $animeType",
                                            color = badgeColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    detail.totalEpisode?.takeIf { it.isNotBlank() && it != "0" }?.let { totalEp ->
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "$totalEp Episode",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Genres FlowRow
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                detail.genreList?.forEach { genre ->
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, NeonPurple, RoundedCornerShape(20.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            genre.genreName,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Metadata Grid
                            MetadataItem(label = "Skor", value = detail.score?.toString() ?: "-")
                            MetadataItem(label = "Status", value = detail.status ?: "-")
                            MetadataItem(label = "Studio", value = detail.studio ?: "-")
                            MetadataItem(label = "Rilis", value = detail.releaseDate ?: "-")
                            MetadataItem(label = "Durasi", value = detail.duration ?: "-")
                            MetadataItem(label = "Produser", value = detail.producer ?: "-")

                            Spacer(modifier = Modifier.height(20.dp))

                            // Synopsis
                            Text("Sinopsis", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            var isExpanded by remember { mutableStateOf(false) }
                            Text(
                                detail.synopsis ?: "Tidak ada sinopsis.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { isExpanded = !isExpanded }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Episode List
                            Text("Daftar Episode", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            val episodes = detail.episodeList ?: emptyList()
                            if (episodes.isEmpty()) {
                                Text("Episode belum tersedia", color = TextSecondary, fontSize = 14.sp)
                            } else {
                                episodes.forEach { episode ->
                                    val epNum = extractEpisodeNumber(episode.title)
                                    val displayTitle = if (epNum.isNotEmpty()) "Episode $epNum" else episode.title
                                    EpisodeRow(
                                        title = displayTitle,
                                        date = episode.uploadedOn ?: "",
                                        onClick = { onEpisodeClick(episode.id, detail.animeId) }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
                is DetailUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.message, color = Color.Red)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.fetchDetail() }) {
                                Text("Coba Lagi")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            "$label:",
            color = TextSecondary,
            modifier = Modifier.width(80.dp),
            fontSize = 13.sp
        )
        Text(
            value,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}

@Composable
fun EpisodeRow(
    title: String,
    date: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (date.isNotEmpty()) {
                Text(
                    date,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Putar",
            tint = NeonPurple,
            modifier = Modifier.size(24.dp)
        )
    }
}

private fun extractEpisodeNumber(title: String): String {
    // Clean Nimegami format: "[Nimegami] Anime_Title_Episode_01_(720p)"
    val cleaned = title
        .replace(Regex("\\[.*?\\]"), "")      // remove [Nimegami]
        .replace(Regex("\\(\\d+p\\)"), "")    // remove (720p), (1080p), (480p), (360p)
        .replace(Regex("\\(BD\\)", RegexOption.IGNORE_CASE), "")
        .replace("_", " ")
        .trim()

    // Priority 1: explicit "Episode XX" or "Ep XX"
    val epMatch = Regex("(?i)(?:episode|ep)[_\\s]*(\\d+)").find(cleaned)
    if (epMatch != null) return epMatch.groupValues[1]

    // Priority 2: Movie / OVA / Special (no number needed)
    if (cleaned.contains("Movie", ignoreCase = true)) return "Movie"
    if (cleaned.contains("OVA", ignoreCase = true)) return "OVA"
    if (cleaned.contains("Special", ignoreCase = true)) return "Special"

    // Priority 3: last numeric segment that is NOT a video quality (< 400)
    val nums = Regex("\\d+").findAll(cleaned)
        .map { it.value.toIntOrNull() ?: 0 }
        .filter { it in 1..999 }
        .toList()
    if (nums.isNotEmpty()) return nums.last().toString()

    // Fallback: use cleaned title truncated
    return cleaned.take(20).trim()
}

