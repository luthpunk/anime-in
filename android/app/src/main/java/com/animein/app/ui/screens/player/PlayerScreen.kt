package com.animein.app.ui.screens.player

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import com.animein.app.data.model.MirrorItem
import com.animein.app.ui.theme.DarkSurface
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.playerState.collectAsState()
    val savedProgress by viewModel.savedProgress.collectAsState()
    val context = LocalContext.current

    var showWebViewFallback by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(isFullscreen) {
        val activity = context as? Activity ?: return@LaunchedEffect
        if (isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                SmallTopAppBar(
                    title = { Text("Player Video", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.smallTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(if (isFullscreen) PaddingValues(0.dp) else innerPadding)
        ) {
            when (val state = uiState) {
                is PlayerUiState.Loading -> {
                    Box(
                        modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NeonPurple)
                    }
                }
                is PlayerUiState.Success -> {
                    val detail = state.data
                    val streamUrl = detail.linkStream ?: ""
                    val episodePageUrl = remember(detail.id, viewModel.episodeId) {
                        val id = detail.id.ifBlank { viewModel.episodeId }
                        when {
                            id.startsWith("http://") || id.startsWith("https://") -> id
                            id.isNotBlank() -> "https://otakudesu.blog/$id"
                            else -> "https://otakudesu.blog/${viewModel.episodeId}"
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Video Player Area
                        if (showWebViewFallback || streamUrl.isEmpty() || streamUrl == "-") {
                            // WebView Player Fallback
                            Box(
                                modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .background(Color.Black)
                            ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.mediaPlaybackRequiresUserGesture = false
                                        webViewClient = WebViewClient()
                                        webChromeClient = WebChromeClient()
                                        loadUrl(episodePageUrl)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // ExoPlayer View
                        Box(
                            modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Color.Black)
                        ) {
                            var playPosition by remember { mutableLongStateOf(savedProgress) }
                            
                            val httpDataSourceFactory = remember {
                                DefaultHttpDataSource.Factory()
                                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                                    .setAllowCrossProtocolRedirects(true)
                            }
                            
                            val mediaSourceFactory = remember {
                                DefaultMediaSourceFactory(context)
                                    .setDataSourceFactory(httpDataSourceFactory)
                            }

                            val exoPlayer = remember {
                                ExoPlayer.Builder(context)
                                    .setMediaSourceFactory(mediaSourceFactory)
                                    .build().apply {
                                        playWhenReady = true
                                        addListener(object : Player.Listener {
                                            override fun onPlaybackStateChanged(playbackState: Int) {
                                                if (playbackState == Player.STATE_READY) {
                                                    // Seek to saved position on first ready
                                                    if (playPosition > 0) {
                                                        seekTo(playPosition)
                                                        playPosition = 0
                                                    }
                                                }
                                            }
                                        })
                                    }
                            }

                            // Load video URI
                            LaunchedEffect(streamUrl) {
                                if (streamUrl.isNotEmpty() && streamUrl != "-") {
                                    val referer = when {
                                        streamUrl.contains("desustream") || streamUrl.contains("desu") -> "https://desustream.me/"
                                        streamUrl.contains("streamwish") || streamUrl.contains("awish") -> "https://streamwish.to/"
                                        streamUrl.contains("filelion") || streamUrl.contains("lion") -> "https://filelion.to/"
                                        streamUrl.contains("otakudesu") -> "https://otakudesu.blog/"
                                        else -> "https://otakudesu.blog/"
                                    }
                                    httpDataSourceFactory.setDefaultRequestProperties(mapOf("Referer" to referer))
                                    
                                    val mediaItem = MediaItem.fromUri(streamUrl)
                                    exoPlayer.setMediaItem(mediaItem)
                                    exoPlayer.prepare()
                                }
                            }

                            // Periodic Progress Saving
                            LaunchedEffect(Unit) {
                                while (true) {
                                    delay(5000)
                                    val progress = exoPlayer.currentPosition
                                    val duration = exoPlayer.duration
                                    if (progress > 0 && duration > 0) {
                                        viewModel.saveProgress(
                                            progress = progress,
                                            duration = duration,
                                            animeTitle = detail.title.split("Episode")[0].trim(),
                                            episodeTitle = detail.title,
                                            thumb = "" // Handled by DetailScreen
                                        )
                                    }
                                }
                            }

                            // Lifecycle handling
                            val lifecycleOwner = LocalLifecycleOwner.current
                            DisposableEffect(lifecycleOwner) {
                                val observer = LifecycleEventObserver { _, event ->
                                    when (event) {
                                        Lifecycle.Event.ON_PAUSE -> {
                                            exoPlayer.pause()
                                            val progress = exoPlayer.currentPosition
                                            val duration = exoPlayer.duration
                                            if (progress > 0 && duration > 0) {
                                                viewModel.saveProgress(
                                                    progress = progress,
                                                    duration = duration,
                                                    animeTitle = detail.title.split("Episode")[0].trim(),
                                                    episodeTitle = detail.title,
                                                    thumb = ""
                                                )
                                            }
                                        }
                                        Lifecycle.Event.ON_RESUME -> {
                                            exoPlayer.play()
                                        }
                                        else -> {}
                                    }
                                }
                                lifecycleOwner.lifecycle.addObserver(observer)
                                onDispose {
                                    lifecycleOwner.lifecycle.removeObserver(observer)
                                    exoPlayer.release()
                                }
                            }

                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        setShowFullscreenButton(true)
                                        setFullscreenButtonClickListener { isFull ->
                                            isFullscreen = isFull
                                        }
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    if (!isFullscreen) {
                        // Metadata & Mirrors List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp)
                        ) {
                        item {
                            Text(
                                detail.title,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Fallback Toggle Button
                            Button(
                                onClick = { showWebViewFallback = !showWebViewFallback },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (showWebViewFallback) NeonPurple else Color.Gray
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showWebViewFallback) "Ganti ke ExoPlayer" else "Ganti ke WebPlayer (Bypass)")
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            Text("Pilih Server Mirror (Streaming)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // List Mirrors
                        val mirrors = (detail.mirror1?.mirrorList ?: emptyList()) +
                                      (detail.mirror2?.mirrorList ?: emptyList()) +
                                      (detail.mirror3?.mirrorList ?: emptyList())

                        if (mirrors.isEmpty()) {
                            item {
                                Text("Tidak ada server mirror alternatif", color = TextSecondary, fontSize = 13.sp)
                            }
                        } else {
                            items(mirrors) { mirror ->
                                MirrorRow(
                                    mirror = mirror,
                                    onClick = { viewModel.switchMirror(mirror.id) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                    }
                }
                is PlayerUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Gagal memuat video", color = Color.Red)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.fetchEpisodeDetail() }) {
                                Text("Coba Lagi")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MirrorRow(
    mirror: MirrorItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Stream via: ${mirror.host}",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Putar",
            color = NeonPurple,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
