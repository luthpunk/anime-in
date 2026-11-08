package com.animein.app.ui.screens.history

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animein.app.data.local.entity.FavoriteEntity
import com.animein.app.data.local.entity.HistoryEntity
import com.animein.app.ui.components.GlassmorphicCard
import com.animein.app.ui.components.frostGlass
import com.animein.app.ui.components.frostGlassBgBrush
import com.animein.app.ui.components.frostGlassBorderBrush
import com.animein.app.ui.theme.DarkSurface
import com.animein.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Favorit", "Riwayat")
    val configuration = LocalConfiguration.current
    val isTV = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Koleksi Saya", color = Color.White, fontWeight = FontWeight.Bold) },
                actions = {
                    if (selectedTabIndex == 1 && history.isNotEmpty()) {
                        var isClearFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(38.dp)
                                .frostGlass(shape = CircleShape, isFocused = isClearFocused)
                                .onFocusChanged { isClearFocused = it.isFocused }
                                .clickable { viewModel.clearAllHistory() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Hapus Semua Riwayat",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // Modern Segmented Frost Glass Pill Tabs
            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTabIndex == index
                        var isTabFocused by remember { mutableStateOf(false) }
                        val tabShape = RoundedCornerShape(18.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(tabShape)
                                .then(
                                    when {
                                        isTabFocused -> Modifier
                                            .background(frostGlassBgBrush(isFocused = true), tabShape)
                                            .border(1.5.dp, frostGlassBorderBrush(isFocused = true), tabShape)
                                        isSelected -> Modifier
                                            .background(frostGlassBgBrush(isFocused = false), tabShape)
                                            .border(1.dp, frostGlassBorderBrush(isFocused = true), tabShape)
                                        else -> Modifier
                                    }
                                )
                                .onFocusChanged { isTabFocused = it.isFocused }
                                .clickable { selectedTabIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected || isTabFocused) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected || isTabFocused) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> FavoritesTabContent(
                        list = favorites,
                        isTV = isTV,
                        onItemClick = onAnimeClick
                    )
                    1 -> HistoryTabContent(
                        list = history,
                        isTV = isTV,
                        onItemClick = onAnimeClick,
                        onDeleteClick = { viewModel.deleteHistoryItem(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoritesTabContent(
    list: List<FavoriteEntity>,
    isTV: Boolean,
    onItemClick: (String) -> Unit
) {
    if (list.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada anime favorit", color = TextSecondary)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = if (isTV) 24.dp else 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(list, key = { it.id }) { fav ->
                var isFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostGlass(
                            shape = RoundedCornerShape(16.dp),
                            isFocused = isFocused,
                            isSubtle = !isFocused
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onItemClick(fav.id) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = fav.thumb,
                        contentDescription = fav.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 60.dp, height = 82.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            fav.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${fav.type} • Skor: ${fav.score}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTabContent(
    list: List<HistoryEntity>,
    isTV: Boolean,
    onItemClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    if (list.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada riwayat menonton", color = TextSecondary)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = if (isTV) 24.dp else 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(list, key = { it.id }) { item ->
                var isFocused by remember { mutableStateOf(false) }
                var isDeleteFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostGlass(
                            shape = RoundedCornerShape(16.dp),
                            isFocused = isFocused,
                            isSubtle = !isFocused
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onItemClick(item.id) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.thumb,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 60.dp, height = 82.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .frostGlass(shape = CircleShape, isFocused = isDeleteFocused, isSubtle = !isDeleteFocused)
                            .onFocusChanged { isDeleteFocused = it.isFocused }
                            .clickable { onDeleteClick(item.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Hapus riwayat",
                            tint = if (isDeleteFocused) Color.White else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
