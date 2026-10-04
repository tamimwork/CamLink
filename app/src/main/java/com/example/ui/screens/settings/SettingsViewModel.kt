package com.example.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsRepository
import com.example.data.model.CameraFacing
import com.example.data.model.StreamFps
import com.example.data.model.StreamResolution
import com.example.data.model.StreamSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val settings: StateFlow<StreamSettings> = repository.streamSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StreamSettings()
    )

    fun setDefaultCamera(cameraFacing: CameraFacing) {
        viewModelScope.launch {
            repository.updateDefaultCamera(cameraFacing)
        }
    }

    fun setDefaultResolution(resolution: StreamResolution) {
        viewModelScope.launch {
            repository.updateResolution(resolution)
        }
    }

    fun setDefaultFps(fps: StreamFps) {
        viewModelScope.launch {
            repository.updateFps(fps)
        }
    }

    fun setMirrorVideo(mirror: Boolean) {
        viewModelScope.launch {
            repository.updateMirrorVideo(mirror)
        }
    }

    fun setKeepScreenOn(keepOn: Boolean) {
        viewModelScope.launch {
            repository.updateKeepScreenOn(keepOn)
        }
    }

    fun setAutoReconnect(autoReconnect: Boolean) {
        viewModelScope.launch {
            repository.updateAutoReconnect(autoReconnect)
        }
    }

    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            repository.updateAppTheme(theme)
        }
    }
}
