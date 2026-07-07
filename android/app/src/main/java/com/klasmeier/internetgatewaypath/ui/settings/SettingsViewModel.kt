package com.klasmeier.internetgatewaypath.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.klasmeier.internetgatewaypath.data.NotificationPrefs
import com.klasmeier.internetgatewaypath.data.PathCheckRepository
import com.klasmeier.internetgatewaypath.data.ReferenceIps
import com.klasmeier.internetgatewaypath.data.SettingsRepository
import com.klasmeier.internetgatewaypath.monitor.PathMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val recalibrating: Boolean = false,
    val clearing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val referenceIps: ReferenceIps? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val pathCheckRepository = PathCheckRepository(application, settingsRepository = settingsRepository)

    val notificationPrefs: StateFlow<NotificationPrefs> = settingsRepository.notificationPrefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationPrefs())

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val snapshot = settingsRepository.snapshot()
            _uiState.value = _uiState.value.copy(
                referenceIps = ReferenceIps(snapshot.homeIp, snapshot.obscuraIp),
            )
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateNotificationPrefs { it.copy(notificationsEnabled = enabled) }
        }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateNotificationPrefs { it.copy(quietHoursEnabled = enabled) }
        }
    }

    fun setQuietStart(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.updateNotificationPrefs { it.copy(quietStartMinutes = minutes) }
        }
    }

    fun setQuietEnd(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.updateNotificationPrefs { it.copy(quietEndMinutes = minutes) }
        }
    }

    fun recalibrate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(recalibrating = true, message = null, error = null)
            pathCheckRepository.recalibrateReferenceIps()
                .onSuccess { reference ->
                    _uiState.value = _uiState.value.copy(
                        recalibrating = false,
                        referenceIps = reference,
                        message = "Reference IPs updated",
                    )
                }
                .onFailure { exc ->
                    _uiState.value = _uiState.value.copy(
                        recalibrating = false,
                        error = exc.message ?: "Recalibrate failed",
                    )
                }
        }
    }

    fun clearSetup(onCleared: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(clearing = true, message = null, error = null)
            try {
                PathMonitor.stop(getApplication())
                settingsRepository.clearAll()
                onCleared()
            } catch (exc: Exception) {
                _uiState.value = _uiState.value.copy(
                    clearing = false,
                    error = exc.message ?: "Clear failed",
                )
            }
        }
    }
}
