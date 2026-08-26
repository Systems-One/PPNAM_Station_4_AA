# Waste Collection Result Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align this handheld with the sister repo's `50841ac` update ("Migrate from single-use Bag ID to reusable Bag Code schema v3") — consume Station 4's new direct `waste_collection_result` response instead of treating broker PUBACK as business acceptance, and make the MQTT collection topic Settings-configurable to match Station 4's own Settings-driven topic.

**Architecture:** A new `WasteCollectionResultChannel` subscribes once per device to the exact, deterministic result topic (`PPNAM/station4/{deviceId}/res/waste_collection_result` — not a wildcard, since the contract names one exact topic), parses each `waste_collection_result`, verifies its echoed identity against the matching durable outbox row, and applies the terminal outcome (`ACCEPTED`/`REJECTED`) to that row. `WasteCollectionPublisher` gains this channel as a dependency and stops marking rows terminal on PUBACK — a row now stays `PENDING` (and therefore retried on reconnect) until a correlated result arrives, matching the contract's "retry regardless of PUBACK" rule. `WasteGatheringViewModel` surfaces a rejection to the operator through the existing "Queued …" banner slot, reusing it for "Rejected …" too rather than adding new UI surface.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), AndroidX ViewModel/StateFlow, Room (local outbox), Gson (wire JSON, via the existing `WireJson.gson` used by every other inbound DTO), JUnit4.

## Global Constraints

- Normative contract (updated, read-only reference): `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`, document version 3.0.0, commit `50841ac` in that repo. Unlike the previous revision, this version's §9 body is internally consistent with its §1/identity table — no stale text to work around this time.
- Result topic: exact, not a wildcard — `PPNAM/station4/{deviceId}/res/waste_collection_result` (contract §3). Distinct from the auth login exchange's `PPNAM/{deviceId}/req|res/...` pattern (no `station4` segment, ported from Station 2) — do not conflate the two topic families or their subscriptions.
- `WasteCollectionResultMessage` wire fields (contract §12, confirmed against the real `PPNAM.Station4.Core/Models/Station4Models.cs`'s `WastageCollectionResultMessage`): `schemaVersion` (Int, always `3`), `messageId`, `inResponseToMessageId`, `deviceId`, `operatorSessionId`, `timestampUtc`, `collectionId`, `bagCode`, `accepted` (Bool), `isDuplicate` (Bool), `collectionStatus`, `errorCode` (nullable, omitted on success), `reason` (nullable, omitted on success), `nextAction`. This does **not** reuse the existing `ResponseEnvelope` DTO — `ResponseEnvelope.schemaVersion` is a `String` (`"4.1"`), the result's `schemaVersion` is an `Int` (`3`); the field sets otherwise diverge too (`collectionId`/`bagCode`/`isDuplicate`/`collectionStatus`/`nextAction` have no `ResponseEnvelope` equivalent).
- PUBACK is never a terminal/business-acceptance signal (contract §12, unchanged principle, now with a real mechanism behind it) — only a correlated `waste_collection_result` with `accepted: true` is acceptance. A `REJECTED` row must never be retried; the contract requires a **new** transaction (new `messageId`/`collectionId`) instead. An `ACCEPTED`/`REJECTED` outbox row is terminal and permanently excluded from `getPending()`/retry.
- The collection topic is now Settings-configurable on the Station 4 side (default unchanged: `station4/waste/collection`) — this handheld must publish to a matching, independently configurable topic, not a hardcoded constant. Configurable means round-tripped through `SettingsRepository`'s DataStore persistence, not just held in the in-memory `AppSettings` draft — a field only wired into the UI without a backing store key silently discards whatever the operator types.
- Result-`messageId` deduplication (acceptance criterion 29) is satisfied structurally, not with an explicit tracked-IDs set: correlation looks up the outbox row by `inResponseToMessageId` (the original request's `messageId`, the row's primary key) rather than by the response's own `messageId`, and applying the same terminal outcome twice to an already-terminal row is idempotent. A genuinely duplicate response therefore can't double-apply, without needing to remember every response `messageId` ever seen.
- Non-goals (explicitly out of scope for this plan): no per-`nextAction` navigation/automation (e.g. auto-redirect to Login on `nextAction: "login"`) — display it as text only; no new timer-driven retry beyond the existing reconnect-triggered `retryPending()` (already covers both "app was killed mid-flight" via the first `CONNECTED` transition on launch, and "connection dropped" via reconnect); no change to the wizard's scan/step UI (`WasteWizardController`, `WasteGatheringScreen`'s step flow) — this plan only touches the publish/result pipeline beneath it.
- Design spec/prior plan for context (unaffected by this plan, still accurate): `docs/superpowers/specs/2026-08-05-scan-driven-waste-wizard-design.md`, `docs/superpowers/plans/2026-08-05-scan-driven-waste-wizard.md`.

---

## Task 1: WasteCollectionResultMessage wire DTO

**Status: complete** (worktree commit `d341fdd`). Left below unchanged as a record; do not re-run.

**Files:**
- Create: `app/src/main/java/com/ppnam/station4aa/data/mqtt/dto/WasteCollectionResultMessage.kt`
- Test: `app/src/test/java/com/ppnam/station4aa/data/mqtt/dto/WasteCollectionResultMessageTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `WasteCollectionResultMessage` data class with the exact wire field set from Global Constraints. Used by Task 4 (`WasteCollectionResultChannel`) to parse inbound results.

- [x] **Step 1: Write the failing test**

Create `app/src/test/java/com/ppnam/station4aa/data/mqtt/dto/WasteCollectionResultMessageTest.kt`:

```kotlin
package com.mitas.ppnam.station4aa.data.mqtt.dto

