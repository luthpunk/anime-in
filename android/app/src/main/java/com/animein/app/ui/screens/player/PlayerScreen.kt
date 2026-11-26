package com.animein.app.ui.screens.player

import android.view.ViewGroup
import kotlin.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.animein.app.ui.theme.BgGraphite
import com.animein.app.ui.theme.DarkSurface
import com.animein.app.ui.theme.DarkSurfaceVariant
import com.animein.app.ui.theme.FgPrimary
import com.animein.app.ui.theme.FgSecondary
import com.animein.app.ui.theme.FgTertiary
import com.animein.app.ui.theme.FrostGlassBgTop
import com.animein.app.ui.theme.Hairline
import com.animein.app.ui.theme.HairlineStrong
import com.animein.app.ui.theme.LocalAccentPreset
import com.animein.app.ui.theme.MonoTextStyle
import com.animein.app.ui.theme.NeonPurple
import com.animein.app.ui.theme.OutlineGrey
import com.animein.app.ui.theme.OutlineGreySubtle
import com.animein.app.ui.theme.Surface1
import com.animein.app.ui.theme.Surface2
import com.animein.app.ui.theme.Surface3
import com.animein.app.ui.theme.TextSecondary
import com.animein.app.ui.components.MusikeErrorState
import com.animein.app.ui.components.PremiumLoadingSpinner
import com.animein.app.ui.components.RadiusTile
import com.animein.app.ui.components.frostGlass
import com.animein.app.ui.components.frostGlassBgBrush
import com.animein.app.ui.components.frostGlassBorderBrush
import com.animein.app.ui.components.musikeSurface
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

    val accent = LocalAccentPreset.current
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
        containerColor = BgGraphite,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgGraphite)
                .padding(if (isFullscreen) PaddingValues(0.dp) else innerPadding)
        ) {
            when (val state = uiState) {
                is PlayerUiState.Loading -> {
                    val initBtnSize = if (isFullscreen) 52.dp else 44.dp
                    val initOffsetY = if (isFullscreen) 0.dp else (-8).dp
                    Box(
                        modifier = if (isFullscreen) Modifier.fillMaxSize().background(Color.Black) else Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(y = initOffsetY)
                                .size(initBtnSize)
                                .clip(CircleShape)
                                .background(Surface2.copy(alpha = 0.88f), CircleShape)
                                .border(1.dp, HairlineStrong, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            PremiumLoadingSpinner(
                                color = accent.accent,
                                size = initBtnSize - 6.dp,
                                strokeWidth = 2.5.dp
                            )
                        }
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
                             var isLocked by remember { mutableStateOf(false) }
                             var lastUserInteractionTick by remember { mutableLongStateOf(0L) }
                             var suppressNextCenterUp by remember { mutableStateOf(false) }
                             val playerContainerFocusRequester = remember { FocusRequester() }
                             val playPauseFocusRequester = remember { FocusRequester() }
                             val lockBtnFocusRequester = remember { FocusRequester() }
                             val skipIntroFocusRequester = remember { FocusRequester() }

                            LaunchedEffect(showControls, isLocked) {
                                if (isTV) {
                                    if (showControls && !isLocked) {
                                        delay(25)
                                        try {
                                            playPauseFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    } else if (showControls && isLocked) {
                                        delay(25)
                                        try {
                                            lockBtnFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    } else {
                                        // Saat kontrol tersembunyi, fokuskan langsung ke container utama player
                                        // agar cukup 1x tekan tombol OK/D-pad untuk memunculkan menu kontrol
                                        try {
                                            playerContainerFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
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
                            var isLockBtnFocused by remember { mutableStateOf(false) }
                            var currentPosition by remember { mutableLongStateOf(savedProgress) }
                            var duration by remember { mutableLongStateOf(0L) }
                            var playerSize by remember { mutableStateOf(IntSize.Zero) }

                            val introInterval by viewModel.introInterval.collectAsState()
                            val isInsideIntro = remember(currentPosition, introInterval) {
                                val interval = introInterval
                                interval != null && currentPosition >= interval.startTimeMs && currentPosition < (interval.endTimeMs - 1000L)
                            }
                            var hasAutoFocusedIntro by remember(introInterval, currentEpisodeId) { mutableStateOf(false) }

                            LaunchedEffect(isInsideIntro) {
                                if (isInsideIntro && isTV && !hasAutoFocusedIntro) {
                                    hasAutoFocusedIntro = true
                                    delay(120)
                                    try {
                                        skipIntroFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                }
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

                            // Auto-hide controls after 3.5 seconds of no remote input / focus movement / touch interaction
                            LaunchedEffect(
                                showControls,
                                isPlaying,
                                showQualityDropdown,
                                showSpeedDropdown,
                                isLocked,
                                lastUserInteractionTick
                            ) {
                                if (showControls && !showQualityDropdown && !showSpeedDropdown) {
                                    delay(3500L)
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
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (!isTV) return@onPreviewKeyEvent false

                                        val isWakeKey = keyEvent.key == Key.DirectionCenter ||
                                            keyEvent.key == Key.Enter ||
                                            keyEvent.key == Key.DirectionLeft ||
                                            keyEvent.key == Key.DirectionRight ||
                                            keyEvent.key == Key.DirectionUp ||
                                            keyEvent.key == Key.DirectionDown

                                        if (!showControls) {
                                            if (keyEvent.type == KeyEventType.KeyDown && isWakeKey) {
                                                showControls = true
                                                lastUserInteractionTick++
                                                if (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) {
                                                    suppressNextCenterUp = true
                                                }
                                                return@onPreviewKeyEvent true
                                            }
                                            return@onPreviewKeyEvent false
                                        } else {
                                            // Controls are currently visible: reset auto-hide timer on any remote key activity
                                            if (keyEvent.type == KeyEventType.KeyDown || keyEvent.type == KeyEventType.KeyUp) {
                                                lastUserInteractionTick++
                                            }
                                            if (suppressNextCenterUp && keyEvent.type == KeyEventType.KeyUp &&
                                                (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)
                                            ) {
                                                suppressNextCenterUp = false
                                                return@onPreviewKeyEvent true
                                            }
                                            return@onPreviewKeyEvent false
                                        }
                                    }
                                    .focusRequester(playerContainerFocusRequester)
                                    .focusable()
                                    .onSizeChanged { playerSize = it }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { offset ->
                                                // Jika layar terkunci, abaikan semua gestur kecuali tap tunggal
                                                if (isLocked) return@detectTapGestures
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
                                                // Tap: tampilkan/sembunyikan kontrol (termasuk saat terkunci agar bisa unlock)
                                                showControls = !showControls
                                                if (showControls) lastUserInteractionTick++
                                            }
                                        )
                                    }
                                    .pointerInput(isLocked) {
                                        if (isLocked) return@pointerInput
                                        var isLeft = false
                                        var dragStartValue = 0f
                                        var accumulatedDeltaY = 0f
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                if (isLocked) return@detectDragGestures
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
                                                if (isLocked) return@detectDragGestures
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
                                            isFocusable = false
                                            isFocusableInTouchMode = false
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
                                // --- Gesture Indicator Overlay (Volume / Brightness Side Sliders) ---
                                if (isDraggingBrightness) {
                                    SideGestureIndicator(
                                        isLeft = true,
                                        progress = brightnessValue,
                                        icon = BrightnessIcon,
                                        color = accent.accent,
                                        modifier = Modifier.align(Alignment.CenterStart)
                                    )
                                }
                                if (isDraggingVolume) {
                                    SideGestureIndicator(
                                        isLeft = false,
                                        progress = volumePercent,
                                        icon = VolumeUpIcon,
                                        color = accent.accent,
                                        modifier = Modifier.align(Alignment.CenterEnd)
                                    )
                                }

                                val isLoadingOrBuffering = isVideoLoading || isBuffering

                                // --- Buffering Spinner (Saat kontrol tersembunyi / layar terkunci) ---
                                if (isLoadingOrBuffering && (!showControls || isLocked)) {
                                    val bufferBtnSize = if (isFullscreen) 52.dp else 44.dp
                                    val bufferOffsetY = if (isFullscreen) 0.dp else (-8).dp
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .offset(y = bufferOffsetY)
                                            .size(bufferBtnSize)
                                            .clip(CircleShape)
                                            .background(Surface2.copy(alpha = 0.88f), CircleShape)
                                            .border(1.dp, HairlineStrong, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PremiumLoadingSpinner(
                                            color = accent.accent,
                                            size = bufferBtnSize - 6.dp,
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                }
                                 if (showControls) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = if (isLocked) 0.3f else 0.58f))
                                    ) {

                                        val controlChipHeight = if (isFullscreen) 32.dp else 28.dp
                                        val controlChipShape = CircleShape
                                        val centerOffsetY = if (isFullscreen) 0.dp else (-8).dp

                                        // ========== TOMBOL KUNCI MINIMALIS (Sisi kiri tengah) ==========
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .offset(y = centerOffsetY)
                                                .padding(start = if (isFullscreen) 16.dp else 12.dp)
                                        ) {
                                            val lockSize = if (isFullscreen) 38.dp else 32.dp
                                            Box(
                                                modifier = Modifier
                                                    .size(lockSize)
                                                    .musikeSurface(
                                                        shape = CircleShape,
                                                        isFocused = isLockBtnFocused,
                                                        accentColor = accent.accent,
                                                        containerColor = if (isLocked) accent.accentSoft else Surface2.copy(alpha = 0.88f),
                                                        focusedContainerColor = Surface3,
                                                        isActive = isLocked
                                                    )
                                                    .focusRequester(lockBtnFocusRequester)
                                                    .onFocusChanged {
                                                        isLockBtnFocused = it.isFocused
                                                        if (it.isFocused) lastUserInteractionTick++
                                                    }
                                                    .clickable {
                                                        isLocked = !isLocked
                                                        lastUserInteractionTick++
                                                        if (!isLocked) showControls = true
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isLocked) LockClosedIcon else LockOpenIcon,
                                                    contentDescription = if (isLocked) "Buka Kunci" else "Kunci Layar",
                                                    tint = if (isLocked || isLockBtnFocused) accent.accent else FgPrimary,
                                                    modifier = Modifier.size(if (isFullscreen) 17.dp else 15.dp)
                                                )
                                            }
                                        }

                                        // Semua kontrol lainnya HANYA tampil jika TIDAK terkunci
                                        if (!isLocked) {

                                        if (isFullscreen) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.TopStart)
                                                    .background(
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                                                        )
                                                    )
                                                    .padding(horizontal = 20.dp, vertical = 12.dp),
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
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        // 2. Center row: Cinema Prev Ep + Solid Accent Play/Pause (ala a-music) + Next Ep
                                        val sideBtnSize = if (isFullscreen) 42.dp else 36.dp
                                        val sideIconSize = if (isFullscreen) 20.dp else 17.dp
                                        val mainBtnSize = if (isFullscreen) 54.dp else 46.dp
                                        val mainIconSize = if (isFullscreen) 26.dp else 22.dp
                                        val centerSpacing = if (isFullscreen) 26.dp else 18.dp
                                        val circleBtnShape = CircleShape

                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .offset(y = centerOffsetY),
                                            horizontalArrangement = Arrangement.spacedBy(centerSpacing, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val prevEpId by viewModel.prevEpisodeId.collectAsState()
                                            val nextEpId by viewModel.nextEpisodeId.collectAsState()

                                            // Previous Episode Button
                                            Box(
                                                modifier = Modifier
                                                    .size(sideBtnSize)
                                                    .musikeSurface(
                                                        shape = circleBtnShape,
                                                        isFocused = isPrevFocused,
                                                        accentColor = accent.accent,
                                                        containerColor = Surface2.copy(alpha = 0.85f),
                                                        focusedContainerColor = Surface3
                                                    )
                                                    .onFocusChanged {
                                                        isPrevFocused = it.isFocused
                                                        if (it.isFocused) lastUserInteractionTick++
                                                    }
                                                    .clickable(enabled = prevEpId != null) {
                                                        lastUserInteractionTick++
                                                        if (prevEpId != null) viewModel.selectEpisode(prevEpId!!)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = SkipPreviousIcon,
                                                    contentDescription = "Episode Sebelumnya",
                                                    tint = when {
                                                        prevEpId == null -> FgTertiary.copy(alpha = 0.35f)
                                                        isPrevFocused -> accent.accent
                                                        else -> FgPrimary
                                                    },
                                                    modifier = Modifier.size(sideIconSize)
                                                )
                                            }

                                            // Play/Pause Button (Signature a-music Solid Accent Circle)
                                            Box(
                                                modifier = Modifier
                                                    .size(mainBtnSize)
                                                    .clip(circleBtnShape)
                                                    .background(accent.accent, circleBtnShape)
                                                    .then(
                                                        if (isPlayFocused) Modifier.border(2.5.dp, Color.White, circleBtnShape)
                                                        else Modifier
                                                    )
                                                    .focusRequester(playPauseFocusRequester)
                                                    .onFocusChanged {
                                                        isPlayFocused = it.isFocused
                                                        if (it.isFocused) lastUserInteractionTick++
                                                    }
                                                    .clickable {
                                                        lastUserInteractionTick++
                                                        if (exoPlayer.isPlaying || exoPlayer.playWhenReady) {
                                                            exoPlayer.pause()
                                                        } else {
                                                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                                                exoPlayer.seekTo(0)
                                                            }
                                                            exoPlayer.play()
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isLoadingOrBuffering) {
                                                    PremiumLoadingSpinner(
                                                        color = accent.onAccent,
                                                        size = mainBtnSize - 6.dp,
                                                        strokeWidth = 2.5.dp
                                                    )
                                                }
                                                Icon(
                                                    imageVector = if (isPlaying) PauseIcon else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    tint = accent.onAccent,
                                                    modifier = Modifier.size(mainIconSize)
                                                )
                                            }

                                            // Next Episode Button
                                            Box(
                                                modifier = Modifier
                                                    .size(sideBtnSize)
                                                    .musikeSurface(
                                                        shape = circleBtnShape,
                                                        isFocused = isNextFocused,
                                                        accentColor = accent.accent,
                                                        containerColor = Surface2.copy(alpha = 0.85f),
                                                        focusedContainerColor = Surface3
                                                    )
                                                    .onFocusChanged {
                                                        isNextFocused = it.isFocused
                                                        if (it.isFocused) lastUserInteractionTick++
                                                    }
                                                    .clickable(enabled = nextEpId != null) {
                                                        lastUserInteractionTick++
                                                        if (nextEpId != null) viewModel.selectEpisode(nextEpId!!)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = SkipNextIcon,
                                                    contentDescription = "Episode Selanjutnya",
                                                    tint = when {
                                                        nextEpId == null -> FgTertiary.copy(alpha = 0.35f)
                                                        isNextFocused -> accent.accent
                                                        else -> FgPrimary
                                                    },
                                                    modifier = Modifier.size(sideIconSize)
                                                )
                                            }
                                        }

                                        // 3. Bottom controls — Monospace time left, Pill chips right, Accent Seekbar bottom
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, BgGraphite.copy(alpha = 0.88f))
                                                    )
                                                )
                                                .padding(
                                                    start = if (isFullscreen) 18.dp else 12.dp,
                                                    end = if (isFullscreen) 18.dp else 12.dp,
                                                    top = 6.dp,
                                                    bottom = if (isFullscreen) 10.dp else 4.dp
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Monospace timestamps (tabular-nums ala a-music)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = formatTime(currentPosition),
                                                        style = MonoTextStyle.copy(
                                                            color = FgPrimary,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    )
                                                    Text(
                                                        text = " / ${formatTime(duration)}",
                                                        style = MonoTextStyle.copy(
                                                            color = FgSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    )
                                                }

                                                // Control Pills: Resolution, Speed, Fullscreen
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // 1. Resolution Dropdown Button
                                                    Box {
                                                        Box(
                                                            modifier = Modifier
                                                                .height(controlChipHeight)
                                                                .musikeSurface(
                                                                    shape = controlChipShape,
                                                                    isFocused = isResFocused,
                                                                    accentColor = accent.accent,
                                                                    containerColor = Surface2.copy(alpha = 0.88f),
                                                                    focusedContainerColor = Surface3
                                                                )
                                                                .onFocusChanged {
                                                                    isResFocused = it.isFocused
                                                                    if (it.isFocused) lastUserInteractionTick++
                                                                }
                                                                .clickable {
                                                                    lastUserInteractionTick++
                                                                    showQualityDropdown = true
                                                                }
                                                                .padding(horizontal = 11.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = selectedResolution,
                                                                style = MonoTextStyle.copy(
                                                                    color = if (isResFocused) accent.accent else FgPrimary,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            )
                                                        }

                                                        DropdownMenu(
                                                             expanded = showQualityDropdown,
                                                             onDismissRequest = {
                                                                 lastUserInteractionTick++
                                                                 showQualityDropdown = false
                                                             },
                                                             modifier = Modifier
                                                                 .width(IntrinsicSize.Max)
                                                                 .background(Surface2)
                                                                 .border(1.dp, HairlineStrong, RoundedCornerShape(12.dp))
                                                         ) {
                                                             allMirrors.forEach { mirror ->
                                                                 val isSelected = selectedResolution == mirror.resolution
                                                                 var isItemFocused by remember { mutableStateOf(false) }
                                                                 val itemShape = RoundedCornerShape(8.dp)
                                                                 Box(
                                                                     modifier = Modifier
                                                                         .fillMaxWidth()
                                                                         .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                         .clip(itemShape)
                                                                         .background(
                                                                             when {
                                                                                 isItemFocused -> Surface3
                                                                                 isSelected -> accent.accentSoft
                                                                                 else -> Color.Transparent
                                                                             },
                                                                             itemShape
                                                                         )
                                                                         .then(
                                                                             when {
                                                                                 isItemFocused -> Modifier.border(2.dp, accent.accent, itemShape)
                                                                                 isSelected -> Modifier.border(1.dp, accent.accent.copy(alpha = 0.45f), itemShape)
                                                                                 else -> Modifier
                                                                             }
                                                                         )
                                                                         .onFocusChanged {
                                                                             isItemFocused = it.isFocused
                                                                             if (it.isFocused) lastUserInteractionTick++
                                                                         }
                                                                         .clickable {
                                                                             lastUserInteractionTick++
                                                                             selectedResolution = mirror.resolution
                                                                             viewModel.switchMirror(mirror.id)
                                                                             showQualityDropdown = false
                                                                         }
                                                                         .padding(horizontal = 12.dp, vertical = 6.dp),
                                                                     contentAlignment = Alignment.Center
                                                                 ) {
                                                                     Text(
                                                                         text = mirror.resolution,
                                                                         style = MonoTextStyle.copy(
                                                                             color = if (isSelected || isItemFocused) accent.accent else FgPrimary,
                                                                             fontSize = 12.sp,
                                                                             fontWeight = if (isSelected || isItemFocused) FontWeight.Bold else FontWeight.Medium
                                                                         ),
                                                                         maxLines = 1
                                                                     )
                                                                 }
                                                             }
                                                         }
                                                    }

                                                    // 2. Playback Speed Dropdown Button
                                                    Box {
                                                        Box(
                                                            modifier = Modifier
                                                                .height(controlChipHeight)
                                                                .musikeSurface(
                                                                    shape = controlChipShape,
                                                                    isFocused = isSpeedFocused,
                                                                    accentColor = accent.accent,
                                                                    containerColor = Surface2.copy(alpha = 0.88f),
                                                                    focusedContainerColor = Surface3
                                                                )
                                                                .onFocusChanged {
                                                                    isSpeedFocused = it.isFocused
                                                                    if (it.isFocused) lastUserInteractionTick++
                                                                }
                                                                .clickable {
                                                                    lastUserInteractionTick++
                                                                    showSpeedDropdown = true
                                                                }
                                                                .padding(horizontal = 11.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "${speed}x",
                                                                style = MonoTextStyle.copy(
                                                                    color = if (isSpeedFocused || speed != 1.0f) accent.accent else FgPrimary,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            )
                                                        }

                                                        DropdownMenu(
                                                            expanded = showSpeedDropdown,
                                                            onDismissRequest = {
                                                                lastUserInteractionTick++
                                                                showSpeedDropdown = false
                                                            },
                                                            modifier = Modifier
                                                                .width(IntrinsicSize.Max)
                                                                .background(Surface2)
                                                                .border(1.dp, HairlineStrong, RoundedCornerShape(12.dp))
                                                        ) {
                                                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { value ->
                                                                val isSelected = speed == value
                                                                var isItemFocused by remember { mutableStateOf(false) }
                                                                val itemShape = RoundedCornerShape(8.dp)
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                        .clip(itemShape)
                                                                        .background(
                                                                            when {
                                                                                isItemFocused -> Surface3
                                                                                isSelected -> accent.accentSoft
                                                                                else -> Color.Transparent
                                                                            },
                                                                            itemShape
                                                                        )
                                                                        .then(
                                                                            when {
                                                                                isItemFocused -> Modifier.border(2.dp, accent.accent, itemShape)
                                                                                isSelected -> Modifier.border(1.dp, accent.accent.copy(alpha = 0.45f), itemShape)
                                                                                else -> Modifier
                                                                            }
                                                                        )
                                                                        .onFocusChanged {
                                                                            isItemFocused = it.isFocused
                                                                            if (it.isFocused) lastUserInteractionTick++
                                                                        }
                                                                        .clickable {
                                                                            lastUserInteractionTick++
                                                                            viewModel.setPlaybackSpeed(value)
                                                                            showSpeedDropdown = false
                                                                        }
                                                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = "${value}x",
                                                                        style = MonoTextStyle.copy(
                                                                            color = if (isSelected || isItemFocused) accent.accent else FgPrimary,
                                                                            fontSize = 12.sp,
                                                                            fontWeight = if (isSelected || isItemFocused) FontWeight.Bold else FontWeight.Medium
                                                                        ),
                                                                        maxLines = 1
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 3. Fullscreen Button
                                                    Box(
                                                        modifier = Modifier
                                                            .height(controlChipHeight)
                                                            .width(if (isFullscreen) 36.dp else 32.dp)
                                                            .musikeSurface(
                                                                shape = controlChipShape,
                                                                isFocused = isFullscreenBtnFocused,
                                                                accentColor = accent.accent,
                                                                containerColor = Surface2.copy(alpha = 0.88f),
                                                                focusedContainerColor = Surface3
                                                            )
                                                            .onFocusChanged {
                                                                isFullscreenBtnFocused = it.isFocused
                                                                if (it.isFocused) lastUserInteractionTick++
                                                            }
                                                            .clickable {
                                                                lastUserInteractionTick++
                                                                isFullscreen = !isFullscreen
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isFullscreen) FullscreenExitIcon else FullscreenIcon,
                                                            contentDescription = "Fullscreen",
                                                            tint = if (isFullscreenBtnFocused) accent.accent else FgPrimary,
                                                            modifier = Modifier.size(if (isFullscreen) 16.dp else 14.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(if (isFullscreen) 4.dp else 2.dp))

                                            // Row 2: Accent Seekbar
                                            Slider(
                                                value = if (duration > 0) currentPosition.toFloat() else 0f,
                                                onValueChange = {
                                                    lastUserInteractionTick++
                                                    currentPosition = it.toLong()
                                                    exoPlayer.seekTo(currentPosition)
                                                },
                                                valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(if (isFullscreen) 22.dp else 18.dp)
                                                    .onKeyEvent { keyEvent ->
                                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                                            when (keyEvent.key) {
                                                                Key.DirectionLeft -> {
                                                                    lastUserInteractionTick++
                                                                    val target = (currentPosition - 10000).coerceAtLeast(0L)
                                                                    currentPosition = target
                                                                    exoPlayer.seekTo(target)
                                                                    true
                                                                }
                                                                Key.DirectionRight -> {
                                                                    lastUserInteractionTick++
                                                                    val target = (currentPosition + 10000).coerceAtMost(duration)
                                                                    currentPosition = target
                                                                    exoPlayer.seekTo(target)
                                                                    true
                                                                }
                                                                else -> false
                                                            }
                                                        } else false
                                                    }
                                                    .onFocusChanged {
                                                        isSliderFocused = it.isFocused
                                                        if (it.isFocused) lastUserInteractionTick++
                                                    }
                                                    .focusable(),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = accent.accent,
                                                    activeTrackColor = accent.accent,
                                                    inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                                                )
                                            )
                                        }
                                        } // end if (!isLocked)
                                    } // end controls Box
                                }

                                // Floating "Lewati Intro" Pill Button (AniSkip — Solid Accent Pill)
                                if (isInsideIntro && !isLocked) {
                                    var isSkipFocused by remember { mutableStateOf(false) }
                                    val skipChipHeight = if (isFullscreen) 34.dp else 28.dp

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(
                                                top = if (isFullscreen) 14.dp else 10.dp,
                                                end = if (isFullscreen) 20.dp else 12.dp
                                            )
                                            .height(skipChipHeight)
                                            .clip(CircleShape)
                                            .background(accent.accent, CircleShape)
                                            .then(
                                                if (isSkipFocused) Modifier.border(2.5.dp, Color.White, CircleShape)
                                                else Modifier
                                            )
                                            .focusRequester(skipIntroFocusRequester)
                                            .onFocusChanged { isSkipFocused = it.isFocused }
                                            .clickable {
                                                introInterval?.let { interval ->
                                                    exoPlayer.seekTo(interval.endTimeMs)
                                                    currentPosition = interval.endTimeMs
                                                }
                                            }
                                            .padding(horizontal = 13.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = SkipNextIcon,
                                                contentDescription = "Lewati Intro",
                                                tint = accent.onAccent,
                                                modifier = Modifier.size(if (isFullscreen) 14.dp else 12.dp)
                                            )
                                            Text(
                                                text = "Lewati Intro",
                                                color = accent.onAccent,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                        }
                        } // end Video Player Area Box

                    if (!isFullscreen) {
                        // Portrait Metadata, Synopsis & Monospace Episode Grid
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                        item {
                            Text(
                                text = animeTitle.ifBlank { "Memuat..." },
                                color = FgPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentEpisodeTitle.isNotBlank()) {
                                val epNum = extractEpisodeNumber(currentEpisodeTitle)
                                Text(
                                    text = if (epNum.isNotBlank()) "Episode $epNum" else currentEpisodeTitle,
                                    style = MonoTextStyle.copy(
                                        color = accent.accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }

                        // Synopsis block inside clean Surface2 card
                        if (animeSynopsis.isNotBlank()) {
                            item {
                                var synopsisExpanded by remember { mutableStateOf(false) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(RadiusTile))
                                        .background(Surface2)
                                        .border(1.dp, Hairline, RoundedCornerShape(RadiusTile))
                                        .clickable { synopsisExpanded = !synopsisExpanded }
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Sinopsis",
                                            color = FgPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (synopsisExpanded) "Sembunyikan ▲" else "Selengkapnya ▼",
                                            color = accent.accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = animeSynopsis,
                                        color = FgSecondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }

                        // Episode grid selection (Solid Accent for current episode)
                        if (episodes.isNotEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 10.dp)
                                ) {
                                    Text(
                                        text = "Pilihan Episode",
                                        color = FgPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${episodes.size}",
                                        style = MonoTextStyle.copy(
                                            color = FgTertiary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
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
                                            var isEpFocused by remember { mutableStateOf(false) }
                                            val epShape = RoundedCornerShape(10.dp)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp)
                                                    .clip(epShape)
                                                    .background(
                                                        when {
                                                            isCurrent -> accent.accent
                                                            isEpFocused -> Surface3
                                                            else -> Surface2
                                                        },
                                                        epShape
                                                    )
                                                    .border(
                                                        width = if (isEpFocused) 2.dp else 1.dp,
                                                        color = when {
                                                            isEpFocused && isCurrent -> Color.White
                                                            isEpFocused -> accent.accent
                                                            isCurrent -> Color.Transparent
                                                            else -> Hairline
                                                        },
                                                        shape = epShape
                                                    )
                                                    .onFocusChanged { isEpFocused = it.isFocused }
                                                    .clickable {
                                                        viewModel.selectEpisode(episode.id)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = extractEpisodeNumber(episode.title),
                                                    style = MonoTextStyle.copy(
                                                        color = if (isCurrent) accent.onAccent else FgPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
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
                        } // end LazyColumn
                    } // end if (!isFullscreen)
                    } // end Column(fillMaxSize)
                } // end is PlayerUiState.Success
                is PlayerUiState.Error -> {
                    MusikeErrorState(
                        message = "Gagal memuat video. Coba muat ulang atau ganti server.",
                        onRetry = { viewModel.fetchEpisodeDetail() },
                        modifier = Modifier.fillMaxSize()
                    )
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
                .background(Surface2.copy(alpha = 0.92f), CircleShape)
                .border(1.dp, HairlineStrong, CircleShape)
                .padding(vertical = 12.dp, horizontal = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            
            // Vertical Progress Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .width(5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(progress.coerceIn(0f, 1f))
                        .clip(CircleShape)
                        .background(color)
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

// =====================================================================
// Icon: Lock Closed (gembok terkunci)
// =====================================================================
val LockClosedIcon: ImageVector
    get() = ImageVector.Builder(
        name = "LockClosed",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Body gembok (persegi panjang bawah)
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.White)) {
            moveTo(18f, 11f)
            lineTo(6f, 11f)
            curveTo(4.9f, 11f, 4f, 11.9f, 4f, 13f)
            lineTo(4f, 20f)
            curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            lineTo(18f, 22f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            lineTo(20f, 13f)
            curveTo(20f, 11.9f, 19.1f, 11f, 18f, 11f)
            close()
        }
        // Titik di tengah (keyhole)
        path(fill = androidx.compose.ui.graphics.SolidColor(Color(0xFF232326))) {
            moveTo(12f, 17f)
            arcTo(1.5f, 1.5f, 0f, false, true, 10.5f, 15.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 12f, 14f)
            arcTo(1.5f, 1.5f, 0f, false, true, 13.5f, 15.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 12f, 17f)
            close()
        }
        // Lingkaran atas (shackle)
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
            strokeLineWidth = 2f
        ) {
            moveTo(8f, 11f)
            lineTo(8f, 7f)
            arcTo(4f, 4f, 0f, false, true, 16f, 7f)
            lineTo(16f, 11f)
        }
    }.build()

// =====================================================================
// Icon: Lock Open (gembok terbuka)
// =====================================================================
val LockOpenIcon: ImageVector
    get() = ImageVector.Builder(
        name = "LockOpen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Body gembok (persegi panjang bawah)
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.White)) {
            moveTo(18f, 11f)
            lineTo(6f, 11f)
            curveTo(4.9f, 11f, 4f, 11.9f, 4f, 13f)
            lineTo(4f, 20f)
            curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            lineTo(18f, 22f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            lineTo(20f, 13f)
            curveTo(20f, 11.9f, 19.1f, 11f, 18f, 11f)
            close()
        }
        // Titik di tengah
        path(fill = androidx.compose.ui.graphics.SolidColor(Color(0xFF232326))) {
            moveTo(12f, 17f)
            arcTo(1.5f, 1.5f, 0f, false, true, 10.5f, 15.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 12f, 14f)
            arcTo(1.5f, 1.5f, 0f, false, true, 13.5f, 15.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 12f, 17f)
            close()
        }
        // Lingkaran atas terbuka (shackle open — ke kanan)
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
            strokeLineWidth = 2f
        ) {
            moveTo(8f, 11f)
            lineTo(8f, 7f)
            arcTo(4f, 4f, 0f, false, true, 16f, 7f)
            lineTo(16f, 9f)
        }
    }.build()
