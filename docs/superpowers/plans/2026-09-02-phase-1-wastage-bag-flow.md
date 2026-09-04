# Phase 1 Wastage Bag Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the handheld's waste-capture wizard to the new Phase 1 process — no machine scan, new Job Number and Operator ID, and a two-level category → waste-type selection served from Station 4 — publishing schema v4.

**Architecture:** A new catalogue subsystem (Room-cached, MQTT-synced, seeded from Station 4's real 18 waste types) feeds a reworked six-step wizard. The wizard controller stays a pure, dependency-free state machine; catalogue values reach it as already-resolved domain objects. The collection event drops the machine fields and gains `jobNumber`/`operatorId`.

**Tech Stack:** Kotlin, Jetpack Compose, Room, HiveMQ MQTT 5 client, Gson, JUnit 4. No Hilt — manual DI through `AppContainer`.

**Spec:** `docs/superpowers/specs/2026-09-02-phase-1-wastage-bag-flow-design.md`

## Global Constraints

- **Schema version is exactly `4`**, a JSON integer, never the string `"4"`.
- **Collection payload field set (v4):** `schemaVersion`, `messageId`, `deviceId`, `operatorSessionId`, `collectionId`, `bagCode`, `jobNumber`, `operatorId`, `wasteTypeCode`, `collectedBy`, `collectedAtUtc`. Nothing else. **No `wasteCategoryCode` on the wire** — the category is local-only and Station 4 derives it from `wasteTypeCode`.
- **Removed from the wire:** `machineCode`, `machineName`. **Renamed:** `machineOperatorUserId` → `operatorId`.
- Gson serializes Kotlin property names verbatim — there is no `@SerializedName` remapping, so **DTO property names ARE the wire keys**.
- **Every DTO constructor parameter must have a default value.** Kotlin only emits the no-arg constructor Gson needs when all parameters have defaults; without them Gson falls back to `UnsafeAllocator` and silently deserializes every field to null regardless of declared non-null types, with no compile error.
- **`WasteWizardController` stays pure** — no Room, no MQTT, no Android imports. Catalogue values are passed in as resolved `WasteCategory`/`WasteType` objects.
- **Catalogue replacement is wholesale, never merged.** An `accepted: true` response carrying zero categories or zero waste types is treated as a failure and never replaces a working catalogue.
- **`messageId`, `collectionId` and `collectedAtUtc` are minted exactly once**, in `WasteCollectionEvent.create()`, and never regenerated on retry.
- Room database version goes 3 → 4. `fallbackToDestructiveMigration()` is already configured and is retained deliberately — queued v3 events can never be accepted by a v4 consumer.
- Run tests with: `./gradlew :app:testDebugUnitTest --tests "<fully.qualified.TestClass>"`
- Run the full suite with: `./gradlew :app:testDebugUnitTest`
- All new files use package prefix `com.mitas.ppnam.station4aa`.

---

### Task 1: Catalogue domain models and seed data

**Files:**
- Create: `app/src/main/java/com/mitas/ppnam/station4aa/domain/model/WasteCategory.kt`
- Create: `app/src/main/java/com/mitas/ppnam/station4aa/domain/model/WasteType.kt`
- Create: `app/src/main/java/com/mitas/ppnam/station4aa/domain/model/CatalogueMeta.kt`
- Create: `app/src/main/java/com/mitas/ppnam/station4aa/data/catalogue/WasteCatalogueSeed.kt`
- Test: `app/src/test/java/com/mitas/ppnam/station4aa/data/catalogue/WasteCatalogueSeedTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `WasteCategory(code: String, name: String, sortOrder: Int)`, `WasteType(code: String, name: String, categoryCode: String, sortOrder: Int)`, `CatalogueMeta(catalogueVersion: String, syncedAtUtc: String?, source: CatalogueSource, lastFailedAtUtc: String?)`, `enum class CatalogueSource { SEED, SYNCED }`, `WasteCatalogueSeed.categories: List<WasteCategory>`, `WasteCatalogueSeed.wasteTypes: List<WasteType>`, `WasteCatalogueSeed.PROVISIONAL_CATEGORY_CODE: String`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/mitas/ppnam/station4aa/data/catalogue/WasteCatalogueSeedTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.data.catalogue.WasteCatalogueSeedTest"`

Expected: FAIL — compilation error, `WasteCatalogueSeed` is unresolved.

- [ ] **Step 3: Create the domain models**

Create `app/src/main/java/com/mitas/ppnam/station4aa/domain/model/WasteCategory.kt`:

```kotlin
package com.mitas.ppnam.station4aa.domain.model

/**
 * A waste category as served by Station 4. Local to this handheld's UI only — the category is a
 * navigation aid that narrows the waste-type list and appears on the review screen, and is
 * deliberately NOT part of the published collection payload (Station 4 derives it from
 * `wasteTypeCode`). See the Phase 1 design doc's decision 3.
 */
data class WasteCategory(
    val code: String,
    val name: String,
    val sortOrder: Int,
)
```

Create `app/src/main/java/com/mitas/ppnam/station4aa/domain/model/WasteType.kt`:

```kotlin
package com.mitas.ppnam.station4aa.domain.model

/**
 * One active waste type as served by Station 4. [code] is the only part of this that reaches the
 * wire, as the collection payload's `wasteTypeCode`; [name] and [categoryCode] exist for display
 * and for narrowing the selection list.
 */
data class WasteType(
    val code: String,
    val name: String,
    val categoryCode: String,
    val sortOrder: Int,
)
```

Create `app/src/main/java/com/mitas/ppnam/station4aa/domain/model/CatalogueMeta.kt`:

```kotlin
package com.mitas.ppnam.station4aa.domain.model

/** Where the currently cached catalogue came from. */
enum class CatalogueSource { SEED, SYNCED }

/**
 * Provenance of the cached catalogue, surfaced in Settings → Diagnostics. Without this, a handheld
 * quietly running the built-in seed against a real station is indistinguishable from a correctly
 * synced one.
 *
 * [catalogueVersion] is opaque to this app: it is stored and displayed so a support call can
 * compare it against the station, and carries no ordering or comparison semantics here.
 */
data class CatalogueMeta(
    val catalogueVersion: String,
    val syncedAtUtc: String?,
    val source: CatalogueSource,
    val lastFailedAtUtc: String?,
)
```

- [ ] **Step 4: Create the seed**

Create `app/src/main/java/com/mitas/ppnam/station4aa/data/catalogue/WasteCatalogueSeed.kt`:

```kotlin
package com.mitas.ppnam.station4aa.data.catalogue

import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import java.util.Locale

/**
 * The fallback catalogue a handheld uses until its first successful sync with Station 4.
 *
 * The codes and names are copied verbatim from Station 4's own schema seed
 * (`PPNAM.Station4.Core/Data/Station4SchemaSql.cs:234-252`). Getting one wrong is not a loud
 * failure: Station 4 accepts any active code, so a mismatched name here means the operator picks
 * one thing and the station records another. `WasteCatalogueSeedTest` guards this.
 *
 * Categories are unknown until the customer confirms them, so everything is seeded under one
 * provisional category. [PROVISIONAL_CATEGORY_CODE] is seed-only and means nothing to Station 4 —
 * the first successful sync replaces it wholesale along with everything else.
 */
object WasteCatalogueSeed {

    const val PROVISIONAL_CATEGORY_CODE = "CAT-00"
    const val PROVISIONAL_CATEGORY_NAME = "Uncategorised"

    val categories: List<WasteCategory> = listOf(
        WasteCategory(
            code = PROVISIONAL_CATEGORY_CODE,
            name = PROVISIONAL_CATEGORY_NAME,
            sortOrder = 1,
        )
    )

    private val TYPE_NAMES = listOf(
        "Bubble breaks",
        "Startup",
        "Technical",
        "Winding",
        "Sticking & folding",
        "Treat",
        "Microns",
        "Registration",
        "Trimmings",
        "Handles",
        "Gusset & layflat",
        "Color variation",
        "Wrong size",
        "Ghost prints",
        "Setting/product change",
        "Sample waste",
        "Sweepings",
        "Customer complaints",
    )

    val wasteTypes: List<WasteType> = TYPE_NAMES.mapIndexed { index, name ->
        val number = index + 1
        WasteType(
            code = String.format(Locale.ROOT, "WT-%02d", number),
            name = name,
            categoryCode = PROVISIONAL_CATEGORY_CODE,
            sortOrder = number,
        )
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.data.catalogue.WasteCatalogueSeedTest"`

Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mitas/ppnam/station4aa/domain/model/WasteCategory.kt \
        app/src/main/java/com/mitas/ppnam/station4aa/domain/model/WasteType.kt \
        app/src/main/java/com/mitas/ppnam/station4aa/domain/model/CatalogueMeta.kt \
        app/src/main/java/com/mitas/ppnam/station4aa/data/catalogue/WasteCatalogueSeed.kt \
        app/src/test/java/com/mitas/ppnam/station4aa/data/catalogue/WasteCatalogueSeedTest.kt
git commit -m "Add waste catalogue domain models and Station 4 seed"
```

---

### Task 2: Room storage for the catalogue

**Files:**
- Create: `app/src/main/java/com/mitas/ppnam/station4aa/data/local/CatalogueEntities.kt`
- Create: `app/src/main/java/com/mitas/ppnam/station4aa/data/local/WasteCatalogueDao.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/data/local/WasteOutboxDatabase.kt`
- Test: `app/src/test/java/com/mitas/ppnam/station4aa/data/local/CatalogueEntitiesTest.kt`

**Interfaces:**
- Consumes: `WasteCategory`, `WasteType`, `CatalogueMeta`, `CatalogueSource` (Task 1).
- Produces: `WasteCategoryEntity(code, name, sortOrder)`, `WasteTypeEntity(code, name, categoryCode, sortOrder)`, `CatalogueMetaEntity(id, catalogueVersion, syncedAtUtc, source, lastFailedAtUtc)` with `CatalogueMetaEntity.SINGLETON_ID = 0`; mappers `WasteCategoryEntity.toDomain()`, `WasteCategory.toEntity()`, `WasteTypeEntity.toDomain()`, `WasteType.toEntity()`, `CatalogueMetaEntity.toDomain()`, `CatalogueMeta.toEntity()`; and `interface WasteCatalogueDao` with `categories(): Flow<List<WasteCategoryEntity>>`, `typesFor(categoryCode: String): Flow<List<WasteTypeEntity>>`, `meta(): Flow<CatalogueMetaEntity?>`, `wasteTypeCount(): Int`, `replaceAll(categories, types, meta)`, `upsertMeta(row)`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/mitas/ppnam/station4aa/data/local/CatalogueEntitiesTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.data.local.CatalogueEntitiesTest"`

Expected: FAIL — compilation error, `toEntity`/`CatalogueMetaEntity` unresolved.

- [ ] **Step 3: Create the entities and mappers**

Create `app/src/main/java/com/mitas/ppnam/station4aa/data/local/CatalogueEntities.kt`:

```kotlin
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
```

- [ ] **Step 4: Create the DAO**

Create `app/src/main/java/com/mitas/ppnam/station4aa/data/local/WasteCatalogueDao.kt`:

```kotlin
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
```

- [ ] **Step 5: Register the entities and bump the database version**

In `app/src/main/java/com/mitas/ppnam/station4aa/data/local/WasteOutboxDatabase.kt`, replace the `@Database` annotation line and add the DAO accessor:

```kotlin
@Database(
    entities = [
        WasteOutboxEntity::class,
        WasteCategoryEntity::class,
        WasteTypeEntity::class,
        CatalogueMetaEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class WasteOutboxDatabase : RoomDatabase() {
    abstract fun wasteOutboxDao(): WasteOutboxDao
    abstract fun wasteCatalogueDao(): WasteCatalogueDao
```

Also replace the comment above `.fallbackToDestructiveMigration()` with:

```kotlin
                // Version 4 changes the outbox to schema v4 (machine fields out, jobNumber and
                // operatorId in) and adds the catalogue tables. Destructive migration is kept
                // deliberately: a queued v3 event carries machineCode and no jobNumber, so it can
                // never be accepted by a v4 consumer — a migration would preserve only messages
                // guaranteed to be rejected. The catalogue re-seeds and re-syncs on next launch.
                // ROLLOUT: upgrade when no handheld holds unsent collections.
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.data.local.CatalogueEntitiesTest"`

Expected: PASS, 5 tests. Room's annotation processor must also succeed — if `kspDebugKotlin` fails, the DAO or entity declarations are wrong.

- [ ] **Step 7: Verify the whole suite still compiles and passes**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS, all existing tests unaffected.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/mitas/ppnam/station4aa/data/local/CatalogueEntities.kt \
        app/src/main/java/com/mitas/ppnam/station4aa/data/local/WasteCatalogueDao.kt \
        app/src/main/java/com/mitas/ppnam/station4aa/data/local/WasteOutboxDatabase.kt \
        app/src/test/java/com/mitas/ppnam/station4aa/data/local/CatalogueEntitiesTest.kt
git commit -m "Add Room storage for the waste catalogue, database version 4"
```

---

### Task 3: Waste catalogue repository

**Files:**
- Create: `app/src/main/java/com/mitas/ppnam/station4aa/data/catalogue/WasteCatalogueRepository.kt`
- Test: `app/src/test/java/com/mitas/ppnam/station4aa/data/catalogue/WasteCatalogueRepositoryTest.kt`

**Interfaces:**
- Consumes: `WasteCatalogueDao` and the mappers (Task 2), `WasteCatalogueSeed` (Task 1).
- Produces: `class WasteCatalogueRepository(dao: WasteCatalogueDao)` with `categories: Flow<List<WasteCategory>>`, `fun typesFor(categoryCode: String): Flow<List<WasteType>>`, `meta: Flow<CatalogueMeta?>`, `suspend fun seedIfEmpty(): Boolean`, `suspend fun replaceWith(categories: List<WasteCategory>, types: List<WasteType>, catalogueVersion: String, nowUtc: String)`, `suspend fun recordSyncFailure(nowUtc: String)`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/mitas/ppnam/station4aa/data/catalogue/WasteCatalogueRepositoryTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.data.catalogue.WasteCatalogueRepositoryTest"`

Expected: FAIL — compilation error, `WasteCatalogueRepository` is unresolved.

- [ ] **Step 3: Implement the repository**

Create `app/src/main/java/com/mitas/ppnam/station4aa/data/catalogue/WasteCatalogueRepository.kt`:

```kotlin
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.data.catalogue.WasteCatalogueRepositoryTest"`

Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mitas/ppnam/station4aa/data/catalogue/WasteCatalogueRepository.kt \
        app/src/test/java/com/mitas/ppnam/station4aa/data/catalogue/WasteCatalogueRepositoryTest.kt
git commit -m "Add WasteCatalogueRepository with seed, wholesale replace and failure recording"
```

---

### Task 4: Catalogue sync over MQTT

**Files:**
- Create: `app/src/main/java/com/mitas/ppnam/station4aa/data/mqtt/RequestChannel.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/data/mqtt/MqttRequestChannel.kt:21-23`
- Create: `app/src/main/java/com/mitas/ppnam/station4aa/data/mqtt/dto/WasteCatalogueMessages.kt`
- Create: `app/src/main/java/com/mitas/ppnam/station4aa/domain/usecase/SyncWasteCatalogueUseCase.kt`
- Test: `app/src/test/java/com/mitas/ppnam/station4aa/domain/usecase/SyncWasteCatalogueUseCaseTest.kt`

**Interfaces:**
- Consumes: `WasteCatalogueRepository` (Task 3), `MqttOutcome`/`FailureKind` (existing).
- Produces: `interface RequestChannel` (implemented by `MqttRequestChannel`); `WasteCatalogueRequestPayload(operatorSessionId)`, `WasteCatalogueResponse(...)`, `WasteCategoryDto(...)`, `WasteTypeDto(...)`; `class SyncWasteCatalogueUseCase(requestChannel, repository, deviceId, clock)` with `suspend fun sync(operatorSessionId: String): CatalogueSyncResult`; `sealed interface CatalogueSyncResult { data class Replaced(categoryCount: Int, typeCount: Int); data class Failed(reason: String) }`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/mitas/ppnam/station4aa/domain/usecase/SyncWasteCatalogueUseCaseTest.kt`:

```kotlin
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
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.domain.usecase.SyncWasteCatalogueUseCaseTest"`

Expected: FAIL — compilation error, `RequestChannel` and `SyncWasteCatalogueUseCase` are unresolved.

- [ ] **Step 3: Extract the RequestChannel interface**

Create `app/src/main/java/com/mitas/ppnam/station4aa/data/mqtt/RequestChannel.kt`:

```kotlin
package com.mitas.ppnam.station4aa.data.mqtt

/**
 * The request/response round trip [MqttRequestChannel] performs, as an interface so use cases can
 * be tested without a broker. Defaults live here; implementations must not repeat them.
 */
interface RequestChannel {
    suspend fun <T : Any> request(
        deviceId: String,
        requestType: String,
        responseClass: Class<T>,
        payload: Any,
        operatorSessionId: String = "",
        timeoutMs: Long = 15_000L,
    ): MqttOutcome<T>
}
```

In `app/src/main/java/com/mitas/ppnam/station4aa/data/mqtt/MqttRequestChannel.kt`, change the class declaration to implement it and mark `request` as an override, removing its default parameter values (defaults now come from the interface):

```kotlin
class MqttRequestChannel(
    private val connectionManager: MqttConnectionManager,
) : RequestChannel {
```

and change the `request` signature to:

```kotlin
    override suspend fun <T : Any> request(
        deviceId: String,
        requestType: String,
        responseClass: Class<T>,
        payload: Any,
        operatorSessionId: String,
        timeoutMs: Long,
    ): MqttOutcome<T> {
```

Delete the `companion object`'s now-unused `DEFAULT_TIMEOUT_MS` only if nothing else references it; otherwise leave it.

- [ ] **Step 4: Create the catalogue DTOs**

Create `app/src/main/java/com/mitas/ppnam/station4aa/data/mqtt/dto/WasteCatalogueMessages.kt`:

```kotlin
package com.mitas.ppnam.station4aa.data.mqtt.dto

/**
 * Wire shapes for the `waste_catalogue_requested` / `waste_catalogue` exchange on
 * `PPNAM/station_4/{deviceId}/req|res/...`, using the same schema 4.1 auth envelope every other
 * request on that channel uses (RequestEnvelope adds messageId/schemaVersion/deviceId/timestampUtc).
 *
 * Gson serializes Kotlin property names verbatim, so these property names ARE the wire keys.
 * Every parameter keeps a default value — without that, Gson falls back to UnsafeAllocator and
 * silently deserializes every field to null regardless of declared non-null types.
 */
data class WasteCatalogueRequestPayload(
    val operatorSessionId: String = "",
)

data class WasteCategoryDto(
    val code: String = "",
    val name: String = "",
    val sortOrder: Int = 0,
)

data class WasteTypeDto(
    val code: String = "",
    val name: String = "",
    val categoryCode: String = "",
    val sortOrder: Int = 0,
)

/**
 * Station 4 sends active types only; this app renders exactly what it receives and never filters.
 * [catalogueVersion] is opaque here — stored and displayed for support, never compared or ordered.
 */
data class WasteCatalogueResponse(
    val messageId: String = "",
    val inResponseToMessageId: String = "",
    val schemaVersion: String = "",
    val deviceId: String = "",
    val timestampUtc: String = "",
    val accepted: Boolean = false,
    val catalogueVersion: String = "",
    val categories: List<WasteCategoryDto> = emptyList(),
    val wasteTypes: List<WasteTypeDto> = emptyList(),
    val errorCode: String? = null,
    val reason: String? = null,
)
```

- [ ] **Step 5: Implement the sync use case**

Create `app/src/main/java/com/mitas/ppnam/station4aa/domain/usecase/SyncWasteCatalogueUseCase.kt`:

```kotlin
package com.mitas.ppnam.station4aa.domain.usecase

import com.mitas.ppnam.station4aa.data.catalogue.WasteCatalogueRepository
import com.mitas.ppnam.station4aa.data.mqtt.MqttOutcome
import com.mitas.ppnam.station4aa.data.mqtt.RequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.describe
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCatalogueRequestPayload
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCatalogueResponse
import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import java.time.Instant

sealed interface CatalogueSyncResult {
    data class Replaced(val categoryCount: Int, val typeCount: Int) : CatalogueSyncResult
    data class Failed(val reason: String) : CatalogueSyncResult
}

/**
 * Pulls the category/waste-type catalogue from Station 4 and replaces the local cache with it.
 *
 * Failure is never fatal and never blocks a collection: the cached (or seeded) catalogue stays in
 * use and the failure is recorded for Diagnostics. The one rule worth stating plainly is that an
 * `accepted: true` response carrying no categories or no waste types is treated as a **failure**,
 * not as an instruction to empty the catalogue — otherwise one bad server-side query would leave
 * every handheld in the plant unable to select a waste type, with nothing on screen explaining why.
 */
class SyncWasteCatalogueUseCase(
    private val requestChannel: RequestChannel,
    private val repository: WasteCatalogueRepository,
    private val deviceId: String,
    private val clock: () -> Instant = Instant::now,
) {
    companion object {
        const val REQUEST_TYPE = "waste_catalogue_requested"
    }

    suspend fun sync(operatorSessionId: String): CatalogueSyncResult {
        val outcome = requestChannel.request(
            deviceId = deviceId,
            requestType = REQUEST_TYPE,
            responseClass = WasteCatalogueResponse::class.java,
            payload = WasteCatalogueRequestPayload(operatorSessionId = operatorSessionId),
            operatorSessionId = operatorSessionId,
        )

        val body = when (outcome) {
            is MqttOutcome.Accepted -> outcome.body
            is MqttOutcome.Rejected ->
                return fail(outcome.reason ?: outcome.errorCode ?: "Station 4 rejected the request")
            is MqttOutcome.NoResponse -> return fail(outcome.kind.describe())
        }

        if (body.categories.isEmpty() || body.wasteTypes.isEmpty()) {
            return fail("Station 4 returned an empty catalogue")
        }

        repository.replaceWith(
            categories = body.categories.map {
                WasteCategory(code = it.code, name = it.name, sortOrder = it.sortOrder)
            },
            types = body.wasteTypes.map {
                WasteType(
                    code = it.code,
                    name = it.name,
                    categoryCode = it.categoryCode,
                    sortOrder = it.sortOrder,
                )
            },
            catalogueVersion = body.catalogueVersion,
            nowUtc = clock().toString(),
        )
        return CatalogueSyncResult.Replaced(
            categoryCount = body.categories.size,
            typeCount = body.wasteTypes.size,
        )
    }

    private suspend fun fail(reason: String): CatalogueSyncResult.Failed {
        repository.recordSyncFailure(clock().toString())
        return CatalogueSyncResult.Failed(reason)
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.domain.usecase.SyncWasteCatalogueUseCaseTest"`

Expected: PASS, 5 tests.

- [ ] **Step 7: Verify the whole suite still passes**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS — the `RequestChannel` extraction must not have broken `AuthUseCase` or `ScramExchange`, which call `request(...)` by named argument.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/mitas/ppnam/station4aa/data/mqtt/RequestChannel.kt \
        app/src/main/java/com/mitas/ppnam/station4aa/data/mqtt/MqttRequestChannel.kt \
        app/src/main/java/com/mitas/ppnam/station4aa/data/mqtt/dto/WasteCatalogueMessages.kt \
        app/src/main/java/com/mitas/ppnam/station4aa/domain/usecase/SyncWasteCatalogueUseCase.kt \
        app/src/test/java/com/mitas/ppnam/station4aa/domain/usecase/SyncWasteCatalogueUseCaseTest.kt
git commit -m "Sync the waste catalogue from Station 4 over the existing request channel"
```

---

### Task 5: Validator support for the new fields

**Files:**
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/domain/validation/WasteCollectionValidator.kt:26-52`
- Test: `app/src/test/java/com/mitas/ppnam/station4aa/domain/validation/WasteCollectionValidatorTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `WasteCollectionValidator.validateOperatorId(raw: String): String?`, `WasteCollectionValidator.validateJobNumber(raw: String): String?`. Both return `null` when valid, or an operator-facing message.

This task is purely additive — `validateMachineCode` and `validateMachineOperatorUserId` stay for now so the build remains green, and are deleted in Task 9.

- [ ] **Step 1: Write the failing tests**

Append these to the existing `WasteCollectionValidatorTest` class body (before its closing brace):

```kotlin
    @Test
    fun `a valid operator id is accepted`() {
        assertNull(WasteCollectionValidator.validateOperatorId("MO-00427"))
    }

    @Test
    fun `a blank operator id is rejected`() {
        assertEquals("Required.", WasteCollectionValidator.validateOperatorId("   "))
    }

    @Test
    fun `a placeholder operator id is rejected`() {
        assertNotNull(WasteCollectionValidator.validateOperatorId("N/A"))
        assertNotNull(WasteCollectionValidator.validateOperatorId("unknown"))
    }

    @Test
    fun `an over-length operator id is rejected`() {
        assertNotNull(WasteCollectionValidator.validateOperatorId("x".repeat(101)))
    }

    @Test
    fun `a valid job number is accepted`() {
        assertNull(WasteCollectionValidator.validateJobNumber("JOB-2026-0041"))
    }

    @Test
    fun `a blank job number is rejected`() {
        assertEquals("Required.", WasteCollectionValidator.validateJobNumber(""))
    }

    @Test
    fun `a placeholder job number is rejected because it can be hand-typed`() {
        assertNotNull(WasteCollectionValidator.validateJobNumber("N/A"))
        assertNotNull(WasteCollectionValidator.validateJobNumber("NONE"))
    }

    @Test
    fun `a job number containing control characters is rejected`() {
        assertNotNull(WasteCollectionValidator.validateJobNumber("JOB\u0007-1"))
    }

    @Test
    fun `an over-length job number is rejected`() {
        assertNotNull(WasteCollectionValidator.validateJobNumber("9".repeat(101)))
    }

    @Test
    fun `job number surrounding whitespace does not itself fail validation`() {
        assertNull(WasteCollectionValidator.validateJobNumber("  JOB-1  "))
    }
```

If `assertNotNull` / `assertNull` are not already imported in that file, add `import org.junit.Assert.assertNotNull` and `import org.junit.Assert.assertNull`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.domain.validation.WasteCollectionValidatorTest"`

Expected: FAIL — compilation error, `validateOperatorId` and `validateJobNumber` are unresolved.

- [ ] **Step 3: Add the two validators**

In `WasteCollectionValidator`, add the two length constants next to the existing ones:

```kotlin
    private const val OPERATOR_ID_MAX_LENGTH = 100
    private const val JOB_NUMBER_MAX_LENGTH = 100
```

and add the two functions after `validateMachineOperatorUserId`:

```kotlin
    /** `operatorId`: the production operator who ran the machine that produced this waste — a
     * different person from the logged-in wastage operator, scanned or typed fresh for every
     * transaction. This is the v3 `machineOperatorUserId` renamed now that the machine itself is
     * no longer scanned, and it keeps that field's placeholder rejection. */
    fun validateOperatorId(raw: String): String? =
        validateRequiredIdentity(raw, OPERATOR_ID_MAX_LENGTH, rejectPlaceholders = true)

    /** `jobNumber`: opaque to this app — no format rule and no list to check against. Placeholder-
     * checked, unlike `bagCode`: a bag code is always a scanned customer barcode, whereas a job
     * number can be hand-typed by someone who does not have one to hand. */
    fun validateJobNumber(raw: String): String? =
        validateRequiredIdentity(raw, JOB_NUMBER_MAX_LENGTH, rejectPlaceholders = true)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.domain.validation.WasteCollectionValidatorTest"`

Expected: PASS — the original 19 tests plus the 10 new ones.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mitas/ppnam/station4aa/domain/validation/WasteCollectionValidator.kt \
        app/src/test/java/com/mitas/ppnam/station4aa/domain/validation/WasteCollectionValidatorTest.kt
git commit -m "Add operator id and job number validation for the Phase 1 flow"
```

---

### Task 6: Phase 1 wizard and schema v4, end to end

This is the one task that cannot be split further: Kotlin will not compile a partial rename across the wizard, the event, the outbox and the screen, so they change together.

**Files:**
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/domain/wizard/WizardStep.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/domain/wizard/WasteTransactionDraft.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/domain/wizard/WasteWizardController.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/domain/model/WasteCollectionEvent.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/data/mqtt/dto/WasteCollectionMessage.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/data/local/WasteOutboxEntity.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringViewModel.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringScreen.kt`
- Test: `app/src/test/java/com/mitas/ppnam/station4aa/domain/wizard/WasteWizardControllerTest.kt`
- Test: `app/src/test/java/com/mitas/ppnam/station4aa/domain/model/WasteCollectionEventTest.kt`
- Test: `app/src/test/java/com/mitas/ppnam/station4aa/data/mqtt/WasteCollectionResultChannelTest.kt` (fixture fields only)

**Interfaces:**
- Consumes: `WasteCategory`, `WasteType` (Task 1); `WasteCatalogueRepository` (Task 3); `validateJobNumber`, `validateOperatorId` (Task 5).
- Produces: `enum class WizardStep { SCAN_BAG, SCAN_JOB, SCAN_OPERATOR, SELECT_CATEGORY, SELECT_WASTE_TYPE, REVIEW }`; `WasteTransactionDraft(bagCode: String?, jobNumber: String?, operatorId: String?, category: WasteCategory?, wasteType: WasteType?)`; `WasteWizardController` with `submitBagCode(raw): String?`, `submitJobNumber(raw): String?`, `submitOperatorId(raw): String?`, `confirmCategory(category: WasteCategory)`, `confirmWasteType(type: WasteType)`, `editField(target: WizardStep)`, `cancel()`, `handleScannedValue(value): ScanDispatchResult`; `WasteCollectionEvent.create(bagCode, jobNumber, operatorId, wasteTypeCode, collectedBy, deviceId, operatorSessionId, now)`.

- [ ] **Step 1: Rewrite the wizard controller test**

Replace the whole body of `app/src/test/java/com/mitas/ppnam/station4aa/domain/wizard/WasteWizardControllerTest.kt`:

```kotlin
package com.mitas.ppnam.station4aa.domain.wizard

import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WasteWizardControllerTest {

    private val process = WasteCategory(code = "CAT-01", name = "Process", sortOrder = 1)
    private val quality = WasteCategory(code = "CAT-02", name = "Quality", sortOrder = 2)
    private val bubbleBreaks =
        WasteType(code = "WT-01", name = "Bubble breaks", categoryCode = "CAT-01", sortOrder = 1)
    private val ghostPrints =
        WasteType(code = "WT-14", name = "Ghost prints", categoryCode = "CAT-02", sortOrder = 14)

    /** Drives a controller to REVIEW with valid values. */
    private fun completedController(): WasteWizardController {
        val controller = WasteWizardController()
        controller.submitBagCode("BAG-01")
        controller.submitJobNumber("JOB-2026-0041")
        controller.submitOperatorId("MO-00427")
        controller.confirmCategory(process)
        controller.confirmWasteType(bubbleBreaks)
        return controller
    }

    @Test
    fun `the wizard starts by scanning the bag`() {
        assertEquals(WizardStep.SCAN_BAG, WasteWizardController().step)
    }

    @Test
    fun `a full pass walks the six steps in the documented order`() {
        val controller = WasteWizardController()
        assertNull(controller.submitBagCode("BAG-01"))
        assertEquals(WizardStep.SCAN_JOB, controller.step)
        assertNull(controller.submitJobNumber("JOB-2026-0041"))
        assertEquals(WizardStep.SCAN_OPERATOR, controller.step)
        assertNull(controller.submitOperatorId("MO-00427"))
        assertEquals(WizardStep.SELECT_CATEGORY, controller.step)
        controller.confirmCategory(process)
        assertEquals(WizardStep.SELECT_WASTE_TYPE, controller.step)
        controller.confirmWasteType(bubbleBreaks)
        assertEquals(WizardStep.REVIEW, controller.step)
    }

    @Test
    fun `the completed draft carries every captured field`() {
        val draft = completedController().draft
        assertEquals("BAG-01", draft.bagCode)
        assertEquals("JOB-2026-0041", draft.jobNumber)
        assertEquals("MO-00427", draft.operatorId)
        assertEquals(process, draft.category)
        assertEquals(bubbleBreaks, draft.wasteType)
    }

    @Test
    fun `an invalid value returns an error and does not advance`() {
        val controller = WasteWizardController()
        assertNotNull(controller.submitBagCode("  "))
        assertEquals(WizardStep.SCAN_BAG, controller.step)
        assertNull(controller.draft.bagCode)
    }

    @Test
    fun `a placeholder job number is refused`() {
        val controller = WasteWizardController()
        controller.submitBagCode("BAG-01")
        assertNotNull(controller.submitJobNumber("N/A"))
        assertEquals(WizardStep.SCAN_JOB, controller.step)
    }

    @Test
    fun `submitted values are trimmed`() {
        val controller = WasteWizardController()
        controller.submitBagCode("  BAG-01  ")
        assertEquals("BAG-01", controller.draft.bagCode)
    }

    @Test
    fun `scans are dispatched to whichever scan step is active`() {
        val controller = WasteWizardController()
        assertEquals(ScanDispatchResult.Applied(null), controller.handleScannedValue("BAG-01"))
        assertEquals(WizardStep.SCAN_JOB, controller.step)
        assertEquals(ScanDispatchResult.Applied(null), controller.handleScannedValue("JOB-1"))
        assertEquals(WizardStep.SCAN_OPERATOR, controller.step)
    }

    @Test
    fun `scans are ignored on the selection and review steps`() {
        val controller = WasteWizardController()
        controller.submitBagCode("BAG-01")
        controller.submitJobNumber("JOB-1")
        controller.submitOperatorId("MO-1")
        assertEquals(ScanDispatchResult.Ignored, controller.handleScannedValue("STRAY"))
        controller.confirmCategory(process)
        assertEquals(ScanDispatchResult.Ignored, controller.handleScannedValue("STRAY"))
        controller.confirmWasteType(bubbleBreaks)
        assertEquals(ScanDispatchResult.Ignored, controller.handleScannedValue("STRAY"))
        assertEquals(WizardStep.REVIEW, controller.step)
    }

    @Test
    fun `cancel resets to the first step and clears the draft`() {
        val controller = completedController()
        controller.cancel()
        assertEquals(WizardStep.SCAN_BAG, controller.step)
        assertEquals(WasteTransactionDraft(), controller.draft)
    }

    @Test
    fun `confirmCategory outside its own step throws`() {
        assertThrows(IllegalStateException::class.java) {
            WasteWizardController().confirmCategory(process)
        }
    }

    @Test
    fun `confirmWasteType outside its own step throws`() {
        assertThrows(IllegalStateException::class.java) {
            WasteWizardController().confirmWasteType(bubbleBreaks)
        }
    }

    @Test
    fun `editField is only legal from review`() {
        val controller = WasteWizardController()
        assertThrows(IllegalStateException::class.java) {
            controller.editField(WizardStep.SCAN_BAG)
        }
    }

    @Test
    fun `editField cannot target review itself`() {
        val controller = completedController()
        assertThrows(IllegalStateException::class.java) {
            controller.editField(WizardStep.REVIEW)
        }
    }

    @Test
    fun `editing the bag code returns straight to review with the new value`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_BAG)
        assertEquals(WizardStep.SCAN_BAG, controller.step)

        assertNull(controller.submitBagCode("BAG-02"))

        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals("BAG-02", controller.draft.bagCode)
        // Nothing else was disturbed.
        assertEquals("JOB-2026-0041", controller.draft.jobNumber)
        assertEquals(bubbleBreaks, controller.draft.wasteType)
    }

    @Test
    fun `editing the job number returns straight to review`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_JOB)
        controller.submitJobNumber("JOB-2026-0099")
        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals("JOB-2026-0099", controller.draft.jobNumber)
    }

    @Test
    fun `editing the operator id returns straight to review`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_OPERATOR)
        controller.submitOperatorId("MO-00999")
        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals("MO-00999", controller.draft.operatorId)
    }

    @Test
    fun `editing the waste type returns straight to review`() {
        val controller = completedController()
        controller.editField(WizardStep.SELECT_WASTE_TYPE)
        controller.confirmWasteType(
            WasteType(code = "WT-02", name = "Startup", categoryCode = "CAT-01", sortOrder = 2)
        )
        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals("WT-02", controller.draft.wasteType?.code)
    }

    @Test
    fun `a failed edit stays on the edited step rather than returning to review`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_BAG)
        assertNotNull(controller.submitBagCode("   "))
        assertEquals(WizardStep.SCAN_BAG, controller.step)
        assertEquals("BAG-01", controller.draft.bagCode)
    }

    @Test
    fun `re-confirming the same category from review returns straight to review`() {
        val controller = completedController()
        controller.editField(WizardStep.SELECT_CATEGORY)
        controller.confirmCategory(process)
        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals(bubbleBreaks, controller.draft.wasteType)
    }

    @Test
    fun `changing the category from review clears the type and forces reselection`() {
        val controller = completedController()
        controller.editField(WizardStep.SELECT_CATEGORY)

        controller.confirmCategory(quality)

        assertEquals(WizardStep.SELECT_WASTE_TYPE, controller.step)
        assertEquals(quality, controller.draft.category)
        assertNull(controller.draft.wasteType)
    }

    @Test
    fun `after a forced reselection the wizard lands back on review`() {
        val controller = completedController()
        controller.editField(WizardStep.SELECT_CATEGORY)
        controller.confirmCategory(quality)

        controller.confirmWasteType(ghostPrints)

        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals(ghostPrints, controller.draft.wasteType)
    }

    @Test
    fun `cancel clears a pending return-to-review`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_BAG)
        controller.cancel()

        // A fresh transaction must walk the whole flow, not jump to review after one scan.
        controller.submitBagCode("BAG-09")
        assertEquals(WizardStep.SCAN_JOB, controller.step)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.domain.wizard.WasteWizardControllerTest"`

Expected: FAIL — compilation errors on `submitJobNumber`, `confirmCategory`, `editField`, `WizardStep.SCAN_JOB`.

- [ ] **Step 3: Rewrite the step enum and draft**

Replace `app/src/main/java/com/mitas/ppnam/station4aa/domain/wizard/WizardStep.kt`:

```kotlin
package com.mitas.ppnam.station4aa.domain.wizard

/** The six states of the Phase 1 wastage-bag wizard, in the order the process document defines —
 * see `docs/superpowers/specs/2026-09-02-phase-1-wastage-bag-flow-design.md`. */
enum class WizardStep { SCAN_BAG, SCAN_JOB, SCAN_OPERATOR, SELECT_CATEGORY, SELECT_WASTE_TYPE, REVIEW }
```

Replace `app/src/main/java/com/mitas/ppnam/station4aa/domain/wizard/WasteTransactionDraft.kt`:

```kotlin
package com.mitas.ppnam.station4aa.domain.wizard

import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType

/**
 * Local-only wizard state — never sent over MQTT itself. Only once every field is non-null does
 * [WasteWizardController] reach [WizardStep.REVIEW]; the completed draft is what
 * WasteGatheringViewModel reads to build the one real
 * [com.mitas.ppnam.station4aa.domain.model.WasteCollectionEvent].
 *
 * [category] never leaves the device: it narrows the [wasteType] list and appears on the review
 * screen, and Station 4 derives the category from the published `wasteTypeCode`.
 */
data class WasteTransactionDraft(
    val bagCode: String? = null,
    val jobNumber: String? = null,
    val operatorId: String? = null,
    val category: WasteCategory? = null,
    val wasteType: WasteType? = null,
)
```

- [ ] **Step 4: Rewrite the controller**

Replace `app/src/main/java/com/mitas/ppnam/station4aa/domain/wizard/WasteWizardController.kt`:

```kotlin
package com.mitas.ppnam.station4aa.domain.wizard

import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import com.mitas.ppnam.station4aa.domain.validation.WasteCollectionValidator

/**
 * Pure step-transition logic for the Phase 1 wastage-bag wizard — no Android, Room or MQTT
 * dependencies, so it is fully unit-testable without fakes. `WasteGatheringViewModel` owns one
 * instance per screen and mirrors [step]/[draft] into StateFlows for the UI. Catalogue values
 * arrive as already-resolved [WasteCategory]/[WasteType] objects, which is what keeps this class
 * free of the repository.
 *
 * No step-back during capture: mid-flow, the only way out of a wrong value is submitting a
 * corrected one, and [cancel] is always available for a full reset. From [WizardStep.REVIEW],
 * [editField] jumps to a single step and returns there once that step is satisfied.
 */
class WasteWizardController {

    var step: WizardStep = WizardStep.SCAN_BAG
        private set
    var draft: WasteTransactionDraft = WasteTransactionDraft()
        private set

    /** Set by [editField]: the next satisfied step returns to REVIEW instead of advancing. */
    private var returnToReview = false

    /** Bag/job/operator barcode scans all funnel through here. Returns
     * [ScanDispatchResult.Ignored] when the active step doesn't accept a scan — a stray scan is
     * dropped, never applied to a step the operator has already moved past, and never allowed to
     * rewrite a field on the review screen the operator is about to confirm. */
    fun handleScannedValue(value: String): ScanDispatchResult = when (step) {
        WizardStep.SCAN_BAG -> ScanDispatchResult.Applied(submitBagCode(value))
        WizardStep.SCAN_JOB -> ScanDispatchResult.Applied(submitJobNumber(value))
        WizardStep.SCAN_OPERATOR -> ScanDispatchResult.Applied(submitOperatorId(value))
        WizardStep.SELECT_CATEGORY,
        WizardStep.SELECT_WASTE_TYPE,
        WizardStep.REVIEW -> ScanDispatchResult.Ignored
    }

    /** Manual-entry fallback for the bag step; a scan calls this too via [handleScannedValue].
     * Returns an error message, or null on success. */
    fun submitBagCode(raw: String): String? {
        val error = WasteCollectionValidator.validateBagCode(raw)
        if (error != null) return error
        draft = draft.copy(bagCode = raw.trim())
        advanceTo(WizardStep.SCAN_JOB)
        return null
    }

    /** Manual-entry fallback for the job-number step. */
    fun submitJobNumber(raw: String): String? {
        val error = WasteCollectionValidator.validateJobNumber(raw)
        if (error != null) return error
        draft = draft.copy(jobNumber = raw.trim())
        advanceTo(WizardStep.SCAN_OPERATOR)
        return null
    }

    /** Manual-entry fallback for the production-operator step. */
    fun submitOperatorId(raw: String): String? {
        val error = WasteCollectionValidator.validateOperatorId(raw)
        if (error != null) return error
        draft = draft.copy(operatorId = raw.trim())
        advanceTo(WizardStep.SELECT_CATEGORY)
        return null
    }

    /**
     * SELECT_CATEGORY's step-local Confirm action. Changing to a *different* category clears the
     * selected type and routes to SELECT_WASTE_TYPE even when editing from review: a type belongs
     * to exactly one category, so keeping the old one would leave a contradiction on the review
     * screen that the operator has no reason to notice.
     */
    fun confirmCategory(category: WasteCategory) {
        check(step == WizardStep.SELECT_CATEGORY) {
            "confirmCategory called outside SELECT_CATEGORY (was $step)"
        }
        val categoryChanged = draft.category?.code != category.code
        draft = draft.copy(category = category)
        if (categoryChanged) {
            draft = draft.copy(wasteType = null)
            returnToReview = false
            step = WizardStep.SELECT_WASTE_TYPE
        } else {
            advanceTo(WizardStep.SELECT_WASTE_TYPE)
        }
    }

    /** SELECT_WASTE_TYPE's step-local Confirm action. */
    fun confirmWasteType(type: WasteType) {
        check(step == WizardStep.SELECT_WASTE_TYPE) {
            "confirmWasteType called outside SELECT_WASTE_TYPE (was $step)"
        }
        draft = draft.copy(wasteType = type)
        advanceTo(WizardStep.REVIEW)
    }

    /**
     * Jumps from the review screen to one capture step to correct a single value, then returns to
     * review once that step is satisfied. Both checks are unreachable through the UI, which renders
     * edit affordances only on the review dialog's own rows.
     */
    fun editField(target: WizardStep) {
        check(step == WizardStep.REVIEW) { "editField called outside REVIEW (was $step)" }
        check(target != WizardStep.REVIEW) { "editField cannot target REVIEW" }
        returnToReview = true
        step = target
    }

    /** Available on every step, including REVIEW. Discards the draft and returns to the first
     * step — there is no partial-edit recovery path for an abandoned transaction. */
    fun cancel() {
        step = WizardStep.SCAN_BAG
        draft = WasteTransactionDraft()
        returnToReview = false
    }

    private fun advanceTo(next: WizardStep) {
        step = if (returnToReview) WizardStep.REVIEW else next
        returnToReview = false
    }
}
```

- [ ] **Step 5: Run the controller test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.domain.wizard.WasteWizardControllerTest"`

Expected: the controller test compiles and passes, 22 tests. Other modules will still fail to compile — that is addressed in the following steps.

- [ ] **Step 6: Update the event test to schema v4**

In `app/src/test/java/com/mitas/ppnam/station4aa/domain/model/WasteCollectionEventTest.kt`, replace its `buildEvent()` helper and any `SCHEMA_VERSION` assertion:

```kotlin
    private fun buildEvent(
        bagCode: String = "BAG-01",
        jobNumber: String = "JOB-2026-0041",
        operatorId: String = "MO-00427",
        wasteTypeCode: String = "WT-01",
        collectedBy: String = "Collector One",
        deviceId: String = "scanner_a1b2c3d4e5f6",
        operatorSessionId: String = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
    ) = WasteCollectionEvent.create(
        bagCode = bagCode,
        jobNumber = jobNumber,
        operatorId = operatorId,
        wasteTypeCode = wasteTypeCode,
        collectedBy = collectedBy,
        deviceId = deviceId,
        operatorSessionId = operatorSessionId,
    )
```

Add these tests to the same class:

```kotlin
    @Test
    fun `the published schema version is 4`() {
        assertEquals(4, WasteCollectionEvent.SCHEMA_VERSION)
        assertEquals(4, buildEvent().toWireMessage().schemaVersion)
    }

    @Test
    fun `the wire message carries the v4 field set`() {
        val message = buildEvent().toWireMessage()
        assertEquals("BAG-01", message.bagCode)
        assertEquals("JOB-2026-0041", message.jobNumber)
        assertEquals("MO-00427", message.operatorId)
        assertEquals("WT-01", message.wasteTypeCode)
    }

    @Test
    fun `fields are trimmed when the event is minted`() {
        val event = buildEvent(jobNumber = "  JOB-7  ", operatorId = "  MO-7  ")
        assertEquals("JOB-7", event.jobNumber)
        assertEquals("MO-7", event.operatorId)
    }

    @Test
    fun `the generated identity fields are minted fresh per event, never shared`() {
        // A retry must republish the exact bytes originally queued, so these three are minted once
        // in create() and never regenerated — two separate transactions must not collide.
        val first = buildEvent()
        val second = buildEvent()
        assertNotEquals(first.messageId, second.messageId)
        assertNotEquals(first.collectionId, second.collectionId)
    }

    @Test
    fun `converting to the wire message does not re-mint anything`() {
        val event = buildEvent()
        val first = event.toWireMessage()
        val second = event.toWireMessage()
        assertEquals(first.messageId, second.messageId)
        assertEquals(first.collectionId, second.collectionId)
        assertEquals(first.collectedAtUtc, second.collectedAtUtc)
    }
```

Add `import org.junit.Assert.assertNotEquals` if it is not already present.

Delete any existing test referencing `machineCode`, `machineName` or `machineOperatorUserId`.

- [ ] **Step 7: Move the event and wire DTO to v4**

In `WasteCollectionEvent.kt`: change `SCHEMA_VERSION` to `4`, replace the three machine properties in the data class with `val jobNumber: String` and `val operatorId: String` (keeping `bagCode` where it is), and replace `create()` and `toWireMessage()`:

```kotlin
        fun create(
            bagCode: String,
            jobNumber: String,
            operatorId: String,
            wasteTypeCode: String,
            collectedBy: String,
            deviceId: String,
            operatorSessionId: String,
            now: Instant = Instant.now(),
        ): WasteCollectionEvent = WasteCollectionEvent(
            messageId = UUID.randomUUID().toString(),
            deviceId = deviceId.trim(),
            operatorSessionId = operatorSessionId.trim(),
            collectionId = generateCollectionId(now),
            bagCode = bagCode.trim(),
            jobNumber = jobNumber.trim(),
            operatorId = operatorId.trim(),
            wasteTypeCode = wasteTypeCode.trim(),
            collectedBy = collectedBy.trim(),
            collectedAtUtc = TIMESTAMP_FORMATTER.format(now),
        )
```

```kotlin
    fun toWireMessage(): WasteCollectionMessage = WasteCollectionMessage(
        schemaVersion = SCHEMA_VERSION,
        messageId = messageId,
        deviceId = deviceId,
        operatorSessionId = operatorSessionId,
        collectionId = collectionId,
        bagCode = bagCode,
        jobNumber = jobNumber,
        operatorId = operatorId,
        wasteTypeCode = wasteTypeCode,
        collectedBy = collectedBy,
        collectedAtUtc = collectedAtUtc,
    )
```

Update the class KDoc: the contract path is now
`C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`, the schema
is v4, and note that the category is deliberately absent from the payload.

In `WasteCollectionMessage.kt`, replace the three machine properties with:

```kotlin
    val jobNumber: String,
    val operatorId: String,
```

and change the KDoc's "schema v3" / "MUST be exactly `3`" to `4`.

- [ ] **Step 8: Move the outbox entity to v4**

In `WasteOutboxEntity.kt`, replace `machineCode`, `machineName` and `machineOperatorUserId` with `val jobNumber: String` and `val operatorId: String` in the entity and in both mapper functions (`toEvent()` and `toOutboxEntity()`), keeping every other field and the whole `Status` object unchanged.

Update the KDoc's "schema v3 event" to "schema v4 event".

- [ ] **Step 9: Update the outbox test fixtures**

In `app/src/test/java/com/mitas/ppnam/station4aa/data/mqtt/WasteCollectionResultChannelTest.kt`, in the `storedRow()` helper replace:

```kotlin
        machineCode = "EXT-04",
        machineName = "EXT-04",
        machineOperatorUserId = "MO-00427",
```

with:

```kotlin
        jobNumber = "JOB-2026-0041",
        operatorId = "MO-00427",
```

- [ ] **Step 10: Update the ViewModel**

In `WasteGatheringViewModel.kt`:

Add the constructor parameter after `scanEventBus`:

```kotlin
    private val catalogueRepository: WasteCatalogueRepository,
```

Add the imports for `WasteCatalogueRepository`, `WasteCategory`, `WasteType`, `kotlinx.coroutines.flow.flatMapLatest`, `kotlinx.coroutines.flow.flowOf` and `kotlinx.coroutines.ExperimentalCoroutinesApi`; remove the `WasteTypeCatalog` import.

Add the catalogue-backed flows next to the other StateFlows:

```kotlin
    /** Categories offered on SELECT_CATEGORY, straight from the cached catalogue. */
    val categories: StateFlow<List<WasteCategory>> = catalogueRepository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Types offered on SELECT_WASTE_TYPE — only those in the chosen category. Empty until a
     * category is chosen, which is exactly when the step is unreachable. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val typesForSelectedCategory: StateFlow<List<WasteType>> = _draft
        .flatMapLatest { current ->
            current.category?.let { catalogueRepository.typesFor(it.code) } ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

Replace the four step handlers with:

```kotlin
    fun onBagCodeSubmitted(raw: String) {
        syncFromController(wizardController.submitBagCode(raw))
    }

    fun onJobNumberSubmitted(raw: String) {
        syncFromController(wizardController.submitJobNumber(raw))
    }

    fun onOperatorIdSubmitted(raw: String) {
        syncFromController(wizardController.submitOperatorId(raw))
    }

    fun onCategoryConfirmed(category: WasteCategory) {
        wizardController.confirmCategory(category)
        syncFromController(null)
    }

    fun onWasteTypeConfirmed(type: WasteType) {
        wizardController.confirmWasteType(type)
        syncFromController(null)
    }

    /** Review-screen correction: jump to one capture step and come back once it is satisfied. */
    fun onEditField(target: WizardStep) {
        wizardController.editField(target)
        syncFromController(null)
    }
```

In `onReviewConfirmed()`, replace the draft destructuring and the `create(...)` call:

```kotlin
        val current = wizardController.draft
        val bagCode = requireNotNull(current.bagCode) { "REVIEW reached without bagCode" }
        val jobNumber = requireNotNull(current.jobNumber) { "REVIEW reached without jobNumber" }
        val operatorId = requireNotNull(current.operatorId) { "REVIEW reached without operatorId" }
        val wasteType = requireNotNull(current.wasteType) { "REVIEW reached without wasteType" }
        val operatorSessionId = requireNotNull(session.value?.operatorSessionId) {
            "REVIEW reached without an active operator session"
        }
```

```kotlin
                val event = WasteCollectionEvent.create(
                    bagCode = bagCode,
                    jobNumber = jobNumber,
                    operatorId = operatorId,
                    wasteTypeCode = wasteType.code,
                    collectedBy = collectedByValue,
                    deviceId = deviceId,
                    operatorSessionId = operatorSessionId,
                )
```

- [ ] **Step 11: Update the screen**

In `WasteGatheringScreen.kt`:

Replace the review dialog's `Column` contents with edit-capable rows:

```kotlin
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConfirmRow("Bag code", draft.bagCode.orEmpty()) {
                        viewModel.onEditField(WizardStep.SCAN_BAG)
                    }
                    ConfirmRow("Job number", draft.jobNumber.orEmpty()) {
                        viewModel.onEditField(WizardStep.SCAN_JOB)
                    }
                    ConfirmRow("Operator ID", draft.operatorId.orEmpty()) {
                        viewModel.onEditField(WizardStep.SCAN_OPERATOR)
                    }
                    ConfirmRow("Waste category", draft.category?.name.orEmpty()) {
                        viewModel.onEditField(WizardStep.SELECT_CATEGORY)
                    }
                    ConfirmRow("Waste type", draft.wasteType?.name.orEmpty()) {
                        viewModel.onEditField(WizardStep.SELECT_WASTE_TYPE)
                    }
                    ConfirmRow("Wastage operator", collectedBy)
                    if (stepError != null) {
                        Text(stepError!!, style = MaterialTheme.typography.labelSmall, color = WarningOrange)
                    }
                }
