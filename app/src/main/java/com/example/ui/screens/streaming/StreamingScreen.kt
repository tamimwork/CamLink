package com.example.ui.screens.streaming

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceVariant
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

    val currentResolution by viewModel.currentResolution.collectAsState()
    val currentFps by viewModel.currentFps.collectAsState()

    var showStatsSheet by remember { mutableStateOf(false) }

    // Keep screen awake while streaming
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Intercept back button to safely stop and return
    BackHandler {
        viewModel.stopStreaming()
        onNavigateBack()
    }

    // Auto-navigate back if disconnected intentionally
    LaunchedEffect(connectionStatus) {
        if (connectionStatus == RtcConnectionStatus.DISCONNECTED && sessionDuration > 3) {
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live WebRTC Video Preview
            var surfaceRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
            val eglContext = StreamSessionManager.webRtcManager?.eglBase?.eglBaseContext

            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        if (eglContext != null) {
                            init(eglContext, null)
                        }
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        setEnableHardwareScaler(true)
                        setMirror(isMirrored)
                        val videoTrack = viewModel.getLocalVideoTrack()
                        videoTrack?.addSink(this)
                        surfaceRenderer = this
                    }
                },
                update = { renderer ->
                    renderer.setMirror(isMirrored)
                    val videoTrack = viewModel.getLocalVideoTrack()
                    videoTrack?.let {
                        it.removeSink(renderer)
                        it.addSink(renderer)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("webrtc_camera_preview")
            )

            DisposableEffect(Unit) {
                onDispose {
                    val track = viewModel.getLocalVideoTrack()
                    surfaceRenderer?.let {
                        track?.removeSink(it)
                        it.release()
                    }
                }
            }

            // Paused Video Cover Overlay
            AnimatedVisibility(
                visible = isVideoPaused,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "Video Paused",
                            tint = Color.White,
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "Camera Paused",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Audio continues streaming to PC",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Top Status Bar Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing LIVE Badge & Duration
                    LiveStatusBadge(
                        status = connectionStatus,
                        durationSeconds = sessionDuration,
                        modifier = Modifier.testTag("live_status_badge")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Battery & Thermal Badge
                        BatteryThermalBadge(
                            health = deviceHealth,
                            modifier = Modifier.testTag("battery_thermal_badge")
                        )

                        // Stats Sheet Button
                        IconButton(
                            onClick = { showStatsSheet = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NavySurface.copy(alpha = 0.85f))
                                .border(1.dp, NavyBorder, CircleShape)
                                .testTag("stats_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Stream Stats",
                                tint = CyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Battery / Thermal Warning Banners
                HealthWarningBanner(
                    warningMessage = deviceHealth.warningMessage,
                    modifier = Modifier.testTag("health_warning_banner")
                )
            }

            // Bottom Floating Controls Dock
            StreamingControlDock(
                isFrontCamera = isFrontCamera,
                isMicMuted = isMicMuted,
                isVideoPaused = isVideoPaused,
                isMirrored = isMirrored,
                isTorchOn = isTorchOn,
                isAfLocked = isAfLocked,
                isAeAwbLocked = isAeAwbLocked,
                currentZoom = currentZoom,
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
                onStopStreaming = {
                    viewModel.stopStreaming()
                    onNavigateBack()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }

    // Stream Stats Bottom Sheet
    if (showStatsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStatsSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = NavyDark,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Live Stream Diagnostics",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                DiagnosticRow(label = "WebRTC State", value = connectionStatus.name)
                DiagnosticRow(label = "Resolution", value = stats.resolution)
                DiagnosticRow(label = "Target Frame Rate", value = "${stats.fps} FPS")
                DiagnosticRow(label = "Bitrate Sent", value = "${stats.bitrateKbps} kbps")
                DiagnosticRow(label = "Session Duration", value = "${sessionDuration}s")
                DiagnosticRow(label = "Device Temperature", value = "${deviceHealth.batteryTempCelsius}°C")
                DiagnosticRow(label = "Battery", value = "${deviceHealth.batteryLevel}% (${if (deviceHealth.isCharging) "Charging" else "Discharging"})")

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NavySurfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 13.sp)
        Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
