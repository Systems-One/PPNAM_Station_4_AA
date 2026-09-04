package com.mitas.ppnam.station4aa.data.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.mitas.ppnam.station4aa.domain.model.AppSettings
import java.util.UUID
import java.util.concurrent.TimeUnit

class MqttClientFactory {

    fun build(
        settings: AppSettings,
        onConnected: () -> Unit = {},
        onDisconnected: () -> Unit = {}
    ): Mqtt5AsyncClient {
        val builder = MqttClient.builder()
            .useMqttVersion5()
            .serverHost(settings.mqttHost)
            .serverPort(settings.mqttPort)
            // Base standard §2 rule 6: the MQTT client id is a separate *transport* identity,
            // unique per connection — never the deviceId, since a stale connection reusing the
            // same client id would kick the live one off the broker.
            .identifier("ScannerApp_" + UUID.randomUUID().toString().take(8))
            .addConnectedListener { onConnected() }
            .addDisconnectedListener { onDisconnected() }

        if (settings.mqttUseWebSocket) {
            // No base path: the deployment's broker serves MQTT-over-WebSocket at the root
            // (`wss://mqtt.sysone.co.za:443/`), not under a `/mqtt` suffix. A wrong path here does
            // not fail loudly — the socket is simply refused and the client retries forever, which
            // reads on the handheld as an unexplained "Offline".
            builder.webSocketConfig().serverPath("").applyWebSocketConfig()
        }
        if (settings.mqttUseTls) {
            builder.sslWithDefaultConfig()
        }
        if (settings.mqttUsername.isNotBlank()) {
            builder.simpleAuth()
                .username(settings.mqttUsername)
                .password(settings.mqttPassword.toByteArray())
                .applySimpleAuth()
        }

        return builder
            .automaticReconnect()
            .initialDelay(1, TimeUnit.SECONDS)
            .maxDelay(30, TimeUnit.SECONDS)
            .applyAutomaticReconnect()
            .buildAsync()
    }
}
