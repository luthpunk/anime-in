package com.animein.app.ui.screens.genrelist

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animein.app.ui.components.PremiumLoadingSpinner
import com.animein.app.ui.components.frostGlass
import com.animein.app.ui.theme.NeonPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreListScreen(
    viewModel: GenreListViewModel,
    onGenreClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val genresState by viewModel.genresState.collectAsState()
    val configuration = LocalConfiguration.current
    val isTV = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Genre Anime", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            when (val state = genresState) {
                is GenresUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PremiumLoadingSpinner(color = NeonPurple)
                    }
                }
                is GenresUiState.Success -> {
                    LazyVerticalGrid(
                        columns = if (isTV) GridCells.Fixed(4) else GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = if (isTV) 24.dp else 100.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.genres) { genre ->
                            var isFocused by remember { mutableStateOf(false) }
                            val scale by animateFloatAsState(
                                targetValue = if (isFocused) 1.04f else 1f,
                                animationSpec = tween(150),
                                label = "genreScale"
                            )
                            val cardShape = RoundedCornerShape(16.dp)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .scale(scale)
                                    .frostGlass(
                                        shape = cardShape,
                                        isFocused = isFocused
                                    )
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .clickable { onGenreClick(genre.id, genre.genreName) }
                                    .padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = genre.genreName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                is GenresUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.message, color = Color.Red, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        var isRetryFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .frostGlass(shape = RoundedCornerShape(14.dp), isFocused = isRetryFocused)
                                .onFocusChanged { isRetryFocused = it.isFocused }
                                .clickable { viewModel.fetchGenres() }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text("Coba Lagi", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
