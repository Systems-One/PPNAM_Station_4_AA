package com.ppnam.station4aa.data.mqtt

import com.google.gson.Gson
import com.ppnam.station4aa.data.local.WasteOutboxDao
import com.ppnam.station4aa.data.local.toEvent
import com.ppnam.station4aa.data.local.toOutboxEntity
import com.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage
import com.ppnam.station4aa.data.settings.SettingsRepository
import com.ppnam.station4aa.domain.model.WasteCollectionEvent
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Implements the handheld side of `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`:
 * durably write before the first publish attempt, only clear the interactive transaction after
 * that durable write, and only treat an event as accepted once a correlated `waste_collection_result`
 * with `accepted: true` arrives — never on PUBACK alone, which confirms only broker receipt (see
 * [WasteCollectionResultChannel] and `MqttConnectionManager`'s class doc).
 */
class WasteCollectionPublisher(
    private val outboxDao: WasteOutboxDao,
    private val connectionManager: MqttConnectionManager,
    private val resultChannel: WasteCollectionResultChannel,
    private val settingsRepository: SettingsRepository,
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

    /** Retries every durably-queued row still awaiting a result, with its original, unchanged
     * payload — call after a reconnect so anything queued while offline gets flushed. The contract
     * requires this "whether or not it saw PUBACK", so a row's fate is decided only by an incoming
     * [WasteCollectionResultChannel] correlation, never by this method. */
    suspend fun retryPending() {
        outboxDao.getPending().forEach { attemptPublish(it.toEvent()) }
    }

    private suspend fun attemptPublish(event: WasteCollectionEvent) {
        resultChannel.ensureSubscribed(event.deviceId)
        val topic = settingsRepository.current().wasteCollectionTopic
        val payload = gson.toJson(event.toWireMessage()).toByteArray(StandardCharsets.UTF_8)
        connectionManager.publish(topic, payload)
        outboxDao.recordAttempt(event.messageId, System.currentTimeMillis())
        // No status write on publish success/failure: PUBACK is not a business outcome. The row
        // stays PENDING (and therefore retried) until WasteCollectionResultChannel applies a
        // correlated ACCEPTED/REJECTED result.
    }
}
