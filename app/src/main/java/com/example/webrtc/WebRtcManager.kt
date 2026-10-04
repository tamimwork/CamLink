package com.example.webrtc

import android.content.Context
import android.util.Log
import com.example.data.model.CameraFacing
import com.example.data.model.StreamFps
import com.example.data.model.StreamResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

enum class RtcConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

data class StreamStats(
    val fps: Int = 0,
    val resolution: String = "1920x1080",
    val bitrateKbps: Long = 0,
    val connectionState: RtcConnectionStatus = RtcConnectionStatus.DISCONNECTED
)

class WebRtcManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val onLocalIceCandidate: (sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit,
    private val onOfferCreated: (String) -> Unit
) {
    private val tag = "CamLinkWebRTC"

    val eglBase: EglBase = EglBase.create()

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null

    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null

    private val _connectionStatus = MutableStateFlow(RtcConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<RtcConnectionStatus> = _connectionStatus.asStateFlow()

    private val _streamStats = MutableStateFlow(StreamStats())
    val streamStats: StateFlow<StreamStats> = _streamStats.asStateFlow()

    private val _isFrontFacing = MutableStateFlow(false)
    val isFrontFacing: StateFlow<Boolean> = _isFrontFacing.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isVideoPaused = MutableStateFlow(false)
    val isVideoPaused: StateFlow<Boolean> = _isVideoPaused.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isAfLocked = MutableStateFlow(false)
    val isAfLocked: StateFlow<Boolean> = _isAfLocked.asStateFlow()

    private val _isAeAwbLocked = MutableStateFlow(false)
    val isAeAwbLocked: StateFlow<Boolean> = _isAeAwbLocked.asStateFlow()

    private val _currentZoom = MutableStateFlow(1.0f)
    val currentZoom: StateFlow<Float> = _currentZoom.asStateFlow()

    private val qualityController = CameraQualityController(context) { videoCapturer }

    private val localIceQueue = mutableListOf<Triple<String, Int, String>>()
    private val remoteIceQueue = mutableListOf<IceCandidate>()
    private var isOfferSent = false
    private var isAnswerSet = false

    private var statsJob: Job? = null

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            /* enableIntelVp8 = */ true,
            /* enableH264HighProfile = */ true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    fun startMedia(
        facing: CameraFacing = CameraFacing.BACK,
        resolution: StreamResolution = StreamResolution.FHD_1080P,
        fps: StreamFps = StreamFps.FPS_30
    ) {
        val f = factory ?: return
        val isFront = facing == CameraFacing.FRONT
        _isFrontFacing.value = isFront

        // 1. Audio Track setup with noise suppression and echo cancellation
        if (audioTrack == null) {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            }
            audioSource = f.createAudioSource(audioConstraints)
            audioTrack = f.createAudioTrack("camlink_audio", audioSource).apply {
                setEnabled(!_isMicMuted.value)
            }
        }

        // 2. Video Capturer & Track setup
        if (videoTrack == null) {
            surfaceTextureHelper = SurfaceTextureHelper.create("WebRtcCaptureThread", eglBase.eglBaseContext)
            videoSource = f.createVideoSource(false)

            videoCapturer = createCameraCapturer(isFront)
            videoCapturer?.let { capturer ->
                capturer.initialize(
                    surfaceTextureHelper,
                    context,
                    videoSource?.capturerObserver
                )
                try {
                    capturer.startCapture(resolution.width, resolution.height, fps.fps)
                    Log.d(tag, "Started camera capture: ${resolution.width}x${resolution.height} @ ${fps.fps}fps")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to start capture: ${e.message}")
                }
            }

            videoTrack = f.createVideoTrack("camlink_video", videoSource).apply {
                setEnabled(!_isVideoPaused.value)
            }
        }

        _streamStats.value = _streamStats.value.copy(
            resolution = "${resolution.width}x${resolution.height}",
            fps = fps.fps
        )
    }

    fun createPeerConnection() {
        val f = factory ?: return

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            enableCpuOveruseDetection = true
        }

        peerConnection?.dispose()
        _connectionStatus.value = RtcConnectionStatus.CONNECTING
        isOfferSent = false
        isAnswerSet = false
        synchronized(localIceQueue) { localIceQueue.clear() }
        synchronized(remoteIceQueue) { remoteIceQueue.clear() }

        peerConnection = f.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(tag, "SignalingState: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(tag, "IceConnectionState: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        _connectionStatus.value = RtcConnectionStatus.CONNECTED
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        _connectionStatus.value = RtcConnectionStatus.RECONNECTING
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        _connectionStatus.value = RtcConnectionStatus.FAILED
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        _connectionStatus.value = RtcConnectionStatus.DISCONNECTED
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(tag, "onIceConnectionReceivingChange: $receiving")
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d(tag, "PeerConnectionState: $newState")
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        _connectionStatus.value = RtcConnectionStatus.CONNECTED
                        startStatsPolling()
                    }
                    PeerConnection.PeerConnectionState.CONNECTING -> {
                        _connectionStatus.value = RtcConnectionStatus.CONNECTING
                    }
                    PeerConnection.PeerConnectionState.FAILED -> {
                        _connectionStatus.value = RtcConnectionStatus.FAILED
                        stopStatsPolling()
                    }
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        _connectionStatus.value = RtcConnectionStatus.RECONNECTING
                    }
                    PeerConnection.PeerConnectionState.CLOSED -> {
                        _connectionStatus.value = RtcConnectionStatus.DISCONNECTED
                        stopStatsPolling()
                    }
                    else -> {}
                }
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d("CamLinkSignal", "Generated local ICE candidate: ${it.sdpMid} / ${it.sdpMLineIndex}")
                    synchronized(localIceQueue) {
                        if (isOfferSent) {
                            onLocalIceCandidate(it.sdpMid ?: "0", it.sdpMLineIndex, it.sdp)
                        } else {
                            Log.d("CamLinkSignal", "Queuing local ICE candidate (offer not sent yet)")
                            localIceQueue.add(Triple(it.sdpMid ?: "0", it.sdpMLineIndex, it.sdp))
                        }
                    }
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        // Attach tracks with sendonly direction
        val pc = peerConnection ?: return
        val streamIds = listOf("camlink_stream_0")
        videoTrack?.let { pc.addTrack(it, streamIds) }
        audioTrack?.let { pc.addTrack(it, streamIds) }

        try {
            pc.transceivers?.forEach { transceiver ->
                transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
            }
        } catch (_: Exception) {}
    }

    fun makeOffer() {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                Log.d("CamLinkSignal", "Local SDP offer created successfully")
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Log.d("CamLinkSignal", "Local description set successfully; dispatching offer")
                        isOfferSent = true
                        onOfferCreated(desc.description)

                        // Drain local queued ICE candidates
                        val queued = synchronized(localIceQueue) {
                            val copy = ArrayList(localIceQueue)
                            localIceQueue.clear()
                            copy
                        }
                        for (c in queued) {
                            Log.d("CamLinkSignal", "Draining queued local ICE candidate: ${c.first} / ${c.second}")
                            onLocalIceCandidate(c.first, c.second, c.third)
                        }
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e(tag, "Failed to set local description: $error")
                    }
                }, desc)
            }

            override fun onCreateFailure(error: String?) {
                Log.e(tag, "Failed to create offer: $error")
            }
        }, constraints)
    }

    fun onAnswerReceived(answerSdp: String) {
        val pc = peerConnection ?: return
        val desc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d("CamLinkSignal", "Remote answer description set successfully; draining remote ICE queue")
                isAnswerSet = true

                // Drain remote queued ICE candidates
                val queued = synchronized(remoteIceQueue) {
                    val copy = ArrayList(remoteIceQueue)
                    remoteIceQueue.clear()
                    copy
                }
                for (ice in queued) {
                    Log.d("CamLinkSignal", "Draining queued remote ICE candidate: ${ice.sdpMid} / ${ice.sdpMLineIndex}")
                    pc.addIceCandidate(ice)
                }
            }

            override fun onSetFailure(error: String?) {
                Log.e(tag, "Failed to set remote description: $error")
            }
        }, desc)
    }

    fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val pc = peerConnection ?: return
        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        synchronized(remoteIceQueue) {
            if (isAnswerSet) {
                Log.d("CamLinkSignal", "Adding remote ICE candidate: $sdpMid / $sdpMLineIndex")
                pc.addIceCandidate(iceCandidate)
            } else {
                Log.d("CamLinkSignal", "Queuing remote ICE candidate (answer not set yet)")
                remoteIceQueue.add(iceCandidate)
            }
        }
    }

    fun switchCamera(onComplete: ((Boolean) -> Unit)? = null) {
        videoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                _isFrontFacing.value = isFront
                Log.d(tag, "Switched camera to isFront: $isFront")
                onComplete?.invoke(isFront)
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                Log.e(tag, "Camera switch error: $errorDescription")
                onComplete?.invoke(_isFrontFacing.value)
            }
        })
    }

    fun toggleMicrophone(): Boolean {
        val newMuted = !_isMicMuted.value
        _isMicMuted.value = newMuted
        audioTrack?.setEnabled(!newMuted)
        return newMuted
    }

    fun toggleVideo(): Boolean {
        val newPaused = !_isVideoPaused.value
        _isVideoPaused.value = newPaused
        videoTrack?.setEnabled(!newPaused)
        return newPaused
    }

    fun toggleTorch(): Boolean {
        val next = !_isTorchOn.value
        qualityController.setTorch(next)
        _isTorchOn.value = next
        return next
    }

    fun toggleAutoFocusLock(): Boolean {
        val next = !_isAfLocked.value
        qualityController.setAutoFocusLock(next)
        _isAfLocked.value = next
        return next
    }

    fun toggleExposureAwbLock(): Boolean {
        val next = !_isAeAwbLocked.value
        qualityController.setExposureAwbLock(next)
        _isAeAwbLocked.value = next
        return next
    }

    fun setZoom(ratio: Float) {
        val clamped = ratio.coerceIn(1.0f, 5.0f)
        qualityController.setZoom(clamped)
        _currentZoom.value = clamped
    }

    fun changeFormat(resolution: StreamResolution, fps: StreamFps) {
        try {
            videoCapturer?.changeCaptureFormat(resolution.width, resolution.height, fps.fps)
            _streamStats.value = _streamStats.value.copy(
                resolution = "${resolution.width}x${resolution.height}",
                fps = fps.fps
            )
            Log.d(tag, "Changed capture format: ${resolution.width}x${resolution.height} @ ${fps.fps}fps")
        } catch (e: Exception) {
            Log.e(tag, "Error changing format: ${e.message}")
        }
    }

    fun getVideoTrack(): VideoTrack? = videoTrack

    private fun createCameraCapturer(isFront: Boolean): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        var selectedName: String? = null
        for (name in deviceNames) {
            if (isFront && enumerator.isFrontFacing(name)) {
                selectedName = name
                break
            } else if (!isFront && enumerator.isBackFacing(name)) {
                selectedName = name
                break
            }
        }

        if (selectedName == null && deviceNames.isNotEmpty()) {
            selectedName = deviceNames[0]
        }

        return selectedName?.let {
            enumerator.createCapturer(it, object : CameraVideoCapturer.CameraEventsHandler {
                override fun onCameraError(p0: String?) {
                    Log.e(tag, "Camera error: $p0")
                }

                override fun onCameraDisconnected() {
                    Log.w(tag, "Camera disconnected")
                }

                override fun onCameraFreezed(p0: String?) {
                    Log.w(tag, "Camera freezed: $p0")
                }

                override fun onCameraOpening(p0: String?) {
                    Log.d(tag, "Camera opening: $p0")
                }

                override fun onFirstFrameAvailable() {
                    Log.d(tag, "Camera first frame available")
                }

                override fun onCameraClosed() {
                    Log.d(tag, "Camera closed")
                }
            })
        }
    }

    private fun startStatsPolling() {
        stopStatsPolling()
        statsJob = scope.launch {
            while (isActive) {
                delay(2000)
                peerConnection?.getStats { report ->
                    var totalBytesSent = 0L
                    for (stat in report.statsMap.values) {
                        if (stat.type == "outbound-rtp" && stat.members["kind"] == "video") {
                            val bytes = stat.members["bytesSent"] as? Long
                            if (bytes != null) {
                                totalBytesSent = bytes
                            }
                        }
                    }
                    _streamStats.value = _streamStats.value.copy(
                        bitrateKbps = (totalBytesSent * 8) / 1024,
                        connectionState = _connectionStatus.value
                    )
                }
            }
        }
    }

    private fun stopStatsPolling() {
        statsJob?.cancel()
        statsJob = null
    }

    fun release() {
        stopStatsPolling()
        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {}
        videoCapturer?.dispose()
        videoCapturer = null

        videoTrack?.dispose()
        videoTrack = null

        videoSource?.dispose()
        videoSource = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        audioTrack?.dispose()
        audioTrack = null

        audioSource?.dispose()
        audioSource = null

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        factory?.dispose()
        factory = null

        eglBase.release()
        _connectionStatus.value = RtcConnectionStatus.DISCONNECTED
    }
}
