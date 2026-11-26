package com.animein.app.ui.screens.animelist

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animein.app.data.model.AnimeListItem
import com.animein.app.ui.components.MonoBadge
import com.animein.app.ui.components.MusikeEmptyState
import com.animein.app.ui.components.MusikeErrorState
import com.animein.app.ui.components.RadiusTile
import com.animein.app.ui.components.ShimmerBox
import com.animein.app.ui.components.musikePosterFrame
import com.animein.app.ui.components.musikeSurface
import com.animein.app.ui.theme.BgGraphite
import com.animein.app.ui.theme.FgPrimary
import com.animein.app.ui.theme.HairlineStrong
import com.animein.app.ui.theme.LocalAccentPreset
import com.animein.app.ui.theme.MonoTextStyle
import com.animein.app.ui.theme.StarYellow
import com.animein.app.ui.theme.Surface1
import com.animein.app.ui.theme.Surface2
import com.animein.app.util.AnimeIdUtils

@Composable
fun AnimeListScreen(
    viewModel: AnimeListViewModel,
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val accent = LocalAccentPreset.current
    val configuration = LocalConfiguration.current
    val isTV = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    val pageBg = if (isTV) Surface1 else BgGraphite
    val horizontalPadding = if (isTV) 20.dp else 16.dp
    val totalCount = (uiState as? AnimeListUiState.Success)?.list?.size

    Scaffold(
        containerColor = pageBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBg)
                    .then(if (!isTV) Modifier.statusBarsPadding() else Modifier)
                    .padding(horizontal = horizontalPadding, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var isBackFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .musikeSurface(
                            shape = CircleShape,
                            isFocused = isBackFocused,
                            accentColor = accent.accent,
                            containerColor = Surface2
                        )
                        .onFocusChanged { isBackFocused = it.isFocused }
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = if (isBackFocused) accent.accent else FgPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = viewModel.listTitle,
                    color = FgPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (totalCount != null && totalCount > 0) {
                    MonoBadge(text = "$totalCount anime")
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
                is AnimeListUiState.Loading -> {
                    ListGridShimmerSkeleton(isTV = isTV, horizontalPadding = horizontalPadding)
                }
                is AnimeListUiState.Success -> {
                    val list = remember(state.list) { state.list.distinctBy { it.id } }
                    if (list.isEmpty()) {
                        MusikeEmptyState(
                            title = "Tidak ada anime ditemukan",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = if (isTV) GridCells.Fixed(5) else GridCells.Fixed(2),
                            contentPadding = PaddingValues(
                                start = horizontalPadding,
                                end = horizontalPadding,
                                top = 6.dp,
                                bottom = 28.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(list, key = { it.id }) { anime ->
                                ListAnimeCard(
                                    anime = anime,
                                    isTV = isTV,
                                    onClick = { onAnimeClick(anime.id) }
                                )
                            }
                        }
                    }
                }
                is AnimeListUiState.Error -> {
                    MusikeErrorState(
                        message = state.message,
                        onRetry = { viewModel.fetchAnimeList(forceRefresh = true) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun ListAnimeCard(
    anime: AnimeListItem,
    isTV: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentPreset.current
    var isFocused by remember { mutableStateOf(false) }
    val formattedEpisode = remember(anime.episode) {
        AnimeIdUtils.formatEpisodeLabel(anime.episode)
    }

    Column(
        modifier = modifier
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
            ) { onClick() }
            .padding(if (isTV) 6.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTV) 204.dp else 206.dp)
                .musikePosterFrame(
                    shape = RoundedCornerShape(RadiusTile),
                    isFocused = false,
                    accentColor = accent.accent
                )
        ) {
            AsyncImage(
                model = anime.thumb,
                contentDescription = anime.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            anime.score?.takeIf { it.isNotBlank() }?.let { score ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgGraphite.copy(alpha = 0.86f))
                        .border(1.dp, HairlineStrong, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = StarYellow,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = score,
                        style = MonoTextStyle.copy(
                            color = FgPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            if (formattedEpisode.isNotBlank()) {
                MonoBadge(
                    text = formattedEpisode,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(7.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = anime.displayTitle,
            color = if (isFocused) accent.accent else FgPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ListGridShimmerSkeleton(
    isTV: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    val cols = if (isTV) 5 else 2
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(cols) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        ShimmerBox(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            shape = RoundedCornerShape(RadiusTile)
                        )
                        ShimmerBox(
                            modifier = Modifier.fillMaxWidth(0.75f).height(12.dp),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                }
            }
        }
    }
}
