package com.example.ui.screens.streaming

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DeviceHealthState
import com.example.data.DeviceMonitor
import com.example.data.SettingsRepository
import com.example.data.model.StreamFps
import com.example.data.model.StreamResolution
import com.example.service.StreamSessionManager
import com.example.webrtc.RtcConnectionStatus
import com.example.webrtc.StreamStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.webrtc.VideoTrack

class StreamingViewModel(application: Application) : AndroidViewModel(application) {

    private val deviceMonitor = DeviceMonitor(application, viewModelScope)
    private val repository = SettingsRepository(application)

    val deviceHealth: StateFlow<DeviceHealthState> = deviceMonitor.healthState

    val connectionStatus: StateFlow<RtcConnectionStatus> = StreamSessionManager.connectionStatus
    val sessionDuration: StateFlow<Long> = StreamSessionManager.sessionDurationSeconds
    val stats: StateFlow<StreamStats> = StreamSessionManager.stats

    val isFrontCamera: StateFlow<Boolean> = StreamSessionManager.isFrontCamera
    val isMicMuted: StateFlow<Boolean> = StreamSessionManager.isMicMuted
    val isVideoPaused: StateFlow<Boolean> = StreamSessionManager.isVideoPaused
    val isMirrored: StateFlow<Boolean> = StreamSessionManager.mirrorVideo

    val isTorchOn: StateFlow<Boolean> = StreamSessionManager.isTorchOn
    val isAfLocked: StateFlow<Boolean> = StreamSessionManager.isAfLocked
    val isAeAwbLocked: StateFlow<Boolean> = StreamSessionManager.isAeAwbLocked
    val currentZoom: StateFlow<Float> = StreamSessionManager.currentZoom

    val currentResolution: StateFlow<StreamResolution> = StreamSessionManager.activeResolution
    val currentFps: StateFlow<StreamFps> = StreamSessionManager.activeFps

    val errorMessage: StateFlow<String?> = StreamSessionManager.errorMessage

    val keepScreenOn: StateFlow<Boolean> = repository.streamSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.example.data.model.StreamSettings()
    ).let {
        kotlinx.coroutines.flow.MutableStateFlow(true)
    }

    fun switchCamera() {
        StreamSessionManager.switchCamera()
    }

    fun toggleMute() {
        StreamSessionManager.toggleMute()
    }

    fun toggleVideo() {
        StreamSessionManager.toggleVideo()
    }

    fun toggleMirror() {
        StreamSessionManager.toggleMirror()
    }

    fun toggleTorch() {
        StreamSessionManager.toggleTorch()
    }

    fun toggleAutoFocusLock() {
        StreamSessionManager.toggleAutoFocusLock()
    }

    fun toggleExposureAwbLock() {
        StreamSessionManager.toggleExposureAwbLock()
    }

    fun setZoom(zoom: Float) {
        StreamSessionManager.setZoom(zoom)
    }

    fun changeQuality(resolution: StreamResolution, fps: StreamFps) {
        StreamSessionManager.changeResolution(resolution, fps)
    }

    fun stopStreaming() {
        StreamSessionManager.stopSession(getApplication())
    }

    fun getLocalVideoTrack(): VideoTrack? {
        return StreamSessionManager.getLocalVideoTrack()
    }
}
