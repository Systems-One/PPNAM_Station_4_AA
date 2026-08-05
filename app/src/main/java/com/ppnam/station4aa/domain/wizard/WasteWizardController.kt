package com.ppnam.station4aa.domain.wizard

import com.ppnam.station4aa.domain.model.WasteTypeCatalog
import com.ppnam.station4aa.domain.validation.WasteCollectionValidator

/**
 * Pure step-transition logic for the scan-driven waste collection wizard — no Android or MQTT
 * dependencies, so it's fully unit-testable without fakes. `WasteGatheringViewModel` owns one
 * instance per screen and mirrors [step]/[draft] into StateFlows for the UI.
 *
 * No step-back: the only way out of a wrong value mid-step is submitting a corrected one; the
 * only way to abandon a transaction is [cancel], which is always available and always performs a
 * full reset — see `docs/superpowers/specs/2026-08-05-scan-driven-waste-wizard-design.md`.
 */
class WasteWizardController {
    var step: WizardStep = WizardStep.SCAN_MACHINE
        private set
    var draft: WasteTransactionDraft = WasteTransactionDraft()
        private set

    /** Machine/operator/bag barcode scans all funnel through here. Returns
     * [ScanDispatchResult.Ignored] when the active step doesn't accept a scan — a stray scan is
     * dropped, not applied to a step the operator has already moved past. */
    fun handleScannedValue(value: String): ScanDispatchResult = when (step) {
        WizardStep.SCAN_MACHINE -> ScanDispatchResult.Applied(submitMachineCode(value))
        WizardStep.SCAN_OPERATOR -> ScanDispatchResult.Applied(submitOperatorId(value))
        WizardStep.SCAN_BAG -> ScanDispatchResult.Applied(submitBagCode(value))
        WizardStep.SELECT_WASTE_TYPE, WizardStep.REVIEW -> ScanDispatchResult.Ignored
    }

    /** Manual-entry fallback for the machine-code step; a scan calls this too via
     * [handleScannedValue]. Returns an error message, or null and advances to SCAN_OPERATOR. */
    fun submitMachineCode(raw: String): String? {
        val error = WasteCollectionValidator.validateMachineCode(raw)
        if (error != null) return error
        draft = draft.copy(machineCode = raw.trim())
        step = WizardStep.SCAN_OPERATOR
        return null
    }

    /** Manual-entry fallback for the machine-operator step. */
    fun submitOperatorId(raw: String): String? {
        val error = WasteCollectionValidator.validateMachineOperatorUserId(raw)
        if (error != null) return error
        draft = draft.copy(machineOperatorUserId = raw.trim())
        step = WizardStep.SELECT_WASTE_TYPE
        return null
    }

    /** SELECT_WASTE_TYPE's step-local Confirm action — not scan-driven, so it has no
     * [ScanDispatchResult] wrapper. Throws if called outside that step, which the UI only ever
     * allows by construction (the Confirm button is only rendered during SELECT_WASTE_TYPE). */
    fun confirmWasteType(type: WasteTypeCatalog) {
        check(step == WizardStep.SELECT_WASTE_TYPE) {
            "confirmWasteType called outside SELECT_WASTE_TYPE (was $step)"
        }
        draft = draft.copy(wasteType = type)
        step = WizardStep.SCAN_BAG
    }

    /** Manual-entry fallback for the bag-code step. */
    fun submitBagCode(raw: String): String? {
        val error = WasteCollectionValidator.validateBagCode(raw)
        if (error != null) return error
        draft = draft.copy(bagCode = raw.trim())
        step = WizardStep.REVIEW
        return null
    }

    /** Available on every step, including REVIEW. Discards the draft and returns to the first
     * step — there is no partial-edit recovery path. */
    fun cancel() {
        step = WizardStep.SCAN_MACHINE
        draft = WasteTransactionDraft()
    }
}
