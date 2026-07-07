package com.klasmeier.internetgatewaypath.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.klasmeier.internetgatewaypath.data.InternetPath
import com.klasmeier.internetgatewaypath.data.PathCheckRepository
import com.klasmeier.internetgatewaypath.data.PathCheckResult
import com.klasmeier.internetgatewaypath.data.db.TransitionEntity
import com.klasmeier.internetgatewaypath.monitor.PathMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val loading: Boolean = false,
    val current: PathCheckResult? = null,
    val previous: PathCheckResult? = null,
    val transitions: List<TransitionEntity> = emptyList(),
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PathCheckRepository(application)
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val result = PathMonitor.runCheckAndNotify(getApplication())
                if (result == null) {
                    _uiState.value = _uiState.value.copy(loading = false)
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    previous = _uiState.value.current?.takeIf { result.path != InternetPath.CHECK_FAILED },
                    current = result,
                )
                loadHistory()
            } catch (exc: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = exc.message ?: "Check failed",
                )
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val transitions = repository.recentTransitions(limit = 10)
            _uiState.value = _uiState.value.copy(transitions = transitions)
        }
    }
}
