package com.mitas.ppnam.station4aa.data.catalogue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seed exists so a handheld that has never synced is still usable. It must match Station 4's
 * own seed exactly — `PPNAM.Station4.Core/Data/Station4SchemaSql.cs:234-252` — because a code that
 * disagrees is not rejected by Station 4, it is silently recorded as a different waste type.
 */
class WasteCatalogueSeedTest {

    @Test
    fun `seeds all eighteen Station 4 waste types`() {
        assertEquals(18, WasteCatalogueSeed.wasteTypes.size)
    }

    @Test
    fun `codes and names match Station 4's own schema seed`() {
        val expected = listOf(
            "WT-01" to "Bubble breaks",
            "WT-02" to "Startup",
            "WT-03" to "Technical",
            "WT-04" to "Winding",
            "WT-05" to "Sticking & folding",
            "WT-06" to "Treat",
            "WT-07" to "Microns",
            "WT-08" to "Registration",
            "WT-09" to "Trimmings",
            "WT-10" to "Handles",
            "WT-11" to "Gusset & layflat",
            "WT-12" to "Color variation",
            "WT-13" to "Wrong size",
            "WT-14" to "Ghost prints",
            "WT-15" to "Setting/product change",
            "WT-16" to "Sample waste",
            "WT-17" to "Sweepings",
            "WT-18" to "Customer complaints",
        )
        assertEquals(expected, WasteCatalogueSeed.wasteTypes.map { it.code to it.name })
    }

    @Test
    fun `every seeded type belongs to the single provisional category`() {
        assertEquals(1, WasteCatalogueSeed.categories.size)
        assertEquals(
            WasteCatalogueSeed.PROVISIONAL_CATEGORY_CODE,
            WasteCatalogueSeed.categories.single().code,
        )
        assertTrue(
            WasteCatalogueSeed.wasteTypes.all {
                it.categoryCode == WasteCatalogueSeed.PROVISIONAL_CATEGORY_CODE
            }
        )
    }

    @Test
    fun `sort order is one-based and matches Station 4's ordering`() {
        assertEquals((1..18).toList(), WasteCatalogueSeed.wasteTypes.map { it.sortOrder })
    }
}
