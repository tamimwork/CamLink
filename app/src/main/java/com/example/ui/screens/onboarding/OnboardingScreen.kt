package com.example.ui.screens.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceVariant
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onPermissionsGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    fun checkPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var hasCamera by remember { mutableStateOf(checkPermission(Manifest.permission.CAMERA)) }
    var hasMic by remember { mutableStateOf(checkPermission(Manifest.permission.RECORD_AUDIO)) }
    var hasNotifications by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else true
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCamera = result[Manifest.permission.CAMERA] ?: hasCamera
        hasMic = result[Manifest.permission.RECORD_AUDIO] ?: hasMic
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotifications = result[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotifications
        }
        if (hasCamera && hasMic) {
            onPermissionsGranted()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = NavyDeep
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar with Skip Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(CyanAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = NavyDark,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "CamLink Studio",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (pagerState.currentPage < 2) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(2)
                            }
                        }
                    ) {
                        Text("Skip", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            // Central Pager Carousel
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> TutorialSlideOne()
                    1 -> TutorialSlideTwo()
                    2 -> PermissionsSlide(
                        hasCamera = hasCamera,
                        hasMic = hasMic,
                        hasNotifications = hasNotifications
                    )
                }
            }

            // Bottom Navigation & Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isSelected) 24.dp else 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isSelected) CyanAccent else NavySurfaceVariant)
                        )
                    }
                }

                // Main Action Button
                Button(
                    onClick = {
                        if (pagerState.currentPage < 2) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            val perms = mutableListOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            if (hasCamera && hasMic) {
                                onPermissionsGranted()
                            } else {
                                permissionsLauncher.launch(perms.toTypedArray())
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_primary_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = NavyDark
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage < 2) "Continue" else (if (hasCamera && hasMic) "Start CamLink" else "Grant Permissions"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialSlideOne() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // High-Quality Illustration
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, NavyBorder, RoundedCornerShape(20.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_tutorial_setup),
                contentDescription = "Studio Setup Illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Studio Tutorial Camera",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "No webcam or microphone on your PC? Turn your Android smartphone into a studio 1080p 60FPS video camera and crystal-clear microphone.",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun TutorialSlideTwo() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // High-Quality Illustration
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, NavyBorder, RoundedCornerShape(20.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_tutorial_pairing),
                contentDescription = "Wi-Fi Pairing Illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Instant 1-Scan Pairing",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Connect over the same local Wi-Fi. Just point your phone at the QR code displayed in the PC Screen Recorder to pair with zero latency.",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun PermissionsSlide(
    hasCamera: Boolean,
    hasMic: Boolean,
    hasNotifications: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera & Audio Access",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Grant permissions so CamLink can capture live video and studio audio for your PC tutorial screen recorder.",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Permission Cards
        PermissionRowItem(
            icon = Icons.Default.CameraAlt,
            title = "Camera Video Feed",
            description = "Live 1080p capture with zoom & exposure lock",
            isGranted = hasCamera
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionRowItem(
            icon = Icons.Default.Mic,
            title = "Microphone Audio",
            description = "Voice recording with noise suppression",
            isGranted = hasMic
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(modifier = Modifier.height(12.dp))
            PermissionRowItem(
                icon = Icons.Default.Notifications,
                title = "Background Streaming",
                description = "Keeps stream live when screen is locked",
                isGranted = hasNotifications
            )
        }
    }
}

@Composable
private fun PermissionRowItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavySurface)
            .border(
                1.dp,
                if (isGranted) EmeraldLive.copy(alpha = 0.5f) else NavyBorder,
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isGranted) EmeraldLive.copy(alpha = 0.15f) else NavySurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else icon,
                contentDescription = null,
                tint = if (isGranted) EmeraldLive else CyanAccent,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}
