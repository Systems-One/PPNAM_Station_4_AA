package com.mitas.ppnam.station4aa.domain.collection

import com.mitas.ppnam.station4aa.data.local.WasteOutboxEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** The banner shown above the wizard for the collection the operator last submitted. */
data class CollectionBanner(val message: String, val isError: Boolean)

/** The last submitted collection, who submitted it, and whether the operator dismissed its banner. */
data class TrackedCollection(
    val collectionId: String,
    val operatorSessionId: String,
    val dismissed: Boolean = false,
)

/**
 * App-scoped (one instance in `AppContainer`, never per ViewModel) so a Dismiss survives the
 * screen being left and re-entered, and so the banner is tied to the submitting sign-in rather
 * than to whichever ViewModel happens to be alive (audit S4-R03).
 */
class CollectionBannerTracker {
    private val _tracked = MutableStateFlow<TrackedCollection?>(null)
    val tracked: StateFlow<TrackedCollection?> = _tracked.asStateFlow()

    fun track(collectionId: String, operatorSessionId: String) {
        _tracked.value = TrackedCollection(collectionId, operatorSessionId)
    }

    fun dismiss() {
        _tracked.value = _tracked.value?.copy(dismissed = true)
    }
}

/**
 * Pure rule: PENDING reads "Queued", ACCEPTED the accepted line, REJECTED the operator-facing
 * rejection copy from the stored error code. Queued and accepted are shown only to the sign-in
 * that submitted them; a rejection is shown to whoever is signed in (the bag still needs
 * re-registering). A dismissed banner stays dismissed.
 */
fun collectionBannerFor(
    tracked: TrackedCollection?,
    row: WasteOutboxEntity?,
    currentSessionId: String,
): CollectionBanner? {
    if (tracked == null || tracked.dismissed || row == null) return null
    val sameSession = currentSessionId.isNotBlank() && tracked.operatorSessionId == currentSessionId
    return when (row.status) {
        WasteOutboxEntity.Status.PENDING ->
            if (sameSession) CollectionBanner("Queued ${row.collectionId} for delivery", isError = false) else null
        WasteOutboxEntity.Status.ACCEPTED ->
            if (sameSession) CollectionBanner("Collection ${row.collectionId} accepted by Station 4.", isError = false) else null
        WasteOutboxEntity.Status.REJECTED ->
            CollectionBanner(CollectionRejections.describe(row.bagCode, row.errorCode).message, isError = true)
        else -> null
    }
}

/** Banner flow driven by the Room outbox row, not by racing the result flow against the submit. */
@OptIn(ExperimentalCoroutinesApi::class)
fun collectionBannerFlow(
    tracker: CollectionBannerTracker,
    observeRow: (String) -> Flow<WasteOutboxEntity?>,
    sessionId: Flow<String>,
): Flow<CollectionBanner?> {
    val rows = tracker.tracked
        .flatMapLatest { t -> if (t == null) flowOf(null) else observeRow(t.collectionId) }
    return combine(tracker.tracked, rows, sessionId) { t, row, session -> collectionBannerFor(t, row, session) }
        .distinctUntilChanged()
}