import com.mitas.ppnam.station4aa.data.mqtt.WireJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WasteCollectionResultMessageTest {

    @Test
    fun `parses a successful result exactly as Station4 publishes it`() {
        val json = """
            {
              "schemaVersion": 3,
              "messageId": "fe3e4ee4d73c49d393c6cc1bb194c1e1",
              "inResponseToMessageId": "01K1F4Y2C8E7K1R6DT5MAB9P3Q",
              "deviceId": "HH-01",
              "operatorSessionId": "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
              "timestampUtc": "2026-07-30T10:15:30.125000Z",
              "collectionId": "01K1F4XZS92T3V7A6Q8C0N5JHE",
              "bagCode": "BAG-01",
              "accepted": true,
              "isDuplicate": false,
              "collectionStatus": "AwaitingWeight",
              "nextAction": "start_next_collection"
            }
        """.trimIndent()

        val result = WireJson.gson.fromJson(json, WasteCollectionResultMessage::class.java)

        assertEquals(3, result.schemaVersion)
        assertEquals("fe3e4ee4d73c49d393c6cc1bb194c1e1", result.messageId)
        assertEquals("01K1F4Y2C8E7K1R6DT5MAB9P3Q", result.inResponseToMessageId)
        assertEquals("HH-01", result.deviceId)
        assertEquals("4dfda8bb-e9bf-4e92-b8a9-acde673fbb83", result.operatorSessionId)
        assertEquals("01K1F4XZS92T3V7A6Q8C0N5JHE", result.collectionId)
        assertEquals("BAG-01", result.bagCode)
        assertTrue(result.accepted)
        assertEquals(false, result.isDuplicate)
        assertEquals("AwaitingWeight", result.collectionStatus)
        assertNull(result.errorCode)
        assertNull(result.reason)
        assertEquals("start_next_collection", result.nextAction)
    }

    @Test
    fun `parses a rejected result including errorCode and reason`() {
        val json = """
            {
              "schemaVersion": 3,
              "messageId": "d7db43bd39d84df2bb1f8d8fb2a0feeb",
              "inResponseToMessageId": "01K1F50N46H9VEK2D7SAB3M8QW",
              "deviceId": "HH-01",
              "operatorSessionId": "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
              "timestampUtc": "2026-07-30T10:16:04.830000Z",
              "collectionId": "01K1F50MZZR4P0B1X6G2K8HV9T",
              "bagCode": "BAG-01",
              "accepted": false,
              "isDuplicate": false,
              "collectionStatus": "Rejected",
              "errorCode": "bag_code_in_use",
              "reason": "Bag code 'BAG-01' is already awaiting weight for collection '01K1F4XZS92T3V7A6Q8C0N5JHE'.",
              "nextAction": "complete_existing_bag_weight"
            }
        """.trimIndent()

        val result = WireJson.gson.fromJson(json, WasteCollectionResultMessage::class.java)

        assertEquals(false, result.accepted)
        assertEquals("Rejected", result.collectionStatus)
        assertEquals("bag_code_in_use", result.errorCode)
        assertEquals(
            "Bag code 'BAG-01' is already awaiting weight for collection '01K1F4XZS92T3V7A6Q8C0N5JHE'.",
            result.reason,
        )
        assertEquals("complete_existing_bag_weight", result.nextAction)
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessageTest"`
Expected: FAIL to compile — `WasteCollectionResultMessage` does not exist yet.

- [x] **Step 3: Create WasteCollectionResultMessage**

`app/src/main/java/com/ppnam/station4aa/data/mqtt/dto/WasteCollectionResultMessage.kt`:

```kotlin
package com.mitas.ppnam.station4aa.data.mqtt.dto

/**
 * Wire shape of the direct application-level response to a schema v3 collection publish, per
 * `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md` §12 — confirmed against the real
 * `PPNAM.Station4.Core/Models/Station4Models.cs`'s `WastageCollectionResultMessage`. Published by
 * Station 4 on `PPNAM/station4/{deviceId}/res/waste_collection_result` (QoS 1, retain false) for
 * every structurally valid collection request, after the accept/quarantine outcome is durable.
 *
 * This is a distinct DTO from [ResponseEnvelope] — [schemaVersion] here is the collection schema's
 * JSON integer (`3`), not the authentication envelope's version string (`"4.1"`), and this carries
 * fields ([collectionId], [bagCode], [isDuplicate], [collectionStatus], [nextAction]) that have no
 * `ResponseEnvelope` equivalent.
 *
 * Every constructor parameter keeps a default so Gson never falls back to `UnsafeAllocator` — see
 * [ResponseEnvelope]'s class doc for why that matters.
 */
data class WasteCollectionResultMessage(
    val schemaVersion: Int = 0,
    val messageId: String = "",
    val inResponseToMessageId: String = "",
    val deviceId: String = "",
    val operatorSessionId: String = "",
    val timestampUtc: String = "",
    val collectionId: String = "",
    val bagCode: String = "",
    val accepted: Boolean = false,
    val isDuplicate: Boolean = false,
    val collectionStatus: String = "",
    /** Omitted by Station 4 on success. */
    val errorCode: String? = null,
    /** Omitted by Station 4 on success. */
    val reason: String? = null,
    val nextAction: String = "",
)
```

- [x] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessageTest"`
Expected: PASS, both tests.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/ppnam/station4aa/data/mqtt/dto/WasteCollectionResultMessage.kt app/src/test/java/com/ppnam/station4aa/data/mqtt/dto/WasteCollectionResultMessageTest.kt
git commit -m "feat: add WasteCollectionResultMessage wire DTO for the schema v3 result topic"
```

---

## Task 2: Make the collection topic Settings-configurable

**Status: Steps 1-4 below cover the field and UI (worktree commit `ed8e490`) and are done — do not re-run them. A task review then caught that `SettingsRepository.kt` (not originally in this task's file list) never persists or reloads the new field, silently discarding whatever an operator types into it — an independent automated security review flagged the same gap. Steps 5-6 below are the outstanding correction; do not skip them.**

**Files:**
- Modify: `app/src/main/java/com/ppnam/station4aa/domain/model/AppSettings.kt` (done)
- Modify: `app/src/main/java/com/ppnam/station4aa/ui/settings/SettingsScreen.kt` (done)
- Modify: `app/src/main/java/com/ppnam/station4aa/data/settings/SettingsRepository.kt` (outstanding — Steps 5-6)

**Interfaces:**
- Consumes: nothing new.
- Produces: `AppSettings.wasteCollectionTopic: String` (default `"station4/waste/collection"`, matching `MqttTopics.WASTE_COLLECTION`'s existing value), actually persisted and reloaded through `SettingsRepository`. Used by Task 5 (`WasteCollectionPublisher`, reading it at publish time instead of the hardcoded constant).

No dedicated test — this repo has no existing test coverage for `AppSettings`' other fields (`mqttHost`, `mqttPort`, etc.), `SettingsScreen`'s other text fields, or `SettingsRepository`'s other DataStore mappings; this follows the same untested-plumbing precedent.

- [x] **Step 1: Add the field to AppSettings** (done)

In `AppSettings.kt`, add `wasteCollectionTopic` as a new field (after `deviceId`, before the MQTT connection fields):

```kotlin
data class AppSettings(
    val deviceId: String = "station4_handheld_1",
    val wasteCollectionTopic: String = "station4/waste/collection",
    val mqttHost: String = "ppnam-mqtt",
    val mqttPort: Int = 1883,
    val mqttUseWebSocket: Boolean = false,
    val mqttUseTls: Boolean = false,
    val mqttUsername: String = "",
    val mqttPassword: String = "",
) {
    /** True once this handheld has been provisioned with its own broker credential. */
    val hasBrokerCredential: Boolean
        get() = mqttUsername.isNotBlank() && mqttPassword.isNotBlank()
}
```

Also update the class doc comment's broker-credential paragraph is unaffected; add one sentence above the data class referencing the new field:

```kotlin
 * [wasteCollectionTopic] is the exact, deployment-configured MQTT topic this handheld publishes
 * waste-collection events to — the contract's default (`station4/waste/collection`) is this
 * field's default, but a Station 4 deployment MAY reconfigure it in its own Settings, and this
 * handheld MUST be reconfigured to match (`Station4_Wastage_MQTT_Contract.md` §3/§9).
```

(Insert that paragraph into the existing class doc comment, before the closing `*/`.)

- [x] **Step 2: Add the Settings UI field** (done)

In `SettingsScreen.kt`, add a new field to the existing `ConfigSection(title = "Station")` block (right after the Device ID field):

```kotlin
                    ConfigSection(title = "Station") {
                        SettingsTextField(
                            value = draft.deviceId,
                            label = "Device ID",
                            onValueChange = { viewModel.updateDraft(draft.copy(deviceId = it)) }
                        )
                        SettingsTextField(
                            value = draft.wasteCollectionTopic,
                            label = "Collection Topic",
                            onValueChange = { viewModel.updateDraft(draft.copy(wasteCollectionTopic = it)) }
                        )
                    }
```

- [x] **Step 3: Build to confirm it compiles** (done)

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Commit** (done, commit `ed8e490`)

```bash
git add app/src/main/java/com/ppnam/station4aa/domain/model/AppSettings.kt app/src/main/java/com/ppnam/station4aa/ui/settings/SettingsScreen.kt
git commit -m "feat: make the MQTT collection topic Settings-configurable"
```

- [ ] **Step 5: Wire wasteCollectionTopic through SettingsRepository**

In `SettingsRepository.kt`, add a new DataStore key, read it in `settingsFlow`, and write it in `save()`:

```kotlin
    private object Keys {
        val DEVICE_ID              = stringPreferencesKey("device_id")
        val WASTE_COLLECTION_TOPIC = stringPreferencesKey("waste_collection_topic")
        val MQTT_HOST              = stringPreferencesKey("mqtt_host")
        val MQTT_PORT              = intPreferencesKey("mqtt_port")
        val MQTT_USE_WEBSOCKET     = booleanPreferencesKey("mqtt_use_websocket")
        val MQTT_USE_TLS           = booleanPreferencesKey("mqtt_use_tls")
        val MQTT_USERNAME          = stringPreferencesKey("mqtt_username")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            deviceId             = prefs[Keys.DEVICE_ID]              ?: defaults.deviceId,
            wasteCollectionTopic = prefs[Keys.WASTE_COLLECTION_TOPIC] ?: defaults.wasteCollectionTopic,
            mqttHost             = prefs[Keys.MQTT_HOST]              ?: defaults.mqttHost,
            mqttPort             = prefs[Keys.MQTT_PORT]              ?: defaults.mqttPort,
            mqttUseWebSocket     = prefs[Keys.MQTT_USE_WEBSOCKET]     ?: defaults.mqttUseWebSocket,
            mqttUseTls           = prefs[Keys.MQTT_USE_TLS]           ?: defaults.mqttUseTls,
            // No `?: "admin"`. An unprovisioned handheld reports no credential rather than
            // silently presenting a shared one — see AppSettings.hasBrokerCredential.
            mqttUsername         = prefs[Keys.MQTT_USERNAME].orEmpty(),
            mqttPassword         = credentialStore.retrieve().orEmpty(),
        )
    }
```

```kotlin
    suspend fun save(settings: AppSettings) {
        // The password goes to the Keystore first: if that fails we must not leave the app
        // believing it saved a credential it cannot retrieve.
        if (settings.mqttPassword.isNotBlank()) {
            credentialStore.store(settings.mqttPassword)
        }
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVICE_ID]              = settings.deviceId
            prefs[Keys.WASTE_COLLECTION_TOPIC] = settings.wasteCollectionTopic
            prefs[Keys.MQTT_HOST]              = settings.mqttHost
            prefs[Keys.MQTT_PORT]              = settings.mqttPort
            prefs[Keys.MQTT_USE_WEBSOCKET]     = settings.mqttUseWebSocket
            prefs[Keys.MQTT_USE_TLS]           = settings.mqttUseTls
            prefs[Keys.MQTT_USERNAME]          = settings.mqttUsername
        }
    }
