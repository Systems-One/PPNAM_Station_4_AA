package com.mitas.ppnam.station4aa.domain.usecase

import com.mitas.ppnam.station4aa.data.catalogue.WasteCatalogueRepository
import com.mitas.ppnam.station4aa.data.local.CatalogueMetaEntity
import com.mitas.ppnam.station4aa.data.local.WasteCatalogueDao
import com.mitas.ppnam.station4aa.data.local.WasteCategoryEntity
import com.mitas.ppnam.station4aa.data.local.WasteTypeEntity
import com.mitas.ppnam.station4aa.data.mqtt.FailureKind
import com.mitas.ppnam.station4aa.data.mqtt.MqttOutcome
import com.mitas.ppnam.station4aa.data.mqtt.RequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCatalogueResponse
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCategoryDto
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteTypeDto
import com.mitas.ppnam.station4aa.domain.model.CatalogueSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private class FakeWasteCatalogueDao : WasteCatalogueDao {
    val categoryRows = MutableStateFlow<List<WasteCategoryEntity>>(emptyList())
    val typeRows = MutableStateFlow<List<WasteTypeEntity>>(emptyList())
    val metaRow = MutableStateFlow<CatalogueMetaEntity?>(null)

    /** When set, [insertTypes] throws this instead of writing — simulates a Room/disk failure
     *  partway through [replaceAll]'s transaction (e.g. a low-storage SQLiteException). */
    var insertTypesFailure: Throwable? = null

    /** When set, [upsertMeta] throws this instead of writing — simulates the same DAO failing
     *  again while recording a sync failure. */
    var upsertMetaFailure: Throwable? = null

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
        insertTypesFailure?.let { throw it }
        typeRows.value = typeRows.value + rows
    }
    override suspend fun upsertMeta(row: CatalogueMetaEntity) {
        upsertMetaFailure?.let { throw it }
        metaRow.value = row
    }

    // Real Room wraps this in @Transaction, so a mid-write throw rolls back rather than
    // leaving a half-applied catalogue. Snapshot/restore reproduces that here so a failing
    // insertTypes leaves the previously cached rows untouched, same as production.
    override suspend fun replaceAll(
        categories: List<WasteCategoryEntity>,
        types: List<WasteTypeEntity>,
        meta: CatalogueMetaEntity,
    ) {
        val categoriesBefore = categoryRows.value
        val typesBefore = typeRows.value
        val metaBefore = metaRow.value
        try {
            deleteAllTypes()
            deleteAllCategories()
            insertCategories(categories)
            insertTypes(types)
            upsertMeta(meta)
        } catch (e: Throwable) {
            categoryRows.value = categoriesBefore
            typeRows.value = typesBefore
            metaRow.value = metaBefore
            throw e
        }
    }
}

private class FakeRequestChannel(
    private val outcome: MqttOutcome<WasteCatalogueResponse>,
) : RequestChannel {
    var lastRequestType: String? = null

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> request(
        deviceId: String,
        requestType: String,
        responseClass: Class<T>,
        payload: Any,
        operatorSessionId: String,
        timeoutMs: Long,
    ): MqttOutcome<T> {
        lastRequestType = requestType
        return outcome as MqttOutcome<T>
    }
}

class SyncWasteCatalogueUseCaseTest {

    private val fixedNow = Instant.parse("2026-09-02T07:00:00Z")

    private fun useCase(
        outcome: MqttOutcome<WasteCatalogueResponse>,
        dao: FakeWasteCatalogueDao,
    ): Pair<SyncWasteCatalogueUseCase, FakeRequestChannel> {
        val channel = FakeRequestChannel(outcome)
        val useCase = SyncWasteCatalogueUseCase(
            requestChannel = channel,
            repository = WasteCatalogueRepository(dao),
            deviceId = "scanner_a1b2c3d4e5f6",
            clock = { fixedNow },
        )
        return useCase to channel
    }

    private fun goodResponse() = WasteCatalogueResponse(
        accepted = true,
        catalogueVersion = "v7",
        categories = listOf(WasteCategoryDto(code = "CAT-01", name = "Process", sortOrder = 1)),
        wasteTypes = listOf(
            WasteTypeDto(code = "WT-01", name = "Bubble breaks", categoryCode = "CAT-01", sortOrder = 1),
        ),
    )