```

Replace `ConfirmRow`:

```kotlin
@Composable
private fun ConfirmRow(label: String, value: String, onEdit: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        }
        if (onEdit != null) {
            TextButton(onClick = onEdit) { Text("Edit", color = AmberPrimary) }
        }
    }
}
```

Collect the catalogue flows alongside the other `collectAsState()` calls:

```kotlin
    val categories by viewModel.categories.collectAsState()
    val wasteTypes by viewModel.typesForSelectedCategory.collectAsState()
```

Replace the `when (step)` block:

```kotlin
            when (step) {
                WizardStep.SCAN_BAG -> ScanStep(
                    label = "Scan bag code",
                    errorMessage = stepError,
                    onSubmit = viewModel::onBagCodeSubmitted,
                )
                WizardStep.SCAN_JOB -> ScanStep(
                    label = "Scan or enter the job number",
                    errorMessage = stepError,
                    onSubmit = viewModel::onJobNumberSubmitted,
                )
                WizardStep.SCAN_OPERATOR -> ScanStep(
                    label = "Scan or enter the operator ID",
                    errorMessage = stepError,
                    onSubmit = viewModel::onOperatorIdSubmitted,
                )
                WizardStep.SELECT_CATEGORY -> CatalogueStep(
                    title = "Select waste category",
                    emptyMessage = "No waste categories available. Refresh the catalogue in Settings.",
                    label = "Waste Category",
                    options = categories,
                    display = { it.name },
                    onConfirm = viewModel::onCategoryConfirmed,
                )
                WizardStep.SELECT_WASTE_TYPE -> CatalogueStep(
                    title = "Select waste type",
                    emptyMessage = "No waste types in this category. Refresh the catalogue in Settings.",
                    label = "Waste Type",
                    options = wasteTypes,
                    display = { "${it.code} — ${it.name}" },
                    onConfirm = viewModel::onWasteTypeConfirmed,
                )
                WizardStep.REVIEW -> Unit // rendered as the AlertDialog above
            }
