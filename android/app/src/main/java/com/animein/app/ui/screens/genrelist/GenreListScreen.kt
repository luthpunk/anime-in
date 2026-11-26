package com.animein.app.ui.screens.genrelist

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animein.app.ui.components.MonoBadge
import com.animein.app.ui.components.MusikeErrorState
import com.animein.app.ui.components.RadiusTile
import com.animein.app.ui.components.ShimmerBox
import com.animein.app.ui.components.musikeSurface
import com.animein.app.ui.theme.BgGraphite
import com.animein.app.ui.theme.FgPrimary
import com.animein.app.ui.theme.LocalAccentPreset
import com.animein.app.ui.theme.Surface1
import com.animein.app.ui.theme.Surface2
import com.animein.app.ui.theme.Surface3

@Composable
fun GenreListScreen(
    viewModel: GenreListViewModel,
    onGenreClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val genresState by viewModel.genresState.collectAsState()
    val accent = LocalAccentPreset.current
    val configuration = LocalConfiguration.current
    val isTV = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    val pageBg = if (isTV) Surface1 else BgGraphite
    val horizontalPadding = if (isTV) 20.dp else 16.dp

    val genreCount = (genresState as? GenresUiState.Success)?.genres?.size

    Scaffold(
        containerColor = pageBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBg)
                    .then(if (!isTV) Modifier.statusBarsPadding() else Modifier)
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = if (isTV) 14.dp else 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Genre Anime",
                    color = FgPrimary,
                    fontSize = if (isTV) 24.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                if (genreCount != null && genreCount > 0) {
                    MonoBadge(text = "$genreCount")
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
            when (val state = genresState) {
                is GenresUiState.Loading -> {
                    GenreListShimmerSkeleton(isTV = isTV, horizontalPadding = horizontalPadding)
                }
                is GenresUiState.Success -> {
                    val uniqueGenres = remember(state.genres) { state.genres.distinctBy { it.id } }
                    LazyVerticalGrid(
                        columns = if (isTV) GridCells.Fixed(4) else GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = 6.dp,
                            bottom = if (isTV) 24.dp else 92.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uniqueGenres, key = { it.id }) { genre ->
                            var isFocused by remember { mutableStateOf(false) }
                            val cardShape = RoundedCornerShape(RadiusTile)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .musikeSurface(
                                        shape = cardShape,
                                        isFocused = isFocused,
                                        accentColor = accent.accent,
                                        containerColor = Surface2,
                                        focusedContainerColor = Surface3
                                    )
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .clickable { onGenreClick(genre.id, genre.genreName) }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isFocused) accent.accent else accent.accent.copy(alpha = 0.45f))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = genre.genreName,
                                    color = if (isFocused) accent.accent else FgPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                is GenresUiState.Error -> {
                    MusikeErrorState(
                        message = state.message,
                        onRetry = { viewModel.fetchGenres() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreListShimmerSkeleton(
    isTV: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    val cols = if (isTV) 4 else 2
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(6) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(cols) {
                    ShimmerBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(RadiusTile)
                    )
                }
            }
        }
    }
}
