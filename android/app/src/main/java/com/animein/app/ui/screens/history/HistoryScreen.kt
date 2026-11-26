package com.animein.app.ui.screens.history

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.animein.app.data.local.entity.ContinueWatchingEntity
import com.animein.app.data.local.entity.FavoriteEntity
import com.animein.app.data.local.entity.HistoryEntity
import com.animein.app.ui.components.IslandPanel
import com.animein.app.ui.components.MusikeEmptyState
import com.animein.app.ui.components.RadiusArt
import com.animein.app.ui.components.RadiusTile
import com.animein.app.ui.components.TonalPillButton
import com.animein.app.ui.components.musikePosterFrame
import com.animein.app.ui.components.musikeSurface
import com.animein.app.ui.theme.BgGraphite
import com.animein.app.ui.theme.DangerCoral
import com.animein.app.ui.theme.FgPrimary
import com.animein.app.ui.theme.FgSecondary
import com.animein.app.ui.theme.FgTertiary
import com.animein.app.ui.theme.Hairline
import com.animein.app.ui.theme.HairlineStrong
import com.animein.app.ui.theme.LocalAccentPreset
import com.animein.app.ui.theme.MonoTextStyle
import com.animein.app.ui.theme.StarYellow
import com.animein.app.ui.theme.Surface1
import com.animein.app.ui.theme.Surface2
import com.animein.app.ui.theme.Surface3
import com.animein.app.util.AnimeIdUtils

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()
    val continueWatching by viewModel.continueWatchingList.collectAsState()

    val accent = LocalAccentPreset.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isTV = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    val pageBg = if (isTV) Surface1 else BgGraphite
    val horizontalPadding = if (isTV) 20.dp else 16.dp

    val cwByAnimeId = remember(continueWatching) {
        continueWatching.associateBy { AnimeIdUtils.normalizeAnimeId(it.animeId) }
    }

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
                    text = "Koleksi Saya",
                    color = FgPrimary,
                    fontSize = if (isTV) 24.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )

                if (selectedTabIndex == 1 && history.isNotEmpty()) {
                    var isClearFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .musikeSurface(
                                shape = CircleShape,
                                isFocused = isClearFocused,
                                accentColor = DangerCoral,
                                containerColor = Surface2
                            )
                            .onFocusChanged { isClearFocused = it.isFocused }
                            .clickable { showClearConfirmDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus Semua Riwayat",
                            tint = if (isClearFocused) DangerCoral else FgSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(innerPadding)
        ) {
            // a-music Pill Filter Tabs with monospace counts (e.g. Favorit 8 | Riwayat 12)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Favorit" to favorites.size,
                    "Riwayat" to history.size
                ).forEachIndexed { index, (title, count) ->
                    val isSelected = selectedTabIndex == index
                    var isTabFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .height(38.dp)
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
                                    else -> Hairline
                                },
                                shape = CircleShape
                            )
                            .onFocusChanged { isTabFocused = it.isFocused }
                            .clickable { selectedTabIndex = index }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) accent.onAccent else FgPrimary
                            )
                            Text(
                                text = count.toString(),
                                style = MonoTextStyle.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) accent.onAccent.copy(alpha = 0.72f) else FgTertiary
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding)
            ) {
                when (selectedTabIndex) {
                    0 -> FavoritesGridContent(
                        list = favorites,
                        isTV = isTV,
                        onItemClick = onAnimeClick
                    )
                    1 -> HistoryListContent(
                        list = history,
                        cwByAnimeId = cwByAnimeId,
                        isTV = isTV,
                        onItemClick = onAnimeClick,
                        onDeleteClick = { viewModel.deleteHistoryItem(it) }
                    )
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        ClearHistoryConfirmDialog(
            onConfirm = {
                viewModel.clearAllHistory()
                showClearConfirmDialog = false
            },
            onDismiss = { showClearConfirmDialog = false }
        )
    }
}

/**
 * Responsive Poster Grid for Favorites (3 cols Mobile / 5 cols TV) — matches a-music's crisp tile grid.
 */
