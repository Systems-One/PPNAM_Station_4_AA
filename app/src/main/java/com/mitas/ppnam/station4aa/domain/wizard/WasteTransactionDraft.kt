package com.mitas.ppnam.station4aa.domain.wizard

import com.mitas.ppnam.station4aa.domain.model.WasteTypeCatalog

/** Local-only wizard state — never sent over MQTT itself. Only once every field is non-null does
 * [WasteWizardController] reach [WizardStep.REVIEW]; the completed draft is what
 * WasteGatheringViewModel reads to build the one real [com.mitas.ppnam.station4aa.domain.model.WasteCollectionEvent]. */
data class WasteTransactionDraft(
    val machineCode: String? = null,
    val machineOperatorUserId: String? = null,
    val wasteType: WasteTypeCatalog? = null,
    val bagCode: String? = null,
)
