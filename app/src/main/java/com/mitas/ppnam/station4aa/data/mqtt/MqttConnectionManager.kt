package com.mitas.ppnam.station4aa.data.mqtt

import android.util.Log
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.mitas.ppnam.station4aa.domain.model.AppSettings
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
 *    `C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`, whose
 *    collection event is publish-only (see [publish] / [WasteCollectionPublisher] — no
 *    application-level ACK; its `waste_collection_result` reply arrives on the scanner's own
 *    `res/` tree, handled by [WasteCollectionResultChannel]);
 *  - the operator-login request/response exchange mirrored from Station 2 AA
 *    (`MqttRequestChannel`, `PPNAM/station_4/{deviceId}/req|res/...`), which does need
 *    subscriptions — hence [subscribe] existing at all despite the waste contract having no use
 *    for it.
 *
 * [subscribe] registrations are re-applied on every `onConnected` (first connect or a silent
 * automaticReconnect), since MQTT5 sessions here don't persist subscriptions across a disconnect
 * (no `sessionExpiryInterval` set). Re-subscribing to an already-active filter is harmless, so
 * this doesn't need to distinguish "first connect" from "reconnect" the way Station 2's actual
 * transport does.
 *
 * Presence follows the fleet-wide convention (`MQTT_TOPIC_STRUCTURE.md` §2): the connection
 * carries a Last Will of retained `offline` on the device's base node
 * `PPNAM/station_4/{deviceId}`, retained `online` is published there after every (re)connect, and
 * retained `offline` is published best-effort on graceful disconnect (a graceful disconnect never
 * fires the LWT).
 *
 * Presence QoS is **1** here. The fleet standard §5 fixes the presence QoS level per station —
 * "2 at Station 1; 1 at Stations 2 and 4" — and defers the detail to each station's own contract,
 * which for Station 4 says QoS 1 for its own presence and LWT
 * (`Station4_Wastage_MQTT_Contract.md` §"Transport"/§"Presence"). `MQTT_BASE_README.md` §3 states
 * a blanket QoS 2 modelled on Station 1; the fleet document and this station's contract agree on
 * 1, so 1 it is. What actually carries presence semantics is `retain`, not the QoS level: a late
 * subscriber sees the current state either way. Business traffic (waste collection and req/res)
 * is QoS 1 too (see [publish]).
 *
 * [stationOnline] tracks Station 4's own retained presence on `PPNAM/station_4` (fleet standard
 * §4's scanner subscription row; base standard §3 rule 5), so the UI can say "station offline"
 * distinctly from "broker disconnected". `null` means not yet known — no retained payload has
 * been delivered on this connection.
 *
 * [deviceId] is the derived, immutable scanner identity (base standard §2 — see
 * `data/identity/DeviceIdentity.kt`), injected once by `AppContainer` rather than read from
 * Settings, so a reconfiguration can never rename the presence node mid-life.
 */
