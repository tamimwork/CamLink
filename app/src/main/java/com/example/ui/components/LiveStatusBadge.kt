package com.example.ui.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavySurface
import com.example.ui.theme.RoseDanger
import com.example.webrtc.RtcConnectionStatus
import java.util.Locale

@Composable
fun LiveStatusBadge(
    status: RtcConnectionStatus,
    durationSeconds: Long,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val (badgeBg, dotColor, labelText) = when (status) {
        RtcConnectionStatus.CONNECTED -> Triple(Color(0xE60C1F17), EmeraldLive, "LIVE")
        RtcConnectionStatus.CONNECTING -> Triple(Color(0xE61F1A0C), AmberWarning, "CONNECTING")
        RtcConnectionStatus.RECONNECTING -> Triple(Color(0xE61F1A0C), AmberWarning, "RETRYING")
        RtcConnectionStatus.FAILED -> Triple(Color(0xE6260D14), RoseDanger, "FAILED")
        RtcConnectionStatus.DISCONNECTED -> Triple(NavySurface.copy(alpha = 0.9f), Color.Gray, "OFFLINE")
    }

    Row(
        modifier = modifier
            .background(badgeBg, RoundedCornerShape(20.dp))
            .border(1.dp, dotColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .scale(if (status == RtcConnectionStatus.CONNECTED || status == RtcConnectionStatus.CONNECTING) pulseScale else 1f)
                .background(dotColor, CircleShape)
        )

        Text(
            text = labelText,
            color = dotColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        if (status == RtcConnectionStatus.CONNECTED) {
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 12.dp)
                    .background(NavyBorder)
            )

            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val secs = durationSeconds % 60
            val timeString = if (hours > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, secs)
            }

            Text(
                text = timeString,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
