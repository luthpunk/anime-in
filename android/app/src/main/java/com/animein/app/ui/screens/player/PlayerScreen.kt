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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Arrangement
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
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.hls.HlsMediaSource
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
    val animeTitle by viewModel.animeTitle.collectAsState()
    val animeSynopsis by viewModel.animeSynopsis.collectAsState()
    val currentEpisodeTitle by viewModel.currentEpisodeTitle.collectAsState()
    val currentEpisodeId by viewModel.currentEpisodeId.collectAsState()
    val isVideoLoading by viewModel.isVideoLoading.collectAsState()
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val isTV = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION

    var isFullscreen by remember { mutableStateOf(isTV) }

    BackHandler(enabled = true) {
        if (isFullscreen && !isTV) {
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
                            val playPauseFocusRequester = remember { FocusRequester() }

                            LaunchedEffect(showControls) {
                                if (showControls && isTV) {
                                    try {
                                        playPauseFocusRequester.requestFocus()
                                    } catch (e: Exception) {}
                                }
                            }
                            var isPlaying by remember { mutableStateOf(true) }
                            var isBuffering by remember { mutableStateOf(false) }
                            var currentPosition by remember { mutableLongStateOf(savedProgress) }
                            var duration by remember { mutableLongStateOf(0L) }
                            val speed by viewModel.playbackSpeed.collectAsState()
                            var playerSize by remember { mutableStateOf(IntSize.Zero) }

                            var showSettingsDropdown by remember { mutableStateOf(false) }
                            val allMirrors = remember(detail) {
                                (detail.mirror1?.mirrorList?.map { UiMirrorItem(it.id, it.host, "360p") } ?: emptyList()) +
                                (detail.mirror2?.mirrorList?.map { UiMirrorItem(it.id, it.host, "480p") } ?: emptyList()) +
                                (detail.mirror3?.mirrorList?.map { UiMirrorItem(it.id, it.host, "720p") } ?: emptyList())
                            }
                            var selectedResolution by remember(allMirrors) {
                                mutableStateOf(
                                    allMirrors.firstOrNull { it.resolution == "480p" }?.resolution
                                        ?: allMirrors.firstOrNull()?.resolution ?: "480p"
                                )
                            }

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
                                    .setUserAgent("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                                    .setAllowCrossProtocolRedirects(true)
                                    .setConnectTimeoutMs(15000)
                                    .setReadTimeoutMs(20000)
                            }

                            val cacheDataSourceFactory = remember {
                                CacheDataSource.Factory()
                                    .setCache(viewModel.simpleCache)
                                    .setUpstreamDataSourceFactory(httpDataSourceFactory)
                                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                            }

                            // DefaultMediaSourceFactory — mendukung HLS (.m3u8) dan Progressive (.mp4) secara otomatis
                            val mediaSourceFactory = remember {
                                DefaultMediaSourceFactory(cacheDataSourceFactory)
                            }

                            val loadControl = remember {
                                DefaultLoadControl.Builder()
                                    .setBufferDurationsMs(
                                        15000, // minBufferMs
                                        50000, // maxBufferMs
                                        2000,  // bufferForPlaybackMs (2s langsung play)
                                        3000   // bufferForPlaybackAfterRebufferMs (3s setelah rebuffer)
                                    )
                                    .build()
                            }

                            val exoPlayer = remember {
                                ExoPlayer.Builder(context)
                                    .setMediaSourceFactory(mediaSourceFactory)
                                    .setLoadControl(loadControl)
                                    .build().apply {
                                        playWhenReady = true
                                        // TrackSelectionParameters: adaptive bitrate sesuai kecepatan jaringan
                                        trackSelectionParameters = TrackSelectionParameters.Builder(context)
                                            .setMaxVideoSizeSd() // mulai dari SD, naik adaptif
                                            .setForceHighestSupportedBitrate(false) // biarkan adaptif
                                            .build()
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
 
                            // Monitor Player state — termasuk deteksi buffering
                            DisposableEffect(exoPlayer) {
                                val listener = object : Player.Listener {
                                    override fun onIsPlayingChanged(playing: Boolean) {
                                        isPlaying = playing
                                    }
                                    override fun onPlaybackStateChanged(state: Int) {
                                        when (state) {
                                            Player.STATE_READY -> {
                                                duration = exoPlayer.duration
                                                isBuffering = false
                                            }
                                            Player.STATE_BUFFERING -> {
                                                isBuffering = true
                                            }
                                            Player.STATE_ENDED, Player.STATE_IDLE -> {
                                                isBuffering = false
                                            }
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

                            // Hide controls after 3.5 seconds, unless settings dropdown is open
                            LaunchedEffect(showControls, isPlaying, showSettingsDropdown) {
                                if (showControls && isPlaying && !showSettingsDropdown) {
                                    delay(3500)
                                    showControls = false
                                }
                            }

                            // Dismiss settings dropdown when controls are hidden
                            LaunchedEffect(showControls) {
                                if (!showControls) {
                                    showSettingsDropdown = false
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
                                        streamUrl.contains("vidhide", ignoreCase = true) ->
                                            "https://vidhide.com/"
                                        streamUrl.contains("desustream", ignoreCase = true) ||
                                        streamUrl.contains("desu", ignoreCase = true) ->
                                            "https://otakudesu.blog/"
                                        streamUrl.contains("streamwish", ignoreCase = true) ||
                                        streamUrl.contains("awish", ignoreCase = true) ->
                                            "https://streamwish.to/"
                                        streamUrl.contains("filelion", ignoreCase = true) ||
                                        streamUrl.contains("lion", ignoreCase = true) ->
                                            "https://filelion.to/"
                                        streamUrl.contains("halahgan", ignoreCase = true) ||
                                        streamUrl.contains("berkasdrive", ignoreCase = true) ||
                                        streamUrl.contains("nimegami", ignoreCase = true) ->
                                            "https://nimegami.id/"
                                        else -> "https://otakudesu.blog/"
                                    }

                                    // Set Referer langsung di MediaItem.RequestMetadata
                                    // agar setiap URL punya header yang tepat dan tidak saling override
                                    httpDataSourceFactory.setDefaultRequestProperties(
                                        mapOf(
                                            "Referer" to referer,
                                            "Origin" to referer.trimEnd('/')
                                        )
                                    )

                                    val mediaItem = MediaItem.Builder()
                                        .setUri(streamUrl)
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setTitle(detail.title)
                                                .build()
                                        )
                                        .build()

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
                                            animeTitle = animeTitle.ifBlank { viewModel.animeId },
                                            episodeTitle = currentEpisodeTitle.ifBlank { "Episode" },
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
                                                    animeTitle = animeTitle.ifBlank { viewModel.animeId },
                                                    episodeTitle = currentEpisodeTitle.ifBlank { "Episode" },
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

                            val prevEpIdBox by viewModel.prevEpisodeId.collectAsState()
                            val nextEpIdBox by viewModel.nextEpisodeId.collectAsState()

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusable()
                                    .onKeyEvent { keyEvent ->
                                        if (!isTV) return@onKeyEvent false
                                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                                        when (keyEvent.key) {
                                            Key.Back -> false // let BackHandler handle it
                                            // Any key when controls hidden → show controls
                                            Key.DirectionCenter, Key.Enter,
                                            Key.DirectionLeft, Key.DirectionRight,
                                            Key.DirectionUp, Key.DirectionDown,
                                            Key.Menu, Key.F -> {
                                                if (!showControls) {
                                                    showControls = true
                                                    // Menu/F key: also open settings right away
                                                    if (keyEvent.key == Key.Menu || keyEvent.key == Key.F) {
                                                        showSettingsDropdown = true
                                                    }
                                                    true
                                                } else {
                                                    // Controls visible — D-Pad actions
                                                    when (keyEvent.key) {
                                                        Key.DirectionCenter, Key.Enter -> {
                                                            if (exoPlayer.isPlaying) exoPlayer.pause()
                                                            else exoPlayer.play()
                                                            true
                                                        }
                                                        Key.DirectionLeft -> {
                                                            val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                                                            exoPlayer.seekTo(target)
                                                            currentPosition = target
                                                            true
                                                        }
                                                        Key.DirectionRight -> {
                                                            val target = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                                                            exoPlayer.seekTo(target)
                                                            currentPosition = target
                                                            true
                                                        }
                                                        Key.DirectionUp -> {
                                                            if (showSettingsDropdown) {
                                                                // navigate settings options (handled by dropdown itself)
                                                                false
                                                            } else {
                                                                if (prevEpIdBox != null) viewModel.selectEpisode(prevEpIdBox!!)
                                                                true
                                                            }
                                                        }
                                                        Key.DirectionDown -> {
                                                            if (showSettingsDropdown) {
                                                                false
                                                            } else {
                                                                if (nextEpIdBox != null) viewModel.selectEpisode(nextEpIdBox!!)
                                                                true
                                                            }
                                                        }
                                                        Key.Menu, Key.F -> {
                                                            // Toggle settings dropdown
                                                            showSettingsDropdown = !showSettingsDropdown
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                }
                                            }
                                            else -> false
                                        }
                                    }
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

                                if (isVideoLoading || isBuffering) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = NeonPurple)
                                    }
                                }

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
                                // --- Gesture Indicator Overlay (Volume / Brightness) ---
                                if (isDraggingBrightness || isDraggingVolume) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.80f)),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.size(110.dp)
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.fillMaxSize().padding(12.dp)
                                            ) {
                                                val percentage = if (isDraggingBrightness) (brightnessValue * 100).toInt() else (volumePercent * 100).toInt()
                                                Icon(
                                                    imageVector = if (isDraggingBrightness) BrightnessIcon else VolumeUpIcon,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                LinearProgressIndicator(
                                                    progress = percentage / 100f,
                                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                                    color = if (isDraggingBrightness) Color(0xFFFFC107) else Color(0xFF4FC3F7),
                                                    trackColor = Color.White.copy(alpha = 0.2f)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "$percentage%",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // --- Buffering Spinner ---
                                if (isBuffering) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }
                                 if (showControls) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.6f))
                                    ) {
                                        var isPrevFocused by remember { mutableStateOf(false) }
                                        var isPlayFocused by remember { mutableStateOf(false) }
                                        var isNextFocused by remember { mutableStateOf(false) }
                                        var isSettingsFocused by remember { mutableStateOf(false) }
                                        var isFullscreenBtnFocused by remember { mutableStateOf(false) }

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
                                                    text = if (animeTitle.isNotBlank()) {
                                                        val epNum = extractEpisodeNumber(currentEpisodeTitle)
                                                        if (epNum.isNotBlank()) "$animeTitle — Episode $epNum"
                                                        else {
                                                            val cleanedEp = currentEpisodeTitle.replace(animeTitle, "", ignoreCase = true)
                                                                .trim().trimStart('-', '—').trim()
                                                            if (cleanedEp.isNotBlank()) "$animeTitle — $cleanedEp"
                                                            else animeTitle
                                                        }
                                                    } else {
                                                        detail.title
                                                    },
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        // 2. Center row: Previous Ep + Play/Pause + Next Ep
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val prevEpId by viewModel.prevEpisodeId.collectAsState()
                                            val nextEpId by viewModel.nextEpisodeId.collectAsState()

                                            // Previous Episode Button
                                            IconButton(
                                                onClick = { if (prevEpId != null) viewModel.selectEpisode(prevEpId!!) },
                                                enabled = prevEpId != null,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .onFocusChanged { isPrevFocused = it.isFocused }
                                                    .focusable()
                                                    .handleRemoteClick { if (prevEpId != null) viewModel.selectEpisode(prevEpId!!) }
                                                    .background(
                                                        if (isPrevFocused) NeonPurple.copy(alpha = 0.4f) else Color.Transparent,
                                                        RoundedCornerShape(24.dp)
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = SkipPreviousIcon,
                                                    contentDescription = "Episode Sebelumnya",
                                                    tint = if (prevEpId != null) Color.White else Color.White.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }

                                            // Play/Pause Button
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
                                                    .focusRequester(playPauseFocusRequester)
                                                    .onFocusChanged { isPlayFocused = it.isFocused }
                                                    .focusable()
                                                    .handleRemoteClick { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }
                                                    .background(
                                                        if (isPlayFocused) NeonPurple else Color.White.copy(alpha = 0.2f),
                                                        RoundedCornerShape(28.dp)
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) PauseIcon else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }

                                            // Next Episode Button
                                            IconButton(
                                                onClick = { if (nextEpId != null) viewModel.selectEpisode(nextEpId!!) },
                                                enabled = nextEpId != null,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .onFocusChanged { isNextFocused = it.isFocused }
                                                    .focusable()
                                                    .handleRemoteClick { if (nextEpId != null) viewModel.selectEpisode(nextEpId!!) }
                                                    .background(
                                                        if (isNextFocused) NeonPurple.copy(alpha = 0.4f) else Color.Transparent,
                                                        RoundedCornerShape(24.dp)
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = SkipNextIcon,
                                                    contentDescription = "Episode Selanjutnya",
                                                    tint = if (nextEpId != null) Color.White else Color.White.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }

                                        // 3. Bottom controls — settings & fullscreen on top, seekbar below
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 2.dp),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Settings dropdown menu containing speed and resolution
                                                Box {
                                                    IconButton(
                                                        onClick = { showSettingsDropdown = true },
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .onFocusChanged { isSettingsFocused = it.isFocused }
                                                            .focusable()
                                                            .handleRemoteClick { showSettingsDropdown = true }
                                                            .background(
                                                                if (isSettingsFocused) NeonPurple.copy(alpha = 0.4f) else Color.Transparent,
                                                                RoundedCornerShape(18.dp)
                                                            )
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Settings,
                                                            contentDescription = "Pengaturan",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }

                                                    DropdownMenu(
                                                        expanded = showSettingsDropdown,
                                                        onDismissRequest = { showSettingsDropdown = false },
                                                        modifier = Modifier.background(DarkSurface)
                                                    ) {
                                                        // 1. Resolutions
                                                        Text(
                                                            text = "Kualitas Video",
                                                            color = Color.Gray,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                                        )
                                                        allMirrors.forEach { mirror ->
                                                            val isSelected = selectedResolution == mirror.resolution
                                                            DropdownMenuItem(
                                                                text = {
                                                                    Text(
                                                                        text = mirror.resolution,
                                                                        color = if (isSelected) NeonPurple else Color.White,
                                                                        fontSize = 13.sp,
                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                                    )
                                                                },
                                                                onClick = {
                                                                    selectedResolution = mirror.resolution
                                                                    viewModel.switchMirror(mirror.id)
                                                                    showSettingsDropdown = false
                                                                }
                                                            )
                                                        }

                                                        Divider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

                                                        // 2. Playback Speed
                                                        Text(
                                                            text = "Kecepatan Pemutaran",
                                                            color = Color.Gray,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                                        )
                                                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { value ->
                                                            val isSelected = speed == value
                                                            DropdownMenuItem(
                                                                text = {
                                                                    Text(
                                                                        text = "${value}x",
                                                                        color = if (isSelected) NeonPurple else Color.White,
                                                                        fontSize = 13.sp,
                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                                    )
                                                                },
                                                                onClick = {
                                                                    viewModel.setPlaybackSpeed(value)
                                                                    showSettingsDropdown = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                IconButton(
                                                    onClick = { isFullscreen = !isFullscreen },
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .onFocusChanged { isFullscreenBtnFocused = it.isFocused }
                                                        .focusable()
                                                        .handleRemoteClick { isFullscreen = !isFullscreen }
                                                        .background(
                                                            if (isFullscreenBtnFocused) NeonPurple.copy(alpha = 0.4f) else Color.Transparent,
                                                            RoundedCornerShape(18.dp)
                                                        )
                                                ) {
                                                    Icon(
                                                        imageVector = if (isFullscreen) FullscreenExitIcon else FullscreenIcon,
                                                        contentDescription = "Fullscreen",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
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
                                                        .padding(horizontal = 6.dp)
                                                        .focusable(),
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

                                            // TV D-Pad hint — hanya muncul di Android TV
                                            if (isTV) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 4.dp, bottom = 2.dp),
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    val hs = androidx.compose.ui.text.TextStyle(
                                                        fontSize = 10.sp,
                                                        color = Color.White.copy(alpha = 0.5f)
                                                    )
                                                    Text("⏎ Play/Pause  ·  ◀▶ ±10s  ·  ▲▼ Prev/Next Ep  ·  ☰ Pengaturan", style = hs)
                                                }
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
                            // Judul anime & episode yang sedang diputar
                            Text(
                                animeTitle.ifBlank { "Memuat..." },
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentEpisodeTitle.isNotBlank()) {
                                val epNum = extractEpisodeNumber(currentEpisodeTitle)
                                Text(
                                    if (epNum.isNotBlank()) "Episode $epNum" else currentEpisodeTitle,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        // Synopsis block
                        if (animeSynopsis.isNotBlank()) {
                            item {
                                var synopsisExpanded by remember { mutableStateOf(false) }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Sinopsis",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    animeSynopsis,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { synopsisExpanded = !synopsisExpanded }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (synopsisExpanded) "Sembunyikan ▲" else "Selengkapnya ▼",
                                    color = NeonPurple,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { synopsisExpanded = !synopsisExpanded }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
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
                                            val isCurrent = AnimeIdUtils.normalizeEpisodeId(episode.id) == AnimeIdUtils.normalizeEpisodeId(currentEpisodeId)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isCurrent) NeonPurple else DarkSurface)
                                                    .clickable {
                                                        viewModel.selectEpisode(episode.id)
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

// Helper extension: handle Android TV remote D-Pad Center / Enter key
private fun Modifier.handleRemoteClick(onClick: () -> Unit): Modifier = this.then(
    Modifier.onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown &&
            (event.key == Key.Enter || event.key == Key.DirectionCenter)
        ) {
            onClick()
            true
        } else false
    }
)

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

val SkipPreviousIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SkipPrevious",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.White)
    ) {
        moveTo(18f, 18f)
        lineTo(9.5f, 12f)
        lineTo(18f, 6f)
        close()
        moveTo(8f, 6f)
        lineTo(8f, 18f)
        lineTo(6f, 18f)
        lineTo(6f, 6f)
        close()
    }.build()

private fun extractEpisodeNumber(title: String): String {
    val matches = Regex("\\b(?:Episode|Ep|E|eps)\\b\\s*(\\d+)|\\b(\\d+)\\b", RegexOption.IGNORE_CASE)
        .findAll(title)
        .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        .toList()
    return if (matches.isNotEmpty()) matches.last() else ""
}

// =====================================================================
// Icon: Volume Speaker (bentuk speaker asli dengan 2 gelombang suara)
// =====================================================================
val VolumeUpIcon: ImageVector
    get() = ImageVector.Builder(
        name = "VolumeUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Speaker body (segitiga + kotak)
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.White)) {
            moveTo(3f, 9f)
            lineTo(3f, 15f)
            lineTo(7f, 15f)
            lineTo(12f, 20f)
            lineTo(12f, 4f)
            lineTo(7f, 9f)
            close()
        }
        // Gelombang kecil (dalam)
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
            strokeLineWidth = 1.8f
        ) {
            moveTo(14.5f, 9.5f)
            arcTo(3.5f, 3.5f, 0f, false, true, 14.5f, 14.5f)
        }
        // Gelombang besar (luar)
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
            strokeLineWidth = 1.8f
        ) {
            moveTo(17f, 7f)
            arcTo(6f, 6f, 0f, false, true, 17f, 17f)
        }
    }.build()

// =====================================================================
// Icon: Brightness (matahari bulat dengan 8 sinar)
// =====================================================================
val BrightnessIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Brightness",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Lingkaran tengah (matahari)
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.White)) {
            moveTo(12f, 9f)
            arcTo(3f, 3f, 0f, false, true, 15f, 12f)
            arcTo(3f, 3f, 0f, false, true, 12f, 15f)
            arcTo(3f, 3f, 0f, false, true, 9f, 12f)
            arcTo(3f, 3f, 0f, false, true, 12f, 9f)
            close()
        }
        // Sinar atas
        path(stroke = androidx.compose.ui.graphics.SolidColor(Color.White), strokeLineWidth = 1.8f) {
            moveTo(12f, 2f); lineTo(12f, 5f)
        }
        // Sinar bawah
        path(stroke = androidx.compose.ui.graphics.SolidColor(Color.White), strokeLineWidth = 1.8f) {
            moveTo(12f, 19f); lineTo(12f, 22f)
        }
        // Sinar kiri
        path(stroke = androidx.compose.ui.graphics.SolidColor(Color.White), strokeLineWidth = 1.8f) {
            moveTo(2f, 12f); lineTo(5f, 12f)
        }
        // Sinar kanan
        path(stroke = androidx.compose.ui.graphics.SolidColor(Color.White), strokeLineWidth = 1.8f) {
            moveTo(19f, 12f); lineTo(22f, 12f)
        }
        // Sinar kiri-atas
        path(stroke = androidx.compose.ui.graphics.SolidColor(Color.White), strokeLineWidth = 1.8f) {
            moveTo(4.22f, 4.22f); lineTo(6.34f, 6.34f)
        }
        // Sinar kanan-bawah
        path(stroke = androidx.compose.ui.graphics.SolidColor(Color.White), strokeLineWidth = 1.8f) {
            moveTo(17.66f, 17.66f); lineTo(19.78f, 19.78f)
        }
        // Sinar kanan-atas
        path(stroke = androidx.compose.ui.graphics.SolidColor(Color.White), strokeLineWidth = 1.8f) {
            moveTo(19.78f, 4.22f); lineTo(17.66f, 6.34f)
        }
        // Sinar kiri-bawah
        path(stroke = androidx.compose.ui.graphics.SolidColor(Color.White), strokeLineWidth = 1.8f) {
            moveTo(6.34f, 17.66f); lineTo(4.22f, 19.78f)
        }
    }.build()



