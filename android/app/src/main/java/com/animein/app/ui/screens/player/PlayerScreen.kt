package com.animein.app.ui.screens.player

import android.view.ViewGroup
import kotlin.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import com.animein.app.data.model.MirrorItem
import com.animein.app.util.AnimeIdUtils
import com.animein.app.ui.theme.DarkSurface
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val episodes by viewModel.episodes.collectAsState()
    val context = LocalContext.current

    var isFullscreen by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBackClick()
        }
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

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Video Player Area - ExoPlayer as default
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
                            var playerSize by remember { mutableStateOf(IntSize.Zero) }

                            var doubleTapFeedbackLeft by remember { mutableStateOf(false) }
                            var doubleTapFeedbackRight by remember { mutableStateOf(false) }
                            val coroutineScope = rememberCoroutineScope()

                            var isDraggingBrightness by remember { mutableStateOf(false) }
                            var isDraggingVolume by remember { mutableStateOf(false) }
                            var brightnessValue by remember { mutableStateOf(0.5f) }
                            var volumePercent by remember { mutableStateOf(0f) }

                            val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
                            val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }

                            fun setBrightness(value: Float) {
                                val activity = context as? Activity ?: return
                                val layoutParams = activity.window.attributes
                                layoutParams.screenBrightness = value.coerceIn(0.01f, 1f)
                                activity.window.attributes = layoutParams
                                brightnessValue = layoutParams.screenBrightness
                            }

                            fun setVolume(value: Float) {
                                val targetVolume = (value * maxVolume).toInt().coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
                                val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                volumePercent = current.toFloat() / maxVolume.toFloat()
                            }
                            
                            val httpDataSourceFactory = remember {
                                DefaultHttpDataSource.Factory()
                                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                                    .setAllowCrossProtocolRedirects(true)
                            }
                            
                            val cacheDataSourceFactory = remember {
                                CacheDataSource.Factory()
                                    .setCache(viewModel.simpleCache)
                                    .setUpstreamDataSourceFactory(httpDataSourceFactory)
                                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                            }
                            
                            val mediaSourceFactory = remember {
                                DefaultMediaSourceFactory(context)
                                    .setDataSourceFactory(cacheDataSourceFactory)
                            }

                            val loadControl = remember {
                                DefaultLoadControl.Builder()
                                    .setBufferDurationsMs(
                                        15000, // minBufferMs
                                        50000, // maxBufferMs
                                        2000,  // bufferForPlaybackMs (2s to play)
                                        3000   // bufferForPlaybackAfterRebufferMs (3s after seek/rebuffer)
                                    )
                                    .build()
                            }
 
                            val exoPlayer = remember {
                                ExoPlayer.Builder(context)
                                    .setMediaSourceFactory(mediaSourceFactory)
                                    .setLoadControl(loadControl)
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
                                    exoPlayer.release()
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
                                    val currentPos = exoPlayer.currentPosition
                                    if (currentPos > 0 && exoPlayer.playbackState != Player.STATE_IDLE) {
                                        playPosition = currentPos
                                    }

                                    val referer = when {
                                        streamUrl.contains("vidhide") || streamUrl.contains("vidhide.com") -> "https://vidhide.com/"
                                        streamUrl.contains("desustream") || streamUrl.contains("desu") -> "https://otakudesu.blog/"
                                        streamUrl.contains("streamwish") || streamUrl.contains("awish") -> "https://streamwish.to/"
                                        streamUrl.contains("filelion") || streamUrl.contains("lion") -> "https://filelion.to/"
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
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onSizeChanged { playerSize = it }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { offset ->
                                                val isLeft = offset.x < playerSize.width / 2
                                                if (isLeft) {
                                                    val target = (exoPlayer.currentPosition - 15000).coerceAtLeast(0L)
                                                    exoPlayer.seekTo(target)
                                                    currentPosition = target
                                                    
                                                    doubleTapFeedbackLeft = true
                                                    doubleTapFeedbackRight = false
                                                    coroutineScope.launch {
                                                        delay(600)
                                                        doubleTapFeedbackLeft = false
                                                    }
                                                } else {
                                                    val target = (exoPlayer.currentPosition + 15000).coerceAtMost(exoPlayer.duration)
                                                    exoPlayer.seekTo(target)
                                                    currentPosition = target
                                                    
                                                    doubleTapFeedbackRight = true
                                                    doubleTapFeedbackLeft = false
                                                    coroutineScope.launch {
                                                        delay(600)
                                                        doubleTapFeedbackRight = false
                                                    }
                                                }
                                            },
                                            onTap = {
                                                showControls = !showControls
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        var isLeft = false
                                        var dragStartValue = 0f
                                        var accumulatedDeltaY = 0f
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                isLeft = offset.x < playerSize.width / 2
                                                accumulatedDeltaY = 0f
                                                if (isLeft) {
                                                    isDraggingBrightness = true
                                                    isDraggingVolume = false
                                                    val activity = context as? Activity
                                                    val currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                                                    dragStartValue = if (currentBrightness < 0f) 0.5f else currentBrightness
                                                } else {
                                                    isDraggingVolume = true
                                                    isDraggingBrightness = false
                                                    val currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                                    dragStartValue = currentVol.toFloat() / maxVolume.toFloat()
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                accumulatedDeltaY -= dragAmount.y
                                                val height = playerSize.height.toFloat().coerceAtLeast(1f)
                                                val deltaPercent = accumulatedDeltaY / height
                                                val targetValue = (dragStartValue + deltaPercent).coerceIn(0f, 1f)
                                                if (isDraggingBrightness) {
                                                    setBrightness(targetValue)
                                                } else if (isDraggingVolume) {
                                                    setVolume(targetValue)
                                                }
                                            },
                                            onDragEnd = {
                                                coroutineScope.launch {
                                                    delay(800)
                                                    isDraggingBrightness = false
                                                    isDraggingVolume = false
                                                }
                                            },
                                            onDragCancel = {
                                                isDraggingBrightness = false
                                                isDraggingVolume = false
                                            }
                                        )
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

                                // Double-tap visual feedback overlays
                                if (doubleTapFeedbackLeft) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(0.5f)
                                            .align(Alignment.CenterStart)
                                            .background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "-15s",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (doubleTapFeedbackRight) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(0.5f)
                                            .align(Alignment.CenterEnd)
                                            .background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+15s",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                 if (isDraggingBrightness || isDraggingVolume) {
                                     Box(
                                         modifier = Modifier
                                             .fillMaxSize(),
                                         contentAlignment = Alignment.Center
                                     ) {
                                         Card(
                                             colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                                             shape = RoundedCornerShape(12.dp),
                                             modifier = Modifier.size(100.dp)
                                         ) {
                                             Column(
                                                 horizontalAlignment = Alignment.CenterHorizontally,
                                                 verticalArrangement = Arrangement.Center,
                                                 modifier = Modifier.fillMaxSize().padding(8.dp)
                                             ) {
                                                 Icon(
                                                     imageVector = if (isDraggingBrightness) BrightnessIcon else VolumeUpIcon,
                                                     contentDescription = null,
                                                     tint = Color.White,
                                                     modifier = Modifier.size(36.dp)
                                                 )
                                                 Spacer(modifier = Modifier.height(8.dp))
                                                 val percentage = if (isDraggingBrightness) (brightnessValue * 100).toInt() else (volumePercent * 100).toInt()
                                                 Text(
                                                     text = "$percentage%",
                                                     color = Color.White,
                                                     fontSize = 14.sp,
                                                     fontWeight = FontWeight.Bold
                                                 )
                                             }
                                         }
                                     }
                                 }

                                 if (showControls) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.6f))
                                    ) {
                                        // 1. Top row: Fullscreen Title & Episode
                                        if (isFullscreen) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.TopStart)
                                                    .background(
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                                                        )
                                                    )
                                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = detail.title,
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        // 2. Center row: Play/Pause only (seek is handled by double-tap)
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
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
                                                    imageVector = if (isPlaying) PauseIcon else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }

                                        // 3. Bottom controls — speed/nav di atas, seekbar di paling bawah
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                        ) {
                                            // Row 1: Speed + Next episode + Fullscreen
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 2.dp),
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
                                                            text = "${speed}x",
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
                                                            onClick = { onEpisodeClick(nextEpId!!, viewModel.animeId) },
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = SkipNextIcon,
                                                                contentDescription = "Episode Selanjutnya",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
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

                                            // Row 2: Seekbar — paling bawah
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
                                                        .padding(horizontal = 6.dp),
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
                                        }
                                    }
                                }
                            }
                    }

                    if (!isFullscreen) {
                        // Metadata & Mirrors Dropdown
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

                            // Mirror Dropdown Button
                            val mirrors = (detail.mirror1?.mirrorList?.map { UiMirrorItem(it.id, it.host, "360p") } ?: emptyList()) +
                                          (detail.mirror2?.mirrorList?.map { UiMirrorItem(it.id, it.host, "480p") } ?: emptyList()) +
                                          (detail.mirror3?.mirrorList?.map { UiMirrorItem(it.id, it.host, "720p") } ?: emptyList())

                            var showMirrorDropdown by remember { mutableStateOf(false) }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { showMirrorDropdown = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Pilih Server", fontWeight = FontWeight.Bold)
                                }
                                DropdownMenu(
                                    expanded = showMirrorDropdown,
                                    onDismissRequest = { showMirrorDropdown = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .background(DarkSurface)
                                ) {
                                    if (mirrors.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Tidak ada server tersedia", color = TextSecondary) },
                                            onClick = { showMirrorDropdown = false }
                                        )
                                    } else {
                                        mirrors.forEach { mirror ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            "Stream via: ${mirror.host} (${mirror.resolution})",
                                                            color = Color.White,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            "Putar",
                                                            color = NeonPurple,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.switchMirror(mirror.id)
                                                    showMirrorDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Collect and render episode grid selection
                        if (episodes.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Pilihan Episode", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            
                            val chunkedEpisodes = episodes.chunked(5)
                            chunkedEpisodes.forEach { rowEpisodes ->
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowEpisodes.forEach { episode ->
                                            val isCurrent = AnimeIdUtils.normalizeEpisodeId(episode.id) == AnimeIdUtils.normalizeEpisodeId(viewModel.episodeId)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isCurrent) NeonPurple else DarkSurface)
                                                    .clickable {
                                                        onEpisodeClick(episode.id, viewModel.animeId)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = extractEpisodeNumber(episode.title),
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        val placeholdersCount = 5 - rowEpisodes.size
                                        if (placeholdersCount > 0) {
                                            repeat(placeholdersCount) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                    } // end if (!isFullscreen)
                    } // end Column(fillMaxSize)
                } // end is PlayerUiState.Success
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

val PauseIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.White)
    ) {
        moveTo(6f, 19f)
        lineTo(10f, 19f)
        lineTo(10f, 5f)
        lineTo(6f, 5f)
        close()
        moveTo(14f, 5f)
        lineTo(14f, 19f)
        lineTo(18f, 19f)
        lineTo(18f, 5f)
        close()
    }.build()

val SkipNextIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SkipNext",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.White)
    ) {
        moveTo(6f, 18f)
        lineTo(14.5f, 12f)
        lineTo(6f, 6f)
        close()
        moveTo(16f, 6f)
        lineTo(16f, 18f)
        lineTo(18f, 18f)
        lineTo(18f, 6f)
        close()
    }.build()

private fun extractEpisodeNumber(title: String): String {
    val matches = Regex("\\b(?:Episode|Ep|E|eps)\\b\\s*(\\d+)|\\b(\\d+)\\b", RegexOption.IGNORE_CASE)
        .findAll(title)
        .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        .toList()
    return if (matches.isNotEmpty()) matches.last() else ""
}

val VolumeUpIcon: ImageVector
    get() = ImageVector.Builder(
        name = "VolumeUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.White)
    ) {
        moveTo(3f, 9f)
        lineTo(3f, 15f)
        lineTo(7f, 15f)
        lineTo(12f, 20f)
        lineTo(12f, 4f)
        lineTo(7f, 9f)
        lineTo(3f, 9f)
        close()
        moveTo(14f, 8f)
        lineTo(14f, 16f)
        lineTo(16f, 14f)
        lineTo(16f, 10f)
        close()
        moveTo(18f, 6f)
        lineTo(18f, 18f)
        lineTo(20f, 16f)
        lineTo(20f, 8f)
        close()
    }.build()

val BrightnessIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Brightness",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.White)
    ) {
        moveTo(10f, 6f)
        lineTo(14f, 6f)
        lineTo(18f, 10f)
        lineTo(18f, 14f)
        lineTo(14f, 18f)
        lineTo(10f, 18f)
        lineTo(6f, 14f)
        lineTo(6f, 10f)
        close()
        moveTo(11f, 2f)
        lineTo(13f, 2f)
        lineTo(13f, 5f)
        lineTo(11f, 5f)
        close()
        moveTo(11f, 19f)
        lineTo(13f, 19f)
        lineTo(13f, 22f)
        lineTo(11f, 22f)
        close()
        moveTo(2f, 11f)
        lineTo(5f, 11f)
        lineTo(5f, 13f)
        lineTo(2f, 13f)
        close()
        moveTo(19f, 11f)
        lineTo(22f, 11f)
        lineTo(22f, 13f)
        lineTo(19f, 13f)
        close()
    }.build()



