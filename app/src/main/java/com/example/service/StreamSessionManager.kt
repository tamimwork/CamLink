package com.example.service

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.VideoTrack

object StreamSessionManager {
    private const val TAG = "CamLinkSession"

    private val scope = CoroutineScope(Dispatchers.Main)

    private var signalingClient: SignalingClient? = null
    var webRtcManager: WebRtcManager? = null
        private set

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

    private val _activeResolution = MutableStateFlow(StreamResolution.FHD_1080P)
    val activeResolution: StateFlow<StreamResolution> = _activeResolution.asStateFlow()

    private val _activeFps = MutableStateFlow(StreamFps.FPS_30)
    val activeFps: StateFlow<StreamFps> = _activeFps.asStateFlow()

    private val _sessionDurationSeconds = MutableStateFlow(0L)
    val sessionDurationSeconds: StateFlow<Long> = _sessionDurationSeconds.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var timerJob: Job? = null
    private var isStreamingActive = false

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var appContext: Context? = null
    private var currentSettings: StreamSettings? = null

    fun startSession(context: Context, config: ConnectionConfig, settings: StreamSettings) {
        if (isStreamingActive) {
            stopSession(context)
        }

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
            scope = scope,
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
        scope.launch {
            rtc.connectionStatus.collect { status ->
                _connectionStatus.value = status
                if (status == RtcConnectionStatus.CONNECTED) {
                    startTimer()
                } else if (status == RtcConnectionStatus.FAILED || status == RtcConnectionStatus.DISCONNECTED) {
                    stopTimer()
                }
            }
        }

        scope.launch {
            rtc.streamStats.collect { st ->
                _stats.value = st
            }
        }

        scope.launch {
            rtc.isFrontFacing.collect { isFront ->
                _isFrontCamera.value = isFront
            }
        }

        scope.launch {
            rtc.isMicMuted.collect { muted ->
                _isMicMuted.value = muted
            }
        }

        scope.launch {
            rtc.isVideoPaused.collect { paused ->
                _isVideoPaused.value = paused
            }
        }

        scope.launch {
            rtc.isTorchOn.collect { torch ->
                _isTorchOn.value = torch
            }
        }

        scope.launch {
            rtc.isAfLocked.collect { locked ->
                _isAfLocked.value = locked
            }
        }

        scope.launch {
            rtc.isAeAwbLocked.collect { locked ->
                _isAeAwbLocked.value = locked
            }
        }

        scope.launch {
            rtc.currentZoom.collect { zoom ->
                _currentZoom.value = zoom
            }
        }

        // 5. Setup Wi-Fi auto-reconnect network monitor
        registerNetworkMonitor(context.applicationContext)
    }

    private fun setupSignaling(config: ConnectionConfig, rtc: WebRtcManager) {
        signalingClient?.stop()
        signalingClient = SignalingClient(
            config = config,
            scope = scope,
            onJoined = {
                Log.d("CamLinkSignal", "PC confirmed 'joined'; initiating WebRTC offer")
                scope.launch {
                    rtc.createPeerConnection()
                    delay(100)
                    rtc.makeOffer()
                }
            },
            onAnswerReceived = { sdp ->
                rtc.onAnswerReceived(sdp)
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
            }
        )
        signalingClient?.start()

        scope.launch {
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
                        _connectionStatus.value = RtcConnectionStatus.RECONNECTING
                    }
                }

                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Network connection restored")
                    if (isStreamingActive) {
                        val config = _currentConfig.value
                        val rtc = webRtcManager
                        if (config != null && rtc != null) {
                            scope.launch {
                                delay(1200) // Brief stabilization wait
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
        timerJob = scope.launch {
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

    fun switchCamera() {
        webRtcManager?.switchCamera { isFront ->
            _isFrontCamera.value = isFront
        }
    }

    fun toggleMute() {
        val muted = webRtcManager?.toggleMicrophone() ?: false
        _isMicMuted.value = muted
    }

    fun toggleVideo() {
        val paused = webRtcManager?.toggleVideo() ?: false
        _isVideoPaused.value = paused
    }

    fun toggleMirror() {
        _mirrorVideo.value = !_mirrorVideo.value
    }

    fun toggleTorch(): Boolean {
        return webRtcManager?.toggleTorch() ?: false
    }

    fun toggleAutoFocusLock(): Boolean {
        return webRtcManager?.toggleAutoFocusLock() ?: false
    }

    fun toggleExposureAwbLock(): Boolean {
        return webRtcManager?.toggleExposureAwbLock() ?: false
    }

    fun setZoom(ratio: Float) {
        webRtcManager?.setZoom(ratio)
    }

    fun changeResolution(resolution: StreamResolution, fps: StreamFps) {
        _activeResolution.value = resolution
        _activeFps.value = fps
        webRtcManager?.changeFormat(resolution, fps)
    }

    fun getLocalVideoTrack(): VideoTrack? {
        return webRtcManager?.getVideoTrack()
    }

    fun stopSession(context: Context? = null) {
        isStreamingActive = false
        stopTimer()
        unregisterNetworkMonitor()

        signalingClient?.stop()
        signalingClient = null

        webRtcManager?.release()
        webRtcManager = null

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