```

(Only `Keys`, `settingsFlow`, and `save()` change — the class doc comment, `current()`, `isProvisioned()`, and `clearCredential()` stay as they are in the file today.)

- [ ] **Step 6: Build to confirm it compiles, then commit**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/ppnam/station4aa/data/settings/SettingsRepository.kt
git commit -m "fix: persist wasteCollectionTopic through SettingsRepository instead of discarding it"
```

---

## Task 3: Outbox schema — terminal ACCEPTED/REJECTED statuses and result fields

**Files:**
- Modify: `app/src/main/java/com/ppnam/station4aa/data/local/WasteOutboxEntity.kt`
- Modify: `app/src/main/java/com/ppnam/station4aa/data/local/WasteOutboxDao.kt`
- Modify: `app/src/main/java/com/ppnam/station4aa/data/local/WasteOutboxDatabase.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `WasteOutboxEntity.errorCode/reason/nextAction: String?` columns; `WasteOutboxEntity.Status.ACCEPTED`/`Status.REJECTED` (replacing `Status.DELIVERED`, which is deleted — PUBACK alone is no longer a status this app tracks); `WasteOutboxDao.findByMessageId(messageId): WasteOutboxEntity?`, `WasteOutboxDao.markAccepted(messageId)`, `WasteOutboxDao.markRejected(messageId, errorCode, reason, nextAction)` (replacing the deleted `markDelivered`). Used by Task 4 (`WasteCollectionResultChannel`) and Task 5 (`WasteCollectionPublisher`, which stops calling the deleted `markDelivered`).

No dedicated automated test, consistent with Task 4 of the prior plan's precedent for this same file (no Room DAO/migration test infrastructure in this repo, `exportSchema = false`). Correctness is exercised by Task 4's `WasteCollectionResultChannel` tests (which construct `WasteOutboxEntity` directly — a plain annotated data class, not requiring the Android runtime) and Task 8's full build.

- [ ] **Step 1: Update WasteOutboxEntity**

Replace the entity and its two mapping functions in `WasteOutboxEntity.kt`:

```kotlin
@Entity(tableName = "waste_outbox")
data class WasteOutboxEntity(
    @PrimaryKey val messageId: String,
    val deviceId: String,
    val operatorSessionId: String,
    val collectionId: String,
    val bagCode: String,
    val machineCode: String,
    val machineName: String,
    val machineOperatorUserId: String,
    val wasteTypeCode: String,
    val collectedBy: String,
    val collectedAtUtc: String,
    val status: String,
    val createdAtEpochMs: Long,
    val lastAttemptEpochMs: Long?,
    val attemptCount: Int,
    val errorCode: String?,
    val reason: String?,
    val nextAction: String?,
) {
    object Status {
        /** Durably written, awaiting a correlated `waste_collection_result` — retried on every
         * reconnect regardless of whether a prior publish attempt received PUBACK, per the
         * contract's "retry the exact queued event... whether or not it saw PUBACK" rule. */
        const val PENDING = "PENDING"
        /** Terminal: a correlated result with `accepted: true` arrived. Never retried again. */
        const val ACCEPTED = "ACCEPTED"
        /** Terminal: a correlated result with `accepted: false` arrived. Never retried — the
         * contract requires a brand-new transaction (new messageId/collectionId) instead. */
        const val REJECTED = "REJECTED"
    }
}