```

Replace `WIZARD_STEP_ORDINALS`, `StepIndicator`'s `when`, and its text:

```kotlin
private val WIZARD_STEP_ORDINALS = mapOf(
    WizardStep.SCAN_BAG to 1,
    WizardStep.SCAN_JOB to 2,
    WizardStep.SCAN_OPERATOR to 3,
    WizardStep.SELECT_CATEGORY to 4,
    WizardStep.SELECT_WASTE_TYPE to 5,
    WizardStep.REVIEW to 5,
)

@Composable
private fun StepIndicator(step: WizardStep) {
    val label = when (step) {
        WizardStep.SCAN_BAG -> "Scan bag code"
        WizardStep.SCAN_JOB -> "Scan or enter the job number"
        WizardStep.SCAN_OPERATOR -> "Scan or enter the operator ID"
        WizardStep.SELECT_CATEGORY -> "Select waste category"
        WizardStep.SELECT_WASTE_TYPE -> "Select waste type"
        WizardStep.REVIEW -> "Review and confirm"
    }
    Text(
        "Step ${WIZARD_STEP_ORDINALS.getValue(step)} of 5 — $label",
        style = MaterialTheme.typography.labelLarge,
        color = AmberPrimary,
    )
}
```

Replace `WasteTypeStep` with a generic catalogue step, and rename `EnumDropdownSelector` to `DropdownSelector` (its body is unchanged; update its one call site inside `CatalogueStep`):

```kotlin
/**
 * One selection step driven by the cached catalogue. Renders an explicit empty state rather than
 * an empty dropdown: a handheld whose catalogue failed to sync must say so, not present a control
 * that silently does nothing.
 */
