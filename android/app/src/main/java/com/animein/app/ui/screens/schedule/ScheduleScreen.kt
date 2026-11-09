package com.animein.app.ui.screens.schedule

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animein.app.data.model.ScheduleAnimeItem
import com.animein.app.data.model.ScheduleItem
import com.animein.app.ui.components.PremiumLoadingSpinner
import com.animein.app.ui.components.frostGlass
import com.animein.app.ui.components.frostGlassBgBrush
import com.animein.app.ui.components.frostGlassBorderBrush
import com.animein.app.ui.components.frostPosterFrame
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.ui.theme.TextSecondary
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.scheduleState.collectAsState()
    val configuration = LocalConfiguration.current
    val isTV = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Jadwal Rilis", fontWeight = FontWeight.Bold, color = Color.White) },
                actions = {
                    IconButton(onClick = { viewModel.fetchSchedule(forceRefresh = true) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Muat Ulang Jadwal",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
                is ScheduleUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PremiumLoadingSpinner(color = NeonPurple)
                    }
                }
                is ScheduleUiState.Success -> {
                    if (state.schedule.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Tidak ada jadwal rilis tersedia.", color = TextSecondary)
                        }
                    } else {
                        val calendar = Calendar.getInstance()
                        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                        val todayName = when (dayOfWeek) {
                            Calendar.MONDAY -> "Senin"
                            Calendar.TUESDAY -> "Selasa"
                            Calendar.WEDNESDAY -> "Rabu"
                            Calendar.THURSDAY -> "Kamis"
                            Calendar.FRIDAY -> "Jumat"
                            Calendar.SATURDAY -> "Sabtu"
                            Calendar.SUNDAY -> "Minggu"
                            else -> ""
                        }

                        val initialDayIndex = remember(state.schedule) {
                            val found = state.schedule.indexOfFirst { it.day.contains(todayName, ignoreCase = true) }
                            if (found >= 0) found else 0
                        }
                        var selectedDayIndex by remember(state.schedule) { mutableStateOf(initialDayIndex) }

                        val activeScheduleItem = state.schedule.getOrElse(selectedDayIndex) { state.schedule.first() }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Day Selector Tabs (Option A)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(state.schedule) { index, item ->
                                    val isSelected = index == selectedDayIndex
                                    val isToday = item.day.contains(todayName, ignoreCase = true)
                                    var isTabFocused by remember { mutableStateOf(false) }
                                    val tabShape = RoundedCornerShape(12.dp)

                                    Box(
                                        modifier = Modifier
                                            .clip(tabShape)
                                            .then(
                                                if (isSelected) {
                                                    Modifier
                                                        .background(frostGlassBgBrush(isFocused = true), tabShape)
                                                        .border(1.5.dp, frostGlassBorderBrush(isFocused = true), tabShape)
                                                } else {
                                                    Modifier.frostGlass(
                                                        shape = tabShape,
                                                        isFocused = isTabFocused,
                                                        isSubtle = !isTabFocused && !isToday
                                                    )
                                                }
                                            )
                                            .onFocusChanged { isTabFocused = it.isFocused }
                                            .clickable { selectedDayIndex = index }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = item.day,
                                            color = when {
                                                isSelected -> Color.White
                                                isToday -> Color(0xFF90CAF9)
                                                else -> TextSecondary
                                            },
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Day Header Summary
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = activeScheduleItem.day,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${activeScheduleItem.animeList.size} Anime",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Grid of Anime Mini-Banners
                            val animeList = activeScheduleItem.animeList
                            if (animeList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Tidak ada anime yang rilis pada hari ${activeScheduleItem.day}.",
                                        color = TextSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(if (isTV) 4 else 2),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 4.dp,
                                        bottom = if (isTV) 24.dp else 100.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(animeList, key = { it.id }) { anime ->
                                        ScheduleMiniBannerCard(
                                            anime = anime,
                                            isTV = isTV,
                                            onClick = { onAnimeClick(anime.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is ScheduleUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.message, color = Color.Red, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        var isRetryFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .frostGlass(shape = RoundedCornerShape(14.dp), isFocused = isRetryFocused)
                                .onFocusChanged { isRetryFocused = it.isFocused }
                                .clickable { viewModel.fetchSchedule(forceRefresh = true) }
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

/**
 * Kartu Mini-Banner untuk jadwal rilis anime:
 * - Ukuran lebih ringkas dari Hero Banner beranda utama (tinggi 110dp di mobile / 130dp di TV)
 * - Rasio aspek horizontal (mini banner grid 2 kolom di mobile / 4 kolom di TV)
 * - Frost glass frame & focus scale animation untuk Android TV
 * - Dark vertical gradient overlay untuk teks yang selalu jelas terbaca
 */
@Composable
fun ScheduleMiniBannerCard(
    anime: ScheduleAnimeItem,
    isTV: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1.0f,
        label = "miniBannerScale"
    )
    val cardShape = RoundedCornerShape(14.dp)
    val bannerHeight = if (isTV) 130.dp else 110.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .frostPosterFrame(shape = cardShape, isFocused = isFocused)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
    ) {
        if (!anime.thumb.isNullOrBlank()) {
            AsyncImage(
                model = anime.thumb,
                contentDescription = anime.animeName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Elegant placeholder when poster thumbnail is unavailable
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Cinematic Dark Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.40f),
                            Color.Black.copy(alpha = 0.92f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Episode / Update Badge (Top End)
        if (!anime.episode.isNullOrBlank() || anime.isUpdated == true) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .frostGlass(shape = RoundedCornerShape(6.dp), isFocused = isFocused)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = anime.episode ?: "Update",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Title overlay at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = anime.animeName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
        }
    }
}
