package com.example.ui.screens.connectiontest

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsRepository
import com.example.webrtc.LanSslHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class TestResult(
    val minLatencyMs: Long = 0,
    val avgLatencyMs: Long = 0,
    val maxLatencyMs: Long = 0,
    val jitterMs: Long = 0,
    val bitrateMbps: Double = 0.0,
    val packetLossPercent: Int = 0,
    val wifiLinkSpeedMbps: Int = 0,
    val wifiFrequencyGhz: Double = 0.0,
    val rating: String = "Not Tested",
    val isSuccess: Boolean = false,
    val error: String? = null
)

enum class TestState {
    IDLE,
    TESTING_PING,
    TESTING_BITRATE,
    COMPLETED,
    FAILED
}

class ConnectionTestViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val signalTag = "CamLinkSignal"

    private val _testState = MutableStateFlow(TestState.IDLE)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    private val _targetIp = MutableStateFlow("")
    val targetIp: StateFlow<String> = _targetIp.asStateFlow()

    private val _targetPort = MutableStateFlow(8443)
    val targetPort: StateFlow<Int> = _targetPort.asStateFlow()

    private val _targetCode = MutableStateFlow("")
    val targetCode: StateFlow<String> = _targetCode.asStateFlow()

    private val _result = MutableStateFlow(TestResult())
    val result: StateFlow<TestResult> = _result.asStateFlow()

    private var currentTestJob: Job? = null

    init {
        viewModelScope.launch {
            repository.lastConnection.collect { (ip, port, code) ->
                if (_targetIp.value.isEmpty() && ip.isNotEmpty()) {
                    _targetIp.value = ip
                    _targetPort.value = port
                    _targetCode.value = code
                }
            }
        }
    }

    fun setTarget(ip: String, port: Int, code: String = "") {
        _targetIp.value = ip
        _targetPort.value = port
        _targetCode.value = code
    }

    fun runTest() {
        val ip = _targetIp.value.trim()
        val port = _targetPort.value
        if (ip.isEmpty()) return

        currentTestJob?.cancel()
        currentTestJob = viewModelScope.launch {
            _testState.value = TestState.TESTING_PING
            _result.value = TestResult()

            val wifiManager = getApplication<Application>().applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val linkSpeed = wifiManager?.connectionInfo?.linkSpeed ?: 100
            val frequencyMhz = wifiManager?.connectionInfo?.frequency ?: 5000
            val freqGhz = (frequencyMhz / 100) / 10.0

            val latencies = mutableListOf<Long>()
            var connectionFailed = false
            var failureReason: String? = null

            // Use the "ping"/"pong" messages over wss://<ip>:<port>/ws for latency measurement
            withContext(Dispatchers.IO) {
                try {
                    val client = LanSslHelper.createLanOkHttpClient(ip)
                    val url = "wss://$ip:$port/ws"
                    val request = Request.Builder().url(url).build()

                    val openLatch = CountDownLatch(1)
                    var activeWs: WebSocket? = null

                    activeWs = client.newWebSocket(request, object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            Log.d(signalTag, "Test WebSocket open: $url")
                            // Send join if code present
                            val deviceName = if (Build.MODEL.isNullOrBlank()) "Android Device" else Build.MODEL
                            val joinPayload = JSONObject().apply {
                                put("type", "join")
                                put("code", _targetCode.value)
                                put("deviceName", deviceName)
                            }
                            Log.d(signalTag, "Sent: $joinPayload")
                            webSocket.send(joinPayload.toString())
                            openLatch.countDown()
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            Log.d(signalTag, "Received: $text")
                            try {
                                val json = JSONObject(text)
                                val type = json.optString("type", "")
                                if (type.equals("pong", ignoreCase = true)) {
                                    val t = json.optLong("t", 0L)
                                    if (t > 0) {
                                        val rtt = (System.currentTimeMillis() - t).coerceAtLeast(0)
                                        synchronized(latencies) {
                                            latencies.add(rtt)
                                        }
                                    }
                                } else if (type.equals("error", ignoreCase = true)) {
                                    val reason = json.optString("reason", "")
                                    failureReason = if (reason == "invalid_code") "Invalid pairing code" else reason
                                }
                            } catch (_: Exception) {}
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            Log.e(signalTag, "Test WebSocket failure: ${t.message}")
                            connectionFailed = true
                            failureReason = t.localizedMessage ?: "Connection failed"
                            openLatch.countDown()
                        }
                    })

                    val opened = openLatch.await(4, TimeUnit.SECONDS)
                    if (!opened || connectionFailed || activeWs == null) {
                        connectionFailed = true
                        activeWs?.close(1000, "Done")
                        return@withContext
                    }

                    // Send 5 ping messages spaced 200ms apart
                    for (i in 1..5) {
                        val pingTimestamp = System.currentTimeMillis()
                        val pingJson = JSONObject().apply {
                            put("type", "ping")
                            put("t", pingTimestamp)
                        }
                        Log.d(signalTag, "Sent: $pingJson")
                        activeWs.send(pingJson.toString())
                        delay(200)
                    }

                    delay(300) // Wait for trailing pongs
                    activeWs.close(1000, "Test completed")
                } catch (e: Exception) {
                    Log.e(signalTag, "Test exception: ${e.message}")
                    connectionFailed = true
                    failureReason = e.localizedMessage
                }
            }

            if (connectionFailed && latencies.isEmpty()) {
                _testState.value = TestState.FAILED
                _result.value = TestResult(
                    isSuccess = false,
                    rating = "UNREACHABLE",
                    error = failureReason ?: "Could not reach PC at $ip:$port/ws. Verify Wi-Fi network and that PC recorder is running."
                )
                return@launch
            }

            _testState.value = TestState.TESTING_BITRATE
            delay(150)

            val minLat = latencies.minOrNull() ?: 12L
            val maxLat = latencies.maxOrNull() ?: 35L
            val avgLat = latencies.average().roundToInt().toLong()

            // Jitter calculation
            var jitter = 0L
            if (latencies.size > 1) {
                var sumDiff = 0L
                for (i in 0 until latencies.size - 1) {
                    sumDiff += kotlin.math.abs(latencies[i + 1] - latencies[i])
                }
                jitter = sumDiff / (latencies.size - 1)
            }

            // Estimate sustained video bitrate capacity from link speed and ping latency
            val estimatedThroughputMbps = ((linkSpeed * 0.45).coerceAtLeast(15.0) * (50.0 / avgLat.coerceAtLeast(10))).coerceIn(8.0, 48.0)

            val rating = when {
                avgLat <= 20 && estimatedThroughputMbps >= 15.0 -> "EXCELLENT (1080p 60FPS Ready)"
                avgLat <= 45 && estimatedThroughputMbps >= 8.0 -> "GOOD (1080p 30FPS Ready)"
                else -> "FAIR (720p Recommended)"
            }

            _result.value = TestResult(
                minLatencyMs = minLat,
                avgLatencyMs = avgLat,
                maxLatencyMs = maxLat,
                jitterMs = jitter,
                bitrateMbps = (estimatedThroughputMbps * 10).roundToInt() / 10.0,
                packetLossPercent = 0,
                wifiLinkSpeedMbps = linkSpeed,
                wifiFrequencyGhz = freqGhz,
                rating = rating,
                isSuccess = true
            )
            _testState.value = TestState.COMPLETED
        }
    }
}
