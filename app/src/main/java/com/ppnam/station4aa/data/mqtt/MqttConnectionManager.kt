package com.ppnam.station4aa.data.mqtt

import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.ppnam.station4aa.domain.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

enum class MqttConnectionState { CONNECTED, RECONNECTING, DISCONNECTED }

/**
 * Transport connection management shared by two independent things this app talks to over MQTT:
 *
 *  - the waste-collection contract at
 *    `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`, which is publish-only (see
 *    [publish] / [WasteCollectionPublisher] — no subscriptions, no presence topic, no
 *    application-level ACK);
 *  - the operator-login request/response exchange mirrored from Station 2 AA
 *    (`MqttRequestChannel`, `PPNAM/{deviceId}/req|res/...`), which does need subscriptions —
 *    hence [subscribe] existing at all despite the waste contract having no use for it.
 *
 * [subscribe] registrations are re-applied on every `onConnected` (first connect or a silent
 * automaticReconnect), since MQTT5 sessions here don't persist subscriptions across a disconnect
 * (no `sessionExpiryInterval` set). Re-subscribing to an already-active filter is harmless, so
 * this doesn't need to distinguish "first connect" from "reconnect" the way Station 2's actual
 * transport does.
 */
class MqttConnectionManager(
    private val clientFactory: MqttClientFactory = MqttClientFactory(),
) {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(MqttConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    private var client: Mqtt5AsyncClient? = null
    private val isTransportConnected = AtomicBoolean(false)

    private data class Subscription(val topicFilter: String, val onMessage: (String, ByteArray) -> Unit)
    private val subscriptions = CopyOnWriteArrayList<Subscription>()

    private fun buildClient(settings: AppSettings): Mqtt5AsyncClient {
        lateinit var built: Mqtt5AsyncClient
        built = clientFactory.build(
            settings = settings,
            onConnected = {
                isTransportConnected.set(true)
                if (client === built) {
                    scope.launch {
                        resubscribeAll(built)
                        _connectionState.value = MqttConnectionState.CONNECTED
                    }
                }
            },
            onDisconnected = {
                if (client === built) {
                    isTransportConnected.set(false)
                    _connectionState.value = MqttConnectionState.RECONNECTING
                }
            },
        )
        return built
    }

    private suspend fun resubscribeAll(target: Mqtt5AsyncClient) {
        subscriptions.forEach { subscribeOne(target, it.topicFilter, it.onMessage) }
    }

    private suspend fun subscribeOne(target: Mqtt5AsyncClient, topicFilter: String, onMessage: (String, ByteArray) -> Unit) {
        try {
            target.subscribeWith()
                .topicFilter(topicFilter)
                .callback { publish -> onMessage(publish.topic.toString(), publish.payloadAsBytes) }
                .send()
                .await()
        } catch (_: Exception) {
            // Best-effort: a failed subscribe here means requests through it will simply time out
            // waiting for a response, which callers already handle.
        }
    }

    /** Registers a standing subscription, applied immediately if already connected and reapplied
     * on every future (re)connect. There is no unsubscribe — every current caller wants it for the
     * life of the process. */
    suspend fun subscribe(topicFilter: String, onMessage: (topic: String, payload: ByteArray) -> Unit) {
        subscriptions.add(Subscription(topicFilter, onMessage))
        client?.let { if (isTransportConnected.get()) subscribeOne(it, topicFilter, onMessage) }
    }

    suspend fun connect(settings: AppSettings) {
        if (isTransportConnected.get()) return
        _connectionState.value = MqttConnectionState.RECONNECTING
        withContext(Dispatchers.IO) {
            if (client == null) client = buildClient(settings)
            val target = client!!
            try {
                withTimeout(CONNECT_TIMEOUT_MS) { target.connectWith().send().await() }
                _connectionState.value = MqttConnectionState.CONNECTED
            } catch (e: Exception) {
                _connectionState.value = MqttConnectionState.DISCONNECTED
            }
        }
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        isTransportConnected.set(false)
        _connectionState.value = MqttConnectionState.DISCONNECTED
    }

    /**
     * Connects a candidate client with [settings] and only swaps it in on success — used by the
     * Settings screen's "Test & Apply" so a bad broker host/credentials never tears down a
     * working connection.
     */
    suspend fun reconnectWith(settings: AppSettings): Result<Unit> = withContext(Dispatchers.IO) {
        val candidate = buildClient(settings)
        try {
            withTimeout(CONNECT_TIMEOUT_MS) { candidate.connectWith().send().await() }
            val old = client
            client = candidate
            isTransportConnected.set(true)
            resubscribeAll(candidate)
            _connectionState.value = MqttConnectionState.CONNECTED
            try { old?.disconnect() } catch (_: Exception) { }
            Result.success(Unit)
        } catch (e: Exception) {
            try { candidate.disconnect() } catch (_: Exception) { }
            Result.failure(e)
        }
    }

    /**
     * Publishes [payload] to [topic] at the contract's required QoS 1 / retain false. Fails fast
     * (no queuing) when not connected — callers needing durability across offline periods must
     * queue themselves; see [WasteCollectionPublisher]'s outbox.
     */
    suspend fun publish(topic: String, payload: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val target = client
        if (target == null || !isTransportConnected.get()) {
            return@withContext Result.failure(IllegalStateException("Not connected to the MQTT broker"))
        }
        try {
            target.publishWith()
                .topic(topic)
                .payload(payload)
                .qos(MqttQos.AT_LEAST_ONCE)
                .retain(false)
                .send()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
