package com.animein.app.ui.screens.animelist

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animein.app.data.model.AnimeListItem
import com.animein.app.ui.components.PremiumLoadingSpinner
import com.animein.app.ui.components.frostGlass
import com.animein.app.ui.components.frostPosterFrame
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.util.AnimeIdUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeListScreen(
    viewModel: AnimeListViewModel,
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isTV = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text(viewModel.listTitle, color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    var isBackFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(38.dp)
                            .frostGlass(shape = CircleShape, isFocused = isBackFocused)
                            .onFocusChanged { isBackFocused = it.isFocused }
                            .clickable { onBackClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is AnimeListUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PremiumLoadingSpinner(color = NeonPurple)
                    }
                }
                is AnimeListUiState.Success -> {
                    val list = state.list
                    if (list.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Tidak ada anime ditemukan", color = Color.White)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = if (isTV) GridCells.Fixed(5) else GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(list) { anime ->
                                ListAnimeCard(
                                    anime = anime,
                                    onClick = { onAnimeClick(anime.id) }
                                )
                            }
                        }
                    }
                }
                is AnimeListUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.message, color = Color.Red)
                            Spacer(modifier = Modifier.height(12.dp))
                            var isRetryFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .frostGlass(shape = RoundedCornerShape(14.dp), isFocused = isRetryFocused)
                                    .onFocusChanged { isRetryFocused = it.isFocused }
                                    .clickable { viewModel.fetchAnimeList(forceRefresh = true) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text("Coba Lagi", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListAnimeCard(
    anime: AnimeListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = tween(160),
        label = "listAnimeScale"
    )
    val formattedEpisode = remember(anime.episode) {
        AnimeIdUtils.formatEpisodeLabel(anime.episode)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .frostPosterFrame(
                    shape = RoundedCornerShape(16.dp),
                    isFocused = isFocused
                )
        ) {
            AsyncImage(
                model = anime.thumb,
                contentDescription = anime.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            anime.score?.let { score ->
                if (score.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .frostGlass(shape = RoundedCornerShape(6.dp), isFocused = isFocused)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            score,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (formattedEpisode.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .frostGlass(shape = RoundedCornerShape(8.dp), isFocused = isFocused)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        formattedEpisode,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            anime.displayTitle,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
