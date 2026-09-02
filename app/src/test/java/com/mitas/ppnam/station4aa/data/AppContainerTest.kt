package com.mitas.ppnam.station4aa.data

import com.mitas.ppnam.station4aa.data.catalogue.WasteCatalogueRepository
import com.mitas.ppnam.station4aa.data.local.CatalogueMetaEntity
import com.mitas.ppnam.station4aa.data.local.WasteCatalogueDao
import com.mitas.ppnam.station4aa.data.local.WasteCategoryEntity
import com.mitas.ppnam.station4aa.data.local.WasteTypeEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors SyncWasteCatalogueUseCaseTest's FakeWasteCatalogueDao: a DAO that can be told to throw
 *  mid-write, standing in for a Room/disk failure (e.g. a full-disk SQLiteException) on the very
 *  first write a fresh install ever makes. */
private class FakeWasteCatalogueDao : WasteCatalogueDao {
    val categoryRows = MutableStateFlow<List<WasteCategoryEntity>>(emptyList())
    val typeRows = MutableStateFlow<List<WasteTypeEntity>>(emptyList())
    val metaRow = MutableStateFlow<CatalogueMetaEntity?>(null)

    var insertTypesFailure: Throwable? = null

    override fun categories(): Flow<List<WasteCategoryEntity>> = categoryRows.map { it }
    override fun typesFor(categoryCode: String): Flow<List<WasteTypeEntity>> =
        typeRows.map { rows -> rows.filter { it.categoryCode == categoryCode } }
    override fun meta(): Flow<CatalogueMetaEntity?> = metaRow
    override suspend fun wasteTypeCount(): Int = typeRows.value.size
    override suspend fun deleteAllCategories() { categoryRows.value = emptyList() }
    override suspend fun deleteAllTypes() { typeRows.value = emptyList() }
    override suspend fun insertCategories(rows: List<WasteCategoryEntity>) {
        categoryRows.value = categoryRows.value + rows
    }
    override suspend fun insertTypes(rows: List<WasteTypeEntity>) {
        insertTypesFailure?.let { throw it }
        typeRows.value = typeRows.value + rows
    }
    override suspend fun upsertMeta(row: CatalogueMetaEntity) { metaRow.value = row }

    override suspend fun replaceAll(
        categories: List<WasteCategoryEntity>,
        types: List<WasteTypeEntity>,
        meta: CatalogueMetaEntity,
    ) {
        deleteAllTypes()
        deleteAllCategories()
        insertCategories(categories)
        insertTypes(types)
        upsertMeta(meta)
    }
}

/** Covers the app-launch seeding guard (final whole-branch review, Important 1): a seed failure
 *  must degrade to an empty catalogue, never crash the caller. */
class AppContainerTest {

    @Test
    fun `a seed failure is swallowed rather than propagating`() = runTest {
        val dao = FakeWasteCatalogueDao()
        dao.insertTypesFailure = IllegalStateException("disk full")
        val repository = WasteCatalogueRepository(dao)

        // Must not throw.
        seedCatalogueSafely(repository)

        // The failed write left the catalogue empty, which is exactly the state the wizard's
        // CatalogueStep already renders as an explicit "Refresh the catalogue in Settings" message.
        assertEquals(0, dao.typeRows.value.size)
    }

    @Test
    fun `a successful seed still populates the catalogue`() = runTest {
        val dao = FakeWasteCatalogueDao()
        val repository = WasteCatalogueRepository(dao)

        seedCatalogueSafely(repository)

        assertTrue(dao.typeRows.value.isNotEmpty())
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown, not swallowed`() = runTest {
        val dao = FakeWasteCatalogueDao()
        dao.insertTypesFailure = CancellationException("cancelled")
        val repository = WasteCatalogueRepository(dao)

        seedCatalogueSafely(repository)
    }
}
