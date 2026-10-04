package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DeviceHealthState
import com.example.ui.theme.FixedAmberWarning
import com.example.ui.theme.FixedEmeraldLive
import com.example.ui.theme.FixedNavyBorder
import com.example.ui.theme.FixedNavySurface
import com.example.ui.theme.FixedRoseDanger

@Composable
fun BatteryThermalBadge(
    health: DeviceHealthState,
    modifier: Modifier = Modifier
) {
    val batteryColor = when {
        health.batteryLevel <= 20 -> FixedRoseDanger
        health.batteryLevel <= 40 -> FixedAmberWarning
        else -> FixedEmeraldLive
    }

    val tempColor = when {
        health.batteryTempCelsius >= 42.0f -> FixedRoseDanger
        health.batteryTempCelsius >= 38.0f -> FixedAmberWarning
        else -> Color(0xFF94A3B8)
    }

    Row(
        modifier = modifier
            .background(FixedNavySurface.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            .border(1.dp, FixedNavyBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Battery Icon
        Icon(
            imageVector = when {
                health.isCharging -> Icons.Default.BatteryChargingFull
                health.batteryLevel <= 20 -> Icons.Default.BatteryAlert
                else -> Icons.Default.BatteryFull
            },
            contentDescription = "Battery ${health.batteryLevel}%",
            tint = batteryColor,
            modifier = Modifier.size(16.dp)
        )

        Text(
            text = "${health.batteryLevel}%",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Box(
            modifier = Modifier
                .size(width = 1.dp, height = 12.dp)
                .background(FixedNavyBorder)
        )

        // Temperature
        Icon(
            imageVector = Icons.Default.LocalFireDepartment,
            contentDescription = "Temperature ${health.batteryTempCelsius.toInt()}°C",
            tint = tempColor,
            modifier = Modifier.size(14.dp)
        )

        Text(
            text = "${health.batteryTempCelsius.toInt()}°C",
            color = tempColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun HealthWarningBanner(
    warningMessage: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = warningMessage != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        warningMessage?.let { msg ->
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .background(Color(0xE62B1705), RoundedCornerShape(12.dp))
                    .border(1.dp, FixedAmberWarning.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = FixedAmberWarning,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = msg,
                    color = Color(0xFFFDE68A),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
