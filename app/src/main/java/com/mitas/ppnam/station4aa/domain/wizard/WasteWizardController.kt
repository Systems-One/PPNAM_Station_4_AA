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

    /** Bag/job/operator barcode scans all funnel through here. Returns
     * [ScanDispatchResult.Ignored] when the active step doesn't accept a scan — a stray scan is
     * dropped, never applied to a step the operator has already moved past, and never allowed to
     * rewrite a field on the review screen the operator is about to confirm. */
    fun handleScannedValue(value: String): ScanDispatchResult = when (step) {
        WizardStep.SCAN_BAG -> ScanDispatchResult.Applied(submitBagCode(value))
        WizardStep.SCAN_JOB -> ScanDispatchResult.Applied(submitJobNumber(value))
        WizardStep.SCAN_OPERATOR -> ScanDispatchResult.Applied(submitOperatorId(value))
        WizardStep.SELECT_CATEGORY,
        WizardStep.SELECT_WASTE_TYPE,
        WizardStep.REVIEW -> ScanDispatchResult.Ignored
    }

    /** Manual-entry fallback for the bag step; a scan calls this too via [handleScannedValue].
     * Returns an error message, or null on success. */
    fun submitBagCode(raw: String): String? {
        val error = WasteCollectionValidator.validateBagCode(raw)
        if (error != null) return error
        draft = draft.copy(bagCode = raw.trim())
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

    /** Manual-entry fallback for the production-operator step. */
    fun submitOperatorId(raw: String): String? {
        val error = WasteCollectionValidator.validateOperatorId(raw)
        if (error != null) return error
        draft = draft.copy(operatorId = raw.trim())
        advanceTo(WizardStep.SELECT_CATEGORY)
        return null
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
