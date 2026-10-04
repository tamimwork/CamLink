package com.example.ui.components

import android.annotation.SuppressLint
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.model.ConnectionConfig
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurface
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun QrScannerView(
    onConfigScanned: (ConnectionConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isTorchEnabled by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var hasScanned by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val boxWidth = maxWidth
        val frameSize = (boxWidth * 0.72f).coerceAtMost(300.dp)

        // CameraX Preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val barcodeScanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                    )

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !hasScanned) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            barcodeScanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val raw = barcode.rawValue ?: continue
                                        val config = ConnectionConfig.parse(raw)
                                        if (config != null && !hasScanned) {
                                            hasScanned = true
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                onConfigScanned(config)
                                            }
                                            break
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Darkened Scrim Overlay with Cutout
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val framePx = frameSize.toPx()
            val left = (canvasWidth - framePx) / 2
            val top = (canvasHeight - framePx) / 2 - 40.dp.toPx()

            // Dim everything
            drawRect(color = Color(0x99000000))

            // Transparent viewfinder window
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(framePx, framePx),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            // Neon cyan border
            drawRoundRect(
                color = CyanAccent,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(framePx, framePx),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Animated laser scan line inside the frame
        val infiniteTransition = rememberInfiniteTransition(label = "laser")
        val laserProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "laserProgress"
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-40).dp)
                .size(frameSize)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = (frameSize * laserProgress) - (frameSize / 2))
                    .size(width = frameSize, height = 3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                CyanAccent,
                                Color.White,
                                CyanAccent,
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Torch toggle button
        FilledIconButton(
            onClick = {
                val next = !isTorchEnabled
                isTorchEnabled = next
                camera?.cameraControl?.enableTorch(next)
            },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = NavySurface.copy(alpha = 0.85f),
                contentColor = if (isTorchEnabled) CyanAccent else Color.White
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-20).dp, y = 48.dp)
                .size(48.dp)
                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(24.dp))
                .testTag("torch_toggle_button")
        ) {
            Icon(
                imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = if (isTorchEnabled) "Turn off flashlight" else "Turn on flashlight"
            )
        }
    }
}
