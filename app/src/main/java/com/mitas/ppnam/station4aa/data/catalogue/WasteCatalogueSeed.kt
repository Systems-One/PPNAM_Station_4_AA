package com.mitas.ppnam.station4aa.data.catalogue

import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import java.util.Locale

/**
 * The fallback catalogue a handheld uses until its first successful sync with Station 4.
 *
 * The codes and names are copied verbatim from Station 4's own schema seed
 * (`PPNAM.Station4.Core/Data/Station4SchemaSql.cs:234-252`). Getting one wrong is not a loud
 * failure: Station 4 accepts any active code, so a mismatched name here means the operator picks
 * one thing and the station records another. `WasteCatalogueSeedTest` guards this.
 *
 * Categories are unknown until the customer confirms them, so everything is seeded under one
 * provisional category. [PROVISIONAL_CATEGORY_CODE] is seed-only and means nothing to Station 4 —
 * the first successful sync replaces it wholesale along with everything else.
 */
object WasteCatalogueSeed {

    const val PROVISIONAL_CATEGORY_CODE = "CAT-00"
    const val PROVISIONAL_CATEGORY_NAME = "Uncategorised"

    val categories: List<WasteCategory> = listOf(
        WasteCategory(
            code = PROVISIONAL_CATEGORY_CODE,
            name = PROVISIONAL_CATEGORY_NAME,
            sortOrder = 1,
        )
    )

    private val TYPE_NAMES = listOf(
        "Bubble breaks",
        "Startup",
        "Technical",
        "Winding",
        "Sticking & folding",
        "Treat",
        "Microns",
        "Registration",
        "Trimmings",
        "Handles",
        "Gusset & layflat",
        "Color variation",
        "Wrong size",
        "Ghost prints",
        "Setting/product change",
        "Sample waste",
        "Sweepings",
        "Customer complaints",
    )

    val wasteTypes: List<WasteType> = TYPE_NAMES.mapIndexed { index, name ->
        val number = index + 1
        WasteType(
            code = String.format(Locale.ROOT, "WT-%02d", number),
            name = name,
            categoryCode = PROVISIONAL_CATEGORY_CODE,
            sortOrder = number,
        )
    }
}
