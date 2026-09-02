package com.mitas.ppnam.station4aa.data.catalogue

import com.mitas.ppnam.station4aa.data.local.CatalogueMetaEntity
import com.mitas.ppnam.station4aa.data.local.WasteCatalogueDao
import com.mitas.ppnam.station4aa.data.local.WasteCategoryEntity
import com.mitas.ppnam.station4aa.data.local.WasteTypeEntity
import com.mitas.ppnam.station4aa.domain.model.CatalogueSource
import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory stand-in for the Room DAO, mirroring FakeWasteOutboxDao's approach. */
private class FakeWasteCatalogueDao : WasteCatalogueDao {
    val categoryRows = MutableStateFlow<List<WasteCategoryEntity>>(emptyList())
    val typeRows = MutableStateFlow<List<WasteTypeEntity>>(emptyList())
    val metaRow = MutableStateFlow<CatalogueMetaEntity?>(null)

    override fun categories(): Flow<List<WasteCategoryEntity>> =
        categoryRows.map { rows -> rows.sortedBy { it.sortOrder } }

    override fun typesFor(categoryCode: String): Flow<List<WasteTypeEntity>> =
        typeRows.map { rows -> rows.filter { it.categoryCode == categoryCode }.sortedBy { it.sortOrder } }

    override fun meta(): Flow<CatalogueMetaEntity?> = metaRow

    override suspend fun wasteTypeCount(): Int = typeRows.value.size

    override suspend fun deleteAllCategories() { categoryRows.value = emptyList() }

    override suspend fun deleteAllTypes() { typeRows.value = emptyList() }

    override suspend fun insertCategories(rows: List<WasteCategoryEntity>) {
        categoryRows.value = categoryRows.value + rows
    }

    override suspend fun insertTypes(rows: List<WasteTypeEntity>) {
        typeRows.value = typeRows.value + rows
    }

    override suspend fun upsertMeta(row: CatalogueMetaEntity) { metaRow.value = row }
}

class WasteCatalogueRepositoryTest {

    private fun category(code: String, sortOrder: Int = 1) =
        WasteCategory(code = code, name = "Name $code", sortOrder = sortOrder)

    private fun type(code: String, categoryCode: String, sortOrder: Int = 1) =
        WasteType(code = code, name = "Name $code", categoryCode = categoryCode, sortOrder = sortOrder)

    @Test
    fun `seedIfEmpty populates the built-in catalogue on a fresh install`() = runTest {
        val dao = FakeWasteCatalogueDao()
        val repository = WasteCatalogueRepository(dao)

        assertTrue(repository.seedIfEmpty())

        assertEquals(18, dao.typeRows.value.size)
        assertEquals(1, dao.categoryRows.value.size)
        assertEquals(CatalogueSource.SEED.name, dao.metaRow.value?.source)
    }

    @Test
    fun `seedIfEmpty does nothing when a catalogue is already cached`() = runTest {
        val dao = FakeWasteCatalogueDao()
        val repository = WasteCatalogueRepository(dao)
        repository.replaceWith(
            categories = listOf(category("CAT-01")),
            types = listOf(type("WT-99", "CAT-01")),
            catalogueVersion = "v1",
            nowUtc = "2026-09-02T07:00:00.000Z",
        )

        assertFalse(repository.seedIfEmpty())

        assertEquals(listOf("WT-99"), dao.typeRows.value.map { it.code })
    }

    @Test
    fun `replaceWith is wholesale, not a merge`() = runTest {
        val dao = FakeWasteCatalogueDao()
        val repository = WasteCatalogueRepository(dao)
        repository.seedIfEmpty()

        repository.replaceWith(
            categories = listOf(category("CAT-01")),
            types = listOf(type("WT-01", "CAT-01")),
            catalogueVersion = "v2",
            nowUtc = "2026-09-02T07:00:00.000Z",
        )

        // The 18 seeded types are gone, not merged with the one that replaced them.
        assertEquals(listOf("WT-01"), dao.typeRows.value.map { it.code })
        assertEquals(listOf("CAT-01"), dao.categoryRows.value.map { it.code })
    }

    @Test
    fun `replaceWith records synced provenance and clears any prior failure`() = runTest {
        val dao = FakeWasteCatalogueDao()
        val repository = WasteCatalogueRepository(dao)
        repository.seedIfEmpty()
        repository.recordSyncFailure("2026-09-02T06:00:00.000Z")

        repository.replaceWith(
            categories = listOf(category("CAT-01")),
            types = listOf(type("WT-01", "CAT-01")),
            catalogueVersion = "v2",
            nowUtc = "2026-09-02T07:00:00.000Z",
        )

        val meta = repository.meta.first()
        assertNotNull(meta)
        assertEquals("v2", meta!!.catalogueVersion)
        assertEquals("2026-09-02T07:00:00.000Z", meta.syncedAtUtc)
        assertEquals(CatalogueSource.SYNCED, meta.source)
        assertEquals(null, meta.lastFailedAtUtc)
    }

    @Test
    fun `recordSyncFailure leaves the cached catalogue untouched`() = runTest {
        val dao = FakeWasteCatalogueDao()
        val repository = WasteCatalogueRepository(dao)
        repository.replaceWith(
            categories = listOf(category("CAT-01")),
            types = listOf(type("WT-01", "CAT-01")),
            catalogueVersion = "v2",
            nowUtc = "2026-09-02T07:00:00.000Z",
        )

        repository.recordSyncFailure("2026-09-02T08:00:00.000Z")

        assertEquals(listOf("WT-01"), dao.typeRows.value.map { it.code })
        val meta = repository.meta.first()!!
        assertEquals("v2", meta.catalogueVersion)
        assertEquals(CatalogueSource.SYNCED, meta.source)
        assertEquals("2026-09-02T08:00:00.000Z", meta.lastFailedAtUtc)
    }

    @Test
    fun `typesFor returns only the requested category, in sort order`() = runTest {
        val dao = FakeWasteCatalogueDao()
        val repository = WasteCatalogueRepository(dao)
        repository.replaceWith(
            categories = listOf(category("CAT-01"), category("CAT-02", sortOrder = 2)),
            types = listOf(
                type("WT-02", "CAT-01", sortOrder = 2),
                type("WT-01", "CAT-01", sortOrder = 1),
                type("WT-09", "CAT-02", sortOrder = 1),
            ),
            catalogueVersion = "v1",
            nowUtc = "2026-09-02T07:00:00.000Z",
        )

        assertEquals(listOf("WT-01", "WT-02"), repository.typesFor("CAT-01").first().map { it.code })
        assertEquals(listOf("WT-09"), repository.typesFor("CAT-02").first().map { it.code })
    }
}
