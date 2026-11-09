package com.animein.app

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.animein.app.ui.theme.DarkSurface
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.ui.theme.TextSecondary
import com.animein.app.ui.components.GlassmorphicCard
import com.animein.app.ui.components.frostGlassBgBrush
import com.animein.app.ui.components.frostGlassBorderBrush
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.scale

import com.animein.app.util.AnimeIdUtils

import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnimeInTheme {
                MainApp()
            }
        }
    }
}

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navItems = listOf(
        NavigationItem("home", "Home", Icons.Default.Home),
        NavigationItem("schedule", "Jadwal", Icons.Default.DateRange),
        NavigationItem("genres_list", "Genre", Icons.Default.Menu),
        NavigationItem("history", "Koleksi", Icons.Default.List)
    )
    var currentRoute by remember { mutableStateOf("home") }

    val configuration = LocalConfiguration.current
    val isTV = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
    val showNav = currentRoute in listOf("home", "schedule", "genres_list", "history")

    if (isTV) {
        // ── Android TV layout: NavigationRail on left ──────────────────────
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

        Row(modifier = Modifier.fillMaxSize()) {
            if (showNav) {
                NavigationRail(
                    containerColor = DarkSurface,
                    modifier = Modifier
                        .fillMaxHeight()
                        .border(
                            width = 1.dp,
                            brush = frostGlassBorderBrush(isSubtle = true),
                            shape = androidx.compose.ui.graphics.RectangleShape
                        )
                ) {
                    // Push items to vertical center
                    Spacer(modifier = Modifier.weight(1f))
                    navItems.forEach { item ->
                        val selected = currentRoute == item.route
                        var isFocused by remember { mutableStateOf(false) }
                        val itemShape = RoundedCornerShape(18.dp)
                        val scale by animateFloatAsState(
                            targetValue = if (isFocused) 1.06f else 1f,
                            animationSpec = tween(150),
                            label = "tvNavScale"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .width(68.dp)
                                .height(60.dp)
                                .scale(scale)
                                .clip(itemShape)
                                .then(
                                    when {
                                        isFocused -> Modifier
                                            .background(brush = frostGlassBgBrush(isFocused = true), shape = itemShape)
                                            .border(
                                                width = 1.5.dp,
                                                brush = frostGlassBorderBrush(isFocused = true),
                                                shape = itemShape
                                            )
                                        selected -> Modifier
                                            .background(brush = frostGlassBgBrush(isFocused = false), shape = itemShape)
                                            .border(
                                                width = 1.dp,
                                                brush = frostGlassBorderBrush(isFocused = false),
                                                shape = itemShape
                                            )
                                        else -> Modifier
                                    }
                                )
                                .then(
                                    if (item.route == "home") Modifier.focusRequester(homeNavFocusRequester)
                                    else Modifier
                                )
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable {
                                    currentRoute = item.route
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
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (isFocused || selected) Color.White else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.label,
                                    color = if (isFocused || selected) Color.White else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isFocused || selected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            // NavHost fills the remaining space
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize()
            ) {
                appNavGraph(navController) { currentRoute = it }
            }
        }
    } else {
        // ── Phone / Tablet layout: Floating Glassmorphic Overlay Bottom Bar ──
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    fadeIn(animationSpec = tween(250))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(250))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(250))
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(250))
                }
            ) {
                appNavGraph(navController) { currentRoute = it }
            }


            if (showNav) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding()
                ) {
                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        val selectedIndex = navItems.indexOfFirst { item -> item.route == currentRoute }.coerceAtLeast(0)
                        val density = LocalDensity.current
                        var itemWidthPx by remember { mutableStateOf(0f) }
                        val indicatorWidthDp = 76.dp
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 8.dp)
                        ) {
                            if (itemWidthPx > 0) {
                                val indicatorWidthPx = with(density) { indicatorWidthDp.toPx() }
                                val pillOffset by animateFloatAsState(
                                    targetValue = selectedIndex * itemWidthPx + (itemWidthPx - indicatorWidthPx) / 2,
                                    animationSpec = tween(300),
                                    label = "pillOffset"
                                )
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(x = pillOffset.toInt(), y = 0) }
                                        .width(indicatorWidthDp)
                                        .height(44.dp) // rounded rect height
                                        .align(Alignment.CenterStart)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            brush = frostGlassBgBrush(isFocused = true),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            brush = frostGlassBorderBrush(isFocused = true),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                )
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        itemWidthPx = coordinates.size.width.toFloat() / navItems.size
                                    },
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                navItems.forEachIndexed { index, item ->
                                    val selected = selectedIndex == index
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(percent = 50))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                currentRoute = item.route
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
                                        androidx.compose.animation.Crossfade(
                                            targetState = selected,
                                            animationSpec = tween(200),
                                            label = "nav_item_crossfade"
                                        ) { isSelected ->
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.label,
                                                tint = if (isSelected) Color.White else com.animein.app.ui.theme.TextSecondary,
                                                modifier = Modifier.size(26.dp)
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
    }
}

// ── Shared NavGraph (no NavHost here — only composable destinations) ────────
private fun NavGraphBuilder.appNavGraph(
    navController: NavHostController,
    onRouteChange: (String) -> Unit
) {
    composable("home") {
        onRouteChange("home")
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
        onRouteChange("schedule")
        val viewModel: ScheduleViewModel = hiltViewModel()
        ScheduleScreen(
            viewModel = viewModel,
            onAnimeClick = { id -> navigateToDetail(navController, id) }
        )
    }

    composable("genres_list") {
        onRouteChange("genres_list")
        val viewModel: GenreListViewModel = hiltViewModel()
        GenreListScreen(
            viewModel = viewModel,
            onGenreClick = { id, name ->
                navController.navigate("genre/${Uri.encode(id)}/${Uri.encode(name)}")
            }
        )
    }

    composable("history") {
        onRouteChange("history")
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
        onRouteChange("detail")
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
        onRouteChange("player")
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
        onRouteChange("genre")
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
        onRouteChange("list")
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
