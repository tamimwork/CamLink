package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * [thermalLevel] is always one of: "nominal", "fair", "serious", "critical" (Shared Protocol).
 */
data class DeviceHealthState(
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 28.0f,
    val isOverheating: Boolean = false,
    val isLowBattery: Boolean = false,
    val warningMessage: String? = null,
    val thermalLevel: String = "nominal"
)

class DeviceMonitor(
    private val context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    val healthState: StateFlow<DeviceHealthState> = callbackFlow {
        var level = 100
        var charging = false
        var tempC = 28.0f
        var pmStatus = PowerManager.THERMAL_STATUS_NONE

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        fun evaluateState(): DeviceHealthState {
            val thermal = worseThermal(
                mapPowerManagerStatus(pmStatus),
                mapBatteryTemp(tempC)
            )
            val isOverheating = thermal == "serious" || thermal == "critical"
            val isLowBattery = level <= 20 && !charging
            val warning = when {
                isOverheating && isLowBattery -> "Device is hot (${tempC.toInt()}°C) and battery is low ($level%)"
                isOverheating -> "Device temperature is high (${tempC.toInt()}°C). Lowering resolution is recommended."
                isLowBattery -> "Battery low ($level%). Connect charger to avoid stream interruption."
                else -> null
            }
            return DeviceHealthState(
                batteryLevel = level,
                isCharging = charging,
                batteryTempCelsius = tempC,
                isOverheating = isOverheating,
                isLowBattery = isLowBattery,
                warningMessage = warning,
                thermalLevel = thermal
            )
        }

        fun readBattery(intent: Intent) {
            val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (rawLevel >= 0 && scale > 0) {
                level = (rawLevel.toFloat() / scale.toFloat() * 100).toInt()
            }
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            if (rawTemp != Int.MIN_VALUE) tempC = rawTemp / 10f
        }

        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    readBattery(intent)
                    trySend(evaluateState())
                }
            }
        }

        // Sticky intent gives the current battery values immediately
        val sticky = context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) readBattery(sticky)

        // System thermal status (API 29+); the listener now emits a new state itself
        var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            pmStatus = powerManager.currentThermalStatus
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                pmStatus = status
                trySend(evaluateState())
            }
            try {
                powerManager.addThermalStatusListener(listener)
                thermalListener = listener
            } catch (_: Exception) {
            }
        }

        trySend(evaluateState())

        awaitClose {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
            val l = thermalListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && l != null) {
                try {
                    powerManager.removeThermalStatusListener(l)
                } catch (_: Exception) {
                }
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceHealthState()
    )

    companion object {
        fun mapPowerManagerStatus(status: Int): String = when (status) {
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT -> "nominal"
            PowerManager.THERMAL_STATUS_MODERATE -> "fair"
            PowerManager.THERMAL_STATUS_SEVERE -> "serious"
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "critical"
            else -> "nominal"
        }

        fun mapBatteryTemp(tempC: Float): String = when {
            tempC >= 44f -> "critical"
            tempC >= 40f -> "serious"
            tempC >= 35f -> "fair"
            else -> "nominal"
        }

        private fun rank(level: String): Int = when (level) {
            "critical" -> 3
            "serious" -> 2
            "fair" -> 1
            else -> 0
        }

        fun worseThermal(a: String, b: String): String = if (rank(a) >= rank(b)) a else b
    }
}