fun WasteOutboxEntity.toEvent(): WasteCollectionEvent = WasteCollectionEvent(
    messageId = messageId,
    deviceId = deviceId,
    operatorSessionId = operatorSessionId,
    collectionId = collectionId,
    bagCode = bagCode,
    machineCode = machineCode,
    machineName = machineName,
    machineOperatorUserId = machineOperatorUserId,
    wasteTypeCode = wasteTypeCode,
    collectedBy = collectedBy,
    collectedAtUtc = collectedAtUtc,
)

fun WasteCollectionEvent.toOutboxEntity(nowEpochMs: Long): WasteOutboxEntity = WasteOutboxEntity(
    messageId = messageId,
    deviceId = deviceId,
    operatorSessionId = operatorSessionId,
    collectionId = collectionId,
    bagCode = bagCode,
    machineCode = machineCode,
    machineName = machineName,
    machineOperatorUserId = machineOperatorUserId,
    wasteTypeCode = wasteTypeCode,
    collectedBy = collectedBy,
    collectedAtUtc = collectedAtUtc,
    status = WasteOutboxEntity.Status.PENDING,
    createdAtEpochMs = nowEpochMs,
    lastAttemptEpochMs = null,
    attemptCount = 0,
    errorCode = null,
    reason = null,
    nextAction = null,
)
```

(Update the class doc comment's second paragraph — "Mark the queued event as broker-delivered only after..." — to instead describe the result-driven terminal states; keep the rest of the comment as-is.)

- [ ] **Step 2: Update WasteOutboxDao**

Replace `WasteOutboxDao.kt`'s full content:

```kotlin
package com.mitas.ppnam.station4aa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WasteOutboxDao {

    // IGNORE, not REPLACE: messageId is the primary key and the event is immutable once created
    // (contract: reuse the same messageId/payload on every retry). A second insert of the same
    // messageId is exactly that retry path re-queuing, not a new event to replace the old row.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: WasteOutboxEntity)

    @Query("SELECT * FROM waste_outbox WHERE status = 'PENDING' ORDER BY createdAtEpochMs ASC")
    suspend fun getPending(): List<WasteOutboxEntity>

    @Query("SELECT COUNT(*) FROM waste_outbox WHERE status = 'PENDING'")
    fun pendingCount(): Flow<Int>

    @Query("SELECT * FROM waste_outbox WHERE messageId = :messageId LIMIT 1")
    suspend fun findByMessageId(messageId: String): WasteOutboxEntity?

    @Query(
        "UPDATE waste_outbox SET attemptCount = attemptCount + 1, lastAttemptEpochMs = :nowEpochMs " +
            "WHERE messageId = :messageId"
    )
    suspend fun recordAttempt(messageId: String, nowEpochMs: Long)

    @Query("UPDATE waste_outbox SET status = 'ACCEPTED' WHERE messageId = :messageId")
    suspend fun markAccepted(messageId: String)

    @Query(
        "UPDATE waste_outbox SET status = 'REJECTED', errorCode = :errorCode, reason = :reason, " +
            "nextAction = :nextAction WHERE messageId = :messageId"
    )
    suspend fun markRejected(messageId: String, errorCode: String?, reason: String?, nextAction: String?)
}
```

- [ ] **Step 3: Bump the database version**

In `WasteOutboxDatabase.kt`, bump `version` from `2` to `3` (keep the existing `.fallbackToDestructiveMigration()` — same justification as the prior bump: no migration infra exists yet, outbox is a transient in-flight queue):

```kotlin
@Database(entities = [WasteOutboxEntity::class], version = 3, exportSchema = false)
```

(Only the version integer changes — everything else in the file stays as-is.)

- [ ] **Step 4: Build to confirm it compiles**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: FAIL — `WasteCollectionPublisher.kt` (Task 5, not yet updated) still calls the now-deleted `outboxDao.markDelivered(...)`. This is expected; Task 5 fixes it. Confirm the *only* compile error is in `WasteCollectionPublisher.kt`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ppnam/station4aa/data/local/WasteOutboxEntity.kt app/src/main/java/com/ppnam/station4aa/data/local/WasteOutboxDao.kt app/src/main/java/com/ppnam/station4aa/data/local/WasteOutboxDatabase.kt
git commit -m "feat: replace PUBACK-driven DELIVERED status with result-driven ACCEPTED/REJECTED"
```

