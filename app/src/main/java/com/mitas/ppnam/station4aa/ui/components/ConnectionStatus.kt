package com.mitas.ppnam.station4aa.ui.components

import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map

enum class ConnectionStatus { Offline, Reconnecting, Connected }

/**
 * Resolves what to tell the operator about connectivity.
 *
 * Station 2's version of this file also weighed station presence and clock skew, because that
 * contract has a retained presence topic and a request/response layer to measure drift from.
 * Station 4's contract (`DOCS/Station4_Wastage_MQTT_Contract.md`) has neither: the handheld only
 * ever publishes, so broker transport state is the entire picture — see MqttConnectionManager's
 * class doc.
 */
fun resolveConnectionStatus(connectionState: MqttConnectionState): ConnectionStatus = when (connectionState) {
    MqttConnectionState.CONNECTED    -> ConnectionStatus.Connected
    MqttConnectionState.RECONNECTING -> ConnectionStatus.Reconnecting
    MqttConnectionState.DISCONNECTED -> ConnectionStatus.Offline
}

/** How long a resolved status must persist before the UI shows it. See [connectionStatusFlow]. */
const val CONNECTION_STATUS_DEBOUNCE_MS = 1_500L

/**
 * The one place every screen derives its [ConnectionStatus] from. Debounced so a single-emission
 * flicker during a reconnect doesn't flash the top-bar pill for a fraction of a second.
 */
@OptIn(FlowPreview::class)
fun connectionStatusFlow(connectionState: Flow<MqttConnectionState>): Flow<ConnectionStatus> =
    connectionState.map(::resolveConnectionStatus).debounce(CONNECTION_STATUS_DEBOUNCE_MS)
