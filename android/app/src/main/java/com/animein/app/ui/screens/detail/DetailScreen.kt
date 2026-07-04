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
import androidx.compose.material.icons.filled.Star
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
import com.animein.app.ui.components.GlassmorphicCard
import com.animein.app.ui.components.AppleGlassIcon
import com.animein.app.ui.theme.DarkSurface
import com.animein.app.ui.theme.NeonPink
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.ui.theme.TextSecondary
import com.animein.app.ui.theme.StarYellow

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
                                .height(320.dp)
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
                                                Color.Black.copy(alpha = 0.4f),
                                                Color.Black.copy(alpha = 0.7f),
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )
                            
                            // Back button (top-left) with Apple Glass style
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp)
                                    .statusBarsPadding()
                            ) {
                                AppleGlassIcon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Kembali",
                                    iconColor = Color.White,
                                    onClick = onBackClick
                                )
                            }
                            
                            // Favorite button (top-right) with Apple Glass style
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .statusBarsPadding()
                            ) {
                                AppleGlassIcon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorit",
                                    iconColor = if (isFavorite) NeonPink else Color.White,
                                    glowColor = if (isFavorite) NeonPink else NeonPurple,
                                    onClick = { viewModel.toggleFavorite(detail) }
                                )
                            }
                            
                            // Title & Quick Badges overlaid on bottom of the banner
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    detail.title,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 28.sp
                                )
                                detail.japanese?.takeIf { it.isNotBlank() }?.let { jpTitle ->
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        jpTitle,
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Compact Info Details
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .offset(y = (-8).dp)
                        ) {
                            // Type & Episode Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                detail.type?.takeIf { it.isNotBlank() }?.let { animeType ->
                                    val (badgeColor, badgeEmoji) = when {
                                        animeType.contains("Movie", ignoreCase = true) -> Pair(Color(0xFFFFD700), "🎬")
                                        animeType.contains("OVA", ignoreCase = true) -> Pair(Color(0xFFAB47BC), "💿")
                                        animeType.contains("Special", ignoreCase = true) -> Pair(Color(0xFF26A69A), "⭐")
                                        animeType.contains("ONA", ignoreCase = true) -> Pair(Color(0xFFEF5350), "📡")
                                        else -> Pair(NeonPurple, "📺")
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "$badgeEmoji $animeType",
                                            color = badgeColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                detail.totalEpisode?.takeIf { it.isNotBlank() && it != "0" }?.let { totalEp ->
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "$totalEp Episode",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Glassmorphic Compact Metadata Card
                            GlassmorphicCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Row 1: Score & Studio
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Skor",
                                                tint = StarYellow,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${detail.score ?: "-"} / 10",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        detail.studio?.takeIf { it.isNotBlank() }?.let { studio ->
                                            Text(
                                                text = studio,
                                                color = NeonPurple,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Divider(color = Color.White.copy(alpha = 0.1f))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Row 2: Status, Durasi, Rilis
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Status", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(detail.status ?: "-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Durasi", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(detail.duration ?: "-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                            Text("Rilis", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(detail.releaseDate ?: "-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }

                            // Genres Scrollable Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                detail.genreList?.forEach { genre ->
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(20.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
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

                            // Synopsis Section
                            Text("Sinopsis", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            var isExpanded by remember { mutableStateOf(false) }
                            Text(
                                detail.synopsis ?: "Tidak ada sinopsis.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { isExpanded = !isExpanded }
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Episode List
                            Text("Daftar Episode", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val episodes = (detail.episodeList ?: emptyList())
                            if (episodes.isEmpty()) {
                                Text("Episode belum tersedia", color = TextSecondary, fontSize = 13.sp)
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
fun EpisodeRow(
    title: String,
    date: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Glassmorphic row for episode list
    GlassmorphicCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (date.isNotEmpty()) {
                    Text(
                        date,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(NeonPurple.copy(alpha = 0.2f))
                    .border(1.dp, NeonPurple.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Putar",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun extractEpisodeNumber(title: String): String {
    val cleaned = title
        .replace(Regex("\\[.*?\\]"), "")      // remove [Nimegami]
        .replace(Regex("\\(\\d+p\\)"), "")    // remove (720p), (1080p), (480p), (360p)
        .replace(Regex("\\(BD\\)", RegexOption.IGNORE_CASE), "")
        .replace("_", " ")
        .trim()

    val epMatch = Regex("(?i)(?:episode|ep)[_\\s]*(\\d+)").find(cleaned)
    if (epMatch != null) return epMatch.groupValues[1]

    if (cleaned.contains("Movie", ignoreCase = true)) return "Movie"
    if (cleaned.contains("OVA", ignoreCase = true)) return "OVA"
    if (cleaned.contains("Special", ignoreCase = true)) return "Special"

    val nums = Regex("\\d+").findAll(cleaned)
        .map { it.value.toIntOrNull() ?: 0 }
        .filter { it in 1..999 }
        .toList()
    if (nums.isNotEmpty()) return nums.last().toString()

    return cleaned.take(20).trim()
}
