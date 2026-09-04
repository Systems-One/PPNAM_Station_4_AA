package com.mitas.ppnam.station4aa.ui.settings

import com.mitas.ppnam.station4aa.domain.model.CatalogueMeta
import com.mitas.ppnam.station4aa.domain.model.CatalogueSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A handheld quietly running the built-in seed against a real station must not look identical to a
 * correctly synced one — that is the whole point of this line.
 */
class CatalogueStatusTest {

    @Test
    fun `no cached catalogue at all reads as not loaded`() {
        assertEquals("Catalogue: not loaded", describeCatalogue(null))
    }

    @Test
    fun `the built-in seed says so explicitly`() {
        val meta = CatalogueMeta(
            catalogueVersion = "",
            syncedAtUtc = null,
            source = CatalogueSource.SEED,
            lastFailedAtUtc = null,
        )
        assertEquals("Catalogue: built-in seed — never synced", describeCatalogue(meta))
    }

    @Test
    fun `a synced catalogue shows its version and timestamp`() {
        val meta = CatalogueMeta(
            catalogueVersion = "v7",
            syncedAtUtc = "2026-09-02T07:00:00Z",
            source = CatalogueSource.SYNCED,
            lastFailedAtUtc = null,
        )
        assertEquals("Catalogue: v7 — synced 2026-09-02T07:00:00Z", describeCatalogue(meta))
    }

    @Test
    fun `a later failed refresh is appended without hiding the good sync`() {
        val meta = CatalogueMeta(
            catalogueVersion = "v7",
            syncedAtUtc = "2026-09-02T07:00:00Z",
            source = CatalogueSource.SYNCED,
            lastFailedAtUtc = "2026-09-02T09:30:00Z",
        )
        assertEquals(
            "Catalogue: v7 — synced 2026-09-02T07:00:00Z, last refresh failed 2026-09-02T09:30:00Z",
            describeCatalogue(meta),
        )
    }

    @Test
    fun `a seed whose refresh failed reports both facts`() {
        val meta = CatalogueMeta(
            catalogueVersion = "",
            syncedAtUtc = null,
            source = CatalogueSource.SEED,
            lastFailedAtUtc = "2026-09-02T09:30:00Z",
        )
        assertEquals(
            "Catalogue: built-in seed — never synced, last refresh failed 2026-09-02T09:30:00Z",
            describeCatalogue(meta),
        )
    }
}
