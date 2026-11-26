package com.animein.app

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.animein.app.ui.MainViewModel
import com.animein.app.ui.components.AppearanceSettingsDialog
import com.animein.app.ui.components.BrandLogoMark
import com.animein.app.ui.components.MusikeGenreIcon
import com.animein.app.ui.components.MusikeLibraryIcon
import com.animein.app.ui.components.RadiusPanel
import com.animein.app.ui.components.RadiusTile
import com.animein.app.ui.components.topHairline
import com.animein.app.ui.screens.animelist.AnimeListScreen
import com.animein.app.ui.screens.animelist.AnimeListViewModel
import com.animein.app.ui.screens.detail.DetailScreen
import com.animein.app.ui.screens.detail.DetailViewModel
import com.animein.app.ui.screens.genre.GenreScreen
import com.animein.app.ui.screens.genre.GenreViewModel
import com.animein.app.ui.screens.genrelist.GenreListScreen
import com.animein.app.ui.screens.genrelist.GenreListViewModel
import com.animein.app.ui.screens.history.HistoryScreen
import com.animein.app.ui.screens.history.HistoryViewModel
import com.animein.app.ui.screens.home.HomeScreen
import com.animein.app.ui.screens.home.HomeViewModel
import com.animein.app.ui.screens.player.PlayerScreen
import com.animein.app.ui.screens.player.PlayerViewModel
import com.animein.app.ui.screens.schedule.ScheduleScreen
import com.animein.app.ui.screens.schedule.ScheduleViewModel
import com.animein.app.ui.theme.AnimeInTheme
import com.animein.app.ui.theme.BgGraphite
import com.animein.app.ui.theme.FgPrimary
import com.animein.app.ui.theme.FgSecondary
import com.animein.app.ui.theme.FgTertiary
import com.animein.app.ui.theme.Hairline
import com.animein.app.ui.theme.LocalAccentPreset
import com.animein.app.ui.theme.Surface1
import com.animein.app.ui.theme.Surface2
import com.animein.app.ui.theme.Surface3
import com.animein.app.util.AnimeIdUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val accentId by mainViewModel.accentId.collectAsState()
            var showAppearanceDialog by remember { mutableStateOf(false) }

            AnimeInTheme(
                accentId = accentId,
                onOpenAppearanceSettings = { showAppearanceDialog = true }
            ) {
                MainApp(
                    onOpenAppearanceSettings = { showAppearanceDialog = true }
                )

                if (showAppearanceDialog) {
                    AppearanceSettingsDialog(
                        currentAccentId = accentId,
                        onSelectAccent = { mainViewModel.setAccentId(it) },
                        onDismiss = { showAppearanceDialog = false }
                    )
                }
            }
        }
    }
}

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun MainApp(
    onOpenAppearanceSettings: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    val accent = LocalAccentPreset.current

    val navItems = remember {
        listOf(
            NavigationItem("home", "Home", Icons.Default.Home),
            NavigationItem("schedule", "Jadwal", Icons.Default.DateRange),
            NavigationItem("genres_list", "Genre", MusikeGenreIcon),
            NavigationItem("history", "Koleksi", MusikeLibraryIcon)
        )
    }

    val configuration = LocalConfiguration.current
    val isTV = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    val showNav = currentRoute in listOf("home", "schedule", "genres_list", "history")
    val isPlayerRoute = currentRoute.startsWith("player/")

    if (isTV) {
        // ── Android TV layout: Musike Floating Islands (Left Rail + Content Island) ──
        val homeNavFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            try {
                homeNavFocusRequester.requestFocus()
            } catch (_: Exception) {}
            kotlinx.coroutines.delay(150)
            try {
                homeNavFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }

        val rootPaddingModifier = if (isPlayerRoute) {
            Modifier.fillMaxSize().background(BgGraphite)
        } else {
            Modifier
                .fillMaxSize()
                .background(BgGraphite)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        }

        Row(
            modifier = rootPaddingModifier,
            horizontalArrangement = Arrangement.spacedBy(if (isPlayerRoute) 0.dp else 10.dp)
        ) {
            if (showNav) {
                val railShape = RoundedCornerShape(RadiusPanel)
                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .fillMaxHeight()
                        .clip(railShape)
                        .background(Surface1, railShape)
                        .border(1.dp, Hairline, railShape)
                        .padding(vertical = 14.dp, horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Brand Mark at Top (ala a-music sidebar)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(RadiusTile))
                            .background(Surface2),
                        contentAlignment = Alignment.Center
                    ) {
                        BrandLogoMark(size = 20.dp)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Navigation Items
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            var isFocused by remember { mutableStateOf(false) }
                            val itemShape = RoundedCornerShape(RadiusTile)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(itemShape)
                                    .background(
                                        color = when {
                                            isFocused -> Surface3
                                            selected -> Surface2
                                            else -> Color.Transparent
                                        },
                                        shape = itemShape
                                    )
                                    .then(
                                        when {
                                            isFocused -> Modifier.border(2.5.dp, accent.accent, itemShape)
                                            selected -> Modifier.border(1.dp, Hairline, itemShape)
                                            else -> Modifier
                                        }
                                    )
                                    .then(
                                        if (item.route == "home") Modifier.focusRequester(homeNavFocusRequester)
                                        else Modifier
                                    )
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .clickable {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Left active accent bar inside tile
                                if (selected && !isFocused) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .padding(start = 3.dp)
                                            .width(3.dp)
                                            .height(20.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(accent.accent)
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = when {
                                            isFocused -> accent.accent
                                            selected -> FgPrimary
                                            else -> FgTertiary
                                        },
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = item.label,
                                        color = when {
                                            isFocused -> FgPrimary
                                            selected -> FgPrimary
                                            else -> FgTertiary
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = if (isFocused || selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Appearance / Accent Settings button at bottom (ala a-music TV rail)
                    var isSettingsFocused by remember { mutableStateOf(false) }
                    val settingsShape = RoundedCornerShape(RadiusTile)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(settingsShape)
                            .background(if (isSettingsFocused) Surface3 else Color.Transparent, settingsShape)
                            .then(
                                if (isSettingsFocused) Modifier.border(2.5.dp, accent.accent, settingsShape)
                                else Modifier
                            )
                            .onFocusChanged { isSettingsFocused = it.isFocused }
                            .clickable { onOpenAppearanceSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Tampilan",
                            tint = if (isSettingsFocused) accent.accent else FgTertiary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            // Main Content Island (full bleed when on Player route)
            val contentShape = RoundedCornerShape(RadiusPanel)
            Box(
                modifier = if (isPlayerRoute) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(contentShape)
                        .background(Surface1, contentShape)
                        .border(1.dp, Hairline, contentShape)
                }
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(animationSpec = tween(200)) },
                    exitTransition = { fadeOut(animationSpec = tween(200)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                    popExitTransition = { fadeOut(animationSpec = tween(200)) }
                ) {
                    appNavGraph(navController)
                }
            }
        }
    } else {
        // ── Phone / Tablet layout: Clean Musike Docked Bottom Bar ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgGraphite)
        ) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(animationSpec = tween(220)) },
                exitTransition = { fadeOut(animationSpec = tween(220)) },
                popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                popExitTransition = { fadeOut(animationSpec = tween(220)) }
            ) {
                appNavGraph(navController)
            }

            if (showNav) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Surface1.copy(alpha = 0.96f))
                        .topHairline(Hairline)
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            val iconTint by animateColorAsState(
                                targetValue = if (selected) FgPrimary else FgTertiary,
                                animationSpec = tween(180),
                                label = "mobileNavTint"
                            )
                            val labelColor by animateColorAsState(
                                targetValue = if (selected) FgPrimary else FgSecondary.copy(alpha = 0.72f),
                                animationSpec = tween(180),
                                label = "mobileNavLabel"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (!selected) {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Signature a-music top accent bar on active tab
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .width(28.dp)
                                            .height(2.5.dp)
                                            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                            .background(accent.accent)
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = iconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = item.label,
                                        color = labelColor,
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
}

// ── Shared NavGraph ────────────────────────────────────────────────────────
private fun NavGraphBuilder.appNavGraph(
    navController: NavHostController
) {
    composable("home") {
        val viewModel: HomeViewModel = hiltViewModel()
        HomeScreen(
            viewModel = viewModel,
            onAnimeClick = { id -> navigateToDetail(navController, id) },
            onSeeAllClick = { type, title ->
                if (type == "ongoing") {
                    navController.navigate("schedule") {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                } else {
                    navController.navigate("list/${Uri.encode(type)}/${Uri.encode(title)}")
                }
            },
            onEpisodePlay = { episodeId, animeId ->
                navigateToPlayer(navController, episodeId, animeId)
            }
        )
    }

    composable("schedule") {
        val viewModel: ScheduleViewModel = hiltViewModel()
        ScheduleScreen(
            viewModel = viewModel,
            onAnimeClick = { id -> navigateToDetail(navController, id) }
        )
    }

    composable("genres_list") {
        val viewModel: GenreListViewModel = hiltViewModel()
        GenreListScreen(
            viewModel = viewModel,
            onGenreClick = { id, name ->
                navController.navigate("genre/${Uri.encode(id)}/${Uri.encode(name)}")
            }
        )
    }

    composable("history") {
        val viewModel: HistoryViewModel = hiltViewModel()
        HistoryScreen(
            viewModel = viewModel,
            onAnimeClick = { id -> navigateToDetail(navController, id) }
        )
    }

    composable(
        route = "detail/{animeId}",
        arguments = listOf(navArgument("animeId") { type = NavType.StringType })
    ) {
        val viewModel: DetailViewModel = hiltViewModel()
        DetailScreen(
            viewModel = viewModel,
            onBackClick = { navController.popBackStack() },
            onEpisodeClick = { episodeId, animeId ->
                navigateToPlayer(navController, episodeId, animeId)
            },
            onGenreClick = { genreId, genreName ->
                navController.navigate("genre/${Uri.encode(genreId)}/${Uri.encode(genreName)}")
            }
        )
    }

    composable(
        route = "player/{episodeId}/{animeId}",
        arguments = listOf(
            navArgument("episodeId") { type = NavType.StringType },
            navArgument("animeId") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val viewModel: PlayerViewModel = hiltViewModel()
        val animeId = backStackEntry.arguments?.getString("animeId") ?: ""
        PlayerScreen(
            viewModel = viewModel,
            onBackClick = {
                val encodedAnimeId = java.net.URLEncoder.encode(animeId, "UTF-8")
                navController.navigate("detail/$encodedAnimeId") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onEpisodeClick = { nextEpId, aId ->
                val encodedEpId = java.net.URLEncoder.encode(nextEpId, "UTF-8")
                val encodedAnimeId = java.net.URLEncoder.encode(aId, "UTF-8")
                navController.navigate("player/$encodedEpId/$encodedAnimeId") {
                    popUpTo("player/{episodeId}/{animeId}") { inclusive = true }
                }
            }
        )
    }

    composable(
        route = "genre/{genreId}/{genreName}",
        arguments = listOf(
            navArgument("genreId") { type = NavType.StringType },
            navArgument("genreName") { type = NavType.StringType }
        )
    ) {
        val viewModel: GenreViewModel = hiltViewModel()
        GenreScreen(
            viewModel = viewModel,
            onBackClick = { navController.popBackStack() },
            onAnimeClick = { id -> navigateToDetail(navController, id) }
        )
    }

    composable(
        route = "list/{listType}/{listTitle}",
        arguments = listOf(
            navArgument("listType") { type = NavType.StringType },
            navArgument("listTitle") { type = NavType.StringType }
        )
    ) {
        val viewModel: AnimeListViewModel = hiltViewModel()
        AnimeListScreen(
            viewModel = viewModel,
            onBackClick = { navController.popBackStack() },
            onAnimeClick = { id -> navigateToDetail(navController, id) }
        )
    }
}

private fun navigateToDetail(navController: NavHostController, rawAnimeId: String) {
    val animeId = AnimeIdUtils.normalizeAnimeId(rawAnimeId)
    if (animeId.isNotBlank()) {
        navController.navigate("detail/${Uri.encode(animeId)}")
    }
}

private fun navigateToPlayer(
    navController: NavHostController,
    rawEpisodeId: String,
    rawAnimeId: String
) {
    val episodeId = AnimeIdUtils.normalizeEpisodeId(rawEpisodeId)
    val animeId = AnimeIdUtils.normalizeAnimeId(rawAnimeId)
    if (episodeId.isNotBlank() && animeId.isNotBlank()) {
        navController.navigate("player/${Uri.encode(episodeId)}/${Uri.encode(animeId)}")
    }
}