    @Test
    fun `an accepted catalogue replaces the cache wholesale`() = runTest {
        val dao = FakeWasteCatalogueDao()
        val (useCase, channel) = useCase(MqttOutcome.Accepted(goodResponse()), dao)
        WasteCatalogueRepository(dao).seedIfEmpty()

        val result = useCase.sync("session-1")

        assertEquals(CatalogueSyncResult.Replaced(categoryCount = 1, typeCount = 1), result)
        assertEquals(listOf("WT-01"), dao.typeRows.value.map { it.code })
        assertEquals("waste_catalogue_requested", channel.lastRequestType)
    }

    @Test
    fun `an accepted but empty catalogue is treated as a failure and never replaces the cache`() = runTest {
        val dao = FakeWasteCatalogueDao()
        val repository = WasteCatalogueRepository(dao)
        repository.seedIfEmpty()
        val (useCase, _) = useCase(
            MqttOutcome.Accepted(goodResponse().copy(wasteTypes = emptyList())),
            dao,
        )

        val result = useCase.sync("session-1")

        assertTrue(result is CatalogueSyncResult.Failed)
        assertEquals(18, dao.typeRows.value.size)
        assertEquals(CatalogueSource.SEED, repository.meta.first()!!.source)
    }

    @Test
    fun `an accepted catalogue with no categories is also treated as a failure`() = runTest {
        val dao = FakeWasteCatalogueDao()
        WasteCatalogueRepository(dao).seedIfEmpty()
        val (useCase, _) = useCase(
            MqttOutcome.Accepted(goodResponse().copy(categories = emptyList())),
            dao,
        )

        assertTrue(useCase.sync("session-1") is CatalogueSyncResult.Failed)
        assertEquals(18, dao.typeRows.value.size)
    }

    @Test
    fun `a rejected response leaves the cached catalogue in place and records the failure`() = runTest {
        val dao = FakeWasteCatalogueDao()
        val repository = WasteCatalogueRepository(dao)
        repository.seedIfEmpty()
        val (useCase, _) = useCase(
            MqttOutcome.Rejected(null, "catalogue_unavailable", "Catalogue is being rebuilt"),
            dao,
        )

        assertTrue(useCase.sync("session-1") is CatalogueSyncResult.Failed)
        assertEquals(18, dao.typeRows.value.size)
        assertEquals("2026-09-02T07:00:00Z", repository.meta.first()!!.lastFailedAtUtc)
    }

    @Test
    fun `a timeout leaves the cached catalogue in place and records the failure`() = runTest {
        val dao = FakeWasteCatalogueDao()
        val repository = WasteCatalogueRepository(dao)
        repository.seedIfEmpty()
        val (useCase, _) = useCase(MqttOutcome.NoResponse(FailureKind.Timeout), dao)

        assertTrue(useCase.sync("session-1") is CatalogueSyncResult.Failed)
        assertEquals(18, dao.typeRows.value.size)
        assertEquals("2026-09-02T07:00:00Z", repository.meta.first()!!.lastFailedAtUtc)
    }

    @Test
    fun `a repository write that throws is converted to Failed instead of propagating`() = runTest {
        val dao = FakeWasteCatalogueDao()
        val repository = WasteCatalogueRepository(dao)
        repository.seedIfEmpty()
        dao.insertTypesFailure = IllegalStateException("disk full")
        val (useCase, _) = useCase(MqttOutcome.Accepted(goodResponse()), dao)

        val result = useCase.sync("session-1")

        assertEquals(CatalogueSyncResult.Failed("disk full"), result)
        // The cached (seeded) catalogue survives the failed write untouched.
        assertEquals(18, dao.typeRows.value.size)
        // The failure was still recorded because upsertMeta itself did not fail.
        assertEquals("2026-09-02T07:00:00Z", repository.meta.first()!!.lastFailedAtUtc)
    }

    @Test
    fun `sync still returns Failed when both the write and the failure recording throw`() = runTest {
        val dao = FakeWasteCatalogueDao()
        val repository = WasteCatalogueRepository(dao)
        repository.seedIfEmpty()
        dao.insertTypesFailure = IllegalStateException("disk full")
        dao.upsertMetaFailure = IllegalStateException("cannot even record the failure")
        val (useCase, _) = useCase(MqttOutcome.Accepted(goodResponse()), dao)

        val result = useCase.sync("session-1")

        // The original write failure's message survives; the follow-on failure while trying
        // to record it is swallowed rather than propagating out of sync().
        assertEquals(CatalogueSyncResult.Failed("disk full"), result)
        assertEquals(18, dao.typeRows.value.size)
    }
}
