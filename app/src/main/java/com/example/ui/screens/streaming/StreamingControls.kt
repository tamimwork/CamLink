package com.example.ui.screens.streaming

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StreamFps
import com.example.data.model.StreamResolution
import com.example.ui.theme.FixedAmberWarning
import com.example.ui.theme.FixedCyanAccent
import com.example.ui.theme.FixedCyanPrimary
import com.example.ui.theme.FixedNavyBorder
import com.example.ui.theme.FixedNavyDark
import com.example.ui.theme.FixedNavySurface
import com.example.ui.theme.FixedNavySurfaceVariant
import com.example.ui.theme.FixedRoseDanger
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingControlDock(
    isFrontCamera: Boolean,
    isMicMuted: Boolean,
    isVideoPaused: Boolean,
    isMirrored: Boolean,
    isTorchOn: Boolean,
    isAfLocked: Boolean,
    isAeAwbLocked: Boolean,
    currentZoom: Float,
    maxZoom: Float = 5.0f,
    hasTorch: Boolean = true,
    canFlip: Boolean = true,
    currentResolution: StreamResolution,
    currentFps: StreamFps,
    onSwitchCamera: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleMirror: () -> Unit,
    onToggleTorch: () -> Unit,
    onToggleAutoFocusLock: () -> Unit,
    onToggleExposureAwbLock: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onChangeQuality: (StreamResolution, StreamFps) -> Unit,
    onEnterRemoteMode: () -> Unit = {},
    onStopStreaming: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showQualitySheet by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }
    var showZoomSlider by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Expandable Smooth Zoom Slider Bar
        AnimatedVisibility(
            visible = showZoomSlider,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(FixedNavyDark.copy(alpha = 0.9f))
                    .border(1.dp, FixedNavyBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Zoom Ratio: ${String.format(Locale.US, "%.1fx", currentZoom)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1.0f, 2.0f, 3.0f, maxZoom).distinct().filter { it <= maxZoom }.forEach { preset ->
                                FilterChip(
                                    selected = (currentZoom == preset),
                                    onClick = { onZoomChange(preset) },
                                    label = {
                                        Text(
                                            text = "${preset.toInt()}x",
                                            fontSize = 10.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FixedCyanAccent,
                                        selectedLabelColor = FixedNavyDark,
                                        containerColor = FixedNavySurfaceVariant,
                                        labelColor = Color.White
                                    ),
                                    border = null,
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }

                    Slider(
                        value = currentZoom,
                        onValueChange = onZoomChange,
                        valueRange = 1.0f..maxZoom.coerceAtLeast(1.1f),
                        colors = SliderDefaults.colors(
                            thumbColor = FixedCyanAccent,
                            activeTrackColor = FixedCyanAccent,
                            inactiveTrackColor = FixedNavyBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stream_zoom_slider")
                    )
                }
            }
        }

        // Secondary controls: AF/AE, Mirror, Zoom Toggle, Quality Settings (Remote Mode lives in the main dock)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AF Lock Pill
            CompactControlPill(
                icon = if (isAfLocked) Icons.Default.CenterFocusWeak else Icons.Default.CenterFocusStrong,
                label = if (isAfLocked) "AF Locked" else "AF Auto",
                isActive = isAfLocked,
                activeColor = FixedAmberWarning,
                onClick = onToggleAutoFocusLock,
                testTag = "btn_af_lock"
            )

            // AE / AWB Lock Pill
            CompactControlPill(
                icon = if (isAeAwbLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                label = if (isAeAwbLocked) "AE Locked" else "AE Auto",
                isActive = isAeAwbLocked,
                activeColor = FixedAmberWarning,
                onClick = onToggleExposureAwbLock,
                testTag = "btn_ae_lock"
            )

            // Mirror Front Preview Pill
            CompactControlPill(
                icon = Icons.Default.Flip,
                label = if (isMirrored) "Mirrored" else "Mirror",
                isActive = isMirrored,
                activeColor = FixedCyanAccent,
                onClick = onToggleMirror,
                testTag = "btn_mirror"
            )

            // Zoom Toggle Pill
            CompactControlPill(
                icon = Icons.Default.ZoomIn,
                label = String.format(Locale.US, "%.1fx", currentZoom),
                isActive = showZoomSlider,
                activeColor = FixedCyanAccent,
                onClick = { showZoomSlider = !showZoomSlider },
                testTag = "btn_zoom_toggle"
            )

            // Quality Format Pill
            CompactControlPill(
                icon = Icons.Default.Settings,
                label = "${currentResolution.height}p",
                isActive = false,
                activeColor = FixedCyanAccent,
                onClick = { showQualitySheet = true },
                testTag = "btn_quality_sheet"
            )
        }

        // Main Hardware Action Buttons Row: Flip, Mute, Video Pause, Torch, Remote, End
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(FixedNavySurfaceVariant.copy(alpha = 0.95f))
                .border(1.dp, FixedNavyBorder, RoundedCornerShape(24.dp))
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flip camera lens
            DockActionButton(
                icon = Icons.Default.Cameraswitch,
                label = if (isFrontCamera) "Front" else "Back",
                isActive = false,
                enabled = canFlip,
                onClick = onSwitchCamera,
                testTag = "stream_btn_switch_camera"
            )

            // Mic mute
            DockActionButton(
                icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMicMuted) "Muted" else "Mic ON",
                isActive = isMicMuted,
                activeColor = FixedRoseDanger,
                onClick = onToggleMute,
                testTag = "stream_btn_mute"
            )

            // Pause video feed
            DockActionButton(
                icon = if (isVideoPaused) Icons.Default.VideocamOff else Icons.Default.Videocam,
                label = if (isVideoPaused) "Paused" else "Video",
                isActive = isVideoPaused,
                activeColor = FixedRoseDanger,
                onClick = onToggleVideo,
                testTag = "stream_btn_pause_video"
            )

            // Torch / Flashlight
            DockActionButton(
                icon = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                label = if (isTorchOn) "Torch ON" else "Torch",
                isActive = isTorchOn,
                activeColor = FixedAmberWarning,
                enabled = hasTorch,
                onClick = onToggleTorch,
                testTag = "stream_btn_torch"
            )

            // Remote Mode
            DockActionButton(
                icon = Icons.Default.SettingsRemote,
                label = "Remote",
                isActive = false,
                activeColor = FixedCyanAccent,
                onClick = onEnterRemoteMode,
                testTag = "stream_btn_remote_mode"
            )

            // Stop / Disconnect Button
            DockActionButton(
                icon = Icons.Default.Stop,
                label = "End",
                isActive = true,
                activeColor = FixedRoseDanger,
                onClick = { showStopDialog = true },
                testTag = "stream_btn_stop"
            )
        }
    }

    // Stop Confirmation Dialog
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = {
                Text(
                    text = "End Streaming Session?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will disconnect the phone webcam stream from your PC recorder.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopDialog = false
                        onStopStreaming()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = FixedRoseDanger
                    ),
                    modifier = Modifier.testTag("dialog_confirm_stop")
                ) {
                    Text("End Session", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStopDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.LightGray
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = FixedNavyDark,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Quality Selection Modal Bottom Sheet
    if (showQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = FixedNavySurface
        ) {
            QualitySelectionContent(
                currentRes = currentResolution,
                currentFps = currentFps,
                onSelect = { res, fps ->
                    onChangeQuality(res, fps)
                    showQualitySheet = false
                }
            )
        }
    }
}