@Composable
private fun <T> CatalogueStep(
    title: String,
    emptyMessage: String,
    label: String,
    options: List<T>,
    display: (T) -> String,
    onConfirm: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        if (options.isEmpty()) {
            Text(emptyMessage, style = MaterialTheme.typography.labelMedium, color = WarningOrange)
            return@Column
        }
        var selectedIndex by remember(options) { mutableStateOf(0) }
        DropdownSelector(
            label = label,
            options = options,
            selected = options[selectedIndex],
            display = display,
            onSelected = { selectedIndex = options.indexOf(it) },
        )
        Button(
            onClick = { onConfirm(options[selectedIndex]) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Confirm")
        }
    }
}
```

Remove the now-unused `WasteTypeCatalog` import.

- [ ] **Step 12: Run the full suite**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS. `WasteGatheringViewModel` gained a `catalogueRepository` parameter, so its construction site — `app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt:58-66` — will not compile. Keep the build green by adding one argument there now (the rest of the wiring is Task 7):

```kotlin
                            scanEventBus = container.scanEventBus,
                            catalogueRepository = container.wasteCatalogueRepository,
                            deviceId = container.deviceId,
```

This requires `AppContainer.wasteCatalogueRepository` to exist. If Task 7 has not run yet, add just that one line to `AppContainer` now:

```kotlin
    val wasteCatalogueRepository = WasteCatalogueRepository(outboxDatabase.wasteCatalogueDao())