class MqttConnectionManager(
    private val deviceId: String,
    private val clientFactory: MqttClientFactory = MqttClientFactory(),
) {
    companion object {
        private const val TAG = "MqttConnectionManager"
        private const val CONNECT_TIMEOUT_MS = 15_000L
        // Presence is raw text, not JSON — the base-node topic carries only these two payloads.
        private val STATUS_ONLINE = "online".toByteArray()
        private val STATUS_OFFLINE = "offline".toByteArray()
        // Fleet standard §5 + Station 4's own contract: presence and its Last Will are QoS 1 at
        // this station. Retain, not QoS, is what makes a late subscriber see the current state.
        private val PRESENCE_QOS = MqttQos.AT_LEAST_ONCE
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(MqttConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    /** Station 4's own presence, read from the retained payload on `PPNAM/station_4`. `null`
     * until one is delivered — and reset to `null` whenever the transport drops, since a
     * previously read value says nothing about the station once we can no longer hear it. */
    private val _stationOnline = MutableStateFlow<Boolean?>(null)
    val stationOnline: StateFlow<Boolean?> = _stationOnline.asStateFlow()

    private var client: Mqtt5AsyncClient? = null
    private val isTransportConnected = AtomicBoolean(false)

    /**
     * Guards the graceful-shutdown race in the presence self-heal below: [disconnect] publishes
     * retained `offline` while still connected, and the broker echoes it back before the
     * DISCONNECT completes — that echo must not resurrect `online`.
     */
    private val wantsConnection = AtomicBoolean(false)

    private data class Subscription(val topicFilter: String, val onMessage: (String, ByteArray) -> Unit)
    private val subscriptions = CopyOnWriteArrayList<Subscription>()

    init {
        // Self-heal (base standard §3 rule 4, ported from Station 1's MqttManager): a restart
        // faster than the broker's dead-connection detection lets the *previous* connection's
        // Last Will — retained `offline` — land AFTER the new connection's retained `online`,
        // sticking presence at `offline` while actually connected. A standing subscription to our
        // own presence node spots that and republishes `online`. No loop is possible: only an
        // `offline` payload triggers it, and only while actually connected.
        subscriptions.add(Subscription(MqttTopics.devicePresence(deviceId)) { _, payload ->
            val status = String(payload, Charsets.UTF_8).trim().lowercase()
            if (status == "offline" && isTransportConnected.get() && wantsConnection.get()) {
                Log.w(TAG, "Own presence read offline while connected; republishing online")
                scope.launch { client?.let { publishPresence(it, STATUS_ONLINE) } }
            }
        })

        // Station presence (fleet standard §4: the scanner subscribes to `PPNAM/station_{x}`).
        // The payload is retained, so the current value arrives immediately on every (re)subscribe
        // rather than only when the station next changes state.
        subscriptions.add(Subscription(MqttTopics.STATION_PRESENCE) { _, payload ->
            when (String(payload, Charsets.UTF_8).trim().lowercase()) {
                "online" -> _stationOnline.value = true
                "offline" -> _stationOnline.value = false
                // Anything else is not this topic's contract; leave the last known state rather
                // than guessing the station is down off a payload we don't understand.
                else -> Log.w(TAG, "Ignoring unrecognised station presence payload")
            }
        })
    }

    private fun buildClient(settings: AppSettings): Mqtt5AsyncClient {
        lateinit var built: Mqtt5AsyncClient
        built = clientFactory.build(
            settings = settings,
            onConnected = {
                isTransportConnected.set(true)
                if (client === built) {
                    scope.launch {
                        resubscribeAll(built)
                        publishPresence(built, STATUS_ONLINE)
                        _connectionState.value = MqttConnectionState.CONNECTED
                    }
                }
            },
            onDisconnected = {
                if (client === built) {
                    isTransportConnected.set(false)
                    _stationOnline.value = null
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
            wantsConnection.set(true)
            try {
                withTimeout(CONNECT_TIMEOUT_MS) { connectWithWill(target) }
                publishPresence(target, STATUS_ONLINE)
                _connectionState.value = MqttConnectionState.CONNECTED
            } catch (e: Exception) {
                _connectionState.value = MqttConnectionState.DISCONNECTED
            }
        }
    }

    fun disconnect() {
        // Stops the presence self-heal from resurrecting `online` off our own echoed `offline`.
        wantsConnection.set(false)
        // A graceful disconnect never fires the LWT, so retained `offline` is published by hand.
        publishOfflineBestEffort(client)
        client?.disconnect()
        client = null
        isTransportConnected.set(false)
        _stationOnline.value = null
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
            withTimeout(CONNECT_TIMEOUT_MS) { connectWithWill(candidate) }
            val old = client
            client = candidate
            wantsConnection.set(true)
            isTransportConnected.set(true)
            resubscribeAll(candidate)
            publishPresence(candidate, STATUS_ONLINE)
            _connectionState.value = MqttConnectionState.CONNECTED
            publishOfflineBestEffort(old)
            try { old?.disconnect() } catch (_: Exception) { }
            Result.success(Unit)
        } catch (e: Exception) {
            try { candidate.disconnect() } catch (_: Exception) { }
            Result.failure(e)
        }
    }

    private suspend fun connectWithWill(target: Mqtt5AsyncClient) {
        target.connectWith()
            .willPublish()
                .topic(MqttTopics.devicePresence(deviceId))
                .payload(STATUS_OFFLINE)
                .qos(PRESENCE_QOS)
                .retain(true)
                .applyWillPublish()
            .send()
            .await()
    }

    // Best-effort: presence must never make connect/reconnect fail — a missed `online` heals on
    // the next reconnect, and the retained LWT still covers ungraceful drops.
    private suspend fun publishPresence(target: Mqtt5AsyncClient, payload: ByteArray) {
        try {
            target.publishWith()
                .topic(MqttTopics.devicePresence(deviceId))
                .payload(payload)
                .qos(PRESENCE_QOS)
                .retain(true)
                .send()
                .await()
        } catch (_: Exception) { }
    }

    // Bounded blocking wait since disconnect() isn't suspend, mirroring Station 2 AA.
    private fun publishOfflineBestEffort(target: Mqtt5AsyncClient?) {
        if (target == null) return
        try {
            target.publishWith()
                .topic(MqttTopics.devicePresence(deviceId))
                .payload(STATUS_OFFLINE)
                .qos(PRESENCE_QOS)
                .retain(true)
                .send()
                .get(2, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) { }
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
