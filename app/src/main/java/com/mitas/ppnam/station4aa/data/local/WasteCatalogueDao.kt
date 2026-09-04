package com.mitas.ppnam.station4aa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WasteCatalogueDao {

    @Query("SELECT * FROM waste_category ORDER BY sortOrder ASC")
    fun categories(): Flow<List<WasteCategoryEntity>>

    @Query("SELECT * FROM waste_type WHERE categoryCode = :categoryCode ORDER BY sortOrder ASC")
    fun typesFor(categoryCode: String): Flow<List<WasteTypeEntity>>

    @Query("SELECT * FROM catalogue_meta WHERE id = 0 LIMIT 1")
    fun meta(): Flow<CatalogueMetaEntity?>

    @Query("SELECT COUNT(*) FROM waste_type")
    suspend fun wasteTypeCount(): Int

    @Query("DELETE FROM waste_category")
    suspend fun deleteAllCategories()

    @Query("DELETE FROM waste_type")
    suspend fun deleteAllTypes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(rows: List<WasteCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTypes(rows: List<WasteTypeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(row: CatalogueMetaEntity)

    /**
     * Wholesale replacement in one transaction, so the UI never observes a half-applied catalogue
     * and a type deleted at the station cannot survive in the cache. Never a merge.
     */
    @Transaction
    suspend fun replaceAll(
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
