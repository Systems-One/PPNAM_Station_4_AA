package com.mitas.ppnam.station4aa.data.local

import com.mitas.ppnam.station4aa.domain.model.CatalogueMeta
import com.mitas.ppnam.station4aa.domain.model.CatalogueSource
import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import org.junit.Assert.assertEquals
import org.junit.Test

/** The entity<->domain mappers are pure, so they are tested without Room. */
class CatalogueEntitiesTest {

    @Test
    fun `category round-trips through its entity`() {
        val domain = WasteCategory(code = "CAT-01", name = "Process", sortOrder = 2)
        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `waste type round-trips through its entity`() {
        val domain = WasteType(
            code = "WT-05",
            name = "Sticking & folding",
            categoryCode = "CAT-01",
            sortOrder = 5,
        )
        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `synced meta round-trips through its entity`() {
        val domain = CatalogueMeta(
            catalogueVersion = "2026-09-02T07:00:00.000000Z",
            syncedAtUtc = "2026-09-02T07:00:01.000Z",
            source = CatalogueSource.SYNCED,
            lastFailedAtUtc = null,
        )
        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `seed meta with a recorded failure round-trips through its entity`() {
        val domain = CatalogueMeta(
            catalogueVersion = "",
            syncedAtUtc = null,
            source = CatalogueSource.SEED,
            lastFailedAtUtc = "2026-09-02T08:00:00.000Z",
        )
        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `meta always occupies the single reserved row`() {
        val entity = CatalogueMeta(
            catalogueVersion = "v1",
            syncedAtUtc = null,
            source = CatalogueSource.SEED,
            lastFailedAtUtc = null,
        ).toEntity()
        assertEquals(CatalogueMetaEntity.SINGLETON_ID, entity.id)
    }
}
