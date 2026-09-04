package com.mitas.ppnam.station4aa.ui.components

import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map

enum class ConnectionStatus { Offline, Reconnecting, StationOffline, Connected }

/**
 * Resolves what to tell the operator about connectivity.
 *
 * Two independent things can be wrong, and the fleet MQTT standard requires the operator to be
 * able to tell them apart (`MQTT_BASE_README.md` §3 rule 5, on top of the fleet standard's
 * scanner subscription to `PPNAM/station_{x}`):
 *
 *  - the **broker** transport is down — nothing this handheld publishes leaves the device;
 *  - the broker is fine but **Station 4 itself** is offline (its retained presence on
 *    `PPNAM/station_4` reads `offline`) — publishes are accepted by the broker, and collection
 *    events queue in the outbox, but nothing is processing them.
 *
 * Broker state wins when both are known, because it is the more fundamental failure and because
 * [stationOnline] is meaningless while we cannot hear the station at all. A `null` [stationOnline]
 * means "no retained presence delivered yet" and is deliberately treated as [Connected], not as
 * station-offline: a station that has never published presence must not make a working handheld
 * look broken.
 */
fun resolveConnectionStatus(
    connectionState: MqttConnectionState,
    stationOnline: Boolean? = null,
): ConnectionStatus = when (connectionState) {
    MqttConnectionState.CONNECTED    ->
        if (stationOnline == false) ConnectionStatus.StationOffline else ConnectionStatus.Connected
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
fun connectionStatusFlow(
    connectionState: Flow<MqttConnectionState>,
    stationOnline: Flow<Boolean?>,
): Flow<ConnectionStatus> =
    combine(connectionState, stationOnline, ::resolveConnectionStatus)
        .debounce(CONNECTION_STATUS_DEBOUNCE_MS)
