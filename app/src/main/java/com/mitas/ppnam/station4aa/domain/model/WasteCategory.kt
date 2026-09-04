package com.mitas.ppnam.station4aa.domain.model

/**
 * A waste category as served by Station 4. Local to this handheld's UI only — the category is a
 * navigation aid that narrows the waste-type list and appears on the review screen, and is
 * deliberately NOT part of the published collection payload (Station 4 derives it from
 * `wasteTypeCode`). See the Phase 1 design doc's decision 3.
 */
data class WasteCategory(
    val code: String,
    val name: String,
    val sortOrder: Int,
)
