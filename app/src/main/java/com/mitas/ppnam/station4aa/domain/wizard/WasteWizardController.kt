package com.mitas.ppnam.station4aa.domain.wizard

import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import com.mitas.ppnam.station4aa.domain.validation.WasteCollectionValidator

/**
 * Pure step-transition logic for the Phase 1 wastage-bag wizard — no Android, Room or MQTT
 * dependencies, so it is fully unit-testable without fakes. `WasteGatheringViewModel` owns one
 * instance per screen and mirrors [step]/[draft] into StateFlows for the UI. Catalogue values
 * arrive as already-resolved [WasteCategory]/[WasteType] objects, which is what keeps this class
 * free of the repository.
 *
 * No step-back during capture: mid-flow, the only way out of a wrong value is submitting a
 * corrected one, and [cancel] is always available for a full reset. From [WizardStep.REVIEW],
 * [editField] jumps to a single step and returns there once that step is satisfied.
 */
class WasteWizardController {

    var step: WizardStep = WizardStep.SCAN_BAG
        private set
    var draft: WasteTransactionDraft = WasteTransactionDraft()
        private set

    /** Set by [editField]: the next satisfied step returns to REVIEW instead of advancing. */
    private var returnToReview = false

    /** Bag/job/operator barcode scans all funnel through here. On the operator step the scan is
     * not applied directly: it comes back as [ScanDispatchResult.LookupOperator] for Station 4 to
     * resolve to a name first (contract 5.4.0 §9.3). Returns
     * [ScanDispatchResult.Ignored] when the active step doesn't accept a scan — a stray scan is
     * dropped, never applied to a step the operator has already moved past, and never allowed to
     * rewrite a field on the review screen the operator is about to confirm. */
    fun handleScannedValue(value: String): ScanDispatchResult = when (step) {
        WizardStep.SCAN_BAG -> ScanDispatchResult.Applied(submitBagCode(value))
        WizardStep.SCAN_JOB -> ScanDispatchResult.Applied(submitJobNumber(value))
        WizardStep.SCAN_OPERATOR -> beginOperatorLookup(value)
        WizardStep.SELECT_CATEGORY,
        WizardStep.SELECT_WASTE_TYPE,
        WizardStep.REVIEW -> ScanDispatchResult.Ignored
    }

    /** An RFID badge read. On the operator step the production operator's login badge is their
     * card, so it goes to Station 4 for a name lookup exactly like a scanned card number. On the
     * bag and job steps it is refused with a hint rather than silently dropped (the draft is left
     * unchanged); elsewhere it is ignored like any stray scan.
     *
     * RFID reads are refused on barcode steps regardless of [com.mitas.ppnam.station4aa.UserTagPolicy];
     * that policy exists for screens that must tell badges from item tags. */
    fun handleScannedBadge(tagId: String): ScanDispatchResult = when (step) {
        WizardStep.SCAN_BAG,
        WizardStep.SCAN_JOB -> ScanDispatchResult.Applied(BADGE_NOT_BARCODE)
        WizardStep.SCAN_OPERATOR -> beginOperatorLookup(tagId)
        WizardStep.SELECT_CATEGORY,
        WizardStep.SELECT_WASTE_TYPE,
        WizardStep.REVIEW -> ScanDispatchResult.Ignored
    }

    private companion object {
        const val BADGE_NOT_BARCODE = "Scan a barcode, not a badge."
    }

    /** Manual-entry fallback for the bag step; a scan calls this too via [handleScannedValue].
     * Returns an error message, or null on success. */
    fun submitBagCode(raw: String): String? {
        val error = WasteCollectionValidator.validateBagCode(raw)
        if (error != null) return error
        val bagCode = raw.trim()
        val bagChanged = draft.bagCode != null && draft.bagCode != bagCode
        draft = draft.copy(bagCode = bagCode)
        // §9.3 resolved the operator's name for the old bag; a new bag needs a fresh lookup.
        if (bagChanged && draft.operatorId != null) {
            draft = draft.copy(operatorId = null, operatorName = null)
            if (returnToReview) {
                step = WizardStep.SCAN_OPERATOR
                return null
            }
        }
        advanceTo(WizardStep.SCAN_JOB)
        return null
    }

    /** Manual-entry fallback for the job-number step. */
    fun submitJobNumber(raw: String): String? {
        val error = WasteCollectionValidator.validateJobNumber(raw)
        if (error != null) return error
        draft = draft.copy(jobNumber = raw.trim())
        advanceTo(WizardStep.SCAN_OPERATOR)
        return null
    }

