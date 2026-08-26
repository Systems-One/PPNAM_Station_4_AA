package com.mitas.ppnam.station4aa.data.mqtt

import com.mitas.ppnam.station4aa.data.mqtt.dto.ResponseEnvelope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Request/response correlation for the mirrored Station 2-style login exchange — see
 * `MqttTopics`' class doc. Not used by the real waste-collection contract, which is fire-and-forget
 * (see `WasteCollectionPublisher`).
 *
 * A scoped-down version of Station 2 AA's `MqttRepositoryImpl.request()`: same envelope shape and
 * `PPNAM/station_4/{deviceId}/req|res/...` correlation-by-`messageId` idea, without that class's retry
 * budget, duplicate-suppression cache, or clock-skew tracking — those exist there to serve a much
 * larger multi-workflow message catalog than a single login exchange needs.
 */
class MqttRequestChannel(
    private val connectionManager: MqttConnectionManager,
) {
    companion object {
        private const val DEFAULT_TIMEOUT_MS = 15_000L
    }

    private val gson = WireJson.gson
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val subscribedDeviceIds = ConcurrentHashMap.newKeySet<String>()

    private suspend fun ensureSubscribed(deviceId: String) {
        if (!subscribedDeviceIds.add(deviceId)) return
        connectionManager.subscribe(MqttTopics.responseWildcard(deviceId)) { _, bytes ->
            handleIncoming(String(bytes, StandardCharsets.UTF_8))
        }
    }

    private fun handleIncoming(raw: String) {
        val envelope = try {
            gson.fromJson(raw, ResponseEnvelope::class.java)
        } catch (e: Exception) {
            return
        }
        val id = envelope.inResponseToMessageId.takeIf { it.isNotBlank() } ?: return
        pending.remove(id)?.complete(raw)
    }

    suspend fun <T : Any> request(
        deviceId: String,
        requestType: String,
        responseClass: Class<T>,
        payload: Any,
        operatorSessionId: String = "",
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): MqttOutcome<T> {
        ensureSubscribed(deviceId)

        val messageId = UUID.randomUUID().toString()
        val json = RequestEnvelope.build(
            gson = gson,
            payload = payload,
            messageId = messageId,
            deviceId = deviceId,
            operatorSessionId = operatorSessionId,
            timestampUtc = MqttSchema.formatTimestamp(Instant.now()),
        )

        val waiter = CompletableDeferred<String>()
        pending[messageId] = waiter
        try {
            val publishResult = connectionManager.publish(
                MqttTopics.request(deviceId, requestType),
                json.toByteArray(StandardCharsets.UTF_8),
            )
            if (publishResult.isFailure) return MqttOutcome.NoResponse(FailureKind.NotConnected)

            val raw = withTimeoutOrNull(timeoutMs) { waiter.await() }
                ?: return MqttOutcome.NoResponse(FailureKind.Timeout)
            return parseOutcome(raw, responseClass)
        } finally {
            pending.remove(messageId)
        }
    }

    private fun <T : Any> parseOutcome(raw: String, responseClass: Class<T>): MqttOutcome<T> = try {
        val envelope = gson.fromJson(raw, ResponseEnvelope::class.java)
        val body = gson.fromJson(raw, responseClass)
        if (body == null) {
            MqttOutcome.NoResponse(FailureKind.MalformedResponse)
        } else if (envelope.accepted) {
            MqttOutcome.Accepted(body)
        } else {
            MqttOutcome.Rejected(body, envelope.errorCode, envelope.displayMessage)
        }
    } catch (e: Exception) {
        MqttOutcome.NoResponse(FailureKind.MalformedResponse)
    }
}