```

- [ ] **Step 13: Verify the app assembles**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 14: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Rework the wizard to the Phase 1 flow and publish schema v4"
```

---

### Task 7: Wire the catalogue into the app and trigger syncs

**Files:**
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/data/AppContainer.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringViewModel.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt` (only if it constructs `WasteGatheringViewModel`)

**Interfaces:**
- Consumes: `WasteCatalogueRepository` (Task 3), `SyncWasteCatalogueUseCase` (Task 4).
- Produces: `AppContainer.wasteCatalogueRepository`, `AppContainer.syncWasteCatalogueUseCase`.

- [ ] **Step 1: Expose the catalogue from AppContainer**

In `AppContainer.kt`, add the imports for `WasteCatalogueRepository` and `SyncWasteCatalogueUseCase`, then after the existing `outboxDatabase` declaration add:

```kotlin
    val wasteCatalogueRepository = WasteCatalogueRepository(outboxDatabase.wasteCatalogueDao())
```

and after `requestChannel` is declared add:

```kotlin
    val syncWasteCatalogueUseCase = SyncWasteCatalogueUseCase(
        requestChannel = requestChannel,
        repository = wasteCatalogueRepository,
        deviceId = deviceId,
    )
```

- [ ] **Step 2: Seed the catalogue on first launch**

Still in `AppContainer.kt`, add a coroutine scope and an init block at the end of the class:

```kotlin
    // Seeding touches disk, so it cannot run on the constructor's thread. Fire-and-forget: a
    // handheld whose seed has not landed yet shows an empty selection step with its own explicit
    // message, which is honest, rather than blocking startup on a database write.
    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        containerScope.launch { wasteCatalogueRepository.seedIfEmpty() }
    }
