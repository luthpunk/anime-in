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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import okhttp3.OkHttpClient
import okhttp3.Interceptor
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
import com.animein.app.ui.theme.DarkSurfaceVariant
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.ui.theme.TextSecondary
import com.animein.app.ui.components.PremiumLoadingSpinner
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
                        PremiumLoadingSpinner(color = NeonPurple)
                    }
                }
                is PlayerUiState.Success -> {
                    val detail = state.data
                    val streamUrl = detail.linkStream ?: ""

                    val allMirrors = remember(detail) {
                        val parseResolution = { host: String, fallback: String ->
                            when {
                                host.contains("1080") -> "1080p"
                                host.contains("720") -> "720p"
                                host.contains("480") -> "480p"
                                host.contains("360") -> "360p"
                                else -> fallback
                            }
                        }
                        (detail.mirror1?.mirrorList?.map { UiMirrorItem(it.id, it.host, parseResolution(it.host, "360p")) } ?: emptyList()) +
                        (detail.mirror2?.mirrorList?.map { UiMirrorItem(it.id, it.host, parseResolution(it.host, "480p")) } ?: emptyList()) +
                        (detail.mirror3?.mirrorList?.map { UiMirrorItem(it.id, it.host, parseResolution(it.host, "720p")) } ?: emptyList())
                    }
                    var selectedResolution by remember(allMirrors) {
                        mutableStateOf(
                            allMirrors.firstOrNull { it.resolution == "480p" }?.resolution
                                ?: allMirrors.firstOrNull()?.resolution ?: "480p"
                        )
                    }
                    var showQualityDropdown by remember { mutableStateOf(false) }
                    var showSpeedDropdown by remember { mutableStateOf(false) }
                    val speed by viewModel.playbackSpeed.collectAsState()

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Video Player Area - ExoPlayer as default
                        Box(
                            modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Color.Black)
                        ) {
                             var playPosition by remember { mutableLongStateOf(savedProgress) }
                             var lastLoadedEpisodeId by remember { mutableStateOf("") }

                             // Sync playPosition when savedProgress loads for a new episode
                             LaunchedEffect(currentEpisodeId, savedProgress) {
                                 if (lastLoadedEpisodeId != currentEpisodeId) {
                                     playPosition = savedProgress
                                 }
                             }

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
                            var isPrevFocused by remember { mutableStateOf(false) }
                            var isPlayFocused by remember { mutableStateOf(false) }
                            var isNextFocused by remember { mutableStateOf(false) }
                            var isResFocused by remember { mutableStateOf(false) }
                            var isSpeedFocused by remember { mutableStateOf(false) }
                            var isFullscreenBtnFocused by remember { mutableStateOf(false) }
                            var isSliderFocused by remember { mutableStateOf(false) }
                            var currentPosition by remember { mutableLongStateOf(savedProgress) }
                            var duration by remember { mutableLongStateOf(0L) }
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
                            
                            // Build OkHttpClient with Referer interceptor — reads URL at request
                            // time so the correct Referer is always sent for each domain
                            val httpDataSourceFactory = remember {
                                val okHttpClient = OkHttpClient.Builder()
                                    .addNetworkInterceptor(Interceptor { chain ->
                                        val req = chain.request()
                                        val url = req.url.toString()
                                        val referer = when {
                                            url.contains("animein", ignoreCase = true) ||
                                            url.contains("storages", ignoreCase = true) ->
                                                "https://animeinweb.com/"
                                            url.contains("vidhide", ignoreCase = true) ->
                                                "https://vidhide.com/"
                                            url.contains("streamwish", ignoreCase = true) ||
                                            url.contains("awish", ignoreCase = true) ->
                                                "https://streamwish.to/"
                                            url.contains("nimegami", ignoreCase = true) ->
                                                "https://nimegami.id/"
                                            else -> "https://otakudesu.blog/"
                                        }
                                        chain.proceed(
                                            req.newBuilder()
                                                .header("Referer", referer)
                                                .header("Origin", referer.trimEnd('/'))
                                                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                                                .build()
                                        )
                                    })
                                    .build()
                                OkHttpDataSource.Factory(okHttpClient)
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

                            // Hide controls after 3.5 seconds, unless settings dropdown is open or a control is focused
                            val isAnyControlFocused = isPrevFocused || isPlayFocused || isNextFocused || isResFocused || isSpeedFocused || isFullscreenBtnFocused || isSliderFocused
                            LaunchedEffect(showControls, isPlaying, showQualityDropdown, showSpeedDropdown, isAnyControlFocused) {
                                if (showControls && isPlaying && !showQualityDropdown && !showSpeedDropdown && !isAnyControlFocused) {
                                    delay(3500)
                                    showControls = false
                                }
                            }

                            // Dismiss settings dropdown when controls are hidden
                            LaunchedEffect(showControls) {
                                if (!showControls) {
                                    showQualityDropdown = false
                                    showSpeedDropdown = false
                                }
                            }


                            // Set playback speed
                            LaunchedEffect(speed) {
                                exoPlayer.setPlaybackSpeed(speed)
                            }

                            // Load video URI
                            LaunchedEffect(streamUrl) {
                                if (streamUrl.isNotEmpty() && streamUrl != "-") {
                                    if (lastLoadedEpisodeId == currentEpisodeId) {
                                        // Same episode (changing quality/mirror), preserve current position
                                        val currentPos = exoPlayer.currentPosition
                                        if (currentPos > 0 && exoPlayer.playbackState != Player.STATE_IDLE) {
                                            playPosition = currentPos
                                        }
                                    } else {
                                        // Different episode (next/prev clicked), update tracked ID and use new progress
                                        lastLoadedEpisodeId = currentEpisodeId
                                        playPosition = savedProgress
                                    }

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
                            var isLongPressTriggered by remember { mutableStateOf(false) }
                            var controlsShownOnThisPress by remember { mutableStateOf(false) }
                            var dpadCenterPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusable()
                                    .onKeyEvent { keyEvent ->
                                         if (!isTV) return@onKeyEvent false
                                         
                                         if (keyEvent.type == KeyEventType.KeyDown) {
                                             if (!showControls) {
                                                 showControls = true
                                                 try {
                                                     playPauseFocusRequester.requestFocus()
                                                 } catch (e: Exception) {}
                                                 return@onKeyEvent true
                                             }
                                         }
                                         false
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
                                            keepScreenOn = true
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
                                        PremiumLoadingSpinner(color = NeonPurple)
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
                                // --- Gesture Indicator Overlay (Volume / Brightness Side Sliders) ---
                                if (isDraggingBrightness) {
                                    SideGestureIndicator(
                                        isLeft = true,
                                        progress = brightnessValue,
                                        icon = BrightnessIcon,
                                        color = Color(0xFFFFC107),
                                        modifier = Modifier.align(Alignment.CenterStart)
                                    )
                                }
                                if (isDraggingVolume) {
                                    SideGestureIndicator(
                                        isLeft = false,
                                        progress = volumePercent,
                                        icon = VolumeUpIcon,
                                        color = Color(0xFF4FC3F7),
                                        modifier = Modifier.align(Alignment.CenterEnd)
                                    )
                                }

                                // --- Buffering Spinner ---
                                if (isBuffering) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PremiumLoadingSpinner(
                                             color = Color.White,
                                             size = 48.dp
                                         )
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
                                                // 1. Resolution Dropdown Button
                                                Box {
                                                    Box(
                                                        modifier = Modifier
                                                            .height(32.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(
                                                                if (isResFocused) NeonPurple.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f)
                                                            )
                                                            .onFocusChanged { isResFocused = it.isFocused }
                                                            .focusable()
                                                            .clickable { showQualityDropdown = true }
                                                            .handleRemoteClick { showQualityDropdown = true }
                                                            .padding(horizontal = 10.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = selectedResolution,
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    DropdownMenu(
                                                        expanded = showQualityDropdown,
                                                        onDismissRequest = { showQualityDropdown = false },
                                                        modifier = Modifier
                                                            .background(DarkSurfaceVariant)
                                                            .border(
                                                                width = 1.dp,
                                                                brush = Brush.verticalGradient(
                                                                    colors = listOf(
                                                                        Color.White.copy(alpha = 0.12f),
                                                                        Color.White.copy(alpha = 0.02f)
                                                                    )
                                                                ),
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                    ) {
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
                                                                leadingIcon = {
                                                                    if (isSelected) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(6.dp)
                                                                                .background(NeonPurple, RoundedCornerShape(50))
                                                                        )
                                                                    }
                                                                },
                                                                colors = MenuDefaults.itemColors(
                                                                    focusedContainerColor = NeonPurple.copy(alpha = 0.25f),
                                                                    focusedTextColor = Color.White
                                                                ),
                                                                onClick = {
                                                                    selectedResolution = mirror.resolution
                                                                    viewModel.switchMirror(mirror.id)
                                                                    showQualityDropdown = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // 2. Playback Speed Dropdown Button
                                                Box {
                                                    Box(
                                                        modifier = Modifier
                                                            .height(32.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(
                                                                if (isSpeedFocused) NeonPurple.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f)
                                                            )
                                                            .onFocusChanged { isSpeedFocused = it.isFocused }
                                                            .focusable()
                                                            .clickable { showSpeedDropdown = true }
                                                            .handleRemoteClick { showSpeedDropdown = true }
                                                            .padding(horizontal = 10.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "${speed}x",
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    DropdownMenu(
                                                        expanded = showSpeedDropdown,
                                                        onDismissRequest = { showSpeedDropdown = false },
                                                        modifier = Modifier
                                                            .background(DarkSurfaceVariant)
                                                            .border(
                                                                width = 1.dp,
                                                                brush = Brush.verticalGradient(
                                                                    colors = listOf(
                                                                        Color.White.copy(alpha = 0.12f),
                                                                        Color.White.copy(alpha = 0.02f)
                                                                    )
                                                                ),
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                    ) {
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
                                                                leadingIcon = {
                                                                    if (isSelected) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(6.dp)
                                                                                .background(NeonPurple, RoundedCornerShape(50))
                                                                        )
                                                                    }
                                                                },
                                                                colors = MenuDefaults.itemColors(
                                                                    focusedContainerColor = NeonPurple.copy(alpha = 0.25f),
                                                                    focusedTextColor = Color.White
                                                                ),
                                                                onClick = {
                                                                    viewModel.setPlaybackSpeed(value)
                                                                    showSpeedDropdown = false
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
                                                        .onFocusChanged { isSliderFocused = it.isFocused }
                                                        .focusable()
                                                        .onKeyEvent { keyEvent ->
                                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                                when (keyEvent.key) {
                                                                    Key.DirectionLeft -> {
                                                                        val target = (currentPosition - 10000).coerceAtLeast(0L)
                                                                        currentPosition = target
                                                                        exoPlayer.seekTo(target)
                                                                        true
                                                                    }
                                                                    Key.DirectionRight -> {
                                                                        val target = (currentPosition + 10000).coerceAtMost(duration)
                                                                        currentPosition = target
                                                                        exoPlayer.seekTo(target)
                                                                        true
                                                                    }
                                                                    else -> false
                                                                }
                                                            } else false
                                                        },
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
                            }
                        }



                        // Collect and render episode grid selection
                        if (episodes.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Pilihan Episode", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            
                            val chunkedEpisodes = episodes.chunked(6)
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
                                                    .height(36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isCurrent) NeonPurple else DarkSurface)
                                                    .clickable {
                                                        viewModel.selectEpisode(episode.id)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = extractEpisodeNumber(episode.title),
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        val placeholdersCount = 6 - rowEpisodes.size
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

@Composable
fun SideGestureIndicator(
    isLeft: Boolean,
    progress: Float,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(60.dp)
            .padding(vertical = 32.dp, horizontal = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxHeight()
                .width(36.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .padding(vertical = 12.dp, horizontal = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            
            // Vertical Progress Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .width(6.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(progress.coerceIn(0f, 1f))
                        .background(color, RoundedCornerShape(3.dp))
                        .align(Alignment.BottomCenter)
                )
            }
        }
    }
}

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



