package com.mitas.ppnam.station4aa.data.mqtt

import com.google.gson.Gson
import com.mitas.ppnam.station4aa.data.local.WasteOutboxDao
import com.mitas.ppnam.station4aa.data.local.toEvent
import com.mitas.ppnam.station4aa.data.local.toOutboxEntity
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage
import com.mitas.ppnam.station4aa.domain.model.WasteCollectionEvent
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Implements the handheld side of `C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`:
 * durably write before the first publish attempt, only clear the interactive transaction after
 * that durable write, and only treat an event as accepted once a correlated `waste_collection_result`
 * with `accepted: true` arrives — never on PUBACK alone, which confirms only broker receipt (see
 * [WasteCollectionResultChannel] and `MqttConnectionManager`'s class doc).
 */
class WasteCollectionPublisher(
    private val outboxDao: WasteOutboxDao,
    private val connectionManager: MqttConnectionManager,
    private val resultChannel: WasteCollectionResultChannel,
) {
    private val gson = Gson()

    /** Rows still awaiting a correlated result — surfaced so the operator can see unconfirmed work
     * exists, per the contract's reconciliation-visibility requirement. */
    val pendingCount: Flow<Int> = outboxDao.pendingCount()

    /** Terminal (accepted or rejected) results, as they're correlated. */
    val results: SharedFlow<WasteCollectionResultMessage> = resultChannel.results

    /**
     * Durably queues [event], then makes one publish attempt. Returns once the row is safely on
     * disk — callers can clear their interactive form the moment this returns, regardless of
     * whether the immediate publish attempt (best-effort) succeeded, per the contract: "clear the
     * interactive transaction only after the durable local write" (not after delivery, and
     * certainly not after acceptance, which is asynchronous and may not arrive for some time).
     */
    suspend fun submit(event: WasteCollectionEvent) {
        outboxDao.insert(event.toOutboxEntity(System.currentTimeMillis()))
        attemptPublish(event)
    }

    /**
     * Retries every durably-queued row still awaiting a result — call after a reconnect or a login
     * so anything queued while offline gets flushed. A row queued under a session Station 4 no
     * longer recognises is re-stamped to [currentSessionId] first (audit S4-04: replaying the old
     * session only ever produced "Session is malformed..."); the event payload shape is
     * unchanged. With no session nothing is sent — Station 4 would refuse it anyway.
     */
    suspend fun retryPending(currentSessionId: String) {
        if (currentSessionId.isBlank()) return
        outboxDao.getPending().forEach { row ->
            val toSend = if (row.operatorSessionId == currentSessionId) {
                row
            } else {
                outboxDao.restampSession(row.messageId, currentSessionId)
                row.copy(operatorSessionId = currentSessionId)
            }
            attemptPublish(toSend.toEvent())
        }
    }

    private suspend fun attemptPublish(event: WasteCollectionEvent) {
        resultChannel.ensureSubscribed(event.deviceId)
        // Contract 5.0.0: the collection is a request on this handheld's own subtree, and its
        // topic device segment MUST equal the payload's `deviceId` — Station 4 refuses a mismatch
        // without a reply. Deriving the topic from the very event being published is what makes
        // that true by construction, including for a row replayed by `retryPending`.
        val topic = MqttTopics.wasteCollectionRequest(event.deviceId)
        val payload = gson.toJson(event.toWireMessage()).toByteArray(StandardCharsets.UTF_8)
        connectionManager.publish(topic, payload)
        outboxDao.recordAttempt(event.messageId, System.currentTimeMillis())
        // No status write on publish success/failure: PUBACK is not a business outcome. The row
        // stays PENDING (and therefore retried) until WasteCollectionResultChannel applies a
        // correlated ACCEPTED/REJECTED result.
    }
}
