package com.example.webrtc

import android.os.Build
import android.util.Log
import com.example.data.model.ConnectionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

sealed interface SignalingState {
    data object Disconnected : SignalingState
    data object Connecting : SignalingState
    data object Connected : SignalingState
    data object Joined : SignalingState
    data class Reconnecting(val attempt: Int) : SignalingState
    data class Error(val message: String) : SignalingState
}

class SignalingClient(
    private val config: ConnectionConfig,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val onJoined: () -> Unit,
    private val onAnswerReceived: (String) -> Unit,
    private val onIceCandidateReceived: (String, Int, String) -> Unit,
    private val onSessionEnded: (String) -> Unit,
    private val onErrorReceived: (String) -> Unit,
    private val onLatencyMeasured: ((Long) -> Unit)? = null
) {
    companion object {
        private const val SIGNAL_TAG = "CamLinkSignal"
    }

    private val _signalingState = MutableStateFlow<SignalingState>(SignalingState.Disconnected)
    val signalingState: StateFlow<SignalingState> = _signalingState.asStateFlow()

    private val _currentLatencyMs = MutableStateFlow<Long>(0)
    val currentLatencyMs: StateFlow<Long> = _currentLatencyMs.asStateFlow()

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private var retryCount = 0
    private var isIntentionalClose = false

    fun start() {
        isIntentionalClose = false
        retryCount = 0
        connect()
    }

    private fun connect() {
        reconnectJob?.cancel()
        pingJob?.cancel()
        _signalingState.value = if (retryCount > 0) SignalingState.Reconnecting(retryCount) else SignalingState.Connecting

        try {
            client = LanSslHelper.createLanOkHttpClient(config.ip)
            val request = Request.Builder()
                .url(config.websocketUrl)
                .build()

            Log.d(SIGNAL_TAG, "Connecting to WebSocket: ${config.websocketUrl}")

            webSocket = client?.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(SIGNAL_TAG, "WebSocket opened with ${config.websocketUrl}")
                    retryCount = 0
                    _signalingState.value = SignalingState.Connected

                    // 1. Phone -> PC: {"type":"join","code":"...","deviceName":"..."}
                    // Immediately after the socket opens
                    val deviceName = if (Build.MODEL.isNullOrBlank()) "Android Device" else Build.MODEL
                    val joinMessage = JSONObject().apply {
                        put("type", "join")
                        put("code", config.code)
                        put("deviceName", deviceName)
                    }
                    sendPayload(webSocket, joinMessage)

                    // Start 2-second ping timer
                    startPingTimer(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(SIGNAL_TAG, "Received: $text")
                    handleIncomingMessage(text, webSocket)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(SIGNAL_TAG, "WebSocket closing: $code / $reason")
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(SIGNAL_TAG, "WebSocket closed: $code / $reason")
                    pingJob?.cancel()
                    _signalingState.value = SignalingState.Disconnected
                    if (!isIntentionalClose) {
                        scheduleReconnect()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(SIGNAL_TAG, "WebSocket failure: ${t.message}")
                    pingJob?.cancel()
                    _signalingState.value = SignalingState.Error(t.localizedMessage ?: "Connection failure")
                    if (!isIntentionalClose) {
                        scheduleReconnect()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(SIGNAL_TAG, "Connect error: ${e.message}")
            _signalingState.value = SignalingState.Error(e.localizedMessage ?: "Failed to connect")
            if (!isIntentionalClose) {
                scheduleReconnect()
            }
        }
    }

    private fun handleIncomingMessage(text: String, ws: WebSocket) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type", "").lowercase()

            when (type) {
                "joined" -> {
                    // Code accepted; now create the offer
                    _signalingState.value = SignalingState.Joined
                    onJoined()
                }
                "error" -> {
                    val reason = json.optString("reason", "")
                    val friendly = when (reason) {
                        "invalid_code" -> "Invalid pairing code. Please check the code on your PC."
                        "busy" -> "PC is already connected to another device."
                        else -> "Connection rejected by PC: $reason"
                    }
                    _signalingState.value = SignalingState.Error(friendly)
                    onErrorReceived(friendly)
                    isIntentionalClose = true
                    ws.close(1000, friendly)
                }
                "answer" -> {
                    val sdp = json.optString("sdp", "")
                    if (sdp.isNotEmpty()) {
                        onAnswerReceived(sdp)
                    }
                }
                "ice" -> {
                    val candidate = json.optString("candidate", "")
                    val sdpMid = json.optString("sdpMid", "0")
                    val sdpMLineIndex = json.optInt("sdpMLineIndex", 0)
                    if (candidate.isNotEmpty()) {
                        onIceCandidateReceived(sdpMid, sdpMLineIndex, candidate)
                    }
                }
                "pong" -> {
                    val t = json.optLong("t", 0L)
                    if (t > 0) {
                        val latency = (System.currentTimeMillis() - t).coerceAtLeast(0)
                        _currentLatencyMs.value = latency
                        onLatencyMeasured?.invoke(latency)
                    }
                }
                "bye" -> {
                    // PC stopped session -> stop streaming and notify
                    _signalingState.value = SignalingState.Disconnected
                    onSessionEnded("Session ended by PC")
                    isIntentionalClose = true
                    ws.close(1000, "PC stopped session")
                }
                "ping" -> {
                    // In case PC also sends ping, echo back pong
                    val t = json.optLong("t", System.currentTimeMillis())
                    val pong = JSONObject().apply {
                        put("type", "pong")
                        put("t", t)
                    }
                    sendPayload(ws, pong)
                }
            }
        } catch (e: Exception) {
            Log.e(SIGNAL_TAG, "Error handling message: ${e.message}")
        }
    }

    private fun startPingTimer(ws: WebSocket) {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(2000)
                try {
                    val ping = JSONObject().apply {
                        put("type", "ping")
                        put("t", System.currentTimeMillis())
                    }
                    sendPayload(ws, ping)
                } catch (e: Exception) {
                    Log.w(SIGNAL_TAG, "Ping error: ${e.message}")
                }
            }
        }
    }

    fun sendOffer(sdp: String) {
        val json = JSONObject().apply {
            put("type", "offer")
            put("sdp", sdp)
        }
        val ws = webSocket
        if (ws != null) {
            sendPayload(ws, json)
        } else {
            Log.w(SIGNAL_TAG, "Cannot send offer: WebSocket is null")
        }
    }

    fun sendIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val json = JSONObject().apply {
            put("type", "ice")
            put("candidate", candidate)
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
        }
        val ws = webSocket
        if (ws != null) {
            sendPayload(ws, json)
        } else {
            Log.w(SIGNAL_TAG, "Cannot send ice: WebSocket is null")
        }
    }

    fun sendLeave() {
        val json = JSONObject().apply {
            put("type", "leave")
        }
        val ws = webSocket
        if (ws != null) {
            sendPayload(ws, json)
        }
    }

    private fun sendPayload(ws: WebSocket, json: JSONObject) {
        val text = json.toString()
        Log.d(SIGNAL_TAG, "Sent: $text")
        ws.send(text)
    }

    private fun scheduleReconnect() {
        if (isIntentionalClose) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            retryCount++
            val backoffMs = (1000L * retryCount).coerceAtMost(8000L)
            Log.d(SIGNAL_TAG, "Scheduling reconnect attempt $retryCount in ${backoffMs}ms")
            delay(backoffMs)
            if (isActive && !isIntentionalClose) {
                connect()
            }
        }
    }

    fun stop() {
        isIntentionalClose = true
        reconnectJob?.cancel()
        pingJob?.cancel()
        try {
            sendLeave()
            webSocket?.close(1000, "User left")
        } catch (_: Exception) {}
        webSocket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
        _signalingState.value = SignalingState.Disconnected
    }
}
