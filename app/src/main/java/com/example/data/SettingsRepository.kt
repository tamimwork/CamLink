package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CameraFacing
import com.example.data.model.StreamFps
import com.example.data.model.StreamResolution
import com.example.data.model.StreamSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "camlink_settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val DEFAULT_CAMERA = stringPreferencesKey("default_camera")
        val RESOLUTION = stringPreferencesKey("resolution")
        val FPS = intPreferencesKey("fps")
        val MIRROR_VIDEO = booleanPreferencesKey("mirror_video")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val APP_THEME = stringPreferencesKey("app_theme")
        val ALLOW_PC_CONTROL = booleanPreferencesKey("allow_pc_control")

        val LAST_IP = stringPreferencesKey("last_ip")
        val LAST_PORT = intPreferencesKey("last_port")
        val LAST_CODE = stringPreferencesKey("last_code")
    }

    val streamSettings: Flow<StreamSettings> = context.dataStore.data.map { prefs ->
        val camera = try {
            CameraFacing.valueOf(prefs[PreferencesKeys.DEFAULT_CAMERA] ?: CameraFacing.BACK.name)
        } catch (_: Exception) {
            CameraFacing.BACK
        }
        val resolution = try {
            StreamResolution.valueOf(prefs[PreferencesKeys.RESOLUTION] ?: StreamResolution.FHD_1080P.name)
        } catch (_: Exception) {
            StreamResolution.FHD_1080P
        }
        val fps = if (prefs[PreferencesKeys.FPS] == 60) StreamFps.FPS_60 else StreamFps.FPS_30
        val mirror = prefs[PreferencesKeys.MIRROR_VIDEO] ?: false
        val keepAwake = prefs[PreferencesKeys.KEEP_SCREEN_ON] ?: true
        val autoReconnect = prefs[PreferencesKeys.AUTO_RECONNECT] ?: true
        val theme = prefs[PreferencesKeys.APP_THEME] ?: "dark"
        val allowPcControl = prefs[PreferencesKeys.ALLOW_PC_CONTROL] ?: true

        StreamSettings(
            defaultCamera = camera,
            resolution = resolution,
            fps = fps,
            mirrorVideo = mirror,
            keepScreenOn = keepAwake,
            autoReconnect = autoReconnect,
            appTheme = theme,
            allowPcControl = allowPcControl
        )
    }

    val lastConnection: Flow<Triple<String, Int, String>> = context.dataStore.data.map { prefs ->
        Triple(
            prefs[PreferencesKeys.LAST_IP] ?: "",
            prefs[PreferencesKeys.LAST_PORT] ?: 8443,
            prefs[PreferencesKeys.LAST_CODE] ?: ""
        )
    }

    suspend fun updateDefaultCamera(cameraFacing: CameraFacing) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DEFAULT_CAMERA] = cameraFacing.name
        }
    }

    suspend fun updateResolution(resolution: StreamResolution) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.RESOLUTION] = resolution.name
        }
    }

    suspend fun updateFps(fps: StreamFps) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.FPS] = fps.fps
        }
    }

    suspend fun updateMirrorVideo(mirror: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.MIRROR_VIDEO] = mirror
        }
    }

    suspend fun updateKeepScreenOn(keepOn: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.KEEP_SCREEN_ON] = keepOn
        }
    }

    suspend fun updateAutoReconnect(autoReconnect: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.AUTO_RECONNECT] = autoReconnect
        }
    }

    suspend fun updateAppTheme(theme: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.APP_THEME] = theme
        }
    }

    suspend fun updateAllowPcControl(allow: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ALLOW_PC_CONTROL] = allow
        }
    }

    suspend fun saveLastConnection(ip: String, port: Int, code: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_IP] = ip
            prefs[PreferencesKeys.LAST_PORT] = port
            prefs[PreferencesKeys.LAST_CODE] = code
        }
    }
}
