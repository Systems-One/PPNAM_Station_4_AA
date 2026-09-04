package com.mitas.ppnam.station4aa.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionManager
import com.mitas.ppnam.station4aa.data.session.OperatorSession
import com.mitas.ppnam.station4aa.data.session.OperatorSessionHolder
import com.mitas.ppnam.station4aa.domain.usecase.AuthUseCase
import com.mitas.ppnam.station4aa.ui.components.ConnectionStatus
import com.mitas.ppnam.station4aa.ui.components.connectionStatusFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The dashboard's own small slice of state: who is signed in, whether the broker and Station 4 are
 * reachable, and the way out. It deliberately owns nothing to do with either workflow — a tile is
 * a door, and the screen behind it keeps its own state.
 */
class HomeViewModel(
    private val connectionManager: MqttConnectionManager,
    private val sessionHolder: OperatorSessionHolder,
    private val authUseCase: AuthUseCase,
) : ViewModel() {

    val connectionStatus: StateFlow<ConnectionStatus> = connectionStatusFlow(
        connectionManager.connectionState,
        connectionManager.stationOnline,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionStatus.Offline)

    val session: StateFlow<OperatorSession?> = sessionHolder.session

    fun logout() {
        viewModelScope.launch { authUseCase.logout() }
    }
}
