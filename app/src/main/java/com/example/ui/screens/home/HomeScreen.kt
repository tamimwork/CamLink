package com.example.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionConfig
import com.example.ui.screens.scanner.ManualConnectDialog
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToScanner: () -> Unit,
    onNavigateToStreaming: () -> Unit,
    onNavigateToConnectionTest: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val networkInfo by viewModel.networkInfo.collectAsState()
    val lastConnection by viewModel.lastConnection.collectAsState()
    val settings by viewModel.streamSettings.collectAsState()

    var showManualDialog by remember { mutableStateOf(false) }
    var showReconnectCodeDialog by remember { mutableStateOf(false) }

    // Pulsing radar animation for scan button
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = NavyDeep,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyanPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = NavyDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "CamLink",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Wireless PC Webcam & Mic",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToConnectionTest,
                        modifier = Modifier.testTag("connection_test_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Connection Speed Test",
                            tint = CyanAccent
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Wi-Fi & Subnet Status Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NavySurface)
                    .border(1.dp, NavyBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (networkInfo.isWifiConnected) EmeraldLive.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (networkInfo.isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = "Wi-Fi Status",
                        tint = if (networkInfo.isWifiConnected) EmeraldLive else AmberWarning,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = networkInfo.wifiName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Phone IP: ${networkInfo.localIpAddress}",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                IconButton(
                    onClick = { viewModel.refreshNetworkInfo() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Network",
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Big Animated Scan Button Card
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Expanding radar circle
                Box(
                    modifier = Modifier
                        .size(165.dp)
                        .scale(pulseScale)
                        .background(CyanGlow.copy(alpha = pulseAlpha), CircleShape)
                )

                // Main Core Button
                Box(
                    modifier = Modifier
                        .size(165.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(CyanPrimary, Color(0xFF0091EA))
                            )
                        )
                        .border(3.dp, CyanAccent, CircleShape)
                        .clickable { onNavigateToScanner() }
                        .testTag("scan_qr_main_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR Code",
                            tint = NavyDark,
                            modifier = Modifier.size(50.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "SCAN QR CODE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NavyDark,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Connect to PC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = NavyDark.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Remembered PC Card (Feature 4: 1-Tap Reconnect with Pairing Code requirement)
            if (lastConnection.first.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(NavySurfaceVariant.copy(alpha = 0.7f), NavySurface)
                            )
                        )
                        .border(1.dp, CyanAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(CyanAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Computer,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Remembered PC",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${lastConnection.first}:${lastConnection.second}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        Button(
                            onClick = { showReconnectCodeDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanAccent,
                                contentColor = NavyDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("one_tap_reconnect_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CastConnected,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "1-Tap Reconnect",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Quick Actions: Manual IP & Connection Speed Test
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showManualDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("manual_ip_connect_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = "Manual IP",
                            modifier = Modifier.size(16.dp),
                            tint = CyanPrimary
                        )
                        Text("Manual IP", fontSize = 13.sp)
                    }
                }

                OutlinedButton(
                    onClick = onNavigateToConnectionTest,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("connection_test_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Speed Test",
                            modifier = Modifier.size(16.dp),
                            tint = EmeraldLive
                        )
                        Text("Speed Test", fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // How it works card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NavySurface)
                    .border(1.dp, NavyBorder, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                        text = "Quick Studio Guide",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                InstructionStep(
                    number = "1",
                    text = "Launch CamLink Screen Recorder on your PC."
                )
                InstructionStep(
                    number = "2",
                    text = "Ensure both devices are on the same Wi-Fi router (5 GHz recommended)."
                )
                InstructionStep(
                    number = "3",
                    text = "Scan the QR code or use 1-Tap Reconnect with your pairing code."
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (showManualDialog) {
        ManualConnectDialog(
            initialIp = lastConnection.first,
            initialPort = lastConnection.second,
            initialCode = lastConnection.third,
            onDismiss = { showManualDialog = false },
            onConnect = { config ->
                showManualDialog = false
                viewModel.startSession(config)
                onNavigateToStreaming()
            }
        )
    }

    // Remembered PC Pairing Code Confirmation Dialog
    if (showReconnectCodeDialog) {
        var inputCode by remember { mutableStateOf(lastConnection.third) }
        var codeError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showReconnectCodeDialog = false },
            containerColor = NavySurface,
            title = {
                Text(
                    text = "Reconnect to PC",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Connecting to ${lastConnection.first}:${lastConnection.second}",
                        color = CyanAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Please enter the pairing code currently displayed on your PC screen:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = {
                            inputCode = it
                            codeError = null
                        },
                        label = { Text("Pairing Code") },
                        placeholder = { Text("e.g. 483921") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NavySurfaceVariant,
                            unfocusedContainerColor = NavySurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reconnect_pairing_code_input")
                    )
                    codeError?.let {
                        Text(text = it, color = Color(0xFFF43F5E), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedCode = inputCode.trim()
                        if (trimmedCode.isEmpty()) {
                            codeError = "Please enter the pairing code from PC"
                            return@Button
                        }
                        val config = ConnectionConfig(
                            ip = lastConnection.first,
                            port = lastConnection.second,
                            code = trimmedCode,
                            useSsl = true
                        )
                        showReconnectCodeDialog = false
                        viewModel.startSession(config)
                        onNavigateToStreaming()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = NavyDark
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("reconnect_code_confirm_button")
                ) {
                    Text("Connect & Stream", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReconnectCodeDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InstructionStep(
    number: String,
    text: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(NavySurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyanAccent
            )
        }
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color(0xFFCBD5E1),
            lineHeight = 18.sp
        )
    }
}
