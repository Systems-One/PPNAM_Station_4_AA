package com.mitas.ppnam.station4aa.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionManager
import com.mitas.ppnam.station4aa.data.rfid.ScanEvent
import com.mitas.ppnam.station4aa.data.rfid.ScanEventBus
import com.mitas.ppnam.station4aa.data.settings.SettingsRepository
import com.mitas.ppnam.station4aa.domain.usecase.AuthUseCase
import com.mitas.ppnam.station4aa.domain.usecase.LoginMethod
import com.mitas.ppnam.station4aa.ui.components.ConnectionStatus
import com.mitas.ppnam.station4aa.ui.components.connectionStatusFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ported from Station 2 AA's LoginUiState/LoginViewModel — see
 * `com.mitas.ppnam.station4aa.data.mqtt.MqttTopics`' class doc. */
sealed class LoginUiState {
    object Idle : LoginUiState()
    object LoggingIn : LoginUiState()
    data class Error(val message: String) : LoginUiState()
    object LoggedIn : LoginUiState()
}

class LoginViewModel(
    private val authUseCase: AuthUseCase,
    private val scanEventBus: ScanEventBus,
    private val connectionManager: MqttConnectionManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _navigationEvent = Channel<String>(Channel.BUFFERED)
    val navigationEvent: Flow<String> = _navigationEvent.receiveAsFlow()

    val connectionStatus: StateFlow<ConnectionStatus> = connectionStatusFlow(
        connectionManager.connectionState,
        connectionManager.stationOnline,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionStatus.Offline)

    private var badgeScanJob: Job? = null

    init {
        viewModelScope.launch { connectionManager.connect(settingsRepository.current()) }
        startListeningForBadgeScans()
    }

    private fun startListeningForBadgeScans() {
        badgeScanJob?.cancel()
        badgeScanJob = viewModelScope.launch {
            scanEventBus.events.filterIsInstance<ScanEvent.RfidTag>().collect { event ->
                attemptLogin(LoginMethod.Badge(event.tagId))
            }
        }
    }

    fun submitCredentials(username: String, password: String) {
        attemptLogin(LoginMethod.Credentials(username, password))
    }

    private fun attemptLogin(method: LoginMethod) {
        // Blocks re-entry for the whole LoggingIn -> LoggedIn span, not just LoggingIn: a repeat
        // badge read (continuous-read RFID hardware commonly re-fires the same tag) arriving
        // after success but before Compose has navigated away must not start a second, concurrent
        // login that could overwrite the just-established session with a different operator.
        if (_uiState.value != LoginUiState.Idle && _uiState.value !is LoginUiState.Error) return
        viewModelScope.launch {
            _uiState.value = LoginUiState.LoggingIn
            authUseCase.login(method)
                .onSuccess {
                    _uiState.value = LoginUiState.LoggedIn
                    badgeScanJob?.cancel()
                    _navigationEvent.send("home")
                }
                .onFailure { e ->
                    _uiState.value = LoginUiState.Error(e.message ?: "Login failed")
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        badgeScanJob?.cancel()
    }
}
