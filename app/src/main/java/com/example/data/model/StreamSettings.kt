package com.example.data.model

enum class CameraFacing {
    BACK,
    FRONT
}

enum class StreamResolution(val width: Int, val height: Int, val label: String) {
    HD_720P(1280, 720, "720p HD"),
    FHD_1080P(1920, 1080, "1080p Full HD")
}

enum class StreamFps(val fps: Int, val label: String) {
    FPS_30(30, "30 FPS"),
    FPS_60(60, "60 FPS")
}

data class StreamSettings(
    val defaultCamera: CameraFacing = CameraFacing.BACK,
    val resolution: StreamResolution = StreamResolution.FHD_1080P,
    val fps: StreamFps = StreamFps.FPS_30,
    val mirrorVideo: Boolean = false,
    val keepScreenOn: Boolean = true,
    val autoReconnect: Boolean = true,
    val appTheme: String = "dark", // "system", "dark", "light"
    val allowPcControl: Boolean = true
)
