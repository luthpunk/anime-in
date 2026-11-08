package com.animein.app.ui.screens.schedule

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animein.app.data.model.ScheduleItem
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.ui.theme.OutlineGreySubtle
import com.animein.app.ui.theme.TextSecondary
import androidx.compose.foundation.lazy.rememberLazyListState
import com.animein.app.ui.components.PremiumLoadingSpinner
import com.animein.app.ui.components.frostGlass
import com.animein.app.ui.components.frostGlassBgBrush
import com.animein.app.ui.components.frostGlassBorderBrush

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
                        val calendar = java.util.Calendar.getInstance()
                        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                        val todayName = when (dayOfWeek) {
                            java.util.Calendar.MONDAY -> "Senin"
                            java.util.Calendar.TUESDAY -> "Selasa"
                            java.util.Calendar.WEDNESDAY -> "Rabu"
                            java.util.Calendar.THURSDAY -> "Kamis"
                            java.util.Calendar.FRIDAY -> "Jumat"
                            java.util.Calendar.SATURDAY -> "Sabtu"
                            java.util.Calendar.SUNDAY -> "Minggu"
                            else -> ""
                        }
                        val todayIndex = remember(state.schedule) {
                            state.schedule.indexOfFirst { it.day.contains(todayName, ignoreCase = true) }.coerceAtLeast(0)
                        }
                        val listState = rememberLazyListState(initialFirstVisibleItemIndex = todayIndex)

                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = if (isTV) 24.dp else 100.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.schedule) { scheduleItem ->
                                DayScheduleSection(
                                    item = scheduleItem,
                                    onAnimeClick = onAnimeClick
                                )
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

@Composable
fun DayScheduleSection(
    item: ScheduleItem,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val calendar = java.util.Calendar.getInstance()
    val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
    val todayName = when (dayOfWeek) {
        java.util.Calendar.MONDAY -> "Senin"
        java.util.Calendar.TUESDAY -> "Selasa"
        java.util.Calendar.WEDNESDAY -> "Rabu"
        java.util.Calendar.THURSDAY -> "Kamis"
        java.util.Calendar.FRIDAY -> "Jumat"
        java.util.Calendar.SATURDAY -> "Sabtu"
        java.util.Calendar.SUNDAY -> "Minggu"
        else -> ""
    }
    val isToday = item.day.contains(todayName, ignoreCase = true)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.day,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (isToday) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .frostGlass(shape = RoundedCornerShape(6.dp), isFocused = true)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Hari Ini",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (item.animeList.isNotEmpty()) {
                Text(
                    "${item.animeList.size} anime",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .frostGlass(
                    shape = RoundedCornerShape(16.dp),
                    isFocused = isToday,
                    isSubtle = !isToday
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 6.dp)
            ) {
                if (item.animeList.isEmpty()) {
                    Text(
                        text = "Tidak ada rilis hari ini",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                } else {
                    item.animeList.forEachIndexed { index, anime ->
                        var isRowFocused by remember { mutableStateOf(false) }
                        val rowShape = RoundedCornerShape(10.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .then(
                                    if (isRowFocused) {
                                        Modifier
                                            .background(frostGlassBgBrush(isFocused = true), rowShape)
                                            .border(1.dp, frostGlassBorderBrush(isFocused = true), rowShape)
                                    } else Modifier
                                )
                                .onFocusChanged { isRowFocused = it.isFocused }
                                .clickable { onAnimeClick(anime.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = anime.animeName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isRowFocused) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (index < item.animeList.size - 1) {
                            Divider(
                                color = OutlineGreySubtle.copy(alpha = 0.45f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