---

## Task 4: WasteCollectionResultChannel — subscribe, correlate, apply outcome

**Files:**
- Create: `app/src/main/java/com/ppnam/station4aa/data/mqtt/WasteCollectionResultChannel.kt`
- Test: `app/src/test/java/com/ppnam/station4aa/data/mqtt/WasteCollectionResultChannelTest.kt`

**Interfaces:**
- Consumes: `WasteCollectionResultMessage` (Task 1); `WasteOutboxEntity`/`WasteOutboxDao.findByMessageId/markAccepted/markRejected` (Task 3); `MqttConnectionManager.subscribe` (existing, unchanged); `WireJson.gson` (existing, unchanged).
- Produces: `WasteCollectionResultChannel(outboxDao: WasteOutboxDao, connectionManager: MqttConnectionManager)` with `suspend fun ensureSubscribed(deviceId: String)` and `val results: SharedFlow<WasteCollectionResultMessage>` (terminal — accepted or rejected — outcomes only, emitted *after* the outbox row is updated). Also produces the pure, dependency-free `evaluateOutcome(result: WasteCollectionResultMessage, stored: WasteOutboxEntity): ResultOutcome` top-level function and `ResultOutcome` sealed class, both in this same file. Used by Task 5 (`WasteCollectionPublisher`, which calls `ensureSubscribed` before every publish attempt) and Task 7 (`WasteGatheringViewModel`, which collects `results` via the publisher).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/ppnam/station4aa/data/mqtt/WasteCollectionResultChannelTest.kt`:

```kotlin
package com.mitas.ppnam.station4aa.data.mqtt

