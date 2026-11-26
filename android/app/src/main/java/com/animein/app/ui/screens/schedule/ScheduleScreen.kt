package com.animein.app.ui.screens.schedule

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animein.app.data.model.ScheduleAnimeItem
import com.animein.app.ui.components.MonoBadge
import com.animein.app.ui.components.MusikeEmptyState
import com.animein.app.ui.components.MusikeErrorState
import com.animein.app.ui.components.RadiusTile
import com.animein.app.ui.components.ShimmerBox
import com.animein.app.ui.components.musikeSurface
import com.animein.app.ui.theme.BgGraphite
import com.animein.app.ui.theme.FgPrimary
import com.animein.app.ui.theme.FgSecondary
import com.animein.app.ui.theme.FgTertiary
import com.animein.app.ui.theme.Hairline
import com.animein.app.ui.theme.LocalAccentPreset
import com.animein.app.ui.theme.MonoTextStyle
import com.animein.app.ui.theme.Surface1
import com.animein.app.ui.theme.Surface2
import com.animein.app.ui.theme.Surface3
import java.util.Calendar

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.scheduleState.collectAsState()
    val accent = LocalAccentPreset.current
    val configuration = LocalConfiguration.current
    val isTV = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    val pageBg = if (isTV) Surface1 else BgGraphite
    val horizontalPadding = if (isTV) 20.dp else 16.dp

    Scaffold(
        containerColor = pageBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBg)
                    .then(if (!isTV) Modifier.statusBarsPadding() else Modifier)
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = if (isTV) 14.dp else 12.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jadwal Rilis",
                    color = FgPrimary,
                    fontSize = if (isTV) 24.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )

                var isRefreshFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .musikeSurface(
                            shape = CircleShape,
                            isFocused = isRefreshFocused,
                            accentColor = accent.accent,
                            containerColor = Surface2
                        )
                        .onFocusChanged { isRefreshFocused = it.isFocused }
                        .clickable { viewModel.fetchSchedule(forceRefresh = true) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Muat Ulang Jadwal",
                        tint = if (isRefreshFocused) accent.accent else FgSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is ScheduleUiState.Loading -> {
                    ScheduleShimmerSkeleton(isTV = isTV, horizontalPadding = horizontalPadding)
                }
                is ScheduleUiState.Success -> {
                    if (state.schedule.isEmpty()) {
                        MusikeEmptyState(
                            title = "Jadwal rilis belum tersedia",
                            icon = Icons.Default.DateRange,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val todayName = remember {
                            when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
                                Calendar.MONDAY -> "Senin"
                                Calendar.TUESDAY -> "Selasa"
                                Calendar.WEDNESDAY -> "Rabu"
                                Calendar.THURSDAY -> "Kamis"
                                Calendar.FRIDAY -> "Jumat"
                                Calendar.SATURDAY -> "Sabtu"
                                Calendar.SUNDAY -> "Minggu"
                                else -> ""
                            }
                        }

                        val initialDayIndex = remember(state.schedule) {
                            val found = state.schedule.indexOfFirst { it.day.contains(todayName, ignoreCase = true) }
                            if (found >= 0) found else 0
                        }
                        var selectedDayIndex by remember(state.schedule) { mutableIntStateOf(initialDayIndex) }
                        val activeScheduleItem = state.schedule.getOrElse(selectedDayIndex) { state.schedule.first() }
                        val animeList = remember(activeScheduleItem.animeList) {
                            activeScheduleItem.animeList.distinctBy { it.id }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Musike Solid-Accent Pill Day Selector Tabs
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(state.schedule) { index, item ->
                                    val isSelected = index == selectedDayIndex
                                    val isToday = item.day.contains(todayName, ignoreCase = true)
                                    var isTabFocused by remember { mutableStateOf(false) }

                                    Box(
                                        modifier = Modifier
                                            .height(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> accent.accent
                                                    isTabFocused -> Surface3
                                                    else -> Surface2
                                                },
                                                CircleShape
                                            )
                                            .border(
                                                width = if (isTabFocused) 2.dp else 1.dp,
                                                color = when {
                                                    isTabFocused && isSelected -> Color.White
                                                    isTabFocused -> accent.accent
                                                    isSelected -> Color.Transparent
                                                    isToday -> accent.accent.copy(alpha = 0.45f)
                                                    else -> Hairline
                                                },
                                                shape = CircleShape
                                            )
                                            .onFocusChanged { isTabFocused = it.isFocused }
                                            .clickable { selectedDayIndex = index }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            if (isToday && !isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(accent.accent)
                                                )
                                            }
                                            Text(
                                                text = item.day,
                                                color = when {
                                                    isSelected -> accent.onAccent
                                                    isToday || isTabFocused -> FgPrimary
                                                    else -> FgSecondary
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            // Day Summary Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = activeScheduleItem.day,
                                        color = FgPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    MonoBadge(text = "${animeList.size} anime")
                                }
                                if (activeScheduleItem.day.contains(todayName, ignoreCase = true)) {
                                    Text(
                                        text = "HARI INI",
                                        style = MonoTextStyle.copy(
                                            color = accent.accent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            // Mini-Banner Grid
                            if (animeList.isEmpty()) {
                                MusikeEmptyState(
                                    title = "Tidak ada rilis hari ${activeScheduleItem.day}",
                                    icon = Icons.Default.DateRange,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(if (isTV) 4 else 2),
                                    contentPadding = PaddingValues(
                                        start = horizontalPadding,
                                        end = horizontalPadding,
                                        top = 2.dp,
                                        bottom = if (isTV) 24.dp else 92.dp
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
                    MusikeErrorState(
                        message = state.message,
                        onRetry = { viewModel.fetchSchedule(forceRefresh = true) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleMiniBannerCard(
    anime: ScheduleAnimeItem,
    isTV: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentPreset.current
    var isFocused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(RadiusTile)
    val bannerHeight = if (isTV) 132.dp else 112.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .clip(cardShape)
            .background(Surface2, cardShape)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) accent.accent else Hairline,
                shape = cardShape
            )
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Surface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = FgTertiary.copy(alpha = 0.4f),
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Graphite vertical scrim for guaranteed legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            BgGraphite.copy(alpha = 0.45f),
                            BgGraphite.copy(alpha = 0.94f)
                        )
                    )
                )
        )

        if (!anime.episode.isNullOrBlank() || anime.isUpdated == true) {
            MonoBadge(
                text = anime.episode ?: "Update",
                isAccent = anime.isUpdated == true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = anime.animeName,
                color = if (isFocused) accent.accent else FgPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ScheduleShimmerSkeleton(
    isTV: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) {
                ShimmerBox(
                    modifier = Modifier.width(68.dp).height(34.dp),
                    shape = CircleShape
                )
            }
        }
        val cols = if (isTV) 4 else 2
        repeat(4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(cols) {
                    ShimmerBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(if (isTV) 132.dp else 112.dp),
                        shape = RoundedCornerShape(RadiusTile)
                    )
                }
            }
        }
    }
}