    /** The production-operator step's card scan, badge read or manual entry. Validates locally and
     * hands back the trimmed card for a Station 4 lookup. A valid new card drops any name resolved
     * for the previous one (§9.3: clear the name whenever the card changes); the new one is only
     * stored by [applyResolvedOperator]. */
    fun beginOperatorLookup(raw: String): ScanDispatchResult {
        val error = WasteCollectionValidator.validateOperatorId(raw)
        if (error != null) return ScanDispatchResult.Applied(error)
        draft = draft.copy(operatorId = null, operatorName = null)
        return ScanDispatchResult.LookupOperator(raw.trim())
    }

    /**
     * Station 4 accepted the card lookup. [operatorId] is the card string as scanned (what the
     * collection publishes); [operatorName] is shown on review. Returns false — and changes
     * nothing — when the wizard has left the operator step, so a late answer can never land on a
     * step the operator has moved past.
     */
    fun applyResolvedOperator(operatorId: String, operatorName: String): Boolean {
        if (step != WizardStep.SCAN_OPERATOR) return false
        draft = draft.copy(operatorId = operatorId, operatorName = operatorName)
        advanceTo(WizardStep.SELECT_CATEGORY)
        return true
    }

    /**
     * Drops the resolved production operator — the collector session changed, so the name was
     * looked up for someone else. A wizard past the operator step goes back to it; from REVIEW it
     * returns to review once a card is resolved again.
     */
    fun invalidateOperator() {
        if (draft.operatorId == null) return
        draft = draft.copy(operatorId = null, operatorName = null)
        when (step) {
            WizardStep.SELECT_CATEGORY,
            WizardStep.SELECT_WASTE_TYPE -> {
                returnToReview = false
                step = WizardStep.SCAN_OPERATOR
            }
            WizardStep.REVIEW -> {
                returnToReview = true
                step = WizardStep.SCAN_OPERATOR
            }
            else -> Unit
        }
    }

    /**
     * SELECT_CATEGORY's step-local Confirm action. Changing to a *different* category clears the
     * selected type and routes to SELECT_WASTE_TYPE even when editing from review: a type belongs
     * to exactly one category, so keeping the old one would leave a contradiction on the review
     * screen that the operator has no reason to notice.
     */
    fun confirmCategory(category: WasteCategory) {
        check(step == WizardStep.SELECT_CATEGORY) {
            "confirmCategory called outside SELECT_CATEGORY (was $step)"
        }
        val categoryChanged = draft.category?.code != category.code
        draft = draft.copy(category = category)
        if (categoryChanged) {
            draft = draft.copy(wasteType = null)
            returnToReview = false
            step = WizardStep.SELECT_WASTE_TYPE
        } else {
            advanceTo(WizardStep.SELECT_WASTE_TYPE)
        }
    }

    /** SELECT_WASTE_TYPE's step-local Confirm action. */
    fun confirmWasteType(type: WasteType) {
        check(step == WizardStep.SELECT_WASTE_TYPE) {
            "confirmWasteType called outside SELECT_WASTE_TYPE (was $step)"
        }
        draft = draft.copy(wasteType = type)
        advanceTo(WizardStep.REVIEW)
    }

    /**
     * Jumps from the review screen to one capture step to correct a single value, then returns to
     * review once that step is satisfied. Both checks are unreachable through the UI, which renders
     * edit affordances only on the review dialog's own rows.
     */
    fun editField(target: WizardStep) {
        check(step == WizardStep.REVIEW) { "editField called outside REVIEW (was $step)" }
        check(target != WizardStep.REVIEW) { "editField cannot target REVIEW" }
        returnToReview = true
        step = target
    }

    /** Back / scrim / "Back" on the review dialog: hide the review and land on the last capture
     * step with every value kept. Previously this was [cancel], which silently threw away the
     * whole five-step transaction (audit S4-03). */
    fun dismissReview() {
        check(step == WizardStep.REVIEW) { "dismissReview called outside REVIEW (was $step)" }
        returnToReview = false
        step = WizardStep.SELECT_WASTE_TYPE
    }

    /** True once any value has been captured — the screen asks before discarding such a draft. */
    val hasDraft: Boolean get() = draft != WasteTransactionDraft()

    /** Available on every step, including REVIEW. Discards the draft and returns to the first
     * step — there is no partial-edit recovery path for an abandoned transaction. */
    fun cancel() {
        step = WizardStep.SCAN_BAG
        draft = WasteTransactionDraft()
        returnToReview = false
    }

    private fun advanceTo(next: WizardStep) {
        step = if (returnToReview) WizardStep.REVIEW else next
        returnToReview = false
    }
}
