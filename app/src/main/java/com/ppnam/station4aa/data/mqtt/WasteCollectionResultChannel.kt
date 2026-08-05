package com.ppnam.station4aa.data.mqtt

import com.ppnam.station4aa.data.local.WasteOutboxDao
import com.ppnam.station4aa.data.local.WasteOutboxEntity
import com.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** The application-level outcome of one `waste_collection_result`, resolved by [evaluateOutcome]
 * before [WasteCollectionResultChannel] applies it to the durable outbox row. */
sealed class ResultOutcome {
    object Accepted : ResultOutcome()
    data class Rejected(val errorCode: String?, val reason: String?, val nextAction: String?) : ResultOutcome()
    /** The result's echoed device/session/collection/bag identity didn't match the stored row —
     * per acceptance criterion 29, this MUST be verified, not just correlated by messageId alone.
     * Never applied to the outbox; the row stays PENDING and is retried like any unanswered event. */
    object IdentityMismatch : ResultOutcome()
}

/** Pure decision logic, dependency-free and directly testable — see `WasteWizardController` for
 * the same pattern elsewhere in this codebase. [result] is presumed already correlated to [stored]
 * by `inResponseToMessageId == stored.messageId`; this only decides the outcome and verifies the
 * echoed identity fields the contract requires checking (§3 acceptance criterion 29). */
fun evaluateOutcome(result: WasteCollectionResultMessage, stored: WasteOutboxEntity): ResultOutcome {
    val identityMatches = result.deviceId == stored.deviceId &&
        result.operatorSessionId == stored.operatorSessionId &&
        result.collectionId == stored.collectionId &&
        result.bagCode == stored.bagCode
    if (!identityMatches) return ResultOutcome.IdentityMismatch

    return if (result.accepted) {
        ResultOutcome.Accepted
    } else {
        ResultOutcome.Rejected(result.errorCode, result.reason, result.nextAction)
    }
}

/**
 * Subscribes to the exact, deterministic collection-result topic
 * (`PPNAM/station4/{deviceId}/res/waste_collection_result`, contract §3/§12) and correlates every
 * inbound result to its durable outbox row by `inResponseToMessageId == messageId`, applying the
 * terminal outcome ([WasteOutboxDao.markAccepted]/[WasteOutboxDao.markRejected]) before emitting it
 * on [results] for any UI layer that wants to react. An unknown `inResponseToMessageId` (row already
 * cleaned up, or a stray/foreign message) and an [ResultOutcome.IdentityMismatch] are both silently
 * dropped — the row is left exactly as it was, so it stays eligible for the normal retry path.
 */
class WasteCollectionResultChannel(
    private val outboxDao: WasteOutboxDao,
    private val connectionManager: MqttConnectionManager,
) {
    private val gson = WireJson.gson
    // Defense-in-depth: this is a best-effort background correlation path — a dropped result
    // (whether from a malformed payload or an unexpected outboxDao failure, e.g. closed DB/disk
    // full) is recovered by the normal retry mechanism, not a fatal condition, so an uncaught
    // exception here must never crash the process.
    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val subscribedDeviceIds = ConcurrentHashMap.newKeySet<String>()

    // replay = 1: a ViewModel that (re)starts collecting after the result already arrived (e.g.
    // the screen was briefly not composed) still sees the most recent terminal result instead of
    // missing it entirely.
    private val _results = MutableSharedFlow<WasteCollectionResultMessage>(replay = 1, extraBufferCapacity = 16)
    val results: SharedFlow<WasteCollectionResultMessage> = _results.asSharedFlow()

    /** Idempotent — safe to call before every publish attempt (see `WasteCollectionPublisher`).
     * Registers the subscription once per device for the process lifetime; `MqttConnectionManager`
     * itself re-applies it across reconnects. */
    suspend fun ensureSubscribed(deviceId: String) {
        if (!subscribedDeviceIds.add(deviceId)) return
        val topic = MqttTopics.wasteCollectionResult(deviceId)
        connectionManager.subscribe(topic) { _, bytes ->
            scope.launch { handleIncoming(String(bytes, StandardCharsets.UTF_8)) }
        }
    }

    internal suspend fun handleIncoming(raw: String) {
        // Gson.fromJson returns null (does not throw) on an empty/zero-length input or a JSON
        // `null` literal — the catch alone would not stop a null result from being dereferenced
        // below, so the null-parse case is folded into the same "drop it" path via `?: return`.
        val result = try {
            gson.fromJson(raw, WasteCollectionResultMessage::class.java)
        } catch (e: Exception) {
            null
        } ?: return
        val stored = outboxDao.findByMessageId(result.inResponseToMessageId) ?: return
        // Terminal rows (ACCEPTED/REJECTED) must never change status again, and a late/duplicate/
        // replayed result for one must not re-emit on `results` either — that would show the
        // operator a stale/wrong banner for a message that changed nothing. The DAO's own
        // "AND status = 'PENDING'" guard (WasteOutboxDao.markAccepted/markRejected) is the
        // race-free defense against a genuine concurrent update; this check just avoids the
        // wrong-banner side effect in the common, non-racing case.
        if (stored.status != WasteOutboxEntity.Status.PENDING) return

        when (val outcome = evaluateOutcome(result, stored)) {
            ResultOutcome.Accepted -> {
                outboxDao.markAccepted(stored.messageId)
                _results.emit(result)
            }
            is ResultOutcome.Rejected -> {
                outboxDao.markRejected(stored.messageId, outcome.errorCode, outcome.reason, outcome.nextAction)
                _results.emit(result)
            }
            ResultOutcome.IdentityMismatch -> Unit
        }
    }
}
