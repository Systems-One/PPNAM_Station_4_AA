package com.mitas.ppnam.station4aa.domain.capture

/**
 * One Station 4 refusal of a handheld weigh, translated for the person holding the scanner.
 *
 * @property errorCode the station's stable machine-readable code, kept for logs and support.
 * @property message what the operator reads. Never the raw code — except for [isRecognised] false,
 *   where naming the unknown token beats saying nothing.
 * @property nextAction the contract's stable transition for this code, per §9.2's table.
 * @property keepBagCode whether the scanned bag code survives so the operator can fix the physical
 *   problem and tap again. True only for the transient scale conditions Station 4 deliberately
 *   does not store.
 * @property requiresLogin whether the operator's session is gone and they must sign in again.
 * @property isRecognised false when this app has no entry for the code, i.e. Station 4 is ahead
 *   of this build.
 */
data class CaptureRefusal(
    val errorCode: String?,
    val message: String,
    val nextAction: String,
    val keepBagCode: Boolean,
    val requiresLogin: Boolean,
    val isRecognised: Boolean,
)

/**
 * The refusal table of `Station4_Wastage_MQTT_Contract.md` §9.2, plus the shared envelope refusals
 * §15 notes the same validator can answer first. Pure and dependency-free so the whole table is
 * directly testable — the same shape as `evaluateOutcome` in the collection result channel.
 *
 * The station's `errorCode`, not its `nextAction`, is the key: the contract fixes the pairing, and
 * this app derives real behaviour ([CaptureRefusal.keepBagCode], [CaptureRefusal.requiresLogin])
 * from it, so a station-side regression in `nextAction` must not be able to steer the handheld.
 * The station's `nextAction` is honoured only for a code this build has never heard of. The
 * free-text `reason` is deliberately never parsed and never shown: the contract calls it
 * unstructured, and these messages tell the operator what to physically do next, which a server
 * sentence written for a WPF screen would not.
 */
object CaptureRefusals {

    private data class Entry(
        val message: String,
        val nextAction: String,
        val keepBagCode: Boolean = false,
        val requiresLogin: Boolean = false,
    )

    private val TABLE: Map<String, Entry> = mapOf(
        // --- §9.2's own workflow refusals -------------------------------------------------
        // Neither of these should be reachable from this app — it publishes retain=false, and
        // MqttRequestChannel mints a fresh messageId per call — so either one means something is
        // wrong rather than something to tap through. Both are stored refusals, so they are
        // terminal for this scan like every non-scale code.
        "retained_message_not_allowed" to Entry(
            "Station 4 refused a retained request. Scan the bag again.",
            "resend_not_retained",
        ),
        "message_id_reused" to Entry(
            "That weigh request clashed with an earlier one. Scan the bag again.",
            "use_new_message_id",
        ),
        "invalid_payload" to Entry(
            "Station 4 could not read that bag code. Scan it again.",
            "correct_and_resubmit",
        ),
        "operator_session_invalid" to Entry(
            "Your session has ended. Sign in again.",
            "login",
            requiresLogin = true,
        ),
        "bag_code_unknown" to Entry(
            "This bag is not waiting to be weighed. Register the collection first.",
            "register_collection",
        ),
        "bag_already_captured" to Entry(
            "This bag has already been weighed. Start the next collection.",
            "start_next_collection",
        ),
        "collection_sync_conflict" to Entry(
            "This collection is in conflict and a manager must review it.",
            "contact_manager",
        ),
        "capture_not_permitted" to Entry(
            "You do not have permission to capture waste. Ask a manager.",
            "contact_manager",
        ),
        "scale_busy" to Entry(
            "The scale is in use at the station. Try again in a moment.",
            "retry",
            keepBagCode = true,
        ),
        "scale_no_load" to Entry(
            "The scale is empty. Put the bag on it and try again.",
            "place_bag_and_retry",
            keepBagCode = true,
        ),
        "scale_unavailable" to Entry(
            "The scale is not available at Station 4. Ask a manager.",
            "contact_manager",
        ),
        "capture_not_confirmed" to Entry(
            "The scale did not settle. Steady the bag and try again.",
            "retry",
            keepBagCode = true,
        ),

        // --- Shared envelope refusals (§15), all `correct_and_resubmit` -------------------
        // Nothing the operator can correct on the handheld, so each says who can help instead.
        "schema_version_unsupported" to Entry(
            "This app is out of date for Station 4. Ask a manager.",
            "correct_and_resubmit",
        ),
        "device_id_mismatch" to Entry(
            "This scanner's identity did not match. Ask a manager.",
            "correct_and_resubmit",
        ),
        "validation_failed" to Entry(
            "Station 4 rejected the weigh request. Scan the bag again.",
            "correct_and_resubmit",
        ),
        "timestamp_invalid" to Entry(
            "This scanner's clock is wrong. Ask a manager.",
            "correct_and_resubmit",
        ),
        "timestamp_in_future" to Entry(
            "This scanner's clock is ahead of Station 4. Ask a manager.",
            "correct_and_resubmit",
        ),
    )

    fun describe(errorCode: String?, nextAction: String?): CaptureRefusal {
        val entry = errorCode?.let { TABLE[it] }
        if (entry != null) {
            return CaptureRefusal(
                errorCode = errorCode,
                message = entry.message,
                nextAction = entry.nextAction,
                keepBagCode = entry.keepBagCode,
                requiresLogin = entry.requiresLogin,
                isRecognised = true,
            )
        }
        // Station 4 is ahead of this build, or refused without a code at all. Name what we were
        // told rather than showing an empty banner, and treat the bag as done — an unknown code is
        // not something to retry blindly against a scale.
        return CaptureRefusal(
            errorCode = errorCode,
            message = if (errorCode.isNullOrBlank()) {
                "Station 4 refused the weigh. Scan the bag again."
            } else {
                "Station 4 refused the weigh: $errorCode. Ask a manager."
            },
            nextAction = nextAction?.takeIf { it.isNotBlank() } ?: "contact_manager",
            keepBagCode = false,
            requiresLogin = false,
            isRecognised = false,
        )
    }
}
