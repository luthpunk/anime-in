package com.animein.app.ui.screens.schedule

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.animein.app.data.model.ScheduleItem
import com.animein.app.ui.theme.DarkSurface
import com.animein.app.ui.theme.FrostGlassBgTop
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.ui.theme.OutlineGrey
import com.animein.app.ui.theme.OutlineGreySubtle
import com.animein.app.ui.theme.TextSecondary
import androidx.compose.foundation.lazy.rememberLazyListState
import com.animein.app.ui.components.PremiumLoadingSpinner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.scheduleState.collectAsState()

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
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                        Button(
                            onClick = { viewModel.fetchSchedule(forceRefresh = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                        ) {
                            Text("Coba Lagi", color = Color.White)
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
    // Detect today's day in Indonesian
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
                        .background(FrostGlassBgTop, RoundedCornerShape(6.dp))
                        .border(1.dp, OutlineGrey, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
            // Anime count badge
            if (item.animeList.isNotEmpty()) {
                Text(
                    "${item.animeList.size} anime",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = FrostGlassBgTop
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (isToday) 1.5.dp else 1.dp,
                    color = if (isToday) Color.White.copy(alpha = 0.7f) else OutlineGreySubtle,
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                if (item.animeList.isEmpty()) {
                    Text(
                        text = "Tidak ada rilis hari ini",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else {
                    item.animeList.forEachIndexed { index, anime ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAnimeClick(anime.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = anime.animeName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
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
                                color = OutlineGreySubtle.copy(alpha = 0.5f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
