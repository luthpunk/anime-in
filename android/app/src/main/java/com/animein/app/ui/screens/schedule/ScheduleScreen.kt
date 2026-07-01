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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animein.app.data.model.ScheduleItem
import com.animein.app.ui.theme.DarkSurface
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.ui.theme.TextSecondary

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
                        CircularProgressIndicator(color = NeonPurple)
                    }
                }
                is ScheduleUiState.Success -> {
                    if (state.schedule.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Tidak ada jadwal rilis tersedia.", color = TextSecondary)
                        }
                    } else {
                        LazyColumn(
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
                tint = if (isToday) Color(0xFF4CAF50) else NeonPurple,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.day,
                color = if (isToday) Color(0xFF4CAF50) else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (isToday) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
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
                containerColor = if (isToday) DarkSurface.copy(alpha = 0.9f) else DarkSurface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isToday) Modifier.border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    else Modifier
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
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAnimeClick(anime.id) }
                                .padding(horizontal = 16.dp, vertical = 11.dp)
                        ) {
                            // Index number
                            Text(
                                text = "${index + 1}",
                                color = if (isToday) Color(0xFF4CAF50) else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(22.dp)
                            )
                            Text(
                                text = anime.animeName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = if (isToday) Color(0xFF4CAF50).copy(alpha = 0.7f) else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (index < item.animeList.size - 1) {
                            Divider(
                                color = Color.White.copy(alpha = 0.06f),
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
