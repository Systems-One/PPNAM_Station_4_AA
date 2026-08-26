package com.mitas.ppnam.station4aa.domain.wizard

/** Result of routing one scanned barcode value into [WasteWizardController.handleScannedValue]. */
sealed class ScanDispatchResult {
    /** The active step accepted the scan attempt; [error] is null on success or an operator-facing
     * validation message on failure. Either way the step only advances on success. */
    data class Applied(val error: String?) : ScanDispatchResult()

    /** The active step (SELECT_WASTE_TYPE or REVIEW) doesn't accept scans — the value was dropped,
     * not queued for later. */
    object Ignored : ScanDispatchResult()
}
