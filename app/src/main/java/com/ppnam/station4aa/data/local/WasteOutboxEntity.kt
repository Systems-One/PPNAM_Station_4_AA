package com.ppnam.station4aa.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ppnam.station4aa.domain.model.WasteCollectionEvent

/**
 * The durable local outbox the contract requires before the first publish attempt: "Durably write
 * the complete immutable schema v3 event to a local handheld outbox before the first publish
 * attempt" / "Mark the queued event as broker-delivered only after the scanner receives PUBACK,
 * while retaining enough information for operational reconciliation" (`Station4_Wastage_MQTT_
 * Contract.md`, "Required handheld workflow" steps 9 and 11).
 *
 * Rows are never mutated after insert except [status]/[attemptCount]/[lastAttemptEpochMs] — the
 * event fields themselves are immutable per the contract, so a retry always republishes the exact
 * bytes originally queued.
 */
@Entity(tableName = "waste_outbox")
data class WasteOutboxEntity(
    @PrimaryKey val messageId: String,
    val deviceId: String,
    val operatorSessionId: String,
    val collectionId: String,
    val bagCode: String,
    val machineCode: String,
    val machineName: String,
    val machineOperatorUserId: String,
    val wasteTypeCode: String,
    val collectedBy: String,
    val collectedAtUtc: String,
    val status: String,
    val createdAtEpochMs: Long,
    val lastAttemptEpochMs: Long?,
    val attemptCount: Int,
) {
    object Status {
        /** Durably written, not yet PUBACKed. */
        const val PENDING = "PENDING"
        /** Scanner received PUBACK for this publish. Per the contract this confirms only broker
         * receipt, never Station 4 business acceptance — see MqttConnectionManager's class doc. */
        const val DELIVERED = "DELIVERED"
    }
}

fun WasteOutboxEntity.toEvent(): WasteCollectionEvent = WasteCollectionEvent(
    messageId = messageId,
    deviceId = deviceId,
    operatorSessionId = operatorSessionId,
    collectionId = collectionId,
    bagCode = bagCode,
    machineCode = machineCode,
    machineName = machineName,
    machineOperatorUserId = machineOperatorUserId,
    wasteTypeCode = wasteTypeCode,
    collectedBy = collectedBy,
    collectedAtUtc = collectedAtUtc,
)

fun WasteCollectionEvent.toOutboxEntity(nowEpochMs: Long): WasteOutboxEntity = WasteOutboxEntity(
    messageId = messageId,
    deviceId = deviceId,
    operatorSessionId = operatorSessionId,
    collectionId = collectionId,
    bagCode = bagCode,
    machineCode = machineCode,
    machineName = machineName,
    machineOperatorUserId = machineOperatorUserId,
    wasteTypeCode = wasteTypeCode,
    collectedBy = collectedBy,
    collectedAtUtc = collectedAtUtc,
    status = WasteOutboxEntity.Status.PENDING,
    createdAtEpochMs = nowEpochMs,
    lastAttemptEpochMs = null,
    attemptCount = 0,
)
