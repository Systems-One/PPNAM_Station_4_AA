package com.mitas.ppnam.station4aa.domain.feedback

import com.mitas.ppnam.station4aa.domain.usecase.WasteCaptureOutcome
import com.mitas.ppnam.station4aa.domain.usecase.WasteOperatorOutcome
import com.mitas.ppnam.station4aa.domain.wizard.ScanDispatchResult

/** The two suite scan sounds (UI_Design/sounds/README.md). */
enum class ScanSound { SUCCESS, FAIL }

/**
 * Suite rule S1: which sound a scan's outcome makes. A scan sounds once — success when it is
 * accepted, fail when it is refused, times out or is refused on the handheld — and never on the
 * raw scanner read, which the Chainway scanner service already beeps for. Non-scan actions
 * (category and type choices, review navigation, login, settings) never reach here. The
 * ViewModels emit what this decides and the screens play it through `ui.feedback.ScanFeedback`.
 */
object ScanSounds {

    /**
     * A bag, job or operator-card scan (or its typed stand-in) routed through the wizard. Bag and
     * job are decided on the handheld; an operator card sent for lookup sounds on Station 4's
     * answer instead ([forOperatorLookup]). A scan on a step that takes none is dropped silently,
     * as Station 5 does for screens that are not taking scans.
     */
    fun forDispatch(result: ScanDispatchResult): ScanSound? = when (result) {
        is ScanDispatchResult.Applied -> if (result.error == null) ScanSound.SUCCESS else ScanSound.FAIL
        is ScanDispatchResult.LookupOperator -> null
        ScanDispatchResult.Ignored -> null
    }

    /** Station 4's answer to a production-operator card lookup (§9.3). */
    fun forOperatorLookup(outcome: WasteOperatorOutcome): ScanSound = when (outcome) {
        is WasteOperatorOutcome.Resolved -> ScanSound.SUCCESS
        is WasteOperatorOutcome.Refused,
        is WasteOperatorOutcome.Invalid,
        is WasteOperatorOutcome.Failed -> ScanSound.FAIL
    }

    /** A Weigh request (§9.2): weight recorded, or not. */
    fun forWeigh(outcome: WasteCaptureOutcome): ScanSound = when (outcome) {
        is WasteCaptureOutcome.Weighed -> ScanSound.SUCCESS
        is WasteCaptureOutcome.Refused,
        is WasteCaptureOutcome.InvalidBagCode,
        is WasteCaptureOutcome.Failed -> ScanSound.FAIL
    }
}

/**
 * One sound per collection submitted from this screen, when Station 4's verdict arrives:
 * accepted → success, rejected → fail. Queuing it (offline, or awaiting the result) makes no
 * sound. Results this screen did not submit — the shared flow's replay to a new screen, or an
 * outbox replay of an older collection — stay silent, so re-entering the screen never repeats a
 * verdict. Not thread-safe: owned by the ViewModel on the main thread.
 */
class CollectionVerdictSounds {
    private val awaiting = HashSet<String>()

    fun submitted(collectionId: String) {
        awaiting += collectionId
    }

    fun onResult(collectionId: String, accepted: Boolean): ScanSound? {
        if (!awaiting.remove(collectionId)) return null
        return if (accepted) ScanSound.SUCCESS else ScanSound.FAIL
    }
}
