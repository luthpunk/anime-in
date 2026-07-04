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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.animein.app.util.AnimeIdUtils



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
        Row(modifier = Modifier.fillMaxSize()) {
            if (showNav) {
                NavigationRail(
                    containerColor = DarkSurface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    // Push items to vertical center
                    Spacer(modifier = Modifier.weight(1f))
                    navItems.forEach { item ->
                        val selected = currentRoute == item.route
                        var isFocused by remember { mutableStateOf(false) }
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                currentRoute = item.route
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = when {
                                        selected  -> NeonPurple
                                        isFocused -> Color.White
                                        else      -> TextSecondary
                                    }
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    color = when {
                                        selected  -> Color.White
                                        isFocused -> Color.White
                                        else      -> TextSecondary
                                    }
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = NeonPurple.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.onFocusChanged { isFocused = it.isFocused }
                        )
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
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(400)
                    ) + fadeIn(animationSpec = tween(400))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { -it / 3 },
                        animationSpec = tween(400)
                    ) + fadeOut(animationSpec = tween(400))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it / 3 },
                        animationSpec = tween(400)
                    ) + fadeIn(animationSpec = tween(400))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(400)
                    ) + fadeOut(animationSpec = tween(400))
                }
            ) {
                appNavGraph(navController) { currentRoute = it }
            }


            if (showNav) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            navItems.forEach { item ->
                                val selected = currentRoute == item.route
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            currentRoute = item.route
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = if (selected) NeonPurple else TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        item.label,
                                        color = if (selected) Color.White else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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
