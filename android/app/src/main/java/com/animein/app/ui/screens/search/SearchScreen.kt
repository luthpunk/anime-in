package com.animein.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animein.app.data.model.GenreItem
import com.animein.app.data.model.SearchItem
import com.animein.app.ui.theme.DarkSurface
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.ui.theme.StarYellow
import com.animein.app.ui.theme.TextSecondary
import com.animein.app.util.AnimeIdUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onAnimeClick: (String) -> Unit,
    onGenreClick: (String, String) -> Unit, // genreId, genreName
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val genresState by viewModel.genresState.collectAsState()

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Cari Anime", color = Color.White) },
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
                .padding(horizontal = 16.dp)
        ) {
            // Search Text Field
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Cari judul anime...", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.textFieldColors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    containerColor = DarkSurface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Body Area (Search Results or Genres List)
            if (searchQuery.isBlank()) {
                Text("Genre Populer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                when (val state = genresState) {
                    is GenresUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NeonPurple)
                        }
                    }
                    is GenresUiState.Success -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.genres) { genre ->
                                GenreGridCard(
                                    genre = genre,
                                    onClick = { onGenreClick(genre.id, genre.genreName) }
                                )
                            }
                        }
                    }
                    is GenresUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(state.message, color = Color.Red)
                        }
                    }
                }
            } else {
                when (val state = searchState) {
                    is SearchUiState.Idle -> {}
                    is SearchUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NeonPurple)
                        }
                    }
                    is SearchUiState.Success -> {
                        val results = state.results
                        if (results.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Tidak ada hasil ditemukan", color = TextSecondary)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(results) { anime ->
                                    SearchResultCard(
                                        anime = anime,
                                        onClick = { onAnimeClick(AnimeIdUtils.normalizeAnimeId(anime.id)) }
                                    )
                                }
                            }
                        }
                    }
                    is SearchUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(state.message, color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenreGridCard(
    genre: GenreItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (!genre.imageLink.isNullOrEmpty()) {
            AsyncImage(
                model = genre.imageLink,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        }
        Text(
            genre.genreName,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SearchResultCard(
    anime: SearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = anime.thumb,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 60.dp, height = 80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                anime.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            anime.status?.let {
                Text(
                    "Status: $it",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            anime.score?.let { score ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = StarYellow,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        score.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
