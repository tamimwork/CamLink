package com.example.ui.screens.home

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsRepository
import com.example.data.model.ConnectionConfig
import com.example.data.model.StreamSettings
import com.example.service.StreamSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

data class NetworkInfoState(
    val isWifiConnected: Boolean = false,
    val wifiName: String = "Not Connected",
    val localIpAddress: String = "Unknown"
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val streamSettings: StateFlow<StreamSettings> = repository.streamSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StreamSettings()
    )

    val lastConnection: StateFlow<Triple<String, Int, String>> = repository.lastConnection.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Triple("", 8443, "")
    )

    private val _networkInfo = MutableStateFlow(NetworkInfoState())
    val networkInfo: StateFlow<NetworkInfoState> = _networkInfo.asStateFlow()

    init {
        refreshNetworkInfo()
    }

    fun refreshNetworkInfo() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val connectivityManager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(network)

            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            val wifiManager = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val rawSsid = wifiManager?.connectionInfo?.ssid?.replace("\"", "") ?: "Wi-Fi"
            val ssid = if (rawSsid == "<unknown ssid>" || rawSsid.isEmpty()) "Wi-Fi Connected" else rawSsid

            val localIp = getLocalIpAddress() ?: "127.0.0.1"

            _networkInfo.value = NetworkInfoState(
                isWifiConnected = isWifi,
                wifiName = if (isWifi) ssid else "No Wi-Fi (Cellular/Offline)",
                localIpAddress = localIp
            )
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun startSession(config: ConnectionConfig) {
        viewModelScope.launch {
            repository.saveLastConnection(config.ip, config.port, config.code)
            StreamSessionManager.startSession(
                context = getApplication(),
                config = config,
                settings = streamSettings.value
            )
        }
    }
}