@Composable
fun FavoritesGridContent(
    list: List<FavoriteEntity>,
    isTV: Boolean,
    onItemClick: (String) -> Unit
) {
    val accent = LocalAccentPreset.current
    if (list.isEmpty()) {
        MusikeEmptyState(
            title = "Belum ada anime favorit",
            subtitle = "Tekan ikon hati di halaman detail anime untuk menyimpannya di sini.",
            icon = Icons.Default.FavoriteBorder,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isTV) 5 else 3),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = if (isTV) 24.dp else 92.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list, key = { it.id }) { fav ->
                var isFocused by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (isFocused) {
                                Modifier
                                    .background(Surface2, RoundedCornerShape(16.dp))
                                    .border(2.5.dp, accent.accent, RoundedCornerShape(16.dp))
                            } else Modifier
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemClick(fav.id) }
                        .padding(if (isTV) 6.dp else 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isTV) 196.dp else 156.dp)
                            .musikePosterFrame(
                                shape = RoundedCornerShape(RadiusTile),
                                isFocused = false,
                                accentColor = accent.accent
                            )
                    ) {
                        AsyncImage(
                            model = fav.thumb,
                            contentDescription = fav.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (fav.score.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(BgGraphite.copy(alpha = 0.86f))
                                    .border(1.dp, HairlineStrong, RoundedCornerShape(7.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = StarYellow,
                                    modifier = Modifier.size(9.dp)
                                )
                                Text(
                                    text = fav.score,
                                    style = MonoTextStyle.copy(
                                        color = FgPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = fav.title,
                        color = if (isFocused) accent.accent else FgPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryListContent(
    list: List<HistoryEntity>,
    cwByAnimeId: Map<String, ContinueWatchingEntity>,
    isTV: Boolean,
    onItemClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    val accent = LocalAccentPreset.current
    if (list.isEmpty()) {
        MusikeEmptyState(
            title = "Belum ada riwayat menonton",
            subtitle = "Anime yang kamu buka atau tonton akan muncul di sini.",
            icon = Icons.Default.List,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = if (isTV) 24.dp else 92.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list, key = { it.id }) { item ->
                var isFocused by remember { mutableStateOf(false) }
                var isDeleteFocused by remember { mutableStateOf(false) }
                val cw = cwByAnimeId[AnimeIdUtils.normalizeAnimeId(item.id)]
                val progressFraction = if (cw != null && cw.duration > 0L) {
                    (cw.progress.toFloat() / cw.duration.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .musikeSurface(
                            shape = RoundedCornerShape(RadiusTile),
                            isFocused = isFocused,
                            accentColor = accent.accent,
                            containerColor = Surface2,
                            focusedContainerColor = Surface3
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onItemClick(item.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.thumb,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 56.dp, height = 76.dp)
                                .clip(RoundedCornerShape(RadiusArt))
                                .background(Surface3)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = item.title,
                                color = if (isFocused) accent.accent else FgPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (cw != null && cw.episodeTitle.isNotBlank()) {
                                val pct = (progressFraction * 100).toInt()
                                val epShort = AnimeIdUtils.formatEpisodeLabel(cw.episodeTitle)
                                Text(
                                    text = if (pct > 0) "$epShort • $pct%" else epShort,
                                    style = MonoTextStyle.copy(
                                        color = accent.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .musikeSurface(
                                    shape = CircleShape,
                                    isFocused = isDeleteFocused,
                                    accentColor = DangerCoral,
                                    containerColor = Surface1
                                )
                                .onFocusChanged { isDeleteFocused = it.isFocused }
                                .clickable { onDeleteClick(item.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Hapus riwayat",
                                tint = if (isDeleteFocused) DangerCoral else FgTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (progressFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .background(Color.White.copy(alpha = 0.06f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressFraction)
                                    .background(accent.accent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClearHistoryConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        IslandPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Hapus Semua Riwayat?",
                    color = FgPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Semua daftar riwayat tontonan akan dihapus dari perangkat ini.",
                    color = FgSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    TonalPillButton(
                        text = "Batal",
                        onClick = onDismiss
                    )

                    var isDangerFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(DangerCoral, CircleShape)
                            .then(
                                if (isDangerFocused) Modifier.border(2.dp, Color.White, CircleShape)
                                else Modifier
                            )
                            .onFocusChanged { isDangerFocused = it.isFocused }
                            .clickable { onConfirm() }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Hapus",
                            color = BgGraphite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