```

with imports for `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.SupervisorJob` and `kotlinx.coroutines.launch`.

- [ ] **Step 3: Pass the repository and sync use case to the ViewModel**

Add `private val syncCatalogue: SyncWasteCatalogueUseCase,` to `WasteGatheringViewModel`'s constructor, next to the `catalogueRepository` parameter added in Task 6.

Then update the initializer at `app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt:58-66` so it reads:

```kotlin
                        WasteGatheringViewModel(
                            settingsRepository = container.settingsRepository,
                            connectionManager = container.connectionManager,
                            publisher = container.wasteCollectionPublisher,
                            sessionHolder = container.operatorSessionHolder,
                            authUseCase = container.authUseCase,
                            scanEventBus = container.scanEventBus,
                            catalogueRepository = container.wasteCatalogueRepository,
                            syncCatalogue = container.syncWasteCatalogueUseCase,
                            deviceId = container.deviceId,
                        )
```

- [ ] **Step 4: Trigger a sync on session start and on reconnect**

In `WasteGatheringViewModel`'s `init` block, add:

```kotlin
        // Refresh the catalogue whenever we have both a session and a live broker link — that
        // covers first login and every reconnect. Failure is deliberately silent here: the cached
        // or seeded catalogue stays usable and Settings → Diagnostics is where staleness shows.
        viewModelScope.launch {
            combine(
                connectionManager.connectionState,
                sessionHolder.session,
            ) { state, activeSession -> state to activeSession }
                .filter { (state, activeSession) ->
                    state == MqttConnectionState.CONNECTED && activeSession != null
                }
                .collect { (_, activeSession) ->
                    syncCatalogue.sync(activeSession!!.operatorSessionId)
                }
        }
