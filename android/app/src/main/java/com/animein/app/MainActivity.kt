package com.animein.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.animein.app.ui.screens.history.HistoryScreen
import com.animein.app.ui.screens.history.HistoryViewModel
import com.animein.app.ui.screens.home.HomeScreen
import com.animein.app.ui.screens.home.HomeViewModel
import com.animein.app.ui.screens.player.PlayerScreen
import com.animein.app.ui.screens.player.PlayerViewModel
import com.animein.app.ui.screens.search.SearchScreen
import com.animein.app.ui.screens.search.SearchViewModel
import com.animein.app.ui.theme.AnimeInTheme
import com.animein.app.ui.theme.DarkSurface
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.ui.theme.TextSecondary
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
        NavigationItem("search", "Cari", Icons.Default.Search),
        NavigationItem("history", "Koleksi", Icons.Default.List)
    )

    var currentRoute by remember { mutableStateOf("home") }

    Scaffold(
        bottomBar = {
            // Only show bottom navigation on home, search, and history screens
            if (currentRoute in listOf("home", "search", "history")) {
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
                        navController.navigate("detail/$id")
                    }
                )
            }

            composable("search") {
                currentRoute = "search"
                val viewModel: SearchViewModel = hiltViewModel()
                SearchScreen(
                    viewModel = viewModel,
                    onAnimeClick = { id ->
                        navController.navigate("detail/$id")
                    },
                    onGenreClick = { id, name ->
                        navController.navigate("genre/$id/$name")
                    }
                )
            }

            composable("history") {
                currentRoute = "history"
                val viewModel: HistoryViewModel = hiltViewModel()
                HistoryScreen(
                    viewModel = viewModel,
                    onAnimeClick = { id ->
                        navController.navigate("detail/$id")
                    },
                    onEpisodePlay = { episodeId, animeId ->
                        navController.navigate("player/$episodeId/$animeId")
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
                        navController.navigate("player/$episodeId/$animeId")
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
                PlayerScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() }
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
                        navController.navigate("detail/$id")
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
