package com.example.ui.screens.settings

import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextPrimary
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CameraFacing
import com.example.data.model.StreamFps
import com.example.data.model.StreamResolution
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = NavyDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDeep
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // SECTION: Video & Audio Defaults
            SettingsSectionHeader(title = "Video & Audio Defaults")

            // Default Camera
            SettingsContainer {
                SettingsRowHeader(
                    icon = Icons.Default.Cameraswitch,
                    title = "Default Camera",
                    subtitle = "Lens selected when starting a new session"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = settings.defaultCamera == CameraFacing.BACK,
                        onClick = { viewModel.setDefaultCamera(CameraFacing.BACK) },
                        label = { Text("Back Camera (Main)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent,
                            selectedLabelColor = NavyDark,
                            containerColor = NavySurfaceVariant,
                            labelColor = TextPrimary
                        ),
                        modifier = Modifier.testTag("settings_camera_back")
                    )
                    FilterChip(
                        selected = settings.defaultCamera == CameraFacing.FRONT,
                        onClick = { viewModel.setDefaultCamera(CameraFacing.FRONT) },
                        label = { Text("Front Camera (Selfie)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent,
                            selectedLabelColor = NavyDark,
                            containerColor = NavySurfaceVariant,
                            labelColor = TextPrimary
                        ),
                        modifier = Modifier.testTag("settings_camera_front")
                    )
                }
            }

            // Default Resolution
            SettingsContainer {
                SettingsRowHeader(
                    icon = Icons.Default.HighQuality,
                    title = "Default Resolution",
                    subtitle = "1080p Full HD provides maximum tutorial clarity"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StreamResolution.entries.forEach { res ->
                        FilterChip(
                            selected = settings.resolution == res,
                            onClick = { viewModel.setDefaultResolution(res) },
                            label = { Text(res.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanAccent,
                                selectedLabelColor = NavyDark,
                                containerColor = NavySurfaceVariant,
                                labelColor = TextPrimary
                            ),
                            modifier = Modifier.testTag("settings_resolution_${res.name}")
                        )
                    }
                }
            }

            // Default FPS
            SettingsContainer {
                SettingsRowHeader(
                    icon = Icons.Default.Speed,
                    title = "Frame Rate (FPS)",
                    subtitle = "60 FPS offers ultra-fluid facecam motion"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StreamFps.entries.forEach { fpsOption ->
                        FilterChip(
                            selected = settings.fps == fpsOption,
                            onClick = { viewModel.setDefaultFps(fpsOption) },
                            label = { Text(fpsOption.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanAccent,
                                selectedLabelColor = NavyDark,
                                containerColor = NavySurfaceVariant,
                                labelColor = TextPrimary
                            ),
                            modifier = Modifier.testTag("settings_fps_${fpsOption.fps}")
                        )
                    }
                }
            }

            // Mirror Front Camera Toggle
            SettingsToggleRow(
                icon = Icons.Default.Flip,
                title = "Mirror Front Video",
                subtitle = "Flips preview horizontally like a natural mirror",
                checked = settings.mirrorVideo,
                onCheckedChange = { viewModel.setMirrorVideo(it) },
                testTag = "settings_mirror_toggle"
            )

            // SECTION: Streaming Behavior
            SettingsSectionHeader(title = "Streaming Behavior")

            // Keep Screen Awake
            SettingsToggleRow(
                icon = Icons.Default.Lightbulb,
                title = "Keep Screen Awake",
                subtitle = "Prevents device display from sleeping while live",
                checked = settings.keepScreenOn,
                onCheckedChange = { viewModel.setKeepScreenOn(it) },
                testTag = "settings_keep_awake_toggle"
            )

            // Auto Reconnect
            SettingsToggleRow(
                icon = Icons.Default.Sync,
                title = "Auto-Reconnect & Wi-Fi Recovery",
                subtitle = "Automatically restores stream when Wi-Fi drops and reconnects",
                checked = settings.autoReconnect,
                onCheckedChange = { viewModel.setAutoReconnect(it) },
                testTag = "settings_auto_reconnect_toggle"
            )

            // Allow PC to Control Camera
            SettingsToggleRow(
                icon = Icons.Default.SettingsRemote,
                title = "Allow PC to control camera",
                subtitle = "Enables desktop recorder to remotely change zoom, torch, lenses, and settings",
                checked = settings.allowPcControl,
                onCheckedChange = { viewModel.setAllowPcControl(it) },
                testTag = "settings_allow_pc_control_toggle"
            )

            // SECTION: Appearance
            SettingsSectionHeader(title = "Appearance & Theme")

            SettingsContainer {
                SettingsRowHeader(
                    icon = Icons.Default.DarkMode,
                    title = "App Theme",
                    subtitle = "Dark theme is optimized for studio streaming"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (themeKey, label) ->
                        FilterChip(
                            selected = settings.appTheme == themeKey,
                            onClick = { viewModel.setAppTheme(themeKey) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanAccent,
                                selectedLabelColor = NavyDark,
                                containerColor = NavySurfaceVariant,
                                labelColor = TextPrimary
                            ),
                            modifier = Modifier.testTag("settings_theme_$themeKey")
                        )
                    }
                }
            }

            // SECTION: About & PC Protocol
            SettingsSectionHeader(title = "PC Companion Protocol")

            SettingsContainer {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Signaling & QR Format",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "CamLink connects to the PC over WebSocket (WSS/WS) and initiates WebRTC streaming using hardware H.264/VP8 encoding and high-fidelity Opus audio.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Expected PC QR code payload:\n{\"ip\":\"192.168.x.x\",\"port\":8443,\"code\":\"123456\"}",
                    fontSize = 11.sp,
                    color = CyanPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = CyanPrimary,
        letterSpacing = 1.sp
    )
}

@Composable
private fun SettingsContainer(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavySurface)
            .border(1.dp, NavyBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsRowHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NavySurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(18.dp)
            )
        }
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavySurface)
            .border(1.dp, NavyBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(NavySurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NavyDark,
                checkedTrackColor = CyanAccent
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
