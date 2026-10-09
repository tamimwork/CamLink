package com.example.ui.screens.streaming

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.service.StreamSessionManager
import com.example.ui.components.BatteryThermalBadge
import com.example.ui.components.HealthWarningBanner
import com.example.ui.components.LiveStatusBadge
import com.example.ui.theme.FixedCyanAccent
import com.example.ui.theme.FixedCyanPrimary
import com.example.ui.theme.FixedNavyBorder
import com.example.ui.theme.FixedNavyDark
import com.example.ui.theme.FixedNavyDeep
import com.example.ui.theme.FixedNavySurface
import com.example.ui.theme.FixedNavySurfaceVariant
import com.example.webrtc.RtcConnectionStatus
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingScreen(
    viewModel: StreamingViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val sessionDuration by viewModel.sessionDuration.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val deviceHealth by viewModel.deviceHealth.collectAsState()

    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val isMicMuted by viewModel.isMicMuted.collectAsState()
    val isVideoPaused by viewModel.isVideoPaused.collectAsState()
    val isMirrored by viewModel.isMirrored.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val isAfLocked by viewModel.isAfLocked.collectAsState()
    val isAeAwbLocked by viewModel.isAeAwbLocked.collectAsState()
    val currentZoom by viewModel.currentZoom.collectAsState()
    val maxZoom by viewModel.maxZoom.collectAsState()
    val hasTorch by viewModel.hasTorch.collectAsState()
    val canFlip by viewModel.canFlip.collectAsState()

    val currentResolution by viewModel.currentResolution.collectAsState()
    val currentFps by viewModel.currentFps.collectAsState()

    var showStatsSheet by remember { mutableStateOf(false) }

    // Remote Mode State & Brightness Memory
    var isRemoteMode by remember { mutableStateOf(false) }
    val originalWindowBrightness = remember(activity) {
        activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
    var previousBrightness by remember {
        mutableFloatStateOf(originalWindowBrightness)
    }

    fun enterRemoteMode() {
        val window = activity?.window
        if (window != null) {
            previousBrightness = window.attributes.screenBrightness
            val lp = window.attributes
            lp.screenBrightness = 0.01f // Minimum brightness
            window.attributes = lp
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        isRemoteMode = true
    }

    fun exitRemoteMode() {
        val window = activity?.window
        if (window != null) {
            val lp = window.attributes
            lp.screenBrightness = if (previousBrightness >= 0f) previousBrightness else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
        isRemoteMode = false
    }

    // Keep screen awake while streaming & restore brightness on disposal
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            val w = activity?.window
            if (w != null) {
                val lp = w.attributes
                lp.screenBrightness = originalWindowBrightness
                w.attributes = lp
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Auto-navigate back if session ended, restoring brightness
    LaunchedEffect(connectionStatus) {
        if (connectionStatus == RtcConnectionStatus.DISCONNECTED) {
            exitRemoteMode()
            onNavigateBack()
        }
    }

    BackHandler {
        if (isRemoteMode) {
            exitRemoteMode()
        } else {
            exitRemoteMode()
            viewModel.stopStreaming()
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FixedNavyDeep
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Background WebRTC Local Camera Video Stream (always captures for PC stream)
            LocalCameraRenderer(
                viewModel = viewModel,
                isMirrored = isMirrored,
                isVideoPaused = isVideoPaused,
                modifier = Modifier.fillMaxSize()
            )

            // Video Paused Overlay (hidden in Remote Mode)
            AnimatedVisibility(
                visible = isVideoPaused && !isRemoteMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(FixedNavyDark.copy(alpha = 0.85f))
                        .border(1.dp, FixedNavyBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "Video Paused",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Camera Feed Paused",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Top Status & HUD Bar (hidden in Remote Mode)
            AnimatedVisibility(
                visible = !isRemoteMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status Badge (LIVE / Connecting / Reconnecting)
                        LiveStatusBadge(
                            status = connectionStatus,
                            durationSeconds = sessionDuration,
                            modifier = Modifier.testTag("stream_live_badge")
                        )

                        // Battery & Thermal Diagnostics
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BatteryThermalBadge(
                                health = deviceHealth
                            )

                            // WebRTC Stream Stats Info Button
                            IconButton(
                                onClick = { showStatsSheet = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(FixedNavySurfaceVariant.copy(alpha = 0.7f))
                                    .border(1.dp, FixedNavyBorder, CircleShape)
                                    .testTag("stream_btn_stats")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Stream Stats",
                                    tint = FixedCyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Health Warnings (Overheating or Low Battery)
                    deviceHealth.warningMessage?.let { warning ->
                        Spacer(modifier = Modifier.height(8.dp))
                        HealthWarningBanner(
                            warningMessage = warning
                        )
                    }
                }
            }

            // Bottom Controls Floating Dock (hidden in Remote Mode)
            AnimatedVisibility(
                visible = !isRemoteMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                StreamingControlDock(
                    isFrontCamera = isFrontCamera,
                    isMicMuted = isMicMuted,
                    isVideoPaused = isVideoPaused,
                    isMirrored = isMirrored,
                    isTorchOn = isTorchOn,
                    isAfLocked = isAfLocked,
                    isAeAwbLocked = isAeAwbLocked,
                    currentZoom = currentZoom,
                    maxZoom = maxZoom,
                    hasTorch = hasTorch,
                    canFlip = canFlip,
                    currentResolution = currentResolution,
                    currentFps = currentFps,
                    onSwitchCamera = { viewModel.switchCamera() },
                    onToggleMute = { viewModel.toggleMute() },
                    onToggleVideo = { viewModel.toggleVideo() },
                    onToggleMirror = { viewModel.toggleMirror() },
                    onToggleTorch = { viewModel.toggleTorch() },
                    onToggleAutoFocusLock = { viewModel.toggleAutoFocusLock() },
                    onToggleExposureAwbLock = { viewModel.toggleExposureAwbLock() },
                    onZoomChange = { viewModel.setZoom(it) },
                    onChangeQuality = { res, fps -> viewModel.changeQuality(res, fps) },
                    onEnterRemoteMode = { enterRemoteMode() },
                    onStopStreaming = {
                        exitRemoteMode()
                        viewModel.stopStreaming()
                        onNavigateBack()
                    }
                )
            }

            // Remote Mode: Near-black display, minimum brightness, small pulsing LIVE dot, screen kept on
            if (isRemoteMode) {
                RemoteModeOverlay(
                    onExit = { exitRemoteMode() }
                )
            }
        }
    }

    // Diagnostics Stats Bottom Sheet
    if (showStatsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStatsSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = FixedNavySurface
        ) {
            StreamStatsContent(
                stats = stats,
                res = currentResolution,
                fps = currentFps,
                batteryLevel = deviceHealth.batteryLevel,
                tempC = deviceHealth.batteryTempCelsius
            )
        }
    }
}

@Composable
private fun RemoteModeOverlay(
    onExit: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "remote_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF010204))
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onExit() },
                    onLongPress = { onExit() }
                )
            }
            .testTag("remote_mode_overlay")
    ) {
        // Small pulsing LIVE dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .clip(CircleShape)
                    .background(Color(0xFF00E676))
            )
            Text(
                text = "LIVE",
                color = Color.White.copy(alpha = pulseAlpha),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        // Helper hint for waking the screen
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Remote Mode Active",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Double-tap or long-press anywhere to wake display",
                color = Color.White.copy(alpha = 0.22f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun LocalCameraRenderer(
    viewModel: StreamingViewModel,
    isMirrored: Boolean,
    isVideoPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val eglBase = StreamSessionManager.webRtcManager?.eglBase

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                if (eglBase != null) {
                    init(eglBase.eglBaseContext, null)
                }
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                setEnableHardwareScaler(true)
                setMirror(isMirrored)
                viewModel.getLocalVideoTrack()?.addSink(this)
            }
        },
        update = { view ->
            view.setMirror(isMirrored)
            if (isVideoPaused) {
                view.alpha = 0.2f
            } else {
                view.alpha = 1.0f
            }
        },
        onRelease = { view ->
            viewModel.getLocalVideoTrack()?.removeSink(view)
            view.release()
        },
        modifier = modifier
    )
}

@Composable
private fun StreamStatsContent(
    stats: com.example.webrtc.StreamStats,
    res: com.example.data.model.StreamResolution,
    fps: com.example.data.model.StreamFps,
    batteryLevel: Int,
    tempC: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Live Stream Diagnostics",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        DiagnosticsRow(label = "Target Format", value = "${res.label} @ ${fps.label}")
        DiagnosticsRow(label = "Reported Stream FPS", value = "${stats.fps} fps")
        DiagnosticsRow(label = "Bitrate", value = if (stats.bitrateKbps > 0) "${stats.bitrateKbps} kbps" else "Estimating...")
        DiagnosticsRow(label = "Connection State", value = stats.connectionState.name)
        DiagnosticsRow(label = "Device Temperature", value = "${tempC.toInt()}°C")
        DiagnosticsRow(label = "Battery Remaining", value = "$batteryLevel%")

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DiagnosticsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.LightGray, fontSize = 13.sp)
        Text(text = value, color = FixedCyanAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
