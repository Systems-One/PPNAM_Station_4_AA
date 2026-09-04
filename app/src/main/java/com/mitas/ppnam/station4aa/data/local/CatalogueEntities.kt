package com.mitas.ppnam.station4aa.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mitas.ppnam.station4aa.domain.model.CatalogueMeta
import com.mitas.ppnam.station4aa.domain.model.CatalogueSource
import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType

@Entity(tableName = "waste_category")
data class WasteCategoryEntity(
    @PrimaryKey val code: String,
    val name: String,
    val sortOrder: Int,
)

@Entity(tableName = "waste_type")
data class WasteTypeEntity(
    @PrimaryKey val code: String,
    val name: String,
    val categoryCode: String,
    val sortOrder: Int,
)

/**
 * Single-row table: the catalogue has exactly one provenance record, so the primary key is a
 * constant rather than something a caller can vary. Writing it with REPLACE therefore always
 * overwrites the one row instead of accumulating history.
 */
@Entity(tableName = "catalogue_meta")
data class CatalogueMetaEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val catalogueVersion: String,
    val syncedAtUtc: String?,
    val source: String,
    val lastFailedAtUtc: String?,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

fun WasteCategoryEntity.toDomain(): WasteCategory =
    WasteCategory(code = code, name = name, sortOrder = sortOrder)

fun WasteCategory.toEntity(): WasteCategoryEntity =
    WasteCategoryEntity(code = code, name = name, sortOrder = sortOrder)

fun WasteTypeEntity.toDomain(): WasteType =
    WasteType(code = code, name = name, categoryCode = categoryCode, sortOrder = sortOrder)

fun WasteType.toEntity(): WasteTypeEntity =
    WasteTypeEntity(code = code, name = name, categoryCode = categoryCode, sortOrder = sortOrder)

fun CatalogueMetaEntity.toDomain(): CatalogueMeta = CatalogueMeta(
    catalogueVersion = catalogueVersion,
    syncedAtUtc = syncedAtUtc,
    // An unrecognised stored value means a downgrade or a corrupt row; treating it as SEED is the
    // honest reading, since it is exactly the "do not trust this catalogue" state.
    source = CatalogueSource.entries.firstOrNull { it.name == source } ?: CatalogueSource.SEED,
    lastFailedAtUtc = lastFailedAtUtc,
)

fun CatalogueMeta.toEntity(): CatalogueMetaEntity = CatalogueMetaEntity(
    id = CatalogueMetaEntity.SINGLETON_ID,
    catalogueVersion = catalogueVersion,
    syncedAtUtc = syncedAtUtc,
    source = source.name,
    lastFailedAtUtc = lastFailedAtUtc,
)
