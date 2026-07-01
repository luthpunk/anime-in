package com.animein.app

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val bottomNavItems = listOf(
        NavigationItem("home", "Home", Icons.Default.Home),
        NavigationItem("schedule", "Jadwal", Icons.Default.DateRange),
        NavigationItem("genres_list", "Genre", Icons.Default.Menu),
        NavigationItem("history", "Koleksi", Icons.Default.List)
    )

    var currentRoute by remember { mutableStateOf("home") }

    Scaffold(
        bottomBar = {
            if (currentRoute in listOf("home", "schedule", "genres_list", "history")) {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = NeonPurple
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
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
                                    tint = if (selected) NeonPurple else TextSecondary
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    color = if (selected) Color.White else TextSecondary
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable("home") {
                currentRoute = "home"
                val viewModel: HomeViewModel = hiltViewModel()
                HomeScreen(
                    viewModel = viewModel,
                    onAnimeClick = { id ->
                        navigateToDetail(navController, id)
                    },
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
                currentRoute = "schedule"
                val viewModel: ScheduleViewModel = hiltViewModel()
                ScheduleScreen(
                    viewModel = viewModel,
                    onAnimeClick = { id ->
                        navigateToDetail(navController, id)
                    }
                )
            }

            composable("genres_list") {
                currentRoute = "genres_list"
                val viewModel: GenreListViewModel = hiltViewModel()
                GenreListScreen(
                    viewModel = viewModel,
                    onGenreClick = { id, name ->
                        navController.navigate("genre/${Uri.encode(id)}/${Uri.encode(name)}")
                    }
                )
            }

            composable("history") {
                currentRoute = "history"
                val viewModel: HistoryViewModel = hiltViewModel()
                HistoryScreen(
                    viewModel = viewModel,
                    onAnimeClick = { id ->
                        navigateToDetail(navController, id)
                    }
                )
            }

            composable(
                route = "detail/{animeId}",
                arguments = listOf(
                    navArgument("animeId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                currentRoute = "detail"
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
                currentRoute = "player"
                val viewModel: PlayerViewModel = hiltViewModel()
                val animeId = backStackEntry.arguments?.getString("animeId") ?: ""
                PlayerScreen(
                    viewModel = viewModel,
                    onBackClick = {
                        val encodedAnimeId = java.net.URLEncoder.encode(animeId, "UTF-8")
                        navController.navigate("detail/$encodedAnimeId") {
                            // Bersihkan player dari backstack, kembali ke home terlebih dahulu
                            popUpTo(navController.graph.startDestinationId) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onEpisodeClick = { nextEpId, aId ->
                        val encodedEpId = java.net.URLEncoder.encode(nextEpId, "UTF-8")
                        val encodedAnimeId = java.net.URLEncoder.encode(aId, "UTF-8")
                        navController.navigate("player/$encodedEpId/$encodedAnimeId") {
                            // Bersihkan player saat ini agar tidak menumpuk backstack
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
            ) { backStackEntry ->
                currentRoute = "genre"
                val viewModel: GenreViewModel = hiltViewModel()
                GenreScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onAnimeClick = { id ->
                        navigateToDetail(navController, id)
                    }
                )
            }

            composable(
                route = "list/{listType}/{listTitle}",
                arguments = listOf(
                    navArgument("listType") { type = NavType.StringType },
                    navArgument("listTitle") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                currentRoute = "list"
                val viewModel: com.animein.app.ui.screens.animelist.AnimeListViewModel = hiltViewModel()
                com.animein.app.ui.screens.animelist.AnimeListScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onAnimeClick = { id ->
                        navigateToDetail(navController, id)
                    }
                )
            }
        }
    }
}

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun navigateToDetail(
    navController: androidx.navigation.NavHostController,
    rawAnimeId: String
) {
    val animeId = AnimeIdUtils.normalizeAnimeId(rawAnimeId)
    if (animeId.isNotBlank()) {
        navController.navigate("detail/${Uri.encode(animeId)}")
    }
}

private fun navigateToPlayer(
    navController: androidx.navigation.NavHostController,
    rawEpisodeId: String,
    rawAnimeId: String
) {
    val episodeId = AnimeIdUtils.normalizeEpisodeId(rawEpisodeId)
    val animeId = AnimeIdUtils.normalizeAnimeId(rawAnimeId)
    if (episodeId.isNotBlank() && animeId.isNotBlank()) {
        navController.navigate("player/${Uri.encode(episodeId)}/${Uri.encode(animeId)}")
    }
}
