package com.mitas.ppnam.station4aa.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionManager
import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionState
import com.mitas.ppnam.station4aa.data.rfid.ScanEvent
import com.mitas.ppnam.station4aa.data.rfid.ScanEventBus
import com.mitas.ppnam.station4aa.data.session.OperatorSessionHolder
import com.mitas.ppnam.station4aa.data.settings.SettingsRepository
import com.mitas.ppnam.station4aa.domain.model.OperatorEntry
import com.mitas.ppnam.station4aa.domain.usecase.AuthUseCase
import com.mitas.ppnam.station4aa.domain.usecase.LoginMethod
import com.mitas.ppnam.station4aa.domain.usecase.OperatorDirectoryUseCase
import com.mitas.ppnam.station4aa.ui.components.ConnectionStatus
import com.mitas.ppnam.station4aa.ui.components.connectionStatusStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
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
    private val sessionHolder: OperatorSessionHolder,
    private val operatorDirectory: OperatorDirectoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _navigationEvent = Channel<String>(Channel.BUFFERED)
    val navigationEvent: Flow<String> = _navigationEvent.receiveAsFlow()

    val connectionStatus: StateFlow<ConnectionStatus> = connectionManager.connectionStatusStateFlow(viewModelScope)

    /** The username dropdown's rows (contract 5.3.0 operator directory). Seeded from the on-device
     * cache so it is populated before — or without — Station 4 answering, then replaced by each
     * accepted refresh. Display-only: a typed username that is not listed still logs in. */
    private val _operators = MutableStateFlow(operatorDirectory.cached())
    val operators: StateFlow<List<OperatorEntry>> = _operators.asStateFlow()

    private var badgeScanJob: Job? = null

    init {
        // A dropped session (inactivity, station refusal) explains itself on the login line.
        sessionHolder.consumeSignedOutReason()?.let { _uiState.value = LoginUiState.Error(it) }
        viewModelScope.launch { connectionManager.connect(settingsRepository.current()) }
        startListeningForBadgeScans()
        refreshOperatorsOnEachConnect()
    }

    /**
     * Station 1's rule: ask for the directory once per broker connect while the login screen is
     * showing — in practice about once per app start, plus each reconnect. Every CONNECTED
     * emission (a reconnect passes through RECONNECTING first, so StateFlow does emit it again)
     * triggers one request; a failure keeps the current list. This ViewModel is scoped to the
     * LOGIN destination and cleared on the way to HOME, which is what bounds it to "while on the
     * login screen"; the session check is belt-and-braces for a connect that lands in the gap
     * between a successful login and that navigation.
     */
    private fun refreshOperatorsOnEachConnect() {
        viewModelScope.launch {
            connectionManager.connectionState
                .filter { it == MqttConnectionState.CONNECTED }
                .collect { refreshOperators() }
        }
    }

    /**
     * Called by the screen each time it comes (back) into view. Settings' Test & Apply swaps the
     * broker client in place and leaves the state CONNECTED throughout, so no CONNECTED emission
     * follows it; without this, a handheld configured on the login screen would show an empty
     * dropdown until the next reconnect or app start. A no-op while disconnected: the connect that
     * follows triggers [refreshOperatorsOnEachConnect] instead.
     */
    fun refreshOperatorsNow() {
        if (connectionManager.connectionState.value != MqttConnectionState.CONNECTED) return
        viewModelScope.launch { refreshOperators() }
    }

    private suspend fun refreshOperators() {
        if (sessionHolder.session.value != null) return
        operatorDirectory.refresh()?.let { fresh -> _operators.value = fresh }
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
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Please fill in all fields")
            return
        }
        attemptLogin(LoginMethod.Credentials(username.trim(), password))
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
                    _uiState.value = LoginUiState.Error(loginErrorMessage(e))
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        badgeScanJob?.cancel()
    }
}
