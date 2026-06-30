package com.animein.app.ui.screens.detail

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
                            // Floating Favorite Button (Top-Right)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .statusBarsPadding()
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleFavorite(detail) },
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
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
    val cleanTitle = title.trim()
    val match = Regex("(?i)(?:episode|ep)\\s*(\\d+)").find(cleanTitle)
    if (match != null) return match.groupValues[1]

    val digits = Regex("\\d+").findAll(cleanTitle).map { it.value }.toList()
    if (digits.isNotEmpty()) {
        return digits.last()
    }

    val lastWord = cleanTitle.substringAfterLast(" ").trim()
    return if (lastWord.length <= 4) lastWord else cleanTitle.take(3)
}

