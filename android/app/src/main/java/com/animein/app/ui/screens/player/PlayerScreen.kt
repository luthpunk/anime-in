package com.animein.app.ui.screens.player

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextOverflow
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
    onEpisodeClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.playerState.collectAsState()
    val savedProgress by viewModel.savedProgress.collectAsState()
    val context = LocalContext.current

    var showWebViewFallback by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

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
                        } } else {
                            // ExoPlayer View
                            Box(
                            modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Color.Black)
                        ) {
                            var playPosition by remember { mutableLongStateOf(savedProgress) }
                            var showControls by remember { mutableStateOf(true) }
                            var isPlaying by remember { mutableStateOf(true) }
                            var currentPosition by remember { mutableLongStateOf(savedProgress) }
                            var duration by remember { mutableLongStateOf(0L) }
                            val speed by viewModel.playbackSpeed.collectAsState()
                            
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
                                                    if (playPosition > 0) {
                                                        seekTo(playPosition)
                                                        playPosition = 0
                                                    }
                                                }
                                            }
                                        })
                                    }
                            }

                            // Monitor Player state
                            DisposableEffect(exoPlayer) {
                                val listener = object : Player.Listener {
                                    override fun onIsPlayingChanged(playing: Boolean) {
                                        isPlaying = playing
                                    }
                                    override fun onPlaybackStateChanged(state: Int) {
                                        if (state == Player.STATE_READY) {
                                            duration = exoPlayer.duration
                                        }
                                    }
                                }
                                exoPlayer.addListener(listener)
                                onDispose {
                                    exoPlayer.removeListener(listener)
                                }
                            }

                            // Periodically update progress
                            LaunchedEffect(isPlaying) {
                                while (isPlaying) {
                                    currentPosition = exoPlayer.currentPosition
                                    delay(250)
                                }
                            }

                            // Hide controls after 3.5 seconds
                            LaunchedEffect(showControls, isPlaying) {
                                if (showControls && isPlaying) {
                                    delay(3500)
                                    showControls = false
                                }
                            }

                            // Set playback speed
                            LaunchedEffect(speed) {
                                exoPlayer.setPlaybackSpeed(speed)
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
                                    val dur = exoPlayer.duration
                                    if (progress > 0 && dur > 0) {
                                        viewModel.saveProgress(
                                            progress = progress,
                                            duration = dur,
                                            animeTitle = detail.title.split("Episode")[0].trim(),
                                            episodeTitle = detail.title,
                                            thumb = ""
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
                                            val dur = exoPlayer.duration
                                            if (progress > 0 && dur > 0) {
                                                viewModel.saveProgress(
                                                    progress = progress,
                                                    duration = dur,
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

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        showControls = !showControls
                                    }
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = exoPlayer
                                            useController = false
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (showControls) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.6f))
                                    ) {
                                        // 1. Top row: Back & Title
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.TopStart)
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = {
                                                if (isFullscreen) {
                                                    isFullscreen = false
                                                } else {
                                                    onBackClick()
                                                }
                                            }) {
                                                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = detail.title,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        // 2. Center row: Replay15, Play/Pause, Forward15
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    val target = (exoPlayer.currentPosition - 15000).coerceAtLeast(0L)
                                                     exoPlayer.seekTo(target)
                                                     currentPosition = target
                                                },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Replay15Icon,
                                                    contentDescription = "Mundur 15s",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(32.dp))

                                            IconButton(
                                                onClick = {
                                                    if (exoPlayer.isPlaying) {
                                                        exoPlayer.pause()
                                                    } else {
                                                        exoPlayer.play()
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(32.dp))

                                            IconButton(
                                                onClick = {
                                                    val target = (exoPlayer.currentPosition + 15000).coerceAtMost(exoPlayer.duration)
                                                    exoPlayer.seekTo(target)
                                                    currentPosition = target
                                                },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Forward15Icon,
                                                    contentDescription = "Maju 15s",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }

                                        // 3. Bottom controls
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = formatTime(currentPosition),
                                                    color = Color.White,
                                                    fontSize = 11.sp
                                                )
                                                
                                                Slider(
                                                    value = if (duration > 0) currentPosition.toFloat() else 0f,
                                                    onValueChange = {
                                                        currentPosition = it.toLong()
                                                        exoPlayer.seekTo(currentPosition)
                                                    },
                                                    valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(horizontal = 8.dp),
                                                     colors = SliderDefaults.colors(
                                                         thumbColor = NeonPurple,
                                                         activeTrackColor = NeonPurple,
                                                         inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                                     )
                                                )

                                                Text(
                                                    text = formatTime(duration),
                                                    color = Color.White,
                                                    fontSize = 11.sp
                                                )
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset(y = (-4).dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                var showSpeedDialog by remember { mutableStateOf(false) }
                                                Box {
                                                    TextButton(
                                                        onClick = { showSpeedDialog = true },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "${speed}x Speed",
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                     }
                                                     
                                                     DropdownMenu(
                                                         expanded = showSpeedDialog,
                                                         onDismissRequest = { showSpeedDialog = false },
                                                         modifier = Modifier.background(DarkSurface)
                                                     ) {
                                                         listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { value ->
                                                             DropdownMenuItem(
                                                                 text = { Text("${value}x", color = Color.White) },
                                                                 onClick = {
                                                                     viewModel.setPlaybackSpeed(value)
                                                                     showSpeedDialog = false
                                                                 }
                                                             )
                                                         }
                                                     }
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val nextEpId by viewModel.nextEpisodeId.collectAsState()
                                                     if (nextEpId != null) {
                                                         IconButton(
                                                             onClick = {
                                                                 onEpisodeClick(nextEpId!!, viewModel.animeId)
                                                             },
                                                             modifier = Modifier.size(36.dp)
                                                         ) {
                                                             Icon(
                                                                 imageVector = Icons.Default.SkipNext,
                                                                 contentDescription = "Episode Selanjutnya",
                                                                 tint = Color.White,
                                                                 modifier = Modifier.size(24.dp)
                                                             )
                                                         }
                                                         Spacer(modifier = Modifier.width(12.dp))
                                                     }

                                                     IconButton(
                                                         onClick = { isFullscreen = !isFullscreen },
                                                         modifier = Modifier.size(36.dp)
                                                     ) {
                                                         Icon(
                                                             imageVector = if (isFullscreen) FullscreenExitIcon else FullscreenIcon,
                                                             contentDescription = "Fullscreen",
                                                             tint = Color.White,
                                                             modifier = Modifier.size(24.dp)
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
                        val mirrors = (detail.mirror1?.mirrorList?.map { UiMirrorItem(it.id, it.host, "360p") } ?: emptyList()) +
                                      (detail.mirror2?.mirrorList?.map { UiMirrorItem(it.id, it.host, "480p") } ?: emptyList()) +
                                      (detail.mirror3?.mirrorList?.map { UiMirrorItem(it.id, it.host, "720p") } ?: emptyList())

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
    mirror: UiMirrorItem,
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
            "Stream via: ${mirror.host} (${mirror.resolution})",
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

val FullscreenIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Fullscreen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        strokeLineWidth = 2f,
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White)
    ) {
        // Top-left corner
        moveTo(4f, 9f)
        lineTo(4f, 4f)
        lineTo(9f, 4f)
        // Top-right corner
        moveTo(15f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 9f)
        // Bottom-right corner
        moveTo(20f, 15f)
        lineTo(20f, 20f)
        lineTo(15f, 20f)
        // Bottom-left corner
        moveTo(9f, 20f)
        lineTo(4f, 20f)
        lineTo(4f, 15f)
    }.build()

val FullscreenExitIcon: ImageVector
    get() = ImageVector.Builder(
        name = "FullscreenExit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        strokeLineWidth = 2f,
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White)
    ) {
        // Top-left corner
        moveTo(4f, 8f)
        lineTo(8f, 8f)
        lineTo(8f, 4f)
        // Top-right corner
        moveTo(20f, 8f)
        lineTo(16f, 8f)
        lineTo(16f, 4f)
        // Bottom-right corner
        moveTo(16f, 20f)
        lineTo(16f, 16f)
        lineTo(20f, 16f)
        // Bottom-left corner
        moveTo(8f, 20f)
        lineTo(8f, 16f)
        lineTo(4f, 16f)
    }.build()

data class UiMirrorItem(
    val id: String,
    val host: String,
    val resolution: String
)

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

val Replay15Icon: ImageVector
    get() = ImageVector.Builder(
        name = "Replay15",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        strokeLineWidth = 2f,
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White)
    ) {
        // Counter-clockwise circular arrow
        moveTo(12f, 4f)
        arcTo(8f, 8f, 0f, true, false, 20f, 12f)
        // Arrow head
        moveTo(12f, 1f)
        lineTo(12f, 7f)
        lineTo(8f, 4f)
        close()
    }.build()

val Forward15Icon: ImageVector
    get() = ImageVector.Builder(
        name = "Forward15",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        strokeLineWidth = 2f,
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White)
    ) {
        // Clockwise circular arrow
        moveTo(12f, 4f)
        arcTo(8f, 8f, 0f, true, true, 4f, 12f)
        // Arrow head
        moveTo(12f, 1f)
        lineTo(12f, 7f)
        lineTo(16f, 4f)
        close()
    }.build()