```

with an import for `kotlinx.coroutines.flow.combine`.

- [ ] **Step 5: Verify the build and full suite**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`

Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mitas/ppnam/station4aa/data/AppContainer.kt \
        app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringViewModel.kt \
        app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt
git commit -m "Seed the catalogue on first launch and refresh it on login and reconnect"
```

---

### Task 8: Catalogue state and manual refresh in Diagnostics

**Files:**
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/CatalogueStatus.kt`
- Test: `app/src/test/java/com/mitas/ppnam/station4aa/ui/settings/CatalogueStatusTest.kt`

**Interfaces:**
- Consumes: `CatalogueMeta`, `CatalogueSource` (Task 1), `WasteCatalogueRepository` (Task 3), `SyncWasteCatalogueUseCase` (Task 4).
- Produces: `fun describeCatalogue(meta: CatalogueMeta?): String`; `SettingsViewModel.catalogueStatus: StateFlow<String>`; `SettingsViewModel.refreshCatalogue()`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/mitas/ppnam/station4aa/ui/settings/CatalogueStatusTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.ui.settings.CatalogueStatusTest"`

Expected: FAIL — compilation error, `describeCatalogue` is unresolved.

- [ ] **Step 3: Implement the description**

Create `app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/CatalogueStatus.kt`:

