package com.mitas.ppnam.station4aa.data.catalogue

import com.mitas.ppnam.station4aa.data.local.WasteCatalogueDao
import com.mitas.ppnam.station4aa.data.local.toDomain
import com.mitas.ppnam.station4aa.data.local.toEntity
import com.mitas.ppnam.station4aa.domain.model.CatalogueMeta
import com.mitas.ppnam.station4aa.domain.model.CatalogueSource
import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The wizard's only view of the catalogue. Everything the UI needs is a Flow off Room, so a
 * successful sync updates a live selection list without the screen knowing sync exists.
 *
 * Replacement is always wholesale — see [replaceWith]. There is deliberately no merge path.
 */
class WasteCatalogueRepository(
    private val dao: WasteCatalogueDao,
) {
    val categories: Flow<List<WasteCategory>> =
        dao.categories().map { rows -> rows.map { it.toDomain() } }

    fun typesFor(categoryCode: String): Flow<List<WasteType>> =
        dao.typesFor(categoryCode).map { rows -> rows.map { it.toDomain() } }

    val meta: Flow<CatalogueMeta?> = dao.meta().map { it?.toDomain() }

    /**
     * Installs the built-in seed if nothing is cached yet, so a handheld that has never reached
     * Station 4 is still usable. Returns true when it seeded. Idempotent: a device with any cached
     * waste type is left alone, including one whose catalogue arrived from a real sync.
     */
    suspend fun seedIfEmpty(): Boolean {
        if (dao.wasteTypeCount() > 0) return false
        dao.replaceAll(
            categories = WasteCatalogueSeed.categories.map { it.toEntity() },
            types = WasteCatalogueSeed.wasteTypes.map { it.toEntity() },
            meta = CatalogueMeta(
                catalogueVersion = "",
                syncedAtUtc = null,
                source = CatalogueSource.SEED,
                lastFailedAtUtc = null,
            ).toEntity(),
        )
        return true
    }

    /**
     * Replaces the entire cached catalogue with what Station 4 just sent, in one transaction, and
     * marks it SYNCED. Clears any recorded failure: the catalogue is current as of [nowUtc], so a
     * stale failure timestamp would only mislead whoever reads Diagnostics.
     *
     * Callers must not pass an empty [types] or [categories] — see SyncWasteCatalogueUseCase,
     * which treats an empty payload as a failed sync rather than a valid replacement.
     */
    suspend fun replaceWith(
        categories: List<WasteCategory>,
        types: List<WasteType>,
        catalogueVersion: String,
        nowUtc: String,
    ) {
        dao.replaceAll(
            categories = categories.map { it.toEntity() },
            types = types.map { it.toEntity() },
            meta = CatalogueMeta(
                catalogueVersion = catalogueVersion,
                syncedAtUtc = nowUtc,
                source = CatalogueSource.SYNCED,
                lastFailedAtUtc = null,
            ).toEntity(),
        )
    }

    /**
     * Notes that a refresh attempt failed, without touching the cached catalogue itself. The
     * operator keeps working from what is already there; Diagnostics is what makes the staleness
     * visible.
     */
    suspend fun recordSyncFailure(nowUtc: String) {
        val current = meta.first() ?: CatalogueMeta(
            catalogueVersion = "",
            syncedAtUtc = null,
            source = CatalogueSource.SEED,
            lastFailedAtUtc = null,
        )
        dao.upsertMeta(current.copy(lastFailedAtUtc = nowUtc).toEntity())
    }
}
