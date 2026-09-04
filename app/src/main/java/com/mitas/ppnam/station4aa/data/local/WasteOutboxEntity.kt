package com.mitas.ppnam.station4aa.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mitas.ppnam.station4aa.domain.model.WasteCollectionEvent

/**
 * The durable local outbox the contract requires before the first publish attempt: "Durably write
 * the complete immutable schema v4 event to a local handheld outbox before the first publish
 * attempt" / a row only leaves [Status.PENDING] once a correlated `waste_collection_result`
 * message resolves it to [Status.ACCEPTED] or [Status.REJECTED], while retaining enough
 * information for operational reconciliation (`Station4_Wastage_MQTT_Contract.md`, "Required
 * handheld workflow" steps 9 and 11).
 *
 * Rows are never mutated after insert except [status]/[attemptCount]/[lastAttemptEpochMs]/
 * [errorCode]/[reason]/[nextAction] — the event fields themselves are immutable per the contract,
 * so a retry always republishes the exact bytes originally queued.
 */
@Entity(tableName = "waste_outbox")
data class WasteOutboxEntity(
    @PrimaryKey val messageId: String,
    val deviceId: String,
    val operatorSessionId: String,
    val collectionId: String,
    val bagCode: String,
    val jobNumber: String,
    val operatorId: String,
    val wasteTypeCode: String,
    val collectedBy: String,
    val collectedAtUtc: String,
    val status: String,
    val createdAtEpochMs: Long,
    val lastAttemptEpochMs: Long?,
    val attemptCount: Int,
    val errorCode: String?,
    val reason: String?,
    val nextAction: String?,
) {
    object Status {
        /** Durably written, awaiting a correlated `waste_collection_result` — retried on every
         * reconnect regardless of whether a prior publish attempt received PUBACK, per the
         * contract's "retry the exact queued event... whether or not it saw PUBACK" rule. */
        const val PENDING = "PENDING"
        /** Terminal: a correlated result with `accepted: true` arrived. Never retried again. */
        const val ACCEPTED = "ACCEPTED"
        /** Terminal: a correlated result with `accepted: false` arrived. Never retried — the
         * contract requires a brand-new transaction (new messageId/collectionId) instead. */
        const val REJECTED = "REJECTED"
    }
}

fun WasteOutboxEntity.toEvent(): WasteCollectionEvent = WasteCollectionEvent(
    messageId = messageId,
    deviceId = deviceId,
    operatorSessionId = operatorSessionId,
    collectionId = collectionId,
    bagCode = bagCode,
    jobNumber = jobNumber,
    operatorId = operatorId,
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
    jobNumber = jobNumber,
    operatorId = operatorId,
    wasteTypeCode = wasteTypeCode,
    collectedBy = collectedBy,
    collectedAtUtc = collectedAtUtc,
    status = WasteOutboxEntity.Status.PENDING,
    createdAtEpochMs = nowEpochMs,
    lastAttemptEpochMs = null,
    attemptCount = 0,
    errorCode = null,
    reason = null,
    nextAction = null,
)
