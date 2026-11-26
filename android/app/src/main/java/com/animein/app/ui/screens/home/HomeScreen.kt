package com.animein.app.ui.screens.home

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.animein.app.data.local.entity.ContinueWatchingEntity
import com.animein.app.data.model.AnimeCompleted
import com.animein.app.data.model.AnimeOngoing
import com.animein.app.data.model.SearchItem
import com.animein.app.ui.components.BrandLogoMark
import com.animein.app.ui.components.MonoBadge
import com.animein.app.ui.components.MusikeEmptyState
import com.animein.app.ui.components.MusikeErrorState
import com.animein.app.ui.components.RadiusArt
import com.animein.app.ui.components.RadiusPanel
import com.animein.app.ui.components.RadiusTile
import com.animein.app.ui.components.ShimmerBox
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
import com.animein.app.ui.theme.LocalOpenAppearanceSettings
import com.animein.app.ui.theme.MonoTextStyle
import com.animein.app.ui.theme.StarYellow
import com.animein.app.ui.theme.Surface1
import com.animein.app.ui.theme.Surface2
import com.animein.app.ui.theme.Surface3
import com.animein.app.ui.theme.Surface4
import com.animein.app.util.AnimeIdUtils
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAnimeClick: (String) -> Unit,
    onSeeAllClick: (String, String) -> Unit,
    onEpisodePlay: (String, String) -> Unit, // episodeId, animeId
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.homeState.collectAsState()
    val continueWatchingList by viewModel.continueWatchingList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val activeSource by viewModel.activeSource.collectAsState()

    val accent = LocalAccentPreset.current
    val openAppearanceSettings = LocalOpenAppearanceSettings.current

    val configuration = LocalConfiguration.current
    val isTV = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    val pageBg = if (isTV) Surface1 else BgGraphite

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 4..10 -> "Selamat pagi"
            in 11..14 -> "Selamat siang"
            in 15..17 -> "Selamat sore"
            else -> "Selamat malam"
        }
    }

    var allowSearchFocus by remember { mutableStateOf(!isTV) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var isSearchEditing by remember { mutableStateOf(false) }
    val searchBoxFocusRequester = remember { FocusRequester() }
    val searchInputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (isTV) {
            delay(400)
            allowSearchFocus = true
        }
    }

    LaunchedEffect(isSearchEditing) {
        if (isTV && isSearchEditing) {
            delay(30)
            try {
                searchInputFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        containerColor = pageBg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBg)
                    .then(if (!isTV) Modifier.statusBarsPadding() else Modifier)
                    .padding(
                        horizontal = if (isTV) 20.dp else 16.dp,
                        vertical = if (isTV) 14.dp else 10.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isTV) {
                    // Mobile header row: Brand mark + App name | Refresh + Appearance settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BrandLogoMark(size = 24.dp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Aura",
                                    color = FgPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.4).sp
                                )
                                Text(
                                    text = "Nime",
                                    color = accent.accent,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.4).sp
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Refresh Home button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .musikeSurface(
                                        shape = CircleShape,
                                        accentColor = accent.accent,
                                        containerColor = Surface2
                                    )
                                    .clickable { viewModel.fetchHome(forceRefresh = true) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Muat Ulang",
                                    tint = FgSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Appearance / Accent Picker button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .musikeSurface(
                                        shape = CircleShape,
                                        accentColor = accent.accent,
                                        containerColor = Surface2
                                    )
                                    .clickable { openAppearanceSettings() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Tampilan & Aksen",
                                    tint = accent.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Search Pill + Server Selector Pill row (TV also shows greeting on the left)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isTV) {
                        Text(
                            text = greeting,
                            color = FgPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.6).sp,
                            maxLines = 1,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Search Pill
                    Box(
                        modifier = Modifier
                            .then(
                                if (isTV) Modifier.width(300.dp) else Modifier.weight(0.70f)
                            )
                            .height(42.dp)
                            .musikeSurface(
                                shape = CircleShape,
                                isFocused = isSearchFocused || isSearchEditing,
                                accentColor = accent.accent,
                                containerColor = Surface2,
                                focusedContainerColor = Surface3
                            )
                            .then(
                                if (isTV) {
                                    Modifier
                                        .focusRequester(searchBoxFocusRequester)
                                        .focusProperties { canFocus = allowSearchFocus && !isSearchEditing }
                                        .onFocusChanged { isSearchFocused = it.isFocused }
                                        .clickable(enabled = allowSearchFocus && !isSearchEditing) {
                                            isSearchEditing = true
                                        }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            enabled = !isTV || isSearchEditing,
                            textStyle = TextStyle(color = FgPrimary, fontSize = 14.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(accent.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    if (isTV) {
                                        isSearchEditing = false
                                        try {
                                            searchBoxFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(searchInputFocusRequester)
                                .focusProperties { canFocus = if (isTV) isSearchEditing else true }
                                .onKeyEvent { event ->
                                    if (isTV && isSearchEditing && event.type == KeyEventType.KeyDown &&
                                        (event.key == Key.Back || event.key == Key.Escape || event.key == Key.DirectionDown)
                                    ) {
                                        isSearchEditing = false
                                        keyboardController?.hide()
                                        try {
                                            searchBoxFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                        true
                                    } else false
                                }
                                .onFocusChanged { state ->
                                    if (!isTV) {
                                        isSearchFocused = state.isFocused
                                    } else if (!state.isFocused && isSearchEditing) {
                                        isSearchEditing = false
                                        keyboardController?.hide()
                                    }
                                },
                            decorationBox = { innerTextField ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Cari",
                                        tint = if (isSearchFocused || isSearchEditing) accent.accent else FgTertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Cari judul anime...",
                                                color = FgTertiary,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.onSearchQueryChanged("") },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Hapus",
                                                tint = FgSecondary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }

                    // Server Selector Pill
                    ServerSelectorPill(
                        activeSource = activeSource,
                        onSelectSource = { viewModel.setActiveSource(it) },
                        modifier = Modifier
                            .then(if (isTV) Modifier.width(114.dp) else Modifier.weight(0.30f))
                            .height(42.dp)
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
            if (searchQuery.isEmpty()) {
                when (val state = uiState) {
                    is HomeUiState.Loading -> {
                        HomeShimmerSkeleton(isTV = isTV)
                    }
                    is HomeUiState.Success -> {
                        val ongoing = remember(state.data.home.ongoing) {
                            state.data.home.ongoing.distinctBy { it.id }
                        }
                        val complete = remember(state.data.home.complete) {
                            state.data.home.complete.distinctBy { it.id }
                        }
                        val featuredItems = remember(state.data.home.featured, ongoing) {
                            val hotOrPopular = state.data.home.featured.orEmpty().distinctBy { it.id }
                            if (hotOrPopular.isNotEmpty()) hotOrPopular.take(8) else ongoing.take(6)
                        }
                        val horizontalPadding = if (isTV) 20.dp else 16.dp

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = if (isTV) 28.dp else 92.dp)
                        ) {
                            // Dynamic Greeting on Mobile (TV already displays greeting in topBar)
                            if (!isTV) {
                                item(key = "greeting") {
                                    Text(
                                        text = greeting,
                                        color = FgPrimary,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.6).sp,
                                        modifier = Modifier.padding(
                                            start = horizontalPadding,
                                            end = horizontalPadding,
                                            top = 2.dp,
                                            bottom = 12.dp
                                        )
                                    )
                                }
                            }

                            // 1. Featured Spotlight Carousel (up to 8 hot/trending/popular items)
                            if (featuredItems.isNotEmpty()) {
                                item(key = "featured_banner") {
                                    FeaturedSpotlightBanner(
                                        items = featuredItems,
                                        isTV = isTV,
                                        onAnimeClick = onAnimeClick,
                                        modifier = Modifier.padding(horizontal = horizontalPadding)
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }

                            // 2. Quick Shortcuts / Lanjut Nonton Grid (a-music signature 2/3-col compact tiles)
                            if (continueWatchingList.isNotEmpty()) {
                                item(key = "continue_watching") {
                                    SectionHeader(
                                        title = "Lanjut Nonton",
                                        count = continueWatchingList.size,
                                        horizontalPadding = horizontalPadding
                                    )
                                    ContinueWatchingQuickGrid(
                                        items = continueWatchingList.take(if (isTV) 6 else 4),
                                        isTV = isTV,
                                        horizontalPadding = horizontalPadding,
                                        onItemClick = { item -> onEpisodePlay(item.episodeId, item.animeId) },
                                        onDeleteClick = { item -> viewModel.deleteContinueWatching(item.episodeId) }
                                    )
                                    Spacer(modifier = Modifier.height(22.dp))
                                }
                            }

                            // 3. Ongoing Anime Section
                            if (ongoing.isNotEmpty()) {
                                item(key = "ongoing_section") {
                                    SectionHeader(
                                        title = "Sedang Tayang",
                                        count = ongoing.size,
                                        horizontalPadding = horizontalPadding,
                                        onSeeAllClick = { onSeeAllClick("ongoing", "Anime Ongoing") }
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = horizontalPadding),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(ongoing, key = { it.id }) { anime ->
                                            OngoingAnimeCard(
                                                anime = anime,
                                                isTV = isTV,
                                                onClick = { onAnimeClick(anime.id) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }

                            // 4. Completed Anime Section
                            if (complete.isNotEmpty()) {
                                item(key = "complete_section") {
                                    SectionHeader(
                                        title = "Anime Tamat",
                                        count = complete.size,
                                        horizontalPadding = horizontalPadding,
                                        onSeeAllClick = { onSeeAllClick("complete", "Anime Tamat") }
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = horizontalPadding),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(complete, key = { it.id }) { anime ->
                                            CompletedAnimeCard(
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
                    is HomeUiState.Error -> {
                        MusikeErrorState(
                            message = state.message,
                            onRetry = { viewModel.fetchHome(forceRefresh = true) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                // Search Results Area
                when (val state = searchState) {
                    is SearchUiState.Idle -> {}
                    is SearchUiState.Loading -> {
                        SearchShimmerSkeleton(isTV = isTV)
                    }
                    is SearchUiState.Success -> {
                        val results = remember(state.results) { state.results.distinctBy { it.id } }
                        if (results.isEmpty()) {
                            MusikeEmptyState(
                                title = "Tidak ada hasil ditemukan",
                                subtitle = "Coba kata kunci lain atau ganti server di pojok kanan atas.",
                                icon = Icons.Default.Search,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = if (isTV) 20.dp else 16.dp,
                                    end = if (isTV) 20.dp else 16.dp,
                                    top = 8.dp,
                                    bottom = if (isTV) 24.dp else 92.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Hasil Pencarian",
                                            color = FgPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        MonoBadge(text = "${results.size} anime")
                                    }
                                }
                                items(results, key = { it.id }) { anime ->
                                    SearchResultCard(
                                        anime = anime,
                                        onClick = { onAnimeClick(AnimeIdUtils.normalizeAnimeId(anime.id)) }
                                    )
                                }
                            }
                        }
                    }
                    is SearchUiState.Error -> {
                        MusikeErrorState(
                            message = state.message,
                            onRetry = { viewModel.retrySearch() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerSelectorPill(
    activeSource: String,
    onSelectSource: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentPreset.current
    val density = LocalDensity.current
    var showDropdown by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val activeLabel = when (activeSource) {
        "animeinweb" -> "Server 1"
        "otakudesu" -> "Server 2"
        else -> "Server 1"
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .musikeSurface(
                    shape = CircleShape,
                    isFocused = isFocused,
                    accentColor = accent.accent,
                    containerColor = Surface2,
                    focusedContainerColor = Surface3
                )
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { showDropdown = !showDropdown }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent.accent)
                )
                Text(
                    text = activeLabel,
                    style = MonoTextStyle.copy(
                        color = FgPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = FgSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (showDropdown) {
            val yOffsetPx = with(density) { 50.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(x = 0, y = yOffsetPx),
                onDismissRequest = { showDropdown = false },
                properties = PopupProperties(focusable = true)
            ) {
                val popupShape = RoundedCornerShape(14.dp)
                Column(
                    modifier = Modifier
                        .width(148.dp)
                        .clip(popupShape)
                        .background(Surface2, popupShape)
                        .border(1.dp, HairlineStrong, popupShape)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "animeinweb" to "Server 1",
                        "otakudesu" to "Server 2"
                    ).forEach { (id, label) ->
                        val isSelected = activeSource == id
                        var isItemFocused by remember { mutableStateOf(false) }
                        val itemShape = RoundedCornerShape(10.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .background(
                                    when {
                                        isItemFocused -> Surface3
                                        isSelected -> accent.accentSoft
                                        else -> Color.Transparent
                                    },
                                    itemShape
                                )
                                .then(
                                    if (isItemFocused) Modifier.border(2.dp, accent.accent, itemShape)
                                    else if (isSelected) Modifier.border(1.dp, accent.accent.copy(alpha = 0.45f), itemShape)
                                    else Modifier
                                )
                                .onFocusChanged { isItemFocused = it.isFocused }
                                .clickable {
                                    onSelectSource(id)
                                    showDropdown = false
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) accent.accent else FgTertiary.copy(alpha = 0.4f))
                                )
                                Text(
                                    text = label,
                                    color = if (isSelected) accent.accent else FgPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected || isItemFocused) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Swipeable Featured Spotlight Carousel (HorizontalPager) displaying Hot / Trending / Popular
 * anime from the active source. Swipe directly on the banner image on Mobile, or use D-pad
 * Left/Right while focused on Android TV.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeaturedSpotlightBanner(
    items: List<AnimeOngoing>,
    isTV: Boolean,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentPreset.current
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    var isBannerFocused by remember { mutableStateOf(false) }
    var tvTargetPage by remember(items) { mutableIntStateOf(0) }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            tvTargetPage = pagerState.currentPage
        }
    }

    // Auto-advance smoothly when not focused and not being swiped
    LaunchedEffect(items.size, isBannerFocused, pagerState.isScrollInProgress) {
        if (items.size > 1 && !isBannerFocused && !pagerState.isScrollInProgress) {
            delay(6000L)
            val next = (pagerState.currentPage + 1) % items.size
            tvTargetPage = next
            pagerState.animateScrollToPage(next)
        }
    }

    val bannerShape = RoundedCornerShape(RadiusPanel)
    val bannerHeight = if (isTV) 224.dp else 210.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight)
                .clip(bannerShape)
                .border(
                    width = if (isBannerFocused) 2.5.dp else 1.dp,
                    color = if (isBannerFocused) accent.accent else Hairline,
                    shape = bannerShape
                )
                .then(
                    if (isTV) {
                        Modifier
                            .onFocusChanged { isBannerFocused = it.isFocused || it.hasFocus }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionRight -> {
                                            if (tvTargetPage < items.lastIndex) {
                                                val next = tvTargetPage + 1
                                                tvTargetPage = next
                                                scope.launch { pagerState.animateScrollToPage(next) }
                                                true
                                            } else false
                                        }
                                        Key.DirectionLeft -> {
                                            if (tvTargetPage > 0) {
                                                val prev = tvTargetPage - 1
                                                tvTargetPage = prev
                                                scope.launch { pagerState.animateScrollToPage(prev) }
                                                true
                                            } else false // let edge Left return focus immediately to TV sidebar
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                items.getOrNull(tvTargetPage.coerceIn(0, items.lastIndex))?.let { onAnimeClick(it.id) }
                            }
                    } else {
                        Modifier
                    }
                )
        ) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            userScrollEnabled = !isTV,
            beyondBoundsPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val anime = items[page]
            val formattedEpisode = remember(anime.episode) {
                AnimeIdUtils.formatEpisodeLabel(anime.episode)
            }
            val backdropModel = anime.cover?.takeIf { it.isNotBlank() } ?: anime.thumb
            val tagText = anime.bannerTag?.takeIf { it.isNotBlank() } ?: "HOT MINGGU INI"

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Surface2)
                    .then(
                        if (!isTV) Modifier.clickable { onAnimeClick(anime.id) } else Modifier
                    )
            ) {
                // Background artwork (prefer widescreen cover if available)
                AsyncImage(
                    model = backdropModel,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark graphite horizontal + vertical scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.0f to BgGraphite.copy(alpha = 0.94f),
                                    0.55f to BgGraphite.copy(alpha = 0.78f),
                                    1.0f to BgGraphite.copy(alpha = 0.35f)
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    accent.accent.copy(alpha = 0.08f),
                                    Color.Transparent,
                                    BgGraphite.copy(alpha = 0.90f)
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isTV) 20.dp else 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Crisp portrait poster on the left
                    Box(
                        modifier = Modifier
                            .width(if (isTV) 124.dp else 104.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(RadiusTile))
                            .background(Surface3)
                            .border(1.dp, HairlineStrong, RoundedCornerShape(RadiusTile))
                    ) {
                        AsyncImage(
                            model = anime.thumb,
                            contentDescription = anime.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Metadata & CTA on the right
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MonoBadge(
                                text = tagText,
                                isAccent = true
                            )
                            if (formattedEpisode.isNotBlank()) {
                                MonoBadge(text = formattedEpisode)
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = anime.title,
                                color = FgPrimary,
                                fontSize = if (isTV) 21.sp else 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.4).sp,
                                lineHeight = if (isTV) 26.sp else 22.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitleText = anime.uploadedOn?.takeIf { it.isNotBlank() }
                                ?: "Pilihan terpopuler minggu ini"
                            Text(
                                text = subtitleText,
                                style = MonoTextStyle.copy(
                                    color = FgSecondary,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(CircleShape)
                                .background(accent.accent)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = accent.onAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Tonton Sekarang",
                                color = accent.onAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        }

        // Interactive indicator pills synced with HorizontalPager
        if (items.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, _ ->
                    val selected = index == pagerState.currentPage
                    val pillWidth by animateDpAsState(
                        targetValue = if (selected) 22.dp else 6.dp,
                        animationSpec = tween(220),
                        label = "bannerDotWidth"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(pillWidth)
                            .clip(CircleShape)
                            .background(if (selected) accent.accent else Surface4)
                            .then(
                                if (!isTV) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                } else Modifier
                            )
                    )
                }
            }
        }
    }
}

/**
 * Compact 2-column (Mobile) / 3-column (TV) Quick Shortcuts Grid for Continue Watching,
 * directly inspired by a-music's home quick-access tiles.
 */
@Composable
private fun ContinueWatchingQuickGrid(
    items: List<ContinueWatchingEntity>,
    isTV: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onItemClick: (ContinueWatchingEntity) -> Unit,
    onDeleteClick: (ContinueWatchingEntity) -> Unit
) {
    val columns = if (isTV) 3 else 2
    val rows = remember(items, columns) { items.chunked(columns) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    ContinueWatchingQuickTile(
                        item = item,
                        onClick = { onItemClick(item) },
                        onDeleteClick = { onDeleteClick(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
                val emptySlots = columns - rowItems.size
                if (emptySlots > 0) {
                    repeat(emptySlots) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingQuickTile(
    item: ContinueWatchingEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentPreset.current
    val progressPercent = remember(item.progress, item.duration) {
        if (item.duration > 0L) (item.progress.toFloat() / item.duration.toFloat()).coerceIn(0f, 1f)
        else 0f
    }
    val episodeLabel = remember(item.episodeTitle) {
        val cleaned = item.episodeTitle
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(\\d+p\\)"), "")
            .replace("_", " ").trim()
        val epMatch = Regex("(?i)(?:episode|ep)[_ ]*(\\d+)").find(cleaned)
        if (epMatch != null) "Ep ${epMatch.groupValues[1]}"
        else {
            val nums = Regex("\\d+").findAll(cleaned)
                .map { it.value.toIntOrNull() ?: 0 }
                .filter { it in 1..999 }
                .toList()
            if (nums.isNotEmpty()) "Ep ${nums.last()}" else "Lanjut"
        }
    }

    var showDeleteOverlay by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val tileShape = RoundedCornerShape(RadiusArt)

    Box(
        modifier = modifier
            .height(58.dp)
            .musikeSurface(
                shape = tileShape,
                isFocused = isFocused,
                accentColor = accent.accent,
                containerColor = Surface2,
                focusedContainerColor = Surface3
            )
            .onFocusChanged { isFocused = it.isFocused }
            .combinedClickable(
                onClick = {
                    if (showDeleteOverlay) showDeleteOverlay = false else onClick()
                },
                onLongClick = { showDeleteOverlay = true }
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left flush square artwork (ala a-music shortcut tile)
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(Surface3),
                contentAlignment = Alignment.Center
            ) {
                if (item.thumb.isNotEmpty()) {
                    AsyncImage(
                        model = item.thumb,
                        contentDescription = item.animeTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.28f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accent.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = accent.onAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = accent.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Title + Mono episode tag
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.animeTitle.ifBlank { "Anime" },
                    color = FgPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = episodeLabel,
                    style = MonoTextStyle.copy(
                        color = accent.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }

        // Thin accent progress bar at bottom edge
        if (progressPercent > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressPercent)
                        .background(accent.accent)
                )
            }
        }

        // Long-press delete confirmation overlay
        if (showDeleteOverlay) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgGraphite.copy(alpha = 0.92f))
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            onDeleteClick()
                            showDeleteOverlay = false
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus",
                        tint = DangerCoral,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Hapus",
                        color = DangerCoral,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Batal",
                    tint = FgSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { showDeleteOverlay = false }
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentPreset.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = FgPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            if (count != null && count > 0) {
                Text(
                    text = count.toString(),
                    style = MonoTextStyle.copy(
                        color = FgTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        if (onSeeAllClick != null) {
            var isFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(
                        if (isFocused) {
                            Modifier
                                .background(Surface3, CircleShape)
                                .border(2.dp, accent.accent, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickable { onSeeAllClick() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Lihat semua",
                    color = if (isFocused) accent.accent else FgSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun OngoingAnimeCard(
    anime: AnimeOngoing,
    isTV: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentPreset.current
    var isFocused by remember { mutableStateOf(false) }
    val formattedEpisode = remember(anime.episode) {
        AnimeIdUtils.formatEpisodeLabel(anime.episode)
    }
    val cardWidth = if (isTV) 156.dp else 136.dp
    val posterHeight = if (isTV) 204.dp else 184.dp
    val cardPadding = if (isTV) 6.dp else 2.dp

    Column(
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isFocused) {
                    Modifier
                        .background(Surface2, RoundedCornerShape(16.dp))
                        .border(2.5.dp, accent.accent, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(cardPadding)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterHeight)
                .musikePosterFrame(
                    shape = RoundedCornerShape(RadiusTile),
                    isFocused = false,
                    accentColor = accent.accent
                )
        ) {
            AsyncImage(
                model = anime.thumb,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
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
            text = anime.title,
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
fun CompletedAnimeCard(
    anime: AnimeCompleted,
    isTV: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentPreset.current
    var isFocused by remember { mutableStateOf(false) }
    val formattedEpisode = remember(anime.episode) {
        AnimeIdUtils.formatEpisodeLabel(anime.episode)
    }
    val cardWidth = if (isTV) 156.dp else 136.dp
    val posterHeight = if (isTV) 204.dp else 184.dp
    val cardPadding = if (isTV) 6.dp else 2.dp

    Column(
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isFocused) {
                    Modifier
                        .background(Surface2, RoundedCornerShape(16.dp))
                        .border(2.5.dp, accent.accent, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(cardPadding)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterHeight)
                .musikePosterFrame(
                    shape = RoundedCornerShape(RadiusTile),
                    isFocused = false,
                    accentColor = accent.accent
                )
        ) {
            AsyncImage(
                model = anime.thumb,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            anime.score?.let { score ->
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
                        text = score.toString(),
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
            text = anime.title,
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
fun SearchResultCard(
    anime: SearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentPreset.current
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .musikeSurface(
                shape = RoundedCornerShape(RadiusTile),
                isFocused = isFocused,
                accentColor = accent.accent,
                containerColor = Surface2,
                focusedContainerColor = Surface3
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = anime.thumb,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 56.dp, height = 78.dp)
                .clip(RoundedCornerShape(RadiusArt))
                .background(Surface3)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = anime.title,
                color = if (isFocused) accent.accent else FgPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                anime.status?.takeIf { it.isNotBlank() }?.let { status ->
                    MonoBadge(text = status)
                }
                anime.score?.takeIf { it.isNotBlank() }?.let { score ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = StarYellow,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = score,
                            style = MonoTextStyle.copy(
                                color = FgPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Musike-style Shimmer Skeleton for Home loading state.
 */
@Composable
private fun HomeShimmerSkeleton(isTV: Boolean) {
    val horizontalPadding = if (isTV) 20.dp else 16.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (!isTV) {
            ShimmerBox(
                modifier = Modifier
                    .width(160.dp)
                    .height(26.dp),
                shape = RoundedCornerShape(8.dp)
            )
        }

        // Featured spotlight skeleton
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTV) 224.dp else 210.dp),
            shape = RoundedCornerShape(RadiusPanel)
        )

        // Section 1 skeleton
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ShimmerBox(
                modifier = Modifier
                    .width(140.dp)
                    .height(18.dp),
                shape = RoundedCornerShape(6.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(if (isTV) 6 else 3) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShimmerBox(
                            modifier = Modifier
                                .width(if (isTV) 146.dp else 132.dp)
                                .height(if (isTV) 206.dp else 188.dp),
                            shape = RoundedCornerShape(RadiusTile)
                        )
                        ShimmerBox(
                            modifier = Modifier
                                .width(100.dp)
                                .height(12.dp),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchShimmerSkeleton(isTV: Boolean) {
    val horizontalPadding = if (isTV) 20.dp else 16.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(6) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(98.dp),
                shape = RoundedCornerShape(RadiusTile)
            )
        }
    }
}
