package com.example.service

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.example.data.DeviceHealthState
import com.example.data.DeviceMonitor
import com.example.data.SettingsRepository
import com.example.data.model.CameraFacing
import com.example.data.model.ConnectionConfig
import com.example.data.model.StreamFps
import com.example.data.model.StreamResolution
import com.example.data.model.StreamSettings
import com.example.webrtc.RtcConnectionStatus
import com.example.webrtc.SignalingClient
import com.example.webrtc.SignalingState
import com.example.webrtc.StreamStats
import com.example.webrtc.WebRtcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.VideoTrack

object StreamSessionManager {
    private const val TAG = "CamLinkSession"

    private val scope = CoroutineScope(Dispatchers.Main)

    // Every coroutine of one streaming session lives under sessionJob so stopSession() cancels all of them
    private var sessionJob: Job? = null
    private var sessionScope: CoroutineScope = scope

    // True only after the network was really lost (registerNetworkCallback fires onAvailable immediately on register)
    private var networkWasLost = false

    private var signalingClient: SignalingClient? = null
    var webRtcManager: WebRtcManager? = null
        private set

    private var deviceMonitor: DeviceMonitor? = null

    private val _currentConfig = MutableStateFlow<ConnectionConfig?>(null)
    val currentConfig: StateFlow<ConnectionConfig?> = _currentConfig.asStateFlow()