import com.mitas.ppnam.station4aa.data.local.WasteOutboxEntity
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WasteCollectionResultChannelTest {

    private fun storedRow(
        messageId: String = "msg-1",
        deviceId: String = "HH-01",
        operatorSessionId: String = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
        collectionId: String = "COL-1",
        bagCode: String = "BAG-01",
    ) = WasteOutboxEntity(
        messageId = messageId,
        deviceId = deviceId,
        operatorSessionId = operatorSessionId,
        collectionId = collectionId,
        bagCode = bagCode,
        machineCode = "EXT-04",
        machineName = "EXT-04",
        machineOperatorUserId = "MO-00427",
        wasteTypeCode = "WT-01",
        collectedBy = "Collector One",
        collectedAtUtc = "2026-07-30T10:15:30.000Z",
        status = WasteOutboxEntity.Status.PENDING,
        createdAtEpochMs = 0L,
        lastAttemptEpochMs = null,
        attemptCount = 1,
        errorCode = null,
        reason = null,
        nextAction = null,
    )

    private fun result(
        inResponseToMessageId: String = "msg-1",
        deviceId: String = "HH-01",
        operatorSessionId: String = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
        collectionId: String = "COL-1",
        bagCode: String = "BAG-01",
        accepted: Boolean = true,
        errorCode: String? = null,
        reason: String? = null,
        nextAction: String = "start_next_collection",
    ) = WasteCollectionResultMessage(
        schemaVersion = 3,
        messageId = "server-response-1",
        inResponseToMessageId = inResponseToMessageId,
        deviceId = deviceId,
        operatorSessionId = operatorSessionId,
        timestampUtc = "2026-07-30T10:15:30.125000Z",
        collectionId = collectionId,
        bagCode = bagCode,
        accepted = accepted,
        isDuplicate = false,
        collectionStatus = if (accepted) "AwaitingWeight" else "Rejected",
        errorCode = errorCode,
        reason = reason,
        nextAction = nextAction,
    )

    @Test
    fun `an accepted result with matching identity resolves to Accepted`() {
        val outcome = evaluateOutcome(result(accepted = true), storedRow())
        assertEquals(ResultOutcome.Accepted, outcome)
    }

    @Test
    fun `a rejected result with matching identity resolves to Rejected carrying the error details`() {
        val outcome = evaluateOutcome(
            result(
                accepted = false,
                errorCode = "bag_code_in_use",
                reason = "Bag code 'BAG-01' is already awaiting weight for collection 'COL-1'.",
                nextAction = "complete_existing_bag_weight",
            ),
            storedRow(),
        )
        assertTrue(outcome is ResultOutcome.Rejected)
        val rejected = outcome as ResultOutcome.Rejected
        assertEquals("bag_code_in_use", rejected.errorCode)
        assertEquals("Bag code 'BAG-01' is already awaiting weight for collection 'COL-1'.", rejected.reason)
        assertEquals("complete_existing_bag_weight", rejected.nextAction)
    }

    @Test
    fun `a result with a mismatched bagCode is treated as an identity mismatch, not applied`() {
        val outcome = evaluateOutcome(result(bagCode = "BAG-02"), storedRow(bagCode = "BAG-01"))
        assertEquals(ResultOutcome.IdentityMismatch, outcome)
    }

    @Test
    fun `a result with a mismatched collectionId is treated as an identity mismatch`() {
        val outcome = evaluateOutcome(result(collectionId = "COL-OTHER"), storedRow(collectionId = "COL-1"))
        assertEquals(ResultOutcome.IdentityMismatch, outcome)
    }

    @Test
    fun `a result with a mismatched deviceId or operatorSessionId is treated as an identity mismatch`() {
        assertEquals(
            ResultOutcome.IdentityMismatch,
            evaluateOutcome(result(deviceId = "HH-99"), storedRow(deviceId = "HH-01")),
        )
        assertEquals(
            ResultOutcome.IdentityMismatch,
            evaluateOutcome(result(operatorSessionId = "different-session"), storedRow()),
        )
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mitas.ppnam.station4aa.data.mqtt.WasteCollectionResultChannelTest"`
Expected: FAIL to compile — `evaluateOutcome`/`ResultOutcome`/`WasteCollectionResultChannel` don't exist yet.

- [ ] **Step 3: Create WasteCollectionResultChannel**

`app/src/main/java/com/ppnam/station4aa/data/mqtt/WasteCollectionResultChannel.kt`:

```kotlin
package com.mitas.ppnam.station4aa.data.mqtt

import com.mitas.ppnam.station4aa.data.local.WasteOutboxDao
import com.mitas.ppnam.station4aa.data.local.WasteOutboxEntity
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** The application-level outcome of one `waste_collection_result`, resolved by [evaluateOutcome]
 * before [WasteCollectionResultChannel] applies it to the durable outbox row. */
sealed class ResultOutcome {
    object Accepted : ResultOutcome()
    data class Rejected(val errorCode: String?, val reason: String?, val nextAction: String?) : ResultOutcome()
    /** The result's echoed device/session/collection/bag identity didn't match the stored row —
     * per acceptance criterion 29, this MUST be verified, not just correlated by messageId alone.
     * Never applied to the outbox; the row stays PENDING and is retried like any unanswered event. */
    object IdentityMismatch : ResultOutcome()
}

/** Pure decision logic, dependency-free and directly testable — see `WasteWizardController` for
 * the same pattern elsewhere in this codebase. [result] is presumed already correlated to [stored]
 * by `inResponseToMessageId == stored.messageId`; this only decides the outcome and verifies the
 * echoed identity fields the contract requires checking (§3 acceptance criterion 29). */
fun evaluateOutcome(result: WasteCollectionResultMessage, stored: WasteOutboxEntity): ResultOutcome {
    val identityMatches = result.deviceId == stored.deviceId &&
        result.operatorSessionId == stored.operatorSessionId &&
        result.collectionId == stored.collectionId &&
        result.bagCode == stored.bagCode
    if (!identityMatches) return ResultOutcome.IdentityMismatch

    return if (result.accepted) {
        ResultOutcome.Accepted
    } else {
        ResultOutcome.Rejected(result.errorCode, result.reason, result.nextAction)
    }
}

/**
 * Subscribes to the exact, deterministic collection-result topic
 * (`PPNAM/station4/{deviceId}/res/waste_collection_result`, contract §3/§12) and correlates every
 * inbound result to its durable outbox row by `inResponseToMessageId == messageId`, applying the
 * terminal outcome ([WasteOutboxDao.markAccepted]/[WasteOutboxDao.markRejected]) before emitting it
 * on [results] for any UI layer that wants to react. An unknown `inResponseToMessageId` (row already
 * cleaned up, or a stray/foreign message) and an [ResultOutcome.IdentityMismatch] are both silently
 * dropped — the row is left exactly as it was, so it stays eligible for the normal retry path.
 */
class WasteCollectionResultChannel(
    private val outboxDao: WasteOutboxDao,
    private val connectionManager: MqttConnectionManager,
) {
    private val gson = WireJson.gson
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val subscribedDeviceIds = ConcurrentHashMap.newKeySet<String>()

    private val _results = MutableSharedFlow<WasteCollectionResultMessage>(extraBufferCapacity = 16)
    val results: SharedFlow<WasteCollectionResultMessage> = _results.asSharedFlow()

    /** Idempotent — safe to call before every publish attempt (see `WasteCollectionPublisher`).
     * Registers the subscription once per device for the process lifetime; `MqttConnectionManager`
     * itself re-applies it across reconnects. */
    suspend fun ensureSubscribed(deviceId: String) {
        if (!subscribedDeviceIds.add(deviceId)) return
        val topic = "PPNAM/station4/$deviceId/res/waste_collection_result"
        connectionManager.subscribe(topic) { _, bytes ->
            scope.launch { handleIncoming(String(bytes, StandardCharsets.UTF_8)) }
        }
    }

    private suspend fun handleIncoming(raw: String) {
        val result = try {
            gson.fromJson(raw, WasteCollectionResultMessage::class.java)
        } catch (e: Exception) {
            return
        }
        val stored = outboxDao.findByMessageId(result.inResponseToMessageId) ?: return

        when (val outcome = evaluateOutcome(result, stored)) {
            ResultOutcome.Accepted -> {
                outboxDao.markAccepted(stored.messageId)
                _results.emit(result)
            }
            is ResultOutcome.Rejected -> {
                outboxDao.markRejected(stored.messageId, outcome.errorCode, outcome.reason, outcome.nextAction)
                _results.emit(result)
            }
            ResultOutcome.IdentityMismatch -> Unit
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mitas.ppnam.station4aa.data.mqtt.WasteCollectionResultChannelTest"`
Expected: PASS, all 5 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ppnam/station4aa/data/mqtt/WasteCollectionResultChannel.kt app/src/test/java/com/ppnam/station4aa/data/mqtt/WasteCollectionResultChannelTest.kt
git commit -m "feat: add WasteCollectionResultChannel to correlate and apply collection results"
```

---

## Task 5: Wire WasteCollectionPublisher to the result channel and configurable topic

**Files:**
- Modify: `app/src/main/java/com/ppnam/station4aa/data/mqtt/WasteCollectionPublisher.kt`

**Interfaces:**
- Consumes: `WasteCollectionResultChannel` (Task 4); `AppSettings.wasteCollectionTopic` (Task 2, via `SettingsRepository` — now actually persisted per Task 2 Steps 5-6); `WasteOutboxDao` (unchanged, now missing `markDelivered` per Task 3 — this task removes the only caller).
- Produces: `WasteCollectionPublisher(outboxDao, connectionManager, resultChannel, settingsRepository)` (two new constructor parameters); `val results: SharedFlow<WasteCollectionResultMessage>` forwarding `resultChannel.results` (so `WasteGatheringViewModel`, Task 7, doesn't need its own `WasteCollectionResultChannel` dependency). Used by Task 6 (`AppContainer` wiring) and Task 7.

No dedicated test — this class already has no test coverage (constructor takes concrete `MqttConnectionManager`/`WasteOutboxDao`, no mocking library in this repo), consistent with the prior plan's precedent for this same file.

- [ ] **Step 1: Replace WasteCollectionPublisher.kt**

Replace the full file content:

```kotlin
package com.mitas.ppnam.station4aa.data.mqtt

import com.google.gson.Gson
import com.mitas.ppnam.station4aa.data.local.WasteOutboxDao
import com.mitas.ppnam.station4aa.data.local.toEvent
import com.mitas.ppnam.station4aa.data.local.toOutboxEntity
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage
import com.mitas.ppnam.station4aa.data.settings.SettingsRepository
import com.mitas.ppnam.station4aa.domain.model.WasteCollectionEvent
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Implements the handheld side of `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`:
 * durably write before the first publish attempt, only clear the interactive transaction after
 * that durable write, and only treat an event as accepted once a correlated `waste_collection_result`
 * with `accepted: true` arrives — never on PUBACK alone, which confirms only broker receipt (see
 * [WasteCollectionResultChannel] and `MqttConnectionManager`'s class doc).
 */
class WasteCollectionPublisher(
    private val outboxDao: WasteOutboxDao,
    private val connectionManager: MqttConnectionManager,
    private val resultChannel: WasteCollectionResultChannel,
    private val settingsRepository: SettingsRepository,
) {
    private val gson = Gson()

    /** Rows still awaiting a correlated result — surfaced so the operator can see unconfirmed work
     * exists, per the contract's reconciliation-visibility requirement. */
    val pendingCount: Flow<Int> = outboxDao.pendingCount()

    /** Terminal (accepted or rejected) results, as they're correlated. */
    val results: SharedFlow<WasteCollectionResultMessage> = resultChannel.results

    /**
     * Durably queues [event], then makes one publish attempt. Returns once the row is safely on
     * disk — callers can clear their interactive form the moment this returns, regardless of
     * whether the immediate publish attempt (best-effort) succeeded, per the contract: "clear the
     * interactive transaction only after the durable local write" (not after delivery, and
     * certainly not after acceptance, which is asynchronous and may not arrive for some time).
     */
    suspend fun submit(event: WasteCollectionEvent) {
        outboxDao.insert(event.toOutboxEntity(System.currentTimeMillis()))
        attemptPublish(event)
    }

    /** Retries every durably-queued row still awaiting a result, with its original, unchanged
     * payload — call after a reconnect so anything queued while offline gets flushed. The contract
     * requires this "whether or not it saw PUBACK", so a row's fate is decided only by an incoming
     * [WasteCollectionResultChannel] correlation, never by this method. */
    suspend fun retryPending() {
        outboxDao.getPending().forEach { attemptPublish(it.toEvent()) }
    }

    private suspend fun attemptPublish(event: WasteCollectionEvent) {
        resultChannel.ensureSubscribed(event.deviceId)
        val topic = settingsRepository.current().wasteCollectionTopic
        val payload = gson.toJson(event.toWireMessage()).toByteArray(StandardCharsets.UTF_8)
        connectionManager.publish(topic, payload)
        outboxDao.recordAttempt(event.messageId, System.currentTimeMillis())
        // No status write on publish success/failure: PUBACK is not a business outcome. The row
        // stays PENDING (and therefore retried) until WasteCollectionResultChannel applies a
        // correlated ACCEPTED/REJECTED result.
    }
}
```

- [ ] **Step 2: Build to confirm the expected downstream breakage**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: FAIL — `AppContainer.kt` (Task 6, not yet updated) still constructs `WasteCollectionPublisher(outboxDao, connectionManager)` with the old two-argument shape. This is expected; Task 6 fixes it. Confirm the *only* compile error is in `AppContainer.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ppnam/station4aa/data/mqtt/WasteCollectionPublisher.kt
git commit -m "feat: drive WasteCollectionPublisher from correlated results, not PUBACK"
```

---

## Task 6: Wire the new dependencies in AppContainer

**Files:**
- Modify: `app/src/main/java/com/ppnam/station4aa/data/AppContainer.kt`

**Interfaces:**
- Consumes: `WasteCollectionResultChannel` (Task 4); `WasteCollectionPublisher`'s new constructor shape (Task 5).
- Produces: `AppContainer.wasteCollectionPublisher` now built with all four dependencies. Nothing new exposed — `WasteGatheringViewModel` (Task 7) continues to receive `container.wasteCollectionPublisher` exactly as it does today.

No dedicated test — this file has never had one (it's the manual DI root).

- [ ] **Step 1: Update the wasteCollectionPublisher construction**

In `AppContainer.kt`, replace the `wasteCollectionPublisher` block:

```kotlin
    private val outboxDatabase = WasteOutboxDatabase.create(appContext)
    private val wasteCollectionResultChannel = WasteCollectionResultChannel(
        outboxDao = outboxDatabase.wasteOutboxDao(),
        connectionManager = connectionManager,
    )
    val wasteCollectionPublisher = WasteCollectionPublisher(
        outboxDao = outboxDatabase.wasteOutboxDao(),
        connectionManager = connectionManager,
        resultChannel = wasteCollectionResultChannel,
        settingsRepository = settingsRepository,
    )
```

Add the import:

```kotlin
import com.mitas.ppnam.station4aa.data.mqtt.WasteCollectionResultChannel
```

- [ ] **Step 2: Build to confirm everything compiles**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL — no remaining errors anywhere.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ppnam/station4aa/data/AppContainer.kt
git commit -m "feat: wire WasteCollectionResultChannel into AppContainer"
```

---

## Task 7: Surface rejected results to the operator

**Files:**
- Modify: `app/src/main/java/com/ppnam/station4aa/ui/waste/WasteGatheringViewModel.kt`

**Interfaces:**
- Consumes: `WasteCollectionPublisher.results: SharedFlow<WasteCollectionResultMessage>` (Task 5).
- Produces: nothing new consumed by a later task — `lastQueuedMessage` (existing `StateFlow<String?>`) now also carries rejection text; `WasteGatheringScreen.kt` needs no change since it already renders whatever string is in that flow.

No dedicated test, consistent with this file's existing precedent (concrete constructor dependencies, no mocking library) — the logic being added is a straight-line `collect` + `when`, and the branching it depends on (`evaluateOutcome`) is already covered by Task 4's tests.

- [ ] **Step 1: Collect publisher.results in init**

In `WasteGatheringViewModel.kt`, add a new import and a new `viewModelScope.launch` block inside `init { ... }`, alongside the two that already exist there:

```kotlin
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage
```

```kotlin
        viewModelScope.launch {
            publisher.results.collect { result ->
                if (!result.accepted) {
                    _lastQueuedMessage.value = "Bag ${result.bagCode} was rejected: " +
                        (result.reason ?: result.errorCode ?: "unknown reason") +
                        " (${result.nextAction})"
                }
                // An accepted result needs no new operator-visible message — "Queued ..." already
                // shown at publish time already told them the transaction is in motion, and the
                // wizard has already moved on to the next one.
            }
        }
```

(Add this as a third block inside the existing `init { ... }`, after the two `viewModelScope.launch` blocks already there for `connect`/`retryPending` and the scan-event collector.)

- [ ] **Step 2: Run the full unit test suite to confirm nothing broke**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ppnam/station4aa/ui/waste/WasteGatheringViewModel.kt
git commit -m "feat: surface rejected collection results to the operator"
```

---

## Task 8: Full test suite and build

**Files:** none (verification only).

**Interfaces:** none — this task only runs commands.

- [ ] **Step 1: Run the full unit test suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL — every suite passes, including all untouched suites from the prior plan (`WasteWizardControllerTest`, `WasteCollectionValidatorTest`, `WasteCollectionEventTest`, `MqttTopicsTest`, `ScramCryptoTest`, `SessionStateTest`) and this plan's new `WasteCollectionResultMessageTest`, `WasteCollectionResultChannelTest`.

- [ ] **Step 2: Assemble the debug APK**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: If either fails, fix and re-run before proceeding**

Do not proceed to Task 9 with a red build or a failing test — fix the specific failure, re-run just that command, then re-run both commands above from clean.

(No commit — nothing changes unless Step 3's fix path is taken, in which case commit that fix with a message describing what was actually wrong.)

---

## Task 9: On-device smoke check

**Files:** none (manual verification only, using the already-connected handheld).

**Interfaces:** none.

This plan's on-device verification is deliberately narrow: as of the prior plan's Task 8, this handheld's login has no reachable Station 4 SCRAM auth backend (a pre-existing, documented condition — see this repo's `CLAUDE.md`), so the wizard screens (and therefore any real publish/result round-trip) cannot be exercised end-to-end on real hardware yet. This task only confirms the app still installs, launches, and survives the outbox database's version bump (2 → 3, Task 3) without crashing — the correlation/outcome logic itself is covered by Task 4's unit tests.

- [ ] **Step 1: Install the freshly built debug APK**

```powershell
& "C:\Users\Jonathan\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "C:\Dev\PPNAM_Station_4_AA\app\build\outputs\apk\debug\app-debug.apk"
```

Expected: `Success`.

- [ ] **Step 2: Launch the app and confirm it doesn't crash**

```powershell
$adb = "C:\Users\Jonathan\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell am force-stop com.mitas.ppnam.station4aa
& $adb shell am start -n com.mitas.ppnam.station4aa/.MainActivity
```

Wait a few seconds, then pull logcat:

```powershell
& $adb logcat -d -v time | Select-String -Pattern "com.mitas.ppnam.station4aa" | Select-String -Pattern "FATAL|AndroidRuntime|Exception"
```

Expected: no matches — in particular, no Room `IllegalStateException` from the version-3 outbox migration (the destructive fallback should silently drop and recreate the local `ppnam_station4_outbox.db` rather than crash).

- [ ] **Step 3: Report the result**

Summarize pass/fail. If the app crashes on launch, treat it as a bug in Task 3 or Task 6's wiring and fix it there before considering this plan complete.
