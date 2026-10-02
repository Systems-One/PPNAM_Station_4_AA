package com.mitas.ppnam.station4aa.ui.settings

import com.mitas.ppnam.station4aa.domain.model.CatalogueMeta
import com.mitas.ppnam.station4aa.domain.model.CatalogueSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

/**
 * A handheld quietly running the built-in seed against a real station must not look identical to a
 * correctly synced one — that is the whole point of this line. Timestamps are shown in local time
 * at minute precision (audit S4-07): the raw ISO string with microseconds wrapped to three lines.
 */
class CatalogueStatusTest {

    private val utc = ZoneOffset.UTC

    @Test
    fun `no cached catalogue at all reads as not loaded`() {
        assertEquals("Catalogue: not loaded", describeCatalogue(null, utc))
    }

    @Test
    fun `the built-in seed says so explicitly`() {
        val meta = CatalogueMeta(
            catalogueVersion = "",
            syncedAtUtc = null,
            source = CatalogueSource.SEED,
            lastFailedAtUtc = null,
        )
        assertEquals("Catalogue: built-in seed — never synced", describeCatalogue(meta, utc))
    }

    @Test
    fun `a synced catalogue shows its version and a readable local timestamp`() {
        val meta = CatalogueMeta(
            catalogueVersion = "v7",
            syncedAtUtc = "2026-09-02T07:00:00Z",
            source = CatalogueSource.SYNCED,
            lastFailedAtUtc = null,
        )
        assertEquals("Catalogue: v7 — synced 2 Sep 2026, 07:00", describeCatalogue(meta, utc))
    }

    @Test
    fun `microsecond timestamps from the station are parsed and shortened`() {
        val meta = CatalogueMeta(
            catalogueVersion = "b1b6841add35",
            syncedAtUtc = "2026-10-01T16:12:37.942434Z",
            source = CatalogueSource.SYNCED,
            lastFailedAtUtc = null,
        )
        assertEquals("Catalogue: b1b6841add35 — synced 1 Oct 2026, 16:12", describeCatalogue(meta, utc))
    }

    @Test
    fun `the zone is applied`() {
        val meta = CatalogueMeta(
            catalogueVersion = "v7",
            syncedAtUtc = "2026-09-02T07:00:00Z",
            source = CatalogueSource.SYNCED,
            lastFailedAtUtc = null,
        )
        assertEquals("Catalogue: v7 — synced 2 Sep 2026, 09:00", describeCatalogue(meta, ZoneOffset.ofHours(2)))
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
            "Catalogue: v7 — synced 2 Sep 2026, 07:00, last refresh failed 2 Sep 2026, 09:30",
            describeCatalogue(meta, utc),
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
            "Catalogue: built-in seed — never synced, last refresh failed 2 Sep 2026, 09:30",
            describeCatalogue(meta, utc),
        )
    }

    @Test
    fun `an unparseable timestamp is shown as-is rather than crashing`() {
        val meta = CatalogueMeta(
            catalogueVersion = "v7",
            syncedAtUtc = "not-a-date",
            source = CatalogueSource.SYNCED,
            lastFailedAtUtc = null,
        )
        assertEquals("Catalogue: v7 — synced not-a-date", describeCatalogue(meta, utc))
    }
}
