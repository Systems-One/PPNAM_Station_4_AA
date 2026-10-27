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
 *
 * [operatorId] is the production operator's card exactly as scanned (contract 5.4.0 §9.3) — it is
 * what the collection publishes. [operatorName] is the name Station 4 resolved that card to, shown
 * on the review screen only; it is set and cleared together with [operatorId], so a draft never
 * holds a card without its looked-up name.
 */
data class WasteTransactionDraft(
    val bagCode: String? = null,
    val jobNumber: String? = null,
    val operatorId: String? = null,
    val operatorName: String? = null,
    val category: WasteCategory? = null,
    val wasteType: WasteType? = null,
)
