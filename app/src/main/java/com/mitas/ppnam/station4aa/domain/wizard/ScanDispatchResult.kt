package com.mitas.ppnam.station4aa.domain.wizard

/** Result of applying one confirmed scan or typed value to the active step via
 * [WasteWizardController.handleScannedValue]. */
sealed class ScanDispatchResult {
    /** The active step accepted the scan attempt; [error] is null on success or an operator-facing
     * validation message on failure. Either way the step only advances on success. */
    data class Applied(val error: String?) : ScanDispatchResult()

    /** The operator step accepted [card] for a Station 4 name lookup (contract 5.4.0 §9.3). The
     * step only advances once the caller hands the resolved name to
     * [WasteWizardController.applyResolvedOperator]. */
    data class LookupOperator(val card: String) : ScanDispatchResult()

    /** The active step (SELECT_WASTE_TYPE or REVIEW) doesn't accept scans — the value was dropped,
     * not queued for later. */
    object Ignored : ScanDispatchResult()
}

/**
 * Result of one raw scanner read ([WasteWizardController.stageScannedValue] /
 * [WasteWizardController.stageScannedBadge]). A read never changes the wizard: the scanner has no
 * debounce, so a trigger held a moment too long delivers the same code twice and the second read
 * used to land on the next step. It is only held for the operator to confirm.
 */
sealed class ScanStageResult {
    /** The active step takes this read; [value] waits in the entry field for Confirm. */
    data class Staged(val value: String) : ScanStageResult()

    /** The active step takes scans but not this kind; [error] is the operator-facing reason. */
    data class Refused(val error: String) : ScanStageResult()

    /** The active step doesn't accept scans — the read was dropped. */
    object Ignored : ScanStageResult()
}
