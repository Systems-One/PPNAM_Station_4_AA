package com.mitas.ppnam.station4aa.domain.collection

import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage

/** Whether [result] echoes the *current* session. This no longer decides whether a result is
 * shown (a result for a bag this device published is always shown); it only gates the one side
 * effect that must not leak across sessions: a stale `operator_session_invalid` rejection must
 * never sign out the operator who has since logged in. */
fun isResultForSession(result: WasteCollectionResultMessage, currentSessionId: String): Boolean =
    currentSessionId.isNotBlank() && result.operatorSessionId == currentSessionId

/**
 * Decides whether a result delivered by `WasteCollectionResultChannel.results` should be shown.
 * The channel only emits results correlated to an outbox row this device published, so session
 * mismatch is never a reason to drop one; the only suppression is a replay (the shared flow
 * replays its last value to every new collector) of a result already shown. Not thread-safe:
 * owned by one collector coroutine.
 */
class ShownResultTracker {
    private val shown = HashSet<String>()

    /** True the first time [result]'s collection is seen, false for any replay. */
    fun shouldShow(result: WasteCollectionResultMessage): Boolean = shown.add(result.collectionId)
}
