package com.mitas.ppnam.station4aa.domain.wizard

import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType

/**
 * Local-only wizard state — never sent over MQTT itself. Only once every field is non-null does
 * [WasteWizardController] reach [WizardStep.REVIEW]; the completed draft is what
 * WasteGatheringViewModel reads to build the one real
 * [com.mitas.ppnam.station4aa.domain.model.WasteCollectionEvent].
 *
 * [category] never leaves the device: it narrows the [wasteType] list and appears on the review
 * screen, and Station 4 derives the category from the published `wasteTypeCode`.
 */
data class WasteTransactionDraft(
    val bagCode: String? = null,
    val jobNumber: String? = null,
    val operatorId: String? = null,
    val category: WasteCategory? = null,
    val wasteType: WasteType? = null,
)