    private val _connectionStatus = MutableStateFlow(RtcConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<RtcConnectionStatus> = _connectionStatus.asStateFlow()

    private val _stats = MutableStateFlow(StreamStats())
    val stats: StateFlow<StreamStats> = _stats.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isVideoPaused = MutableStateFlow(false)
    val isVideoPaused: StateFlow<Boolean> = _isVideoPaused.asStateFlow()

    private val _mirrorVideo = MutableStateFlow(false)
    val mirrorVideo: StateFlow<Boolean> = _mirrorVideo.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isAfLocked = MutableStateFlow(false)
    val isAfLocked: StateFlow<Boolean> = _isAfLocked.asStateFlow()

    private val _isAeAwbLocked = MutableStateFlow(false)
    val isAeAwbLocked: StateFlow<Boolean> = _isAeAwbLocked.asStateFlow()

    private val _currentZoom = MutableStateFlow(1.0f)
    val currentZoom: StateFlow<Float> = _currentZoom.asStateFlow()

    private val _maxZoom = MutableStateFlow(5.0f)
    val maxZoom: StateFlow<Float> = _maxZoom.asStateFlow()

    private val _hasTorch = MutableStateFlow(true)
    val hasTorch: StateFlow<Boolean> = _hasTorch.asStateFlow()

    private val _canFlip = MutableStateFlow(true)
    val canFlip: StateFlow<Boolean> = _canFlip.asStateFlow()

    private val _activeResolution = MutableStateFlow(StreamResolution.FHD_1080P)
    val activeResolution: StateFlow<StreamResolution> = _activeResolution.asStateFlow()

    private val _activeFps = MutableStateFlow(StreamFps.FPS_30)
    val activeFps: StateFlow<StreamFps> = _activeFps.asStateFlow()

    private val _sessionDurationSeconds = MutableStateFlow(0L)
    val sessionDurationSeconds: StateFlow<Long> = _sessionDurationSeconds.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _latestHealthState = MutableStateFlow(DeviceHealthState())
    val latestHealthState: StateFlow<DeviceHealthState> = _latestHealthState.asStateFlow()

    private var timerJob: Job? = null
    private var isStreamingActive = false

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var appContext: Context? = null
    private var currentSettings: StreamSettings? = null

    // State publisher flow & debounce job (150ms debounce for conflating rapid UI/slider events)
    private val stateChangeTrigger = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var stateDebounceJob: Job? = null
    private var heartbeatJob: Job? = null

    fun notifyStateChanged() {
        stateChangeTrigger.tryEmit(Unit)
    }

    fun startSession(context: Context, config: ConnectionConfig, settings: StreamSettings) {
        if (isStreamingActive) {
            stopSession(context)
        }

        sessionJob?.cancel()
        val job = SupervisorJob()
        sessionJob = job
        sessionScope = CoroutineScope(Dispatchers.Main + job)
        networkWasLost = false

        appContext = context.applicationContext
        currentSettings = settings
        _currentConfig.value = config
        _activeResolution.value = settings.resolution
        _activeFps.value = settings.fps
        _mirrorVideo.value = settings.mirrorVideo
        _isFrontCamera.value = settings.defaultCamera == CameraFacing.FRONT
        _isTorchOn.value = false
        _isAfLocked.value = false
        _isAeAwbLocked.value = false
        _currentZoom.value = 1.0f
        _sessionDurationSeconds.value = 0L
        _errorMessage.value = null
        _connectionStatus.value = RtcConnectionStatus.CONNECTING
        isStreamingActive = true

        inspectCameraHardwareCapabilities(context.applicationContext)

        // Observe settings changes continuously (e.g., Allow PC to control camera toggle)
        sessionScope.launch {
            SettingsRepository(context.applicationContext).streamSettings.collect { newSettings ->
                currentSettings = newSettings
            }
        }

        // Initialize DeviceMonitor for state echoing & health telemetry
        deviceMonitor = DeviceMonitor(context.applicationContext, scope)
        sessionScope.launch {
            deviceMonitor?.healthState?.collect { health ->
                _latestHealthState.value = health
                if (isStreamingActive) {
                    notifyStateChanged()
                }
            }
        }

        // Setup 150ms debounce state publisher for conflating changes
        stateDebounceJob?.cancel()
        stateDebounceJob = sessionScope.launch {
            stateChangeTrigger
                .debounce(150)
                .collect {
                    if (isStreamingActive) {
                        broadcastStateEcho()
                    }
                }
        }

        // Setup 5-second heartbeat to periodically report state
        heartbeatJob?.cancel()
        heartbeatJob = sessionScope.launch {
            while (isActive && isStreamingActive) {
                delay(5000)
                if (isStreamingActive) {
                    broadcastStateEcho()
                }
            }
        }

        // 1. Start Foreground Service
        val serviceIntent = Intent(context, CamLinkStreamService::class.java).apply {
            action = CamLinkStreamService.ACTION_START
            putExtra(CamLinkStreamService.EXTRA_KEEP_SCREEN_ON, settings.keepScreenOn)
            putExtra(CamLinkStreamService.EXTRA_TARGET_IP, config.ip)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 2. Initialize WebRTC Manager
        val rtc = WebRtcManager(
            context = context.applicationContext,
            scope = sessionScope,
            onLocalIceCandidate = { sdpMid, sdpMLineIndex, candidate ->
                signalingClient?.sendIceCandidate(sdpMid, sdpMLineIndex, candidate)
            },
            onOfferCreated = { sdp ->
                signalingClient?.sendOffer(sdp)
            }
        )
        webRtcManager = rtc

        // Start media hardware capturers
        rtc.startMedia(
            facing = settings.defaultCamera,
            resolution = settings.resolution,
            fps = settings.fps
        )

        // 3. Connect Signaling WebSocket
        setupSignaling(config, rtc)

        // 4. Observe RTC Status, Stats & Picture Controls
        sessionScope.launch {
            rtc.connectionStatus.collect { status ->
                _connectionStatus.value = status
                if (status == RtcConnectionStatus.CONNECTED) {
                    startTimer()
                    broadcastStateEcho()
                } else if (status == RtcConnectionStatus.FAILED || status == RtcConnectionStatus.DISCONNECTED) {
                    stopTimer()
                }
            }
        }

        sessionScope.launch {
            rtc.streamStats.collect { st ->
                _stats.value = st
            }
        }

        sessionScope.launch {
            rtc.isFrontFacing.collect { isFront ->
                _isFrontCamera.value = isFront
                inspectCameraHardwareCapabilities(context.applicationContext)
                notifyStateChanged()
            }
        }

        sessionScope.launch {
            rtc.isMicMuted.collect { muted ->
                _isMicMuted.value = muted
                notifyStateChanged()
            }
        }

        sessionScope.launch {
            rtc.isVideoPaused.collect { paused ->
                _isVideoPaused.value = paused
                notifyStateChanged()
            }
        }

        sessionScope.launch {
            rtc.isTorchOn.collect { torch ->
                _isTorchOn.value = torch
                notifyStateChanged()
            }
        }

        sessionScope.launch {
            rtc.isAfLocked.collect { locked ->
                _isAfLocked.value = locked
                notifyStateChanged()
            }
        }

        sessionScope.launch {
            rtc.isAeAwbLocked.collect { locked ->
                _isAeAwbLocked.value = locked
                notifyStateChanged()
            }
        }

        sessionScope.launch {
            rtc.currentZoom.collect { zoom ->
                _currentZoom.value = zoom
                notifyStateChanged()
            }
        }

        // 5. Setup Wi-Fi auto-reconnect network monitor
        registerNetworkMonitor(context.applicationContext)
    }

    private fun inspectCameraHardwareCapabilities(context: Context) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val ids = cm.cameraIdList
            _canFlip.value = ids.size > 1

            for (id in ids) {
                val chars = cm.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val isTarget = if (_isFrontCamera.value) {
                    facing == CameraCharacteristics.LENS_FACING_FRONT
                } else {
                    facing == CameraCharacteristics.LENS_FACING_BACK
                }
                if (isTarget) {
                    val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    _hasTorch.value = hasFlash
                    if (!hasFlash && _isTorchOn.value) {
                        _isTorchOn.value = false
                    }

                    val maxZ = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.upper ?: 5.0f
                    } else {
                        chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 5.0f
                    }
                    _maxZoom.value = maxZ.coerceAtLeast(1.0f)
                    if (_currentZoom.value > _maxZoom.value) {
                        _currentZoom.value = _maxZoom.value
                        webRtcManager?.setZoom(_maxZoom.value)
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error inspecting camera characteristics: ${e.message}")
        }
    }

    private fun setupSignaling(config: ConnectionConfig, rtc: WebRtcManager) {
        signalingClient?.stop()
        signalingClient = SignalingClient(
            config = config,
            scope = sessionScope,
            onJoined = {
                Log.d("CamLinkSignal", "PC confirmed 'joined'; sending immediate state and initiating WebRTC offer")
                // Requirement 5(a): send "state" right after "joined"
                broadcastStateEcho()
                sessionScope.launch {
                    rtc.createPeerConnection()
                    delay(100)
                    rtc.makeOffer()
                }
            },
            onAnswerReceived = { sdp ->
                rtc.onAnswerReceived(sdp)
                broadcastStateEcho()
            },
            onIceCandidateReceived = { sdpMid, sdpMLineIndex, candidate ->
                rtc.onIceCandidateReceived(sdpMid, sdpMLineIndex, candidate)
            },
            onSessionEnded = { reason ->
                Log.d("CamLinkSignal", "Session ended from PC: $reason")
                _errorMessage.value = reason
                stopSession()
            },
            onErrorReceived = { error ->
                Log.e("CamLinkSignal", "Signaling error: $error")
                _errorMessage.value = error
                stopSession()
            },
            onControlActionReceived = { action, payload ->
                handleRemoteControlAction(action, payload)
            }
        )
        signalingClient?.start()

        sessionScope.launch {
            signalingClient?.signalingState?.collect { state ->
                when (state) {
                    is SignalingState.Error -> {
                        _errorMessage.value = state.message
                        Log.e(TAG, "Signaling error: ${state.message}")
                    }
                    is SignalingState.Reconnecting -> {
                        _connectionStatus.value = RtcConnectionStatus.RECONNECTING
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Executes the exact remote control actions from "Shared Protocol":
     * - switchCamera
     * - toggleMic
     * - toggleVideo
     * - toggleMirror
     * - toggleTorch
     * - toggleAf
     * - toggleAe
     * - setZoom {value}
     * - setQuality {resolution, fps}
     * - endSession
     *
     * If "Allow PC to control camera" is OFF, all control actions are ignored (including endSession)
     * but the state message is still echoed back to the PC.
     */
    private fun handleRemoteControlAction(action: String, payload: JSONObject) {
        Log.d(TAG, "Handling remote control action: $action with payload: $payload")

        // Requirement 3: When "Allow PC to control camera" is OFF, ignore every control action but still send state
        if (currentSettings?.allowPcControl == false) {
            Log.i(TAG, "Ignoring control action '$action' because 'Allow PC to control camera' is disabled")
            broadcastStateEcho()
            return
        }

        when (action) {
            "switchCamera" -> {
                switchCamera()
            }
            "toggleMic" -> {
                toggleMute()
            }
            "toggleVideo" -> {
                toggleVideo()
            }
            "toggleMirror" -> {
                toggleMirror()
            }
            "toggleTorch" -> {
                toggleTorch()
            }
            "toggleAf" -> {
                toggleAutoFocusLock()
            }
            "toggleAe" -> {
                toggleExposureAwbLock()
            }
            "setZoom" -> {
                try {
                    val rawVal = when {
                        payload.has("value") -> payload.optDouble("value", Double.NaN)
                        payload.has("zoom") -> payload.optDouble("zoom", Double.NaN)
                        else -> Double.NaN
                    }
                    if (!rawVal.isNaN() && !rawVal.isInfinite() && rawVal > 0.0) {
                        val target = rawVal.toFloat().coerceIn(1.0f, _maxZoom.value.coerceAtLeast(1.0f))
                        // Skip tiny changes so a dragged PC slider does not flood the camera
                        if (kotlin.math.abs(target - _currentZoom.value) >= 0.02f) {
                            setZoom(target)
                        }
                    } else {
                        Log.w(TAG, "Ignoring invalid zoom value: $rawVal")
                        notifyStateChanged()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Malformed setZoom payload: ${e.message}")
                    notifyStateChanged()
                }
            }
            "setQuality" -> {
                try {
                    val resStr = payload.optString("resolution", "")
                    val fpsVal = payload.optInt("fps", 30)
                    val targetRes = when (resStr) {
                        "720p" -> StreamResolution.HD_720P
                        "1080p" -> StreamResolution.FHD_1080P
                        else -> null
                    }
                    val targetFps = when (fpsVal) {
                        30 -> StreamFps.FPS_30
                        60 -> StreamFps.FPS_60
                        else -> null
                    }
                    if (targetRes == null || targetFps == null) {
                        Log.w(TAG, "Ignoring invalid setQuality: $resStr@$fpsVal")
                    } else if (targetRes != _activeResolution.value || targetFps != _activeFps.value) {
                        changeResolution(targetRes, targetFps)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Malformed setQuality payload: ${e.message}")
                }
            }
            "endSession" -> {
                stopSession()
            }
            else -> {
                Log.w(TAG, "Unknown remote control action: $action")
            }
        }
        notifyStateChanged()
    }

    /**
     * Builds and sends the JSON state echo message matching the exact Shared Protocol schema:
     * {
     *   "type": "state",
     *   "facing": "back" | "front",
     *   "micMuted": Boolean,
     *   "videoPaused": Boolean,
     *   "mirrored": Boolean,
     *   "torch": Boolean,
     *   "afLocked": Boolean,
     *   "aeLocked": Boolean,
     *   "zoom": Float,
     *   "maxZoom": Float,
     *   "resolution": String,
     *   "fps": Int,
     *   "battery": Int,
     *   "thermal": "nominal" | "fair" | "serious" | "critical",
     *   "tempC": Float,
     *   "hasTorch": Boolean,
     *   "canFlip": Boolean
     * }
     */
    fun broadcastStateEcho() {
        val client = signalingClient ?: return
        val health = _latestHealthState.value

        // Thermal is computed in DeviceMonitor: always one of nominal / fair / serious / critical
        val thermalString = health.thermalLevel

        val resString = if (_activeResolution.value == StreamResolution.HD_720P) "720p" else "1080p"

        val json = JSONObject().apply {
            put("type", "state")
            put("facing", if (_isFrontCamera.value) "front" else "back")
            put("micMuted", _isMicMuted.value)
            put("videoPaused", _isVideoPaused.value)
            put("mirrored", _mirrorVideo.value)
            put("torch", _isTorchOn.value)
            put("afLocked", _isAfLocked.value)
            put("aeLocked", _isAeAwbLocked.value)
            put("zoom", _currentZoom.value.toDouble())
            put("maxZoom", _maxZoom.value.toDouble())
            put("resolution", resString)
            put("fps", _activeFps.value.fps)
            put("battery", health.batteryLevel)
            put("thermal", thermalString)
            put("tempC", health.batteryTempCelsius.toDouble())
            put("hasTorch", _hasTorch.value)
            put("canFlip", _canFlip.value)
        }

        client.sendState(json)
    }

    private fun registerNetworkMonitor(context: Context) {
        unregisterNetworkMonitor()
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivityManager = cm

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    Log.w(TAG, "Wi-Fi network lost during streaming session")
                    if (isStreamingActive) {
                        networkWasLost = true
                        _connectionStatus.value = RtcConnectionStatus.RECONNECTING
                    }
                }

                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Network available (wasLost=$networkWasLost)")
                    val sigState = signalingClient?.signalingState?.value
                    val alreadyUp = sigState is SignalingState.Connected || sigState is SignalingState.Joined
                    if (isStreamingActive && networkWasLost && !alreadyUp) {
                        networkWasLost = false
                        val config = _currentConfig.value
                        val rtc = webRtcManager
                        if (config != null && rtc != null) {
                            sessionScope.launch {
                                delay(1200)
                                Log.i(TAG, "Auto-reconnecting signaling & WebRTC after Wi-Fi recovery...")
                                setupSignaling(config, rtc)
                            }
                        }
                    }
                }
            }
            cm?.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    private fun unregisterNetworkMonitor() {
        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {}
        networkCallback = null
        connectivityManager = null
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = sessionScope.launch {
            while (isActive) {
                delay(1000)
                _sessionDurationSeconds.value += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Requirement 5: After switchCamera, re-read maxZoom and hasTorch for the new lens and send state again.
     */
    fun switchCamera() {
        webRtcManager?.switchCamera { isFront ->
            _isFrontCamera.value = isFront
            appContext?.let { inspectCameraHardwareCapabilities(it) }
            broadcastStateEcho()
        }
    }

    fun toggleMute() {
        val muted = webRtcManager?.toggleMicrophone() ?: false
        _isMicMuted.value = muted
        notifyStateChanged()
    }

    fun toggleVideo() {
        val paused = webRtcManager?.toggleVideo() ?: false
        _isVideoPaused.value = paused
        notifyStateChanged()
    }

    fun toggleMirror() {
        _mirrorVideo.value = !_mirrorVideo.value
        notifyStateChanged()
    }

    fun toggleTorch(): Boolean {
        val next = webRtcManager?.toggleTorch() ?: false
        _isTorchOn.value = next
        notifyStateChanged()
        return next
    }

    fun toggleAutoFocusLock(): Boolean {
        val next = webRtcManager?.toggleAutoFocusLock() ?: false
        _isAfLocked.value = next
        notifyStateChanged()
        return next
    }

    fun toggleExposureAwbLock(): Boolean {
        val next = webRtcManager?.toggleExposureAwbLock() ?: false
        _isAeAwbLocked.value = next
        notifyStateChanged()
        return next
    }

    /**
     * Requirement 6: Clamp to 1.0..maxZoom of the CURRENT camera; ignore NaN/negative values and malformed input without crashing.
     */
    fun setZoom(ratio: Float) {
        if (ratio.isNaN() || ratio.isInfinite() || ratio <= 0f) {
            Log.w(TAG, "Ignoring non-finite or non-positive zoom ratio: $ratio")
            return
        }
        val currentMax = _maxZoom.value.takeIf { it >= 1.0f } ?: 5.0f
        val clamped = ratio.coerceIn(1.0f, currentMax)
        webRtcManager?.setZoom(clamped)
        _currentZoom.value = clamped
        notifyStateChanged()
    }

    fun changeResolution(resolution: StreamResolution, fps: StreamFps) {
        _activeResolution.value = resolution
        _activeFps.value = fps
        webRtcManager?.changeFormat(resolution, fps)
        notifyStateChanged()
    }

    fun getLocalVideoTrack(): VideoTrack? {
        return webRtcManager?.getVideoTrack()
    }

    fun stopSession(context: Context? = null) {
        isStreamingActive = false
        networkWasLost = false
        sessionJob?.cancel()
        sessionJob = null
        stopTimer()
        heartbeatJob?.cancel()
        heartbeatJob = null
        stateDebounceJob?.cancel()
        stateDebounceJob = null
        unregisterNetworkMonitor()

        signalingClient?.stop()
        signalingClient = null

        webRtcManager?.release()
        webRtcManager = null

        deviceMonitor = null

        _connectionStatus.value = RtcConnectionStatus.DISCONNECTED
        _currentConfig.value = null

        val targetCtx = context ?: appContext
        targetCtx?.let {
            val stopIntent = Intent(it, CamLinkStreamService::class.java).apply {
                action = CamLinkStreamService.ACTION_STOP
            }
            it.startService(stopIntent)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
