package com.mitas.ppnam.station4aa.ui.components

import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The fleet MQTT standard requires "station offline" to be distinguishable from "broker
 * disconnected" (`MQTT_BASE_README.md` §3 rule 5, on the scanner subscription to
 * `PPNAM/station_4` mandated by `MQTT_TOPIC_STRUCTURE.md` §4).
 */
class ConnectionStatusTest {

    @Test
    fun `connected with the station online is plain Connected`() {
        assertEquals(
            ConnectionStatus.Connected,
            resolveConnectionStatus(MqttConnectionState.CONNECTED, stationOnline = true)
        )
    }

    @Test
    fun `connected with the station offline is reported distinctly`() {
        assertEquals(
            ConnectionStatus.StationOffline,
            resolveConnectionStatus(MqttConnectionState.CONNECTED, stationOnline = false)
        )
    }

    @Test
    fun `unknown station presence does not make a working handheld look broken`() {
        // No retained payload delivered yet — a station that has never published presence must
        // not be reported as down.
        assertEquals(
            ConnectionStatus.Connected,
            resolveConnectionStatus(MqttConnectionState.CONNECTED, stationOnline = null)
        )
    }

    @Test
    fun `broker state wins over station presence`() {
        assertEquals(
            ConnectionStatus.Offline,
            resolveConnectionStatus(MqttConnectionState.DISCONNECTED, stationOnline = true)
        )
        assertEquals(
            ConnectionStatus.Reconnecting,
            resolveConnectionStatus(MqttConnectionState.RECONNECTING, stationOnline = false)
        )
    }
}
