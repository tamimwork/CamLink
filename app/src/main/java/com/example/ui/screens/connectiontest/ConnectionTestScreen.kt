package com.example.ui.screens.connectiontest

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionConfig
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.RoseDanger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionTestScreen(
    viewModel: ConnectionTestViewModel,
    onStartStreaming: (ConnectionConfig) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val testState by viewModel.testState.collectAsState()
    val targetIp by viewModel.targetIp.collectAsState()
    val targetPort by viewModel.targetPort.collectAsState()
    val targetCode by viewModel.targetCode.collectAsState()
    val result by viewModel.result.collectAsState()

    BackHandler {
        onNavigateBack()
    }

    val isTesting = testState == TestState.TESTING_PING || testState == TestState.TESTING_BITRATE

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = NavyDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Connection & Speed Test",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("connection_test_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // PC Target Input Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NavySurface)
                    .border(1.dp, NavyBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Target PC Address",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CyanAccent
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = targetIp,
                        onValueChange = { viewModel.setTarget(it, targetPort, targetCode) },
                        label = { Text("PC IP") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NavySurfaceVariant,
                            unfocusedContainerColor = NavySurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(2f)
                            .testTag("test_target_ip_input")
                    )

                    OutlinedTextField(
                        value = targetPort.toString(),
                        onValueChange = {
                            val p = it.toIntOrNull() ?: 8443
                            viewModel.setTarget(targetIp, p, targetCode)
                        },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NavySurfaceVariant,
                            unfocusedContainerColor = NavySurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_target_port_input")
                    )
                }

                Button(
                    onClick = { viewModel.runTest() },
                    enabled = !isTesting && targetIp.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = NavyDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("run_speed_test_button")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            color = NavyDark,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = if (testState == TestState.TESTING_PING) "Pinging PC..." else "Analyzing Bandwidth...",
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Run Network Diagnostic", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Central Latency & Rating Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(NavySurface, NavyDark)
                        )
                    )
                    .border(
                        1.dp,
                        if (result.isSuccess) EmeraldLive.copy(alpha = 0.5f) else NavyBorder,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Latency Dial Circle
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .scale(if (isTesting) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        when {
                                            !result.isSuccess -> NavySurfaceVariant
                                            result.avgLatencyMs <= 25 -> EmeraldLive.copy(alpha = 0.2f)
                                            result.avgLatencyMs <= 50 -> AmberWarning.copy(alpha = 0.2f)
                                            else -> RoseDanger.copy(alpha = 0.2f)
                                        },
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(
                                3.dp,
                                when {
                                    !result.isSuccess -> NavyBorder
                                    result.avgLatencyMs <= 25 -> EmeraldLive
                                    result.avgLatencyMs <= 50 -> AmberWarning
                                    else -> RoseDanger
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (result.isSuccess) "${result.avgLatencyMs}" else "--",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "ms latency",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Rating Badge
                    if (result.isSuccess) {
                        Text(
                            text = result.rating,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldLive,
                            textAlign = TextAlign.Center
                        )
                    } else if (result.error != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = RoseDanger,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = result.error ?: "",
                                fontSize = 12.sp,
                                color = RoseDanger,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            text = "Tap 'Run Network Diagnostic' to test PC link speed",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Diagnostic Detail Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Estimated Bitrate
                MetricCard(
                    icon = Icons.Default.Speed,
                    label = "Throughput",
                    value = if (result.isSuccess) "${result.bitrateMbps} Mbps" else "--",
                    color = CyanAccent,
                    modifier = Modifier.weight(1f)
                )

                // Jitter
                MetricCard(
                    icon = Icons.Default.Timer,
                    label = "Jitter",
                    value = if (result.isSuccess) "±${result.jitterMs} ms" else "--",
                    color = AmberWarning,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Wi-Fi Band
                MetricCard(
                    icon = Icons.Default.Wifi,
                    label = "Wi-Fi Frequency",
                    value = if (result.isSuccess) "${result.wifiFrequencyGhz} GHz" else "--",
                    color = CyanPrimary,
                    modifier = Modifier.weight(1f)
                )

                // Wi-Fi Link Speed
                MetricCard(
                    icon = Icons.Default.CheckCircle,
                    label = "Link PHY Speed",
                    value = if (result.isSuccess) "${result.wifiLinkSpeedMbps} Mbps" else "--",
                    color = EmeraldLive,
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick Launch Stream Button if Test Passes
            if (result.isSuccess) {
                Button(
                    onClick = {
                        onStartStreaming(
                            ConnectionConfig(
                                ip = targetIp.trim(),
                                port = targetPort,
                                code = targetCode,
                                useSsl = true
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldLive,
                        contentColor = NavyDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_streaming_from_test_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Start Streaming to this PC",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(1.dp, NavyBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily.Monospace
        )
    }
}