```kotlin
package com.mitas.ppnam.station4aa.ui.settings

import com.mitas.ppnam.station4aa.domain.model.CatalogueMeta
import com.mitas.ppnam.station4aa.domain.model.CatalogueSource

/**
 * One honest line about where the cached catalogue came from, for Settings → Diagnostics. Pure, so
 * it is tested without Android.
 */
fun describeCatalogue(meta: CatalogueMeta?): String {
    if (meta == null) return "Catalogue: not loaded"
    val base = when (meta.source) {
        CatalogueSource.SEED -> "Catalogue: built-in seed — never synced"
        CatalogueSource.SYNCED ->
            "Catalogue: ${meta.catalogueVersion} — synced ${meta.syncedAtUtc}"
    }
    return meta.lastFailedAtUtc
        ?.let { "$base, last refresh failed $it" }
        ?: base
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.ui.settings.CatalogueStatusTest"`

Expected: PASS, 5 tests.

- [ ] **Step 5: Expose it from SettingsViewModel**

Add two constructor parameters to `SettingsViewModel`:

```kotlin
    private val catalogueRepository: WasteCatalogueRepository,
    private val syncCatalogue: SyncWasteCatalogueUseCase,
```

Add the status flow and the refresh action:

```kotlin
    val catalogueStatus: StateFlow<String> = catalogueRepository.meta
        .map(::describeCatalogue)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Catalogue: not loaded")

    /** Manual "Refresh catalogue". Requires an active session, because the request carries the
     * operatorSessionId Station 4 authorizes against. */
    fun refreshCatalogue() {
        val activeSession = session.value
        if (activeSession == null) {
            applyState.value = ApplyState.Failure("Log in before refreshing the catalogue")
            return
        }
        viewModelScope.launch {
            applyState.value = ApplyState.Testing
            applyState.value = when (val result = syncCatalogue.sync(activeSession.operatorSessionId)) {
                is CatalogueSyncResult.Replaced ->
                    ApplyState.Success("Catalogue updated — ${result.typeCount} waste types")
                is CatalogueSyncResult.Failed ->
                    ApplyState.Failure("Catalogue refresh failed: ${result.reason}")
            }
        }
    }
```

with imports for `kotlinx.coroutines.flow.map`, `WasteCatalogueRepository`, `SyncWasteCatalogueUseCase` and `CatalogueSyncResult`.

Then update the initializer at `app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt:79-85` so it reads:

```kotlin
                        SettingsViewModel(
                            settingsRepository = container.settingsRepository,
                            connectionManager = container.connectionManager,
                            sessionHolder = container.operatorSessionHolder,
                            authUseCase = container.authUseCase,
                            catalogueRepository = container.wasteCatalogueRepository,
                            syncCatalogue = container.syncWasteCatalogueUseCase,
                            deviceId = container.deviceId,
                        )
```

- [ ] **Step 6: Add the Diagnostics row**

In `SettingsScreen.kt`, inside the existing Diagnostics card that renders the read-only device id, add below it:

```kotlin
            val catalogueStatus by viewModel.catalogueStatus.collectAsState()
            Text(
                catalogueStatus,
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
            TextButton(onClick = { viewModel.refreshCatalogue() }) {
                Text("Refresh catalogue", color = AmberPrimary)
            }
```

Match the imports and colour constants already used in that file.

- [ ] **Step 7: Verify the build and full suite**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`

Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/ \
        app/src/test/java/com/mitas/ppnam/station4aa/ui/settings/
git commit -m "Show catalogue provenance and add a manual refresh in Diagnostics"
```

---

### Task 9: Remove the dead machine-era code and verify

**Files:**
- Delete: `app/src/main/java/com/mitas/ppnam/station4aa/domain/model/WasteTypeCatalog.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/domain/validation/WasteCollectionValidator.kt`
- Modify: `app/src/test/java/com/mitas/ppnam/station4aa/domain/validation/WasteCollectionValidatorTest.kt`

- [ ] **Step 1: Confirm nothing still references the old catalogue or validators**

Run:

```bash
grep -rn "WasteTypeCatalog\|validateMachineCode\|validateMachineOperatorUserId\|machineOperatorUserId\|machineCode\|machineName" app/src --include=*.kt
```

Expected: no matches in `app/src/main`. Any match in `app/src/test` is a stale test to delete in the next step. If a match appears in `app/src/main`, a previous task is incomplete — fix it there rather than here.

- [ ] **Step 2: Delete the dead code**

```bash
rm app/src/main/java/com/mitas/ppnam/station4aa/domain/model/WasteTypeCatalog.kt
```

In `WasteCollectionValidator.kt`, delete `validateMachineCode`, `validateMachineOperatorUserId`, and the now-unused `MACHINE_CODE_MAX_LENGTH` and `MACHINE_OPERATOR_ID_MAX_LENGTH` constants.

In `WasteCollectionValidatorTest.kt`, delete every test that calls either removed function.

- [ ] **Step 3: Run the full suite**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS. Confirm the summary shows the expected suites, including `WasteCatalogueSeedTest`, `WasteCatalogueRepositoryTest`, `SyncWasteCatalogueUseCaseTest`, `WasteWizardControllerTest` and `CatalogueStatusTest`.

- [ ] **Step 4: Verify the release build**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Refresh the knowledge graph**

Run: `graphify update .`

- [ ] **Step 6: Commit**

```bash
git add -A app/src graphify-out
git commit -m "Remove the machine-era waste type catalogue and validators"
```

---

## Verification checklist

Run after Task 9. Every item must hold before this work is considered complete.

- [ ] `./gradlew :app:testDebugUnitTest` passes with no skipped suites.
- [ ] `./gradlew :app:assembleDebug` succeeds.
- [ ] `grep -rn "machineCode\|machineName\|machineOperatorUserId\|WasteTypeCatalog" app/src --include=*.kt` returns nothing.
- [ ] `grep -rn "wasteCategoryCode" app/src --include=*.kt` returns nothing — the category must not have leaked onto the wire.
- [ ] `WasteCollectionEvent.SCHEMA_VERSION` is `4` and `WasteCollectionMessage` has exactly the 11 v4 fields listed in Global Constraints.
- [ ] `WasteWizardController.kt` imports nothing from `android.*`, `androidx.*`, `data.local.*` or `data.mqtt.*`.
- [ ] On a fresh install with no broker, the wizard offers 18 waste types under "Uncategorised" and Diagnostics reads `Catalogue: built-in seed — never synced`.
