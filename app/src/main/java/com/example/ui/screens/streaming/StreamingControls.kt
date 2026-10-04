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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
    onStopStreaming: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showQualitySheet by remember { mutableStateOf(false) }
    var showStopConfirmDialog by remember { mutableStateOf(false) }
    var showZoomSlider by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Zoom Slider Overlay
        AnimatedVisibility(
            visible = showZoomSlider,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .background(FixedNavySurface.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                    .border(1.dp, FixedNavyBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = null,
                    tint = FixedCyanAccent,
                    modifier = Modifier.size(20.dp)
                )

                Slider(
                    value = currentZoom,
                    onValueChange = onZoomChange,
                    valueRange = 1.0f..5.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = FixedCyanAccent,
                        activeTrackColor = FixedCyanPrimary,
                        inactiveTrackColor = FixedNavySurfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("camera_zoom_slider")
                )

                Text(
                    text = String.format(Locale.US, "%.1fx", currentZoom),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(36.dp)
                )

                // Quick Reset to 1x
                if (currentZoom > 1.05f) {
                    Text(
                        text = "1x",
                        color = FixedCyanAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onZoomChange(1.0f) }
                            .padding(4.dp)
                    )
                }
            }
        }

        // Secondary Picture Control Row (AF Lock, AE/AWB Lock, Zoom Toggle)
        Row(
            modifier = Modifier
                .background(FixedNavySurface.copy(alpha = 0.88f), RoundedCornerShape(20.dp))
                .border(1.dp, FixedNavyBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AF Lock Chip
            QualityToggleChip(
                label = if (isAfLocked) "AF Locked" else "AF Auto",
                active = isAfLocked,
                activeColor = FixedAmberWarning,
                icon = if (isAfLocked) Icons.Default.Lock else Icons.Default.CenterFocusStrong,
                testTag = "toggle_af_lock_button",
                onClick = onToggleAutoFocusLock
            )

            // AE / AWB Lock Chip
            QualityToggleChip(
                label = if (isAeAwbLocked) "AE/AWB Locked" else "AE Auto",
                active = isAeAwbLocked,
                activeColor = FixedAmberWarning,
                icon = if (isAeAwbLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                testTag = "toggle_ae_lock_button",
                onClick = onToggleExposureAwbLock
            )

            // Zoom Button Toggle
            QualityToggleChip(
                label = String.format(Locale.US, "%.1fx", currentZoom),
                active = showZoomSlider || currentZoom > 1.05f,
                activeColor = FixedCyanAccent,
                icon = Icons.Default.ZoomIn,
                testTag = "toggle_zoom_slider_button",
                onClick = { showZoomSlider = !showZoomSlider }
            )
        }

        // Primary Control Dock
        Row(
            modifier = Modifier
                .background(FixedNavySurface.copy(alpha = 0.94f), RoundedCornerShape(32.dp))
                .border(1.dp, FixedNavyBorder, RoundedCornerShape(32.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Switch Camera
            ControlButton(
                icon = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                active = isFrontCamera,
                activeColor = FixedCyanAccent,
                testTag = "switch_camera_button",
                onClick = onSwitchCamera
            )

            // Flashlight / Torch (available on back camera)
            if (!isFrontCamera) {
                ControlButton(
                    icon = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Torch",
                    active = isTorchOn,
                    activeColor = FixedAmberWarning,
                    testTag = "torch_stream_button",
                    onClick = onToggleTorch
                )
            }

            // Mic Mute / Unmute
            ControlButton(
                icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isMicMuted) "Unmute Microphone" else "Mute Microphone",
                active = isMicMuted,
                activeColor = FixedRoseDanger,
                testTag = "mute_mic_button",
                onClick = onToggleMute
            )

            // Video Pause / Resume
            ControlButton(
                icon = if (isVideoPaused) Icons.Default.VideocamOff else Icons.Default.Videocam,
                contentDescription = if (isVideoPaused) "Resume Video" else "Pause Video",
                active = isVideoPaused,
                activeColor = FixedRoseDanger,
                testTag = "pause_video_button",
                onClick = onToggleVideo
            )

            // Mirror / Flip
            ControlButton(
                icon = Icons.Default.Flip,
                contentDescription = "Mirror Video",
                active = isMirrored,
                activeColor = FixedCyanAccent,
                testTag = "mirror_video_button",
                onClick = onToggleMirror
            )

            // Quality Settings Button
            ControlButton(
                icon = Icons.Default.Settings,
                contentDescription = "Stream Quality",
                active = false,
                testTag = "quality_settings_button",
                onClick = { showQualitySheet = true }
            )

            // Stop Streaming Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(FixedRoseDanger)
                    .clickable { showStopConfirmDialog = true }
                    .testTag("stop_streaming_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Streaming",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // Quality Selection Bottom Sheet
    if (showQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = FixedNavyDark,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Stream Quality & Camera Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Resolution",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StreamResolution.entries.forEach { res ->
                        FilterChip(
                            selected = res == currentResolution,
                            onClick = { onChangeQuality(res, currentFps) },
                            label = { Text(res.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FixedCyanAccent,
                                selectedLabelColor = FixedNavyDark,
                                containerColor = FixedNavySurfaceVariant,
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Text(
                    text = "Frame Rate (FPS)",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StreamFps.entries.forEach { fpsOption ->
                        FilterChip(
                            selected = fpsOption == currentFps,
                            onClick = { onChangeQuality(currentResolution, fpsOption) },
                            label = { Text(fpsOption.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FixedCyanAccent,
                                selectedLabelColor = FixedNavyDark,
                                containerColor = FixedNavySurfaceVariant,
                                labelColor = Color.White
                            )
                        )
                    }
                }

                TextButton(
                    onClick = { showQualitySheet = false },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Done", color = FixedCyanPrimary)
                }
            }
        }
    }

    // Stop Streaming Confirmation Dialog
    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            containerColor = FixedNavySurface,
            title = {
                Text(text = "Stop Recording Stream?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "This will immediately disconnect the camera and microphone feed from the PC.",
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopConfirmDialog = false
                        onStopStreaming()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = FixedRoseDanger)
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStopConfirmDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun QualityToggleChip(
    label: String,
    active: Boolean,
    activeColor: Color,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) activeColor.copy(alpha = 0.2f) else FixedNavySurfaceVariant)
            .border(1.dp, if (active) activeColor.copy(alpha = 0.7f) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) activeColor else Color(0xFF94A3B8),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) activeColor else Color(0xFFE2E8F0)
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    activeColor: Color = FixedCyanAccent,
    testTag: String,
    onClick: () -> Unit
) {
    val bg = if (active) activeColor.copy(alpha = 0.2f) else FixedNavySurfaceVariant
    val tint = if (active) activeColor else Color.White

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(bg)
            .border(
                1.dp,
                if (active) activeColor else Color.Transparent,
                CircleShape
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
