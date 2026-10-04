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

data class DeviceHealthState(
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 28.0f,
    val isOverheating: Boolean = false,
    val isLowBattery: Boolean = false,
    val warningMessage: String? = null
)

class DeviceMonitor(
    private val context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    val healthState: StateFlow<DeviceHealthState> = callbackFlow {
        var currentThermalOverheat = false

        fun evaluateState(
            level: Int,
            charging: Boolean,
            tempC: Float,
            thermalOverheat: Boolean
        ): DeviceHealthState {
            val isOverheating = thermalOverheat || tempC >= 42.0f
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
                warningMessage = warning
            )
        }

        // Battery Receiver
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val level = if (rawLevel >= 0 && scale > 0) {
                        (rawLevel.toFloat() / scale.toFloat() * 100).toInt()
                    } else 100

                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 280)
                    val tempC = rawTemp / 10f

                    trySend(evaluateState(level, isCharging, tempC, currentThermalOverheat))
                }
            }
        }

        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // Thermal Status Listener on API 29+
        var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                currentThermalOverheat = status >= PowerManager.THERMAL_STATUS_SEVERE
            }
            try {
                powerManager.addThermalStatusListener(thermalListener)
            } catch (_: Exception) {
                // ignore
            }
        }

        awaitClose {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
                // ignore
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && thermalListener != null) {
                try {
                    powerManager.removeThermalStatusListener(thermalListener)
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceHealthState()
    )
}