@Composable
private fun CompactControlPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isActive) activeColor.copy(alpha = 0.18f)
                else FixedNavyDark.copy(alpha = 0.75f)
            )
            .border(
                1.dp,
                if (isActive) activeColor.copy(alpha = 0.8f) else FixedNavyBorder,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else Color.LightGray,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) activeColor else Color.LightGray
            )
        }
    }
}

@Composable
private fun DockActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = FixedCyanAccent,
    enabled: Boolean = true,
    onClick: () -> Unit,
    testTag: String
) {
    val alpha = if (enabled) 1.0f else 0.35f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) activeColor.copy(alpha = 0.22f * alpha)
                    else FixedNavyDark.copy(alpha = 0.8f * alpha)
                )
                .border(
                    1.5.dp,
                    if (isActive) activeColor.copy(alpha = alpha) else FixedNavyBorder.copy(alpha = alpha),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor.copy(alpha = alpha) else Color.White.copy(alpha = alpha),
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = if (isActive) activeColor.copy(alpha = alpha) else Color.LightGray.copy(alpha = alpha)
        )
    }
}

@Composable
private fun QualitySelectionContent(
    currentRes: StreamResolution,
    currentFps: StreamFps,
    onSelect: (StreamResolution, StreamFps) -> Unit
) {
    var selectedRes by remember { mutableStateOf(currentRes) }
    var selectedFps by remember { mutableStateOf(currentFps) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Camera Stream Quality",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Resolution",
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StreamResolution.entries.forEach { res ->
                FilterChip(
                    selected = selectedRes == res,
                    onClick = { selectedRes = res },
                    label = { Text(res.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FixedCyanAccent,
                        selectedLabelColor = FixedNavyDark,
                        containerColor = FixedNavySurfaceVariant,
                        labelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text(
            text = "Frame Rate (FPS)",
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StreamFps.entries.forEach { fps ->
                FilterChip(
                    selected = selectedFps == fps,
                    onClick = { selectedFps = fps },
                    label = { Text(fps.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FixedCyanAccent,
                        selectedLabelColor = FixedNavyDark,
                        containerColor = FixedNavySurfaceVariant,
                        labelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        androidx.compose.material3.Button(
            onClick = { onSelect(selectedRes, selectedFps) },
            colors = ButtonDefaults.buttonColors(
                containerColor = FixedCyanAccent,
                contentColor = FixedNavyDark
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "Apply Quality Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
