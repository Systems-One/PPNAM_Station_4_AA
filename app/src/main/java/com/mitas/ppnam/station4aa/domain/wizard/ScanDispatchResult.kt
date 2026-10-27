package com.mitas.ppnam.station4aa.domain.wizard

/** Result of routing one scanned barcode value into [WasteWizardController.handleScannedValue]. */
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
