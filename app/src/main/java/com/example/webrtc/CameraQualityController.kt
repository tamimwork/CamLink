package com.example.webrtc

import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
import android.view.Surface
import org.webrtc.CameraVideoCapturer

class CameraQualityController(
    private val context: Context,
    private val capturerProvider: () -> CameraVideoCapturer?
) {
    private val tag = "CamQualityController"

    var isTorchOn: Boolean = false
        private set

    var isAfLocked: Boolean = false
        private set

    var isAeAwbLocked: Boolean = false
        private set

    var currentZoom: Float = 1.0f
        private set

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    fun setTorch(enable: Boolean): Boolean {
        isTorchOn = enable
        val appliedViaSession = updateCaptureSession()
        if (!appliedViaSession) {
            // Fallback via CameraManager
            try {
                val capturer = capturerProvider()
                val cameraName = getCameraNameViaReflection(capturer) ?: "0"
                cameraManager?.setTorchMode(cameraName, enable)
                return true
            } catch (e: Exception) {
                Log.w(tag, "Torch fallback failed: ${e.message}")
            }
        }
        return appliedViaSession
    }

    fun setAutoFocusLock(lock: Boolean): Boolean {
        isAfLocked = lock
        return updateCaptureSession()
    }

    fun setExposureAwbLock(lock: Boolean): Boolean {
        isAeAwbLocked = lock
        return updateCaptureSession()
    }

    fun setZoom(ratio: Float): Boolean {
        currentZoom = ratio.coerceIn(1.0f, 5.0f)
        return updateCaptureSession()
    }

    private fun updateCaptureSession(): Boolean {
        return try {
            val capturer = capturerProvider() ?: return false
            val session = getCurrentSessionViaReflection(capturer) ?: return false

            val captureSession = getFieldViaReflection<CameraCaptureSession>(session, "captureSession")
                ?: return false
            val cameraDevice = getFieldViaReflection<CameraDevice>(session, "cameraDevice")
                ?: return false
            val surface = getFieldViaReflection<Surface>(session, "surface")
                ?: return false
            val characteristics = getFieldViaReflection<CameraCharacteristics>(session, "cameraCharacteristics")

            val requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            requestBuilder.addTarget(surface)

            // Auto-focus configuration
            if (isAfLocked) {
                requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            } else {
                requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            }

            // AE & AWB Lock
            if (isAeAwbLocked) {
                requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true)
                requestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true)
            } else {
                requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false)
                requestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, false)
            }

            // Flashlight / Torch
            if (isTorchOn) {
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            } else {
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }

            // Digital Zoom
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val maxZoom = characteristics?.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.upper ?: 5.0f
                val clamped = currentZoom.coerceIn(1.0f, maxZoom)
                requestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, clamped)
            } else if (characteristics != null) {
                val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                if (activeArray != null) {
                    val cropW = (activeArray.width() / currentZoom).toInt()
                    val cropH = (activeArray.height() / currentZoom).toInt()
                    val cropX = (activeArray.width() - cropW) / 2
                    val cropY = (activeArray.height() - cropH) / 2
                    val cropRegion = Rect(cropX, cropY, cropX + cropW, cropY + cropH)
                    requestBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
                }
            }

            captureSession.setRepeatingRequest(requestBuilder.build(), null, null)
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply camera quality settings: ${e.message}")
            false
        }
    }

    private fun getCurrentSessionViaReflection(capturer: CameraVideoCapturer): Any? {
        return try {
            val field = capturer.javaClass.superclass?.getDeclaredField("currentSession")
                ?: capturer.javaClass.getDeclaredField("currentSession")
            field.isAccessible = true
            field.get(capturer)
        } catch (_: Exception) {
            null
        }
    }

    private fun getCameraNameViaReflection(capturer: CameraVideoCapturer?): String? {
        if (capturer == null) return null
        return try {
            val field = capturer.javaClass.superclass?.getDeclaredField("cameraName")
                ?: capturer.javaClass.getDeclaredField("cameraName")
            field.isAccessible = true
            field.get(capturer) as? String
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getFieldViaReflection(obj: Any, fieldName: String): T? {
        return try {
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(obj) as? T
        } catch (_: Exception) {
            null
        }
    }
}
