package com.animein.app.ui.screens.detail

import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import com.animein.app.data.local.entity.ContinueWatchingEntity
import com.animein.app.ui.components.AccentPillButton
import com.animein.app.ui.components.MonoBadge
import com.animein.app.ui.components.MusikeEmptyState
import com.animein.app.ui.components.MusikeErrorState
import com.animein.app.ui.components.RadiusArt
import com.animein.app.ui.components.RadiusPanel
import com.animein.app.ui.components.RadiusTile
import com.animein.app.ui.components.ShimmerBox
import com.animein.app.ui.components.TonalPillButton
import com.animein.app.ui.components.musikeSurface
import com.animein.app.ui.theme.BgGraphite
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
import kotlinx.coroutines.delay

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBackClick: () -> Unit,
    onEpisodeClick: (String, String) -> Unit, // episodeId, animeId
    onGenreClick: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.detailState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val continueWatchingList by viewModel.continueWatchingList.collectAsState()

    val accent = LocalAccentPreset.current
    val configuration = LocalConfiguration.current
    val isTV = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    val pageBg = if (isTV) Surface1 else BgGraphite
    val horizontalPadding = if (isTV) 22.dp else 16.dp

    Scaffold(
        containerColor = pageBg,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    DetailShimmerSkeleton(isTV = isTV, horizontalPadding = horizontalPadding)
                }
                is DetailUiState.Success -> {
                    val detail = state.data
                    var isSynopsisExpanded by remember { mutableStateOf(false) }
                    var isAscendingOrder by remember { mutableStateOf(false) }
                    var allowTopBarFocus by remember { mutableStateOf(!isTV) }
                    var selectedRangeIndex by remember { mutableIntStateOf(0) }

                    val rawEpisodes = remember(detail.episodeList) {
                        (detail.episodeList ?: emptyList()).distinctBy { it.id }
                    }
                    val orderedEpisodes = remember(rawEpisodes, isAscendingOrder) {
                        if (isAscendingOrder) rawEpisodes.reversed() else rawEpisodes
                    }

                    // Range chunking for long series (> 50 episodes)
                    val rangeSize = 50
                    val episodeRanges = remember(orderedEpisodes) {
                        if (orderedEpisodes.size > rangeSize) orderedEpisodes.chunked(rangeSize)
                        else listOf(orderedEpisodes)
                    }
                    val safeRangeIndex = selectedRangeIndex.coerceIn(0, (episodeRanges.size - 1).coerceAtLeast(0))
                    val displayedEpisodes = remember(episodeRanges, safeRangeIndex) {
                        episodeRanges.getOrElse(safeRangeIndex) { emptyList() }
                    }

                    val progressByEpisodeId = remember(continueWatchingList) {
                        continueWatchingList.associateBy { AnimeIdUtils.normalizeEpisodeId(it.episodeId) }
                    }

                    val normAnimeId = remember(detail.animeId, viewModel.animeId) {
                        AnimeIdUtils.normalizeAnimeId(detail.animeId.ifBlank { viewModel.animeId })
                    }
                    val latestWatched = remember(continueWatchingList, normAnimeId, rawEpisodes) {
                        val episodeIds = rawEpisodes.map { AnimeIdUtils.normalizeEpisodeId(it.id) }.toSet()
                        continueWatchingList.firstOrNull { cw ->
                            AnimeIdUtils.normalizeAnimeId(cw.animeId) == normAnimeId ||
                                AnimeIdUtils.normalizeEpisodeId(cw.episodeId) in episodeIds
                        }
                    }
                    val firstEpisode = remember(rawEpisodes) {
                        rawEpisodes.lastOrNull { extractEpisodeNumber(it.title) == "1" }
                            ?: rawEpisodes.lastOrNull()
                    }
                    val targetWatchEpisodeId = latestWatched?.episodeId ?: firstEpisode?.id
                    val watchButtonLabel = remember(latestWatched, rawEpisodes) {
                        if (latestWatched != null) {
                            val epNum = extractEpisodeNumber(latestWatched.episodeTitle).ifEmpty {
                                rawEpisodes.firstOrNull {
                                    AnimeIdUtils.normalizeEpisodeId(it.id) ==
                                        AnimeIdUtils.normalizeEpisodeId(latestWatched.episodeId)
                                }?.let { extractEpisodeNumber(it.title) } ?: ""
                            }
                            when {
                                epNum.equals("Movie", true) || epNum.equals("OVA", true) || epNum.equals("Special", true) ->
                                    "Lanjut $epNum"
                                epNum.isNotEmpty() -> "Lanjut Ep $epNum"
                                else -> "Lanjut Tonton"
                            }
                        } else {
                            "Mulai Tonton"
                        }
                    }

                    val watchBtnFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(targetWatchEpisodeId) {
                        if (isTV) {
                            if (targetWatchEpisodeId != null) {
                                delay(80)
                                try {
                                    watchBtnFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                            }
                            delay(250)
                            allowTopBarFocus = true
                        }
                    }

                    val favHeartScale by animateFloatAsState(
                        targetValue = if (isFavorite) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "favHeartScale"
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 36.dp)
                    ) {
                        // ── 1. Clean Album-View Hero (All metadata unified once — zero duplication) ──
                        item {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = detail.thumb,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    alpha = 0.22f,
                                    modifier = Modifier.matchParentSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colorStops = arrayOf(
                                                    0.0f to accent.accent.copy(alpha = 0.14f),
                                                    0.55f to pageBg.copy(alpha = 0.80f),
                                                    1.0f to pageBg
                                                )
                                            )
                                        )
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (!isTV) Modifier.statusBarsPadding() else Modifier)
                                        .padding(
                                            start = horizontalPadding,
                                            end = horizontalPadding,
                                            top = 14.dp,
                                            bottom = 18.dp
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Minimal Top Row: Back pill only (Favorite lives cleanly in the Action Row)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        var isBackFocused by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier
                                                .height(36.dp)
                                                .musikeSurface(
                                                    shape = CircleShape,
                                                    isFocused = isBackFocused,
                                                    accentColor = accent.accent,
                                                    containerColor = Surface2.copy(alpha = 0.88f)
                                                )
                                                .focusProperties { canFocus = allowTopBarFocus }
                                                .onFocusChanged { isBackFocused = it.isFocused }
                                                .clickable { onBackClick() }
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = "Kembali",
                                                tint = if (isBackFocused) accent.accent else FgPrimary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Text(
                                                text = "Kembali",
                                                color = FgPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    // Main Hero Lockup: Uncropped Poster (Left) + Unified Metadata & Single Action Row (Right)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(if (isTV) 22.dp else 14.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(
                                                    width = if (isTV) 144.dp else 112.dp,
                                                    height = if (isTV) 204.dp else 160.dp
                                                )
                                                .clip(RoundedCornerShape(RadiusTile))
                                                .background(Surface2)
                                                .border(1.dp, HairlineStrong, RoundedCornerShape(RadiusTile))
                                        ) {
                                            AsyncImage(
                                                model = detail.thumb,
                                                contentDescription = detail.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            // Top Badges: Type, Status, Score (shown once)
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                detail.type?.takeIf { it.isNotBlank() }?.let { type ->
                                                    MonoBadge(text = type.uppercase())
                                                }
                                                detail.status?.takeIf { it.isNotBlank() }?.let { status ->
                                                    MonoBadge(text = status)
                                                }
                                                detail.score?.takeIf { it.isNotBlank() }?.let { score ->
                                                    Row(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Surface2.copy(alpha = 0.88f))
                                                            .border(1.dp, HairlineStrong, RoundedCornerShape(8.dp))
                                                            .padding(horizontal = 7.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Star,
                                                            contentDescription = null,
                                                            tint = StarYellow,
                                                            modifier = Modifier.size(11.dp)
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
                                            }

                                            // Title + Japanese Title
                                            Text(
                                                text = detail.title,
                                                color = FgPrimary,
                                                fontSize = if (isTV) 26.sp else 19.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = (-0.5).sp,
                                                lineHeight = if (isTV) 31.sp else 24.sp,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            detail.japanese?.takeIf { it.isNotBlank() }?.let { jp ->
                                                Text(
                                                    text = jp,
                                                    color = FgSecondary,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            // Single unified metadata line: Studio • Rilis (eps & durasi dihapus agar ringkas)
                                            val unifiedMeta = remember(detail) {
                                                listOfNotNull(
                                                    detail.studio?.takeIf { it.isNotBlank() },
                                                    detail.releaseDate?.takeIf { it.isNotBlank() }
                                                ).joinToString(" • ")
                                            }
                                            if (unifiedMeta.isNotBlank()) {
                                                Text(
                                                    text = unifiedMeta,
                                                    style = MonoTextStyle.copy(
                                                        color = FgSecondary,
                                                        fontSize = 11.sp,
                                                        lineHeight = 16.sp
                                                    ),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            // Unified Action Row: Play CTA + Single Favorite button
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (targetWatchEpisodeId != null) {
                                                    AccentPillButton(
                                                        text = watchButtonLabel,
                                                        icon = Icons.Default.PlayArrow,
                                                        height = if (isTV) 40.dp else 38.dp,
                                                        onClick = {
                                                            onEpisodeClick(
                                                                targetWatchEpisodeId,
                                                                detail.animeId.ifBlank { viewModel.animeId }
                                                            )
                                                        },
                                                        modifier = Modifier.focusRequester(watchBtnFocusRequester)
                                                    )
                                                }

                                                if (isTV) {
                                                    TonalPillButton(
                                                        text = if (isFavorite) "Tersimpan" else "Favorit",
                                                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                        isActive = isFavorite,
                                                        height = 40.dp,
                                                        onClick = { viewModel.toggleFavorite(detail) }
                                                    )
                                                } else {
                                                    // Compact circle Favorite button on Mobile so CTA text always has full width
                                                    var isFavFocused by remember { mutableStateOf(false) }
                                                    Box(
                                                        modifier = Modifier
                                                            .size(38.dp)
                                                            .musikeSurface(
                                                                shape = CircleShape,
                                                                isFocused = isFavFocused,
                                                                accentColor = accent.accent,
                                                                containerColor = if (isFavorite) accent.accentSoft else Surface2,
                                                                isActive = isFavorite
                                                            )
                                                            .onFocusChanged { isFavFocused = it.isFocused }
                                                            .clickable { viewModel.toggleFavorite(detail) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                            contentDescription = if (isFavorite) "Hapus dari Favorit" else "Tambah ke Favorit",
                                                            tint = if (isFavorite) accent.accent else FgPrimary,
                                                            modifier = Modifier
                                                                .size(19.dp)
                                                                .scale(favHeartScale)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── 2. Genres + Synopsis + Episode List Header (Zero redundant metadata box!) ──
                        item {
                            val synopsisText = detail.synopsis?.takeIf { it.isNotBlank() } ?: "Tidak ada sinopsis."
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Clickable Genre Chips Row
                                if (!detail.genreList.isNullOrEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        detail.genreList.forEach { genre ->
                                            GenreChipPill(
                                                label = genre.genreName,
                                                onClick = {
                                                    val gId = genre.genreId.ifBlank { genre.genreName }
                                                    onGenreClick(gId, genre.genreName)
                                                }
                                            )
                                        }
                                    }
                                }

                                // Clean Full-Width Expandable Synopsis Card
                                DetailSynopsisCard(
                                    synopsisText = synopsisText,
                                    isExpanded = isSynopsisExpanded,
                                    collapsedMaxLines = if (isTV) 4 else 3,
                                    expandThreshold = if (isTV) 260 else 140,
                                    onToggle = { isSynopsisExpanded = !isSynopsisExpanded },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Episode List Header + Sort Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Daftar Episode",
                                            color = FgPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = (-0.3).sp
                                        )
                                        MonoBadge(text = "${rawEpisodes.size}")
                                    }

                                    if (rawEpisodes.size > 1) {
                                        TonalPillButton(
                                            text = if (isAscendingOrder) "Ep 1 ▲" else "Terbaru ▼",
                                            height = 32.dp,
                                            onClick = {
                                                isAscendingOrder = !isAscendingOrder
                                                selectedRangeIndex = 0
                                            }
                                        )
                                    }
                                }

                                // Range Selector Chips for long anime (> 50 episodes)
                                if (episodeRanges.size > 1) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        itemsIndexed(episodeRanges) { idx, chunk ->
                                            val startNum = idx * rangeSize + 1
                                            val endNum = startNum + chunk.size - 1
                                            val isSelected = idx == safeRangeIndex
                                            var isFocused by remember { mutableStateOf(false) }

                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            isSelected -> accent.accent
                                                            isFocused -> Surface3
                                                            else -> Surface2
                                                        },
                                                        CircleShape
                                                    )
                                                    .border(
                                                        width = if (isFocused) 2.dp else 1.dp,
                                                        color = if (isFocused) Color.White else if (isSelected) Color.Transparent else Hairline,
                                                        shape = CircleShape
                                                    )
                                                    .onFocusChanged { isFocused = it.isFocused }
                                                    .clickable { selectedRangeIndex = idx }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "$startNum–$endNum",
                                                    style = MonoTextStyle.copy(
                                                        color = if (isSelected) accent.onAccent else FgPrimary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                if (displayedEpisodes.isEmpty()) {
                                    MusikeEmptyState(
                                        title = "Episode belum tersedia",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }

                        // ── 3. Episode Rows (3-col Grid on TV, Tracklist Rows on Mobile) ──
                        if (isTV) {
                            val episodeRows = displayedEpisodes.chunked(3)
                            itemsIndexed(episodeRows, key = { idx, row -> "${idx}_${row.first().id}" }) { _, rowEpisodes ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = horizontalPadding, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowEpisodes.forEach { episode ->
                                        val epNum = extractEpisodeNumber(episode.title)
                                        val displayTitle = if (epNum.isNotEmpty()) "Episode $epNum" else AnimeIdUtils.formatEpisodeLabel(episode.title)
                                        val normEpId = AnimeIdUtils.normalizeEpisodeId(episode.id)
                                        val cwEntity = progressByEpisodeId[normEpId]
                                        val isLatest = latestWatched != null &&
                                            AnimeIdUtils.normalizeEpisodeId(latestWatched.episodeId) == normEpId

                                        EpisodeRow(
                                            indexBadge = epNum.ifEmpty { "•" },
                                            title = displayTitle,
                                            date = episode.uploadedOn ?: "",
                                            progressEntity = cwEntity,
                                            isCurrentWatched = isLatest,
                                            onClick = { onEpisodeClick(episode.id, detail.animeId.ifBlank { viewModel.animeId }) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    val emptySlots = 3 - rowEpisodes.size
                                    if (emptySlots > 0) {
                                        repeat(emptySlots) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(displayedEpisodes, key = { idx, ep -> "${idx}_${ep.id}" }) { _, episode ->
                                val epNum = extractEpisodeNumber(episode.title)
                                val displayTitle = if (epNum.isNotEmpty()) "Episode $epNum" else AnimeIdUtils.formatEpisodeLabel(episode.title)
                                val normEpId = AnimeIdUtils.normalizeEpisodeId(episode.id)
                                val cwEntity = progressByEpisodeId[normEpId]
                                val isLatest = latestWatched != null &&
                                    AnimeIdUtils.normalizeEpisodeId(latestWatched.episodeId) == normEpId

                                Box(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp)) {
                                    EpisodeRow(
                                        indexBadge = epNum.ifEmpty { "•" },
                                        title = displayTitle,
                                        date = episode.uploadedOn ?: "",
                                        progressEntity = cwEntity,
                                        isCurrentWatched = isLatest,
                                        onClick = { onEpisodeClick(episode.id, detail.animeId.ifBlank { viewModel.animeId }) }
                                    )
                                }
                            }
                        }
                    }
                }
                is DetailUiState.Error -> {
                    MusikeErrorState(
                        message = state.message,
                        onRetry = { viewModel.fetchDetail() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreChipPill(
    label: String,
    onClick: () -> Unit
) {
    val accent = LocalAccentPreset.current
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .musikeSurface(
                shape = CircleShape,
                isFocused = isFocused,
                accentColor = accent.accent,
                containerColor = Surface2,
                focusedContainerColor = Surface3
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isFocused) accent.accent else FgPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun DetailSynopsisCard(
    synopsisText: String,
    isExpanded: Boolean,
    collapsedMaxLines: Int,
    expandThreshold: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentPreset.current
    var isFocused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(RadiusPanel)
    val canExpand = synopsisText.length > expandThreshold

    Column(
        modifier = modifier
            .musikeSurface(
                shape = cardShape,
                isFocused = isFocused,
                accentColor = accent.accent,
                containerColor = Surface2,
                focusedContainerColor = Surface3
            )
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (canExpand) Modifier.clickable { onToggle() } else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sinopsis",
                color = FgPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (canExpand) {
                Text(
                    text = if (isExpanded) "Sembunyikan ▲" else "Selengkapnya ▼",
                    color = accent.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text(
            text = synopsisText,
            color = FgSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = if (isExpanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EpisodeRow(
    indexBadge: String,
    title: String,
    date: String,
    progressEntity: ContinueWatchingEntity?,
    isCurrentWatched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentPreset.current
    var isFocused by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(RadiusTile)

    val progressFraction = remember(progressEntity) {
        if (progressEntity != null && progressEntity.duration > 0L) {
            (progressEntity.progress.toFloat() / progressEntity.duration.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .musikeSurface(
                shape = rowShape,
                isFocused = isFocused,
                accentColor = accent.accent,
                containerColor = if (isCurrentWatched) Surface3 else Surface2,
                focusedContainerColor = Surface3,
                isActive = isCurrentWatched
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(RadiusArt))
                    .background(if (isCurrentWatched || isFocused) accent.accentSoft else Surface1)
                    .border(
                        width = 1.dp,
                        color = if (isCurrentWatched || isFocused) accent.accent.copy(alpha = 0.5f) else Hairline,
                        shape = RoundedCornerShape(RadiusArt)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = indexBadge.take(4),
                    style = MonoTextStyle.copy(
                        color = if (isCurrentWatched || isFocused) accent.accent else FgSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isCurrentWatched || isFocused) accent.accent else FgPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (isCurrentWatched || isFocused) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subText = remember(date, progressFraction, isCurrentWatched) {
                    val pct = (progressFraction * 100).toInt()
                    when {
                        pct in 1..95 -> if (date.isNotBlank()) "Sedang ditonton ($pct%) • $date" else "Sedang ditonton ($pct%)"
                        pct > 95 -> if (date.isNotBlank()) "Selesai • $date" else "Selesai ditonton"
                        isCurrentWatched -> if (date.isNotBlank()) "Terakhir ditonton • $date" else "Terakhir ditonton"
                        else -> date
                    }
                }
                if (subText.isNotBlank()) {
                    Text(
                        text = subText,
                        color = if (progressFraction > 0f) accent.accent.copy(alpha = 0.85f) else FgTertiary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (isFocused) accent.accent else Surface1)
                    .border(
                        width = 1.dp,
                        color = if (isFocused) Color.Transparent else Hairline,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Putar",
                    tint = if (isFocused) accent.onAccent else FgPrimary,
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

@Composable
private fun DetailShimmerSkeleton(
    isTV: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!isTV) Modifier.statusBarsPadding() else Modifier)
            .padding(horizontal = horizontalPadding, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            ShimmerBox(
                modifier = Modifier.size(
                    width = if (isTV) 144.dp else 112.dp,
                    height = if (isTV) 204.dp else 160.dp
                ),
                shape = RoundedCornerShape(RadiusTile)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShimmerBox(modifier = Modifier.width(120.dp).height(18.dp), shape = RoundedCornerShape(6.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.85f).height(24.dp), shape = RoundedCornerShape(6.dp))
                ShimmerBox(modifier = Modifier.width(160.dp).height(14.dp), shape = RoundedCornerShape(6.dp))
                ShimmerBox(modifier = Modifier.width(150.dp).height(38.dp), shape = CircleShape)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) {
                ShimmerBox(modifier = Modifier.width(68.dp).height(28.dp), shape = CircleShape)
            }
        }
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(84.dp), shape = RoundedCornerShape(RadiusPanel))
        repeat(5) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(RadiusTile))
        }
    }
}

private fun extractEpisodeNumber(title: String): String {
    val cleaned = title
        .replace(Regex("\\[.*?\\]"), "")
        .replace(Regex("\\(\\d+p\\)"), "")
        .replace(Regex("\\(BD\\)", RegexOption.IGNORE_CASE), "")
        .replace("_", " ")
        .trim()

    val epMatch = Regex("(?i)(?:episode|ep)[_\\s]*(\\d+)").find(cleaned)
    if (epMatch != null) return epMatch.groupValues[1]

    if (cleaned.contains("Movie", ignoreCase = true)) return "Movie"
    if (cleaned.contains("OVA", ignoreCase = true)) return "OVA"
    if (cleaned.contains("Special", ignoreCase = true)) return "Special"

    val nums = Regex("\\d+").findAll(cleaned)
        .map { it.value.toIntOrNull() ?: 0 }
        .filter { it in 1..999 }
        .toList()
    if (nums.isNotEmpty()) return nums.last().toString()

    return cleaned.take(20).trim()
}
