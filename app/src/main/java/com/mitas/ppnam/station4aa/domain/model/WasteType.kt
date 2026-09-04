package com.mitas.ppnam.station4aa.domain.model

/**
 * One active waste type as served by Station 4. [code] is the only part of this that reaches the
 * wire, as the collection payload's `wasteTypeCode`; [name] and [categoryCode] exist for display
 * and for narrowing the selection list.
 */
data class WasteType(
    val code: String,
    val name: String,
    val categoryCode: String,
    val sortOrder: Int,
)
