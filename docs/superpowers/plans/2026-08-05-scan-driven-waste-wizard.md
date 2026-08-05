# Scan-Driven Waste Collection Wizard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single-page Waste Gathering form with a step-by-step scan-driven wizard (machine → operator → waste type → bag code) while keeping the MQTT wire behavior byte-for-byte contract-compliant — exactly one publish, at the end of the transaction.

**Architecture:** A new pure, dependency-free `WasteWizardController` (domain layer, no Android/MQTT imports) owns all step-transition and validation-dispatch logic and is fully unit-testable without fakes. `WasteGatheringViewModel` becomes a thin wrapper that mirrors the controller's state into `StateFlow`s, feeds it barcode scans from the existing `ScanEventBus`, and — only once, at `REVIEW` confirm — builds a `WasteCollectionEvent` (now carrying a `bagCode` field) and hands it to the existing, unchanged `WasteCollectionPublisher` durable-write-then-publish path. `WasteGatheringScreen` is rewritten to render one active step at a time instead of a single form.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), AndroidX ViewModel/StateFlow, Room (local outbox), Gson (wire JSON), JUnit4 (`kotlinx-coroutines-test` available, no mocking library).

## Global Constraints

- Wire contract: `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md` — exactly one MQTT publish per completed transaction; no partial/incremental publishes; `messageId`/`collectionId`/`collectedAtUtc` generated only once, at transaction completion.
- `bagCode` is an additive JSON property (10th field, after the 9 contract-defined fields) — Station 4 may ignore it; it is never a substitute for a required field.
- No step-back navigation. "Cancel transaction" is available on every step and always performs a full reset to `SCAN_MACHINE` with an empty draft.
- `machineName` mirrors the scanned `machineCode` verbatim — no catalog lookup, no operator-typed name.
- Manual text entry is an allowed fallback on every scan step (machine code, operator ID, bag code), validated identically to a scan.
- Design spec: `docs/superpowers/specs/2026-08-05-scan-driven-waste-wizard-design.md`.

---

## Task 1: Extend WasteCollectionValidator with machine-code and bag-code rules

**Files:**
- Modify: `app/src/main/java/com/ppnam/station4aa/domain/validation/WasteCollectionValidator.kt`
- Test: `app/src/test/java/com/ppnam/station4aa/domain/validation/WasteCollectionValidatorTest.kt`

**Interfaces:**
- Consumes: nothing new (existing `validateRequiredIdentity` private helper).
- Produces: `WasteCollectionValidator.validateMachineCode(raw: String): String?` and `WasteCollectionValidator.validateBagCode(raw: String): String?`, both returning `null` for valid input or an operator-facing error message otherwise. Used by Task 2's `WasteWizardController`.

- [ ] **Step 1: Write the failing tests**

Append to `WasteCollectionValidatorTest.kt` (inside the existing `WasteCollectionValidatorTest` class, after the last `collected by over 200 characters is rejected` test):

```kotlin
    @Test
    fun `blank machine code is rejected`() {
        assertEquals("Required.", WasteCollectionValidator.validateMachineCode(""))
        assertEquals("Required.", WasteCollectionValidator.validateMachineCode("   "))
    }

    @Test
    fun `machine code is not placeholder-checked`() {
        // A real machine could plausibly be labeled with a code that collides with the
        // placeholder denylist; unlike machineOperatorUserId this isn't freshly typed per
        // transaction under identity rules, so it isn't placeholder-checked.
        assertNull(WasteCollectionValidator.validateMachineCode("UNKNOWN"))
    }

    @Test
    fun `control characters in machine code are rejected`() {
        val bell = 7.toChar()
        assertNotNull(WasteCollectionValidator.validateMachineCode("EXT-04$bell"))
    }

    @Test
    fun `machine code over 100 characters is rejected`() {
        assertNotNull(WasteCollectionValidator.validateMachineCode("A".repeat(101)))
    }

    @Test
    fun `valid machine code is accepted`() {
        assertNull(WasteCollectionValidator.validateMachineCode("EXT-04"))
    }

    @Test
    fun `blank bag code is rejected`() {
        assertEquals("Required.", WasteCollectionValidator.validateBagCode(""))
        assertEquals("Required.", WasteCollectionValidator.validateBagCode("   "))
    }

    @Test
    fun `placeholder bag code is rejected case-insensitively`() {
        assertNotNull(WasteCollectionValidator.validateBagCode("UNKNOWN"))
        assertNotNull(WasteCollectionValidator.validateBagCode("n/a"))
    }

    @Test
    fun `control characters in bag code are rejected`() {
        val bell = 7.toChar()
        assertNotNull(WasteCollectionValidator.validateBagCode("BAG-001$bell"))
    }

    @Test
    fun `bag code over 100 characters is rejected`() {
        assertNotNull(WasteCollectionValidator.validateBagCode("A".repeat(101)))
    }

    @Test
    fun `valid bag code is accepted`() {
        assertNull(WasteCollectionValidator.validateBagCode("BAG-00931"))
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.ppnam.station4aa.domain.validation.WasteCollectionValidatorTest"`
Expected: FAIL — `validateMachineCode`/`validateBagCode` are unresolved references.

- [ ] **Step 3: Implement the two new validators**

In `WasteCollectionValidator.kt`, add a shared max-length constant and the two new public functions (place them right after the existing `validateCollectedBy`):

```kotlin
    private const val MACHINE_CODE_MAX_LENGTH = 100
    private const val BAG_CODE_MAX_LENGTH = 100

    /** `machineCode`: scanned fresh at the start of every transaction. Not placeholder-checked —
     * unlike `machineOperatorUserId` it isn't a freely typed identity field under the handheld's
     * identity rules, it's whatever a real machine's printed barcode contains. */
    fun validateMachineCode(raw: String): String? =
        validateRequiredIdentity(raw, MACHINE_CODE_MAX_LENGTH, rejectPlaceholders = false)

    /** `bagCode`: scanned fresh for every transaction, same placeholder-rejection posture as
     * `machineOperatorUserId` since it's the same kind of freshly-scanned opaque identifier. */
    fun validateBagCode(raw: String): String? =
        validateRequiredIdentity(raw, BAG_CODE_MAX_LENGTH, rejectPlaceholders = true)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.ppnam.station4aa.domain.validation.WasteCollectionValidatorTest"`
Expected: PASS, all tests including the new ones.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ppnam/station4aa/domain/validation/WasteCollectionValidator.kt app/src/test/java/com/ppnam/station4aa/domain/validation/WasteCollectionValidatorTest.kt
git commit -m "feat: add machine-code and bag-code validation rules"
```

---

## Task 2: Pure wizard step-transition controller

**Files:**
- Create: `app/src/main/java/com/ppnam/station4aa/domain/wizard/WizardStep.kt`
- Create: `app/src/main/java/com/ppnam/station4aa/domain/wizard/WasteTransactionDraft.kt`
- Create: `app/src/main/java/com/ppnam/station4aa/domain/wizard/ScanDispatchResult.kt`
- Create: `app/src/main/java/com/ppnam/station4aa/domain/wizard/WasteWizardController.kt`
- Test: `app/src/test/java/com/ppnam/station4aa/domain/wizard/WasteWizardControllerTest.kt`

**Interfaces:**
- Consumes: `WasteCollectionValidator.validateMachineCode/validateMachineOperatorUserId/validateBagCode` (Task 1 + existing), `WasteTypeCatalog` (existing enum, unchanged).
- Produces: `WizardStep` enum (`SCAN_MACHINE, SCAN_OPERATOR, SELECT_WASTE_TYPE, SCAN_BAG, REVIEW`); `WasteTransactionDraft(machineCode: String?, machineOperatorUserId: String?, wasteType: WasteTypeCatalog?, bagCode: String?)`; `ScanDispatchResult` sealed class (`Applied(error: String?)`, `Ignored`); `WasteWizardController` with public `step: WizardStep`, `draft: WasteTransactionDraft`, and functions `handleScannedValue(value: String): ScanDispatchResult`, `submitMachineCode(raw: String): String?`, `submitOperatorId(raw: String): String?`, `confirmWasteType(type: WasteTypeCatalog)`, `submitBagCode(raw: String): String?`, `cancel()`. Used by Task 5's `WasteGatheringViewModel`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/ppnam/station4aa/domain/wizard/WasteWizardControllerTest.kt`:

```kotlin
package com.ppnam.station4aa.domain.wizard

import com.ppnam.station4aa.domain.model.WasteTypeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WasteWizardControllerTest {

    @Test
    fun `starts on SCAN_MACHINE with an empty draft`() {
        val controller = WasteWizardController()
        assertEquals(WizardStep.SCAN_MACHINE, controller.step)
        assertEquals(WasteTransactionDraft(), controller.draft)
    }

    @Test
    fun `submitMachineCode with a valid code advances to SCAN_OPERATOR and trims the value`() {
        val controller = WasteWizardController()
        val error = controller.submitMachineCode("  EXT-04  ")
        assertNull(error)
        assertEquals(WizardStep.SCAN_OPERATOR, controller.step)
        assertEquals("EXT-04", controller.draft.machineCode)
    }

    @Test
    fun `submitMachineCode with a blank code returns an error and does not advance`() {
        val controller = WasteWizardController()
        val error = controller.submitMachineCode("   ")
        assertEquals("Required.", error)
        assertEquals(WizardStep.SCAN_MACHINE, controller.step)
        assertNull(controller.draft.machineCode)
    }

    @Test
    fun `full happy path walk populates every draft field and reaches REVIEW`() {
        val controller = WasteWizardController()

        assertNull(controller.submitMachineCode("EXT-04"))
        assertNull(controller.submitOperatorId("MO-00427"))
        controller.confirmWasteType(WasteTypeCatalog.RECYCLABLE)
        assertNull(controller.submitBagCode("BAG-00931"))

        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals(
            WasteTransactionDraft(
                machineCode = "EXT-04",
                machineOperatorUserId = "MO-00427",
                wasteType = WasteTypeCatalog.RECYCLABLE,
                bagCode = "BAG-00931",
            ),
            controller.draft,
        )
    }

    @Test
    fun `handleScannedValue routes to the field the active step expects`() {
        val controller = WasteWizardController()

        val machineResult = controller.handleScannedValue("EXT-04")
        assertEquals(ScanDispatchResult.Applied(null), machineResult)
        assertEquals(WizardStep.SCAN_OPERATOR, controller.step)

        val operatorResult = controller.handleScannedValue("MO-00427")
        assertEquals(ScanDispatchResult.Applied(null), operatorResult)
        assertEquals(WizardStep.SELECT_WASTE_TYPE, controller.step)
    }

    @Test
    fun `handleScannedValue is ignored during SELECT_WASTE_TYPE and does not mutate the draft`() {
        val controller = WasteWizardController()
        controller.submitMachineCode("EXT-04")
        controller.submitOperatorId("MO-00427")

        val before = controller.draft
        val result = controller.handleScannedValue("stray-scan")

        assertEquals(ScanDispatchResult.Ignored, result)
        assertEquals(WizardStep.SELECT_WASTE_TYPE, controller.step)
        assertEquals(before, controller.draft)
    }

    @Test
    fun `handleScannedValue is ignored during REVIEW`() {
        val controller = WasteWizardController()
        controller.submitMachineCode("EXT-04")
        controller.submitOperatorId("MO-00427")
        controller.confirmWasteType(WasteTypeCatalog.GENERAL)
        controller.submitBagCode("BAG-001")

        val before = controller.draft
        val result = controller.handleScannedValue("stray-scan")

        assertEquals(ScanDispatchResult.Ignored, result)
        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals(before, controller.draft)
    }

    @Test
    fun `an invalid scanned value is applied as an error and does not advance`() {
        val controller = WasteWizardController()
        val result = controller.handleScannedValue("   ")
        assertTrue(result is ScanDispatchResult.Applied)
        assertEquals("Required.", (result as ScanDispatchResult.Applied).error)
        assertEquals(WizardStep.SCAN_MACHINE, controller.step)
    }

    @Test
    fun `cancel resets to SCAN_MACHINE with an empty draft from any step`() {
        val controller = WasteWizardController()
        controller.submitMachineCode("EXT-04")
        controller.submitOperatorId("MO-00427")
        controller.confirmWasteType(WasteTypeCatalog.GENERAL)

        controller.cancel()

        assertEquals(WizardStep.SCAN_MACHINE, controller.step)
        assertEquals(WasteTransactionDraft(), controller.draft)
    }

    @Test
    fun `cancel from REVIEW also fully resets`() {
        val controller = WasteWizardController()
        controller.submitMachineCode("EXT-04")
        controller.submitOperatorId("MO-00427")
        controller.confirmWasteType(WasteTypeCatalog.GENERAL)
        controller.submitBagCode("BAG-001")
        assertEquals(WizardStep.REVIEW, controller.step)

        controller.cancel()

        assertEquals(WizardStep.SCAN_MACHINE, controller.step)
        assertEquals(WasteTransactionDraft(), controller.draft)
    }

    @Test(expected = IllegalStateException::class)
    fun `confirmWasteType outside SELECT_WASTE_TYPE throws`() {
        WasteWizardController().confirmWasteType(WasteTypeCatalog.GENERAL)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.ppnam.station4aa.domain.wizard.WasteWizardControllerTest"`
Expected: FAIL to compile — `com.ppnam.station4aa.domain.wizard` package and its types don't exist yet.

- [ ] **Step 3: Create WizardStep**

`app/src/main/java/com/ppnam/station4aa/domain/wizard/WizardStep.kt`:

```kotlin
package com.ppnam.station4aa.domain.wizard

/** The five states of the scan-driven waste collection wizard — see
 * `docs/superpowers/specs/2026-08-05-scan-driven-waste-wizard-design.md`. */
enum class WizardStep { SCAN_MACHINE, SCAN_OPERATOR, SELECT_WASTE_TYPE, SCAN_BAG, REVIEW }
```

- [ ] **Step 4: Create WasteTransactionDraft**

`app/src/main/java/com/ppnam/station4aa/domain/wizard/WasteTransactionDraft.kt`:

```kotlin
package com.ppnam.station4aa.domain.wizard

import com.ppnam.station4aa.domain.model.WasteTypeCatalog

/** Local-only wizard state — never sent over MQTT itself. Only once every field is non-null does
 * [WasteWizardController] reach [WizardStep.REVIEW]; the completed draft is what
 * WasteGatheringViewModel reads to build the one real [com.ppnam.station4aa.domain.model.WasteCollectionEvent]. */
data class WasteTransactionDraft(
    val machineCode: String? = null,
    val machineOperatorUserId: String? = null,
    val wasteType: WasteTypeCatalog? = null,
    val bagCode: String? = null,
)
```

- [ ] **Step 5: Create ScanDispatchResult**

`app/src/main/java/com/ppnam/station4aa/domain/wizard/ScanDispatchResult.kt`:

```kotlin
package com.ppnam.station4aa.domain.wizard

/** Result of routing one scanned barcode value into [WasteWizardController.handleScannedValue]. */
sealed class ScanDispatchResult {
    /** The active step accepted the scan attempt; [error] is null on success or an operator-facing
     * validation message on failure. Either way the step only advances on success. */
    data class Applied(val error: String?) : ScanDispatchResult()

    /** The active step (SELECT_WASTE_TYPE or REVIEW) doesn't accept scans — the value was dropped,
     * not queued for later. */
    object Ignored : ScanDispatchResult()
}
```

- [ ] **Step 6: Create WasteWizardController**

`app/src/main/java/com/ppnam/station4aa/domain/wizard/WasteWizardController.kt`:

```kotlin
package com.ppnam.station4aa.domain.wizard

import com.ppnam.station4aa.domain.model.WasteTypeCatalog
import com.ppnam.station4aa.domain.validation.WasteCollectionValidator

/**
 * Pure step-transition logic for the scan-driven waste collection wizard — no Android or MQTT
 * dependencies, so it's fully unit-testable without fakes. `WasteGatheringViewModel` owns one
 * instance per screen and mirrors [step]/[draft] into StateFlows for the UI.
 *
 * No step-back: the only way out of a wrong value mid-step is submitting a corrected one; the
 * only way to abandon a transaction is [cancel], which is always available and always performs a
 * full reset — see `docs/superpowers/specs/2026-08-05-scan-driven-waste-wizard-design.md`.
 */
class WasteWizardController {
    var step: WizardStep = WizardStep.SCAN_MACHINE
        private set
    var draft: WasteTransactionDraft = WasteTransactionDraft()
        private set

    /** Machine/operator/bag barcode scans all funnel through here. Returns
     * [ScanDispatchResult.Ignored] when the active step doesn't accept a scan — a stray scan is
     * dropped, not applied to a step the operator has already moved past. */
    fun handleScannedValue(value: String): ScanDispatchResult = when (step) {
        WizardStep.SCAN_MACHINE -> ScanDispatchResult.Applied(submitMachineCode(value))
        WizardStep.SCAN_OPERATOR -> ScanDispatchResult.Applied(submitOperatorId(value))
        WizardStep.SCAN_BAG -> ScanDispatchResult.Applied(submitBagCode(value))
        WizardStep.SELECT_WASTE_TYPE, WizardStep.REVIEW -> ScanDispatchResult.Ignored
    }

    /** Manual-entry fallback for the machine-code step; a scan calls this too via
     * [handleScannedValue]. Returns an error message, or null and advances to SCAN_OPERATOR. */
    fun submitMachineCode(raw: String): String? {
        val error = WasteCollectionValidator.validateMachineCode(raw)
        if (error != null) return error
        draft = draft.copy(machineCode = raw.trim())
        step = WizardStep.SCAN_OPERATOR
        return null
    }

    /** Manual-entry fallback for the machine-operator step. */
    fun submitOperatorId(raw: String): String? {
        val error = WasteCollectionValidator.validateMachineOperatorUserId(raw)
        if (error != null) return error
        draft = draft.copy(machineOperatorUserId = raw.trim())
        step = WizardStep.SELECT_WASTE_TYPE
        return null
    }

    /** SELECT_WASTE_TYPE's step-local Confirm action — not scan-driven, so it has no
     * [ScanDispatchResult] wrapper. Throws if called outside that step, which the UI only ever
     * allows by construction (the Confirm button is only rendered during SELECT_WASTE_TYPE). */
    fun confirmWasteType(type: WasteTypeCatalog) {
        check(step == WizardStep.SELECT_WASTE_TYPE) {
            "confirmWasteType called outside SELECT_WASTE_TYPE (was $step)"
        }
        draft = draft.copy(wasteType = type)
        step = WizardStep.SCAN_BAG
    }

    /** Manual-entry fallback for the bag-code step. */
    fun submitBagCode(raw: String): String? {
        val error = WasteCollectionValidator.validateBagCode(raw)
        if (error != null) return error
        draft = draft.copy(bagCode = raw.trim())
        step = WizardStep.REVIEW
        return null
    }

    /** Available on every step, including REVIEW. Discards the draft and returns to the first
     * step — there is no partial-edit recovery path. */
    fun cancel() {
        step = WizardStep.SCAN_MACHINE
        draft = WasteTransactionDraft()
    }
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.ppnam.station4aa.domain.wizard.WasteWizardControllerTest"`
Expected: PASS, all 11 tests.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ppnam/station4aa/domain/wizard app/src/test/java/com/ppnam/station4aa/domain/wizard
git commit -m "feat: add pure WasteWizardController for the scan-driven collection flow"
```

---

## Task 3: Add bagCode to WasteCollectionEvent and the wire message

**Files:**
- Modify: `app/src/main/java/com/ppnam/station4aa/domain/model/WasteCollectionEvent.kt`
- Modify: `app/src/main/java/com/ppnam/station4aa/data/mqtt/dto/WasteCollectionMessage.kt`
- Modify: `app/src/test/java/com/ppnam/station4aa/domain/model/WasteCollectionEventTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `WasteCollectionEvent.create(machineCode, machineName, wasteTypeCode, collectedBy, machineOperatorUserId, bagCode, now = Instant.now())` (bagCode is a new required parameter, inserted before the defaulted `now`); `WasteCollectionEvent.bagCode: String`; `WasteCollectionMessage.bagCode: String` as the JSON payload's 10th property. Used by Task 4 (outbox mapping) and Task 5 (ViewModel's `onReviewConfirmed`).

- [ ] **Step 1: Write the failing tests**

Update `WasteCollectionEventTest.kt` — every existing `WasteCollectionEvent.create(...)` call needs a `bagCode` argument, and one new test is added. Replace the full file content with:

```kotlin
package com.ppnam.station4aa.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WasteCollectionEventTest {

    private val fixedInstant: Instant = Instant.parse("2026-07-30T10:15:30.000Z")

    @Test
    fun `create trims fields and stamps schema version 2 on the wire message`() {
        val event = WasteCollectionEvent.create(
            machineCode = "  EXT-04  ",
            machineName = " Extruder 4 ",
            wasteTypeCode = " WT-01 ",
            collectedBy = " WO-00112 ",
            machineOperatorUserId = " MO-00427 ",
            bagCode = " BAG-00931 ",
            now = fixedInstant,
        )

        assertEquals("EXT-04", event.machineCode)
        assertEquals("Extruder 4", event.machineName)
        assertEquals("WT-01", event.wasteTypeCode)
        assertEquals("WO-00112", event.collectedBy)
        assertEquals("MO-00427", event.machineOperatorUserId)
        assertEquals("BAG-00931", event.bagCode)
        assertEquals("2026-07-30T10:15:30.000Z", event.collectedAtUtc)
        assertEquals(2, event.toWireMessage().schemaVersion)
    }

    @Test
    fun `collectionId follows the contract's WC-yyyyMMdd- shape`() {
        val event = WasteCollectionEvent.create(
            machineCode = "EXT-04",
            machineName = "Extruder 4",
            wasteTypeCode = "WT-01",
            collectedBy = "WO-00112",
            machineOperatorUserId = "MO-00427",
            bagCode = "BAG-00931",
            now = fixedInstant,
        )
        assertTrue(event.collectionId.matches(Regex("WC-20260730-\\d{6}")))
    }

    @Test
    fun `wire JSON uses the exact camelCase property names the contract requires, plus bagCode`() {
        val event = WasteCollectionEvent.create(
            machineCode = "EXT-04",
            machineName = "Extruder 4",
            wasteTypeCode = "WT-01",
            collectedBy = "WO-00112",
            machineOperatorUserId = "MO-00427",
            bagCode = "BAG-00931",
            now = fixedInstant,
        )
        val json = Gson().toJson(event.toWireMessage())

        listOf(
            "\"schemaVersion\":2",
            "\"messageId\"",
            "\"collectionId\"",
            "\"machineCode\":\"EXT-04\"",
            "\"machineName\":\"Extruder 4\"",
            "\"wasteTypeCode\":\"WT-01\"",
            "\"collectedBy\":\"WO-00112\"",
            "\"machineOperatorUserId\":\"MO-00427\"",
            "\"collectedAtUtc\":\"2026-07-30T10:15:30.000Z\"",
            "\"bagCode\":\"BAG-00931\"",
        ).forEach { expectedFragment ->
            assertTrue("Expected JSON to contain $expectedFragment but was $json", json.contains(expectedFragment))
        }
    }

    @Test
    fun `two events created back to back get different messageIds`() {
        val first = WasteCollectionEvent.create("EXT-04", "Extruder 4", "WT-01", "WO-00112", "MO-00427", "BAG-001", fixedInstant)
        val second = WasteCollectionEvent.create("EXT-04", "Extruder 4", "WT-01", "WO-00112", "MO-00427", "BAG-001", fixedInstant)
        assertTrue(first.messageId != second.messageId)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.ppnam.station4aa.domain.model.WasteCollectionEventTest"`
Expected: FAIL to compile — `create(...)` doesn't accept a `bagCode` argument yet, `event.bagCode` is unresolved.

- [ ] **Step 3: Add bagCode to WasteCollectionMessage**

In `WasteCollectionMessage.kt`, add `bagCode` as the last property:

```kotlin
data class WasteCollectionMessage(
    val schemaVersion: Int,
    val messageId: String,
    val collectionId: String,
    val machineCode: String,
    val machineName: String,
    val wasteTypeCode: String,
    val collectedBy: String,
    val machineOperatorUserId: String,
    val collectedAtUtc: String,
    val bagCode: String,
)
```

- [ ] **Step 4: Add bagCode to WasteCollectionEvent**

In `WasteCollectionEvent.kt`, add the field, the `create()` parameter, and wire it through `toWireMessage()`:

```kotlin
data class WasteCollectionEvent(
    val messageId: String,
    val collectionId: String,
    val machineCode: String,
    val machineName: String,
    val wasteTypeCode: String,
    val collectedBy: String,
    val machineOperatorUserId: String,
    val collectedAtUtc: String,
    val bagCode: String,
) {
    companion object {
        const val SCHEMA_VERSION = 2

        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        fun create(
            machineCode: String,
            machineName: String,
            wasteTypeCode: String,
            collectedBy: String,
            machineOperatorUserId: String,
            bagCode: String,
            now: Instant = Instant.now(),
        ): WasteCollectionEvent = WasteCollectionEvent(
            messageId = UUID.randomUUID().toString(),
            collectionId = generateCollectionId(now),
            machineCode = machineCode.trim(),
            machineName = machineName.trim(),
            wasteTypeCode = wasteTypeCode.trim(),
            collectedBy = collectedBy.trim(),
            machineOperatorUserId = machineOperatorUserId.trim(),
            collectedAtUtc = TIMESTAMP_FORMATTER.format(now),
            bagCode = bagCode.trim(),
        )

        private fun generateCollectionId(now: Instant): String {
            val datePart = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(now)
            val suffix = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
            return "WC-$datePart-$suffix"
        }
    }

    fun toWireMessage(): WasteCollectionMessage = WasteCollectionMessage(
        schemaVersion = SCHEMA_VERSION,
        messageId = messageId,
        collectionId = collectionId,
        machineCode = machineCode,
        machineName = machineName,
        wasteTypeCode = wasteTypeCode,
        collectedBy = collectedBy,
        machineOperatorUserId = machineOperatorUserId,
        collectedAtUtc = collectedAtUtc,
        bagCode = bagCode,
    )
}
```

(Only the data class fields, `create()`'s signature/body, and `toWireMessage()` change — the class doc comment, imports, and `generateCollectionId` stay as they are in the file today.)

- [ ] **Step 5: Run the tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.ppnam.station4aa.domain.model.WasteCollectionEventTest"`
Expected: PASS, all 4 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ppnam/station4aa/domain/model/WasteCollectionEvent.kt app/src/main/java/com/ppnam/station4aa/data/mqtt/dto/WasteCollectionMessage.kt app/src/test/java/com/ppnam/station4aa/domain/model/WasteCollectionEventTest.kt
git commit -m "feat: add bagCode as an additive field on the waste collection event and wire message"
```

---

## Task 4: Carry bagCode through the local outbox

**Files:**
- Modify: `app/src/main/java/com/ppnam/station4aa/data/local/WasteOutboxEntity.kt`
- Modify: `app/src/main/java/com/ppnam/station4aa/data/local/WasteOutboxDatabase.kt`

**Interfaces:**
- Consumes: `WasteCollectionEvent.bagCode` (Task 3).
- Produces: `WasteOutboxEntity.bagCode: String` column; `WasteOutboxDatabase` at schema version 2. Used by Task 5 indirectly (via the unchanged `WasteCollectionPublisher`, which already round-trips whatever `toEvent()`/`toOutboxEntity()` carry).

No dedicated automated test: this repo has no Room DAO/migration test infrastructure yet (`exportSchema = false`, no `androidTest` DAO coverage, no `Migration` classes anywhere), and the outbox is a transient in-flight queue rather than a permanent record, so a destructive-migration fallback is acceptable rather than authoring net-new Room test infra for this one column. Correctness is exercised end-to-end by Task 8's on-device verification (a queued row must survive to "Queued ..." and drop `pendingCount` back down after PUBACK).

- [ ] **Step 1: Add the bagCode column and update the mapping functions**

In `WasteOutboxEntity.kt`, add the column to the entity and both mapping functions:

```kotlin
@Entity(tableName = "waste_outbox")
data class WasteOutboxEntity(
    @PrimaryKey val messageId: String,
    val collectionId: String,
    val machineCode: String,
    val machineName: String,
    val wasteTypeCode: String,
    val collectedBy: String,
    val machineOperatorUserId: String,
    val collectedAtUtc: String,
    val bagCode: String,
    val status: String,
    val createdAtEpochMs: Long,
    val lastAttemptEpochMs: Long?,
    val attemptCount: Int,
) {
    object Status {
        const val PENDING = "PENDING"
        const val DELIVERED = "DELIVERED"
    }
}

fun WasteOutboxEntity.toEvent(): WasteCollectionEvent = WasteCollectionEvent(
    messageId = messageId,
    collectionId = collectionId,
    machineCode = machineCode,
    machineName = machineName,
    wasteTypeCode = wasteTypeCode,
    collectedBy = collectedBy,
    machineOperatorUserId = machineOperatorUserId,
    collectedAtUtc = collectedAtUtc,
    bagCode = bagCode,
)

fun WasteCollectionEvent.toOutboxEntity(nowEpochMs: Long): WasteOutboxEntity = WasteOutboxEntity(
    messageId = messageId,
    collectionId = collectionId,
    machineCode = machineCode,
    machineName = machineName,
    wasteTypeCode = wasteTypeCode,
    collectedBy = collectedBy,
    machineOperatorUserId = machineOperatorUserId,
    collectedAtUtc = collectedAtUtc,
    bagCode = bagCode,
    status = WasteOutboxEntity.Status.PENDING,
    createdAtEpochMs = nowEpochMs,
    lastAttemptEpochMs = null,
    attemptCount = 0,
)
```

(Entity doc comment and `Status` object body are unchanged from the current file — only the new `bagCode` column and its two mapping-function wirings are added.)

- [ ] **Step 2: Bump the database version with a destructive-migration fallback**

In `WasteOutboxDatabase.kt`:

```kotlin
package com.ppnam.station4aa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WasteOutboxEntity::class], version = 2, exportSchema = false)
abstract class WasteOutboxDatabase : RoomDatabase() {
    abstract fun wasteOutboxDao(): WasteOutboxDao

    companion object {
        fun create(context: Context): WasteOutboxDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WasteOutboxDatabase::class.java,
                "ppnam_station4_outbox.db",
            )
                // No migration path exists yet for the pre-bagCode schema (version 1). The
                // outbox is a transient in-flight queue, not a permanent record, so dropping and
                // recreating it on upgrade is acceptable rather than authoring a real migration
                // for one column pre-production.
                .fallbackToDestructiveMigration()
                .build()
    }
}
```

- [ ] **Step 3: Build to confirm it compiles**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (`WasteCollectionPublisher` and `WasteCollectionEventTest`/etc. all still compile since Task 3 already added `bagCode` everywhere `WasteCollectionEvent` is constructed.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ppnam/station4aa/data/local/WasteOutboxEntity.kt app/src/main/java/com/ppnam/station4aa/data/local/WasteOutboxDatabase.kt
git commit -m "feat: carry bagCode through the local waste outbox"
```

---

## Task 5: Rewrite WasteGatheringViewModel around the wizard controller

**Files:**
- Modify: `app/src/main/java/com/ppnam/station4aa/ui/waste/WasteGatheringViewModel.kt`
- Modify: `app/src/main/java/com/ppnam/station4aa/navigation/AppNavGraph.kt`

**Interfaces:**
- Consumes: `WasteWizardController`/`WizardStep`/`WasteTransactionDraft`/`ScanDispatchResult` (Task 2); `WasteCollectionEvent.create(..., bagCode, now)` (Task 3); existing `ScanEventBus`/`ScanEvent.Barcode`, `MqttConnectionManager`, `WasteCollectionPublisher`, `OperatorSessionHolder`, `SettingsRepository`, `AuthUseCase`, `ConnectionStatus`/`connectionStatusFlow` — all unchanged.
- Produces (new public ViewModel surface, consumed by Task 6's screen): `connectionStatus: StateFlow<ConnectionStatus>`, `pendingCount: StateFlow<Int>`, `session: StateFlow<OperatorSession?>`, `collectedBy: StateFlow<String>`, `step: StateFlow<WizardStep>`, `draft: StateFlow<WasteTransactionDraft>`, `stepError: StateFlow<String?>`, `lastQueuedMessage: StateFlow<String?>`, and functions `onMachineCodeSubmitted(raw: String)`, `onOperatorIdSubmitted(raw: String)`, `onWasteTypeConfirmed(type: WasteTypeCatalog)`, `onBagCodeSubmitted(raw: String)`, `onCancelTransaction()`, `onReviewConfirmed()`, `dismissLastQueuedMessage()`, `logout()`. Constructor gains a `scanEventBus: ScanEventBus` parameter.

This task has no new automated tests of its own — `WasteWizardController` (Task 2) already covers every step-transition/validation/cancel/ignore scenario this ViewModel delegates to, and this repo has no mocking library to cheaply fake the ViewModel's five other constructor dependencies (`MqttConnectionManager`, `WasteCollectionPublisher`, etc. are concrete classes with no existing test doubles). Task 8's on-device walkthrough is this task's verification.

- [ ] **Step 1: Replace WasteGatheringViewModel.kt**

Replace the full file content:

```kotlin
package com.ppnam.station4aa.ui.waste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ppnam.station4aa.data.mqtt.MqttConnectionManager
import com.ppnam.station4aa.data.mqtt.MqttConnectionState
import com.ppnam.station4aa.data.mqtt.WasteCollectionPublisher
import com.ppnam.station4aa.data.rfid.ScanEvent
import com.ppnam.station4aa.data.rfid.ScanEventBus
import com.ppnam.station4aa.data.session.OperatorSession
import com.ppnam.station4aa.data.session.OperatorSessionHolder
import com.ppnam.station4aa.data.settings.SettingsRepository
import com.ppnam.station4aa.domain.model.WasteCollectionEvent
import com.ppnam.station4aa.domain.model.WasteTypeCatalog
import com.ppnam.station4aa.domain.usecase.AuthUseCase
import com.ppnam.station4aa.domain.wizard.ScanDispatchResult
import com.ppnam.station4aa.domain.wizard.WasteTransactionDraft
import com.ppnam.station4aa.domain.wizard.WasteWizardController
import com.ppnam.station4aa.domain.wizard.WizardStep
import com.ppnam.station4aa.ui.components.ConnectionStatus
import com.ppnam.station4aa.ui.components.connectionStatusFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the scan-driven waste collection wizard implementing
 * `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`'s "Required handheld workflow",
 * per `docs/superpowers/specs/2026-08-05-scan-driven-waste-wizard-design.md`. All step transitions
 * (scan machine → scan operator → select+confirm waste type → scan bag) only mutate local
 * [wizardController] state; [onReviewConfirmed] is the wizard's one and only MQTT publish point.
 */
class WasteGatheringViewModel(
    private val settingsRepository: SettingsRepository,
    private val connectionManager: MqttConnectionManager,
    private val publisher: WasteCollectionPublisher,
    private val sessionHolder: OperatorSessionHolder,
    private val authUseCase: AuthUseCase,
    private val scanEventBus: ScanEventBus,
) : ViewModel() {

    private val wizardController = WasteWizardController()

    val connectionStatus: StateFlow<ConnectionStatus> = connectionStatusFlow(
        connectionManager.connectionState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionStatus.Offline)

    /** Durably queued events awaiting PUBACK — surfaced so the operator can see unsynced work
     * exists, per the contract's reconciliation-visibility requirement. */
    val pendingCount: StateFlow<Int> = publisher.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val session: StateFlow<OperatorSession?> = sessionHolder.session

    /** The logged-in operator's own identity — "collectedBy" per the contract. SessionWatcher
     * guarantees this screen is never reached without a session. */
    val collectedBy: StateFlow<String> = session
        .map { it?.operatorName?.ifBlank { it.operatorId } ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _step = MutableStateFlow(wizardController.step)
    val step: StateFlow<WizardStep> = _step.asStateFlow()

    private val _draft = MutableStateFlow(wizardController.draft)
    val draft: StateFlow<WasteTransactionDraft> = _draft.asStateFlow()

    /** Set by a failed scan or manual-entry attempt on the active step; cleared on every new
     * attempt, successful advance, or cancel. */
    private val _stepError = MutableStateFlow<String?>(null)
    val stepError: StateFlow<String?> = _stepError.asStateFlow()

    private val _lastQueuedMessage = MutableStateFlow<String?>(null)
    val lastQueuedMessage: StateFlow<String?> = _lastQueuedMessage.asStateFlow()

    init {
        viewModelScope.launch { connectionManager.connect(settingsRepository.current()) }
        // Flush anything durably queued while offline as soon as the broker link comes back —
        // the contract requires retrying with the exact original payload, which retryPending()
        // does by re-reading the immutable rows rather than re-deriving anything.
        viewModelScope.launch {
            connectionManager.connectionState
                .filter { it == MqttConnectionState.CONNECTED }
                .collect { publisher.retryPending() }
        }
        viewModelScope.launch {
            scanEventBus.events.filterIsInstance<ScanEvent.Barcode>().collect { event ->
                when (val result = wizardController.handleScannedValue(event.value)) {
                    is ScanDispatchResult.Applied -> syncFromController(result.error)
                    ScanDispatchResult.Ignored -> Unit
                }
            }
        }
    }

    fun onMachineCodeSubmitted(raw: String) {
        syncFromController(wizardController.submitMachineCode(raw))
    }

    fun onOperatorIdSubmitted(raw: String) {
        syncFromController(wizardController.submitOperatorId(raw))
    }

    fun onWasteTypeConfirmed(type: WasteTypeCatalog) {
        wizardController.confirmWasteType(type)
        syncFromController(null)
    }

    fun onBagCodeSubmitted(raw: String) {
        syncFromController(wizardController.submitBagCode(raw))
    }

    /** Available on every step, including the review dialog. Always a full reset — there is no
     * partial-edit recovery path. */
    fun onCancelTransaction() {
        wizardController.cancel()
        syncFromController(null)
    }

    /**
     * The review dialog's Confirm action — the wizard's one and only publish point. Builds the
     * complete event from the finished draft plus the session's [collectedBy], durably queues it
     * exactly like the previous single-page form did (see [WasteCollectionPublisher.submit]),
     * then resets the wizard for the next transaction.
     */
    fun onReviewConfirmed() {
        val current = wizardController.draft
        val machineCode = requireNotNull(current.machineCode) { "REVIEW reached without machineCode" }
        val machineOperatorUserId = requireNotNull(current.machineOperatorUserId) {
            "REVIEW reached without machineOperatorUserId"
        }
        val wasteType = requireNotNull(current.wasteType) { "REVIEW reached without wasteType" }
        val bagCode = requireNotNull(current.bagCode) { "REVIEW reached without bagCode" }

        val event = WasteCollectionEvent.create(
            machineCode = machineCode,
            machineName = machineCode,
            wasteTypeCode = wasteType.code,
            collectedBy = collectedBy.value,
            machineOperatorUserId = machineOperatorUserId,
            bagCode = bagCode,
        )
        viewModelScope.launch {
            publisher.submit(event)
            wizardController.cancel()
            syncFromController(null)
            // Acceptance criterion 20: a PUBACK (or even just a durable local write) is never
            // presented as Station 4 business acceptance — "Queued", not "Submitted"/"Accepted".
            _lastQueuedMessage.value = "Queued ${event.collectionId} for delivery"
        }
    }

    private fun syncFromController(error: String?) {
        _step.value = wizardController.step
        _draft.value = wizardController.draft
        _stepError.value = error
    }

    fun dismissLastQueuedMessage() {
        _lastQueuedMessage.value = null
    }

    /** SessionWatcher (mounted at the nav-graph root) handles the actual navigation back to
     * Login once [sessionHolder]'s session goes null — this just triggers that. */
    fun logout() {
        viewModelScope.launch { authUseCase.logout() }
    }
}
```

- [ ] **Step 2: Wire the new constructor parameter at the call site**

In `AppNavGraph.kt`, update the `WasteGatheringViewModel(...)` construction (inside the `NavRoutes.WASTE_GATHERING` composable) to pass `scanEventBus`:

```kotlin
                        WasteGatheringViewModel(
                            settingsRepository = container.settingsRepository,
                            connectionManager = container.connectionManager,
                            publisher = container.wasteCollectionPublisher,
                            sessionHolder = container.operatorSessionHolder,
                            authUseCase = container.authUseCase,
                            scanEventBus = container.scanEventBus,
                        )
```

- [ ] **Step 3: Build to confirm it compiles**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: FAIL — `WasteGatheringScreen.kt` (Task 6, not yet rewritten) still references the old `WasteGatheringViewModel` API (`machineOperatorUserId`, `submit(machine, wasteType)`, etc.) that no longer exists. This is expected; Task 6 fixes it. Confirm the *only* compile errors reported are in `WasteGatheringScreen.kt`, not in `WasteGatheringViewModel.kt` or `AppNavGraph.kt`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ppnam/station4aa/ui/waste/WasteGatheringViewModel.kt app/src/main/java/com/ppnam/station4aa/navigation/AppNavGraph.kt
git commit -m "feat: drive WasteGatheringViewModel from the scan wizard controller"
```

---

## Task 6: Rewrite WasteGatheringScreen as a step wizard, remove MachineCatalog

**Files:**
- Modify: `app/src/main/java/com/ppnam/station4aa/ui/waste/WasteGatheringScreen.kt`
- Delete: `app/src/main/java/com/ppnam/station4aa/domain/model/MachineCatalog.kt`

**Interfaces:**
- Consumes: Task 5's `WasteGatheringViewModel` public surface (`step`, `draft`, `stepError`, `collectedBy`, and all the `on*` functions); `WasteTypeCatalog` (existing, unchanged); `WizardStep` (Task 2).
- Produces: nothing consumed by a later task — this is the outermost layer.

No new automated test (Compose UI tests aren't part of this repo's existing test setup — no `androidTest` Compose test infra exists yet). Verified on-device in Task 8.

- [ ] **Step 1: Replace WasteGatheringScreen.kt**

Replace the full file content:

```kotlin
package com.ppnam.station4aa.ui.waste

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ppnam.station4aa.domain.model.WasteTypeCatalog
import com.ppnam.station4aa.domain.wizard.WizardStep
import com.ppnam.station4aa.ui.components.AppScaffold
import com.ppnam.station4aa.ui.theme.AmberPrimary
import com.ppnam.station4aa.ui.theme.GraphiteBorder
import com.ppnam.station4aa.ui.theme.GraphiteSurface
import com.ppnam.station4aa.ui.theme.TextMuted
import com.ppnam.station4aa.ui.theme.TextPrimary
import com.ppnam.station4aa.ui.theme.WarningOrange

@Composable
fun WasteGatheringScreen(
    onSettings: () -> Unit,
    viewModel: WasteGatheringViewModel,
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val session by viewModel.session.collectAsState()
    val collectedBy by viewModel.collectedBy.collectAsState()
    val step by viewModel.step.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val stepError by viewModel.stepError.collectAsState()
    val lastQueuedMessage by viewModel.lastQueuedMessage.collectAsState()

    if (step == WizardStep.REVIEW) {
        AlertDialog(
            onDismissRequest = { viewModel.onCancelTransaction() },
            title = { Text("Confirm waste collection", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConfirmRow("Machine", draft.machineCode.orEmpty())
                    ConfirmRow("Waste type", draft.wasteType?.display.orEmpty())
                    ConfirmRow("Wastage operator", collectedBy)
                    ConfirmRow("Machine operator ID", draft.machineOperatorUserId.orEmpty())
                    ConfirmRow("Bag code", draft.bagCode.orEmpty())
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onReviewConfirmed() }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onCancelTransaction() }) { Text("Cancel") }
            },
            containerColor = GraphiteSurface
        )
    }

    AppScaffold(
        title = "Waste Gathering",
        status = connectionStatus,
        onSettings = onSettings,
        operatorName = session?.operatorName?.ifBlank { session?.operatorId },
        operatorRole = session?.role,
        onLogout = viewModel::logout,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (pendingCount > 0) {
                Text(
                    "$pendingCount collection${if (pendingCount == 1) "" else "s"} queued, awaiting delivery",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarningOrange,
                )
            }
            lastQueuedMessage?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            }

            StepIndicator(step)

            when (step) {
                WizardStep.SCAN_MACHINE -> ScanStep(
                    label = "Scan machine code",
                    errorMessage = stepError,
                    onSubmit = viewModel::onMachineCodeSubmitted,
                )
                WizardStep.SCAN_OPERATOR -> ScanStep(
                    label = "Scan machine operator code",
                    errorMessage = stepError,
                    onSubmit = viewModel::onOperatorIdSubmitted,
                )
                WizardStep.SELECT_WASTE_TYPE -> WasteTypeStep(
                    onConfirm = viewModel::onWasteTypeConfirmed,
                )
                WizardStep.SCAN_BAG -> ScanStep(
                    label = "Scan bag code",
                    errorMessage = stepError,
                    onSubmit = viewModel::onBagCodeSubmitted,
                )
                WizardStep.REVIEW -> Unit // rendered as the AlertDialog above
            }

            TextButton(onClick = { viewModel.onCancelTransaction() }) {
                Text("Cancel transaction", color = WarningOrange)
            }
        }
    }
}

private val WIZARD_STEP_ORDINALS = mapOf(
    WizardStep.SCAN_MACHINE to 1,
    WizardStep.SCAN_OPERATOR to 2,
    WizardStep.SELECT_WASTE_TYPE to 3,
    WizardStep.SCAN_BAG to 4,
    WizardStep.REVIEW to 4,
)

@Composable
private fun StepIndicator(step: WizardStep) {
    val label = when (step) {
        WizardStep.SCAN_MACHINE -> "Scan machine code"
        WizardStep.SCAN_OPERATOR -> "Scan machine operator code"
        WizardStep.SELECT_WASTE_TYPE -> "Select waste type"
        WizardStep.SCAN_BAG -> "Scan bag code"
        WizardStep.REVIEW -> "Review and confirm"
    }
    Text(
        "Step ${WIZARD_STEP_ORDINALS.getValue(step)} of 4 \u2014 $label",
        style = MaterialTheme.typography.labelLarge,
        color = AmberPrimary,
    )
}

@Composable
private fun ScanStep(
    label: String,
    errorMessage: String?,
    onSubmit: (String) -> Unit,
) {
    var manualValue by remember(label) { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(
            "Scan the barcode, or enter it manually below.",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
        OutlinedTextField(
            value = manualValue,
            onValueChange = { manualValue = it },
            label = { Text("Manual entry") },
            singleLine = true,
            isError = errorMessage != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmberPrimary,
                focusedLabelColor = AmberPrimary,
                cursorColor = AmberPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (errorMessage != null) {
            Text(errorMessage, style = MaterialTheme.typography.labelSmall, color = WarningOrange)
        }
        Button(
            onClick = {
                onSubmit(manualValue)
                manualValue = ""
            },
            enabled = manualValue.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Submit")
        }
    }
}

@Composable
private fun WasteTypeStep(onConfirm: (WasteTypeCatalog) -> Unit) {
    var selected by remember { mutableStateOf(WasteTypeCatalog.GENERAL) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Select waste type", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        EnumDropdownSelector(
            label = "Waste Type",
            options = WasteTypeCatalog.entries,
            selected = selected,
            display = { it.display },
            onSelected = { selected = it },
        )
        Button(
            onClick = { onConfirm(selected) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Confirm")
        }
    }
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdownSelector(
    label: String,
    options: List<T>,
    selected: T,
    display: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
        border = BorderStroke(1.dp, GraphiteBorder),
    ) {
        Box(Modifier.padding(12.dp)) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                TextField(
                    value = display(selected),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(label, color = TextMuted) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(display(option)) },
                            onClick = {
                                onSelected(option)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Delete the now-unused MachineCatalog**

```bash
git rm app/src/main/java/com/ppnam/station4aa/domain/model/MachineCatalog.kt
```

- [ ] **Step 3: Build the full app to confirm everything compiles**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL, no remaining references to `MachineCatalog` anywhere (it was only used by the two files this task and Task 5 rewrote).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ppnam/station4aa/ui/waste/WasteGatheringScreen.kt
git commit -m "feat: rewrite Waste Gathering screen as a scan-driven step wizard"
```

---

## Task 7: Full test suite and build

**Files:** none (verification only).

**Interfaces:** none — this task only runs commands.

- [ ] **Step 1: Run the full unit test suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL — every suite passes, including the untouched `MqttTopicsTest`, `ScramCryptoTest`, `SessionStateTest`, and this plan's new/updated `WasteCollectionValidatorTest`, `WasteWizardControllerTest`, `WasteCollectionEventTest`.

- [ ] **Step 2: Assemble the debug APK**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: If either fails, fix and re-run before proceeding**

Do not proceed to Task 8 with a red build or a failing test — fix the specific failure, re-run just that command, then re-run both commands above from clean.

(No commit — nothing changes unless Step 3's fix path is taken, in which case commit that fix with a message describing what was actually wrong.)

---

## Task 8: On-device verification

**Files:** none (manual verification only, using the already-connected handheld).

**Interfaces:** none.

A device (`HC720DE260100322`) is already connected via `adb` with `com.ppnam.station4aa` installed and logged in. This task installs the new build and drives the wizard through both the scan path (via simulated DataWedge broadcasts — `DataWedgeReceiver` is registered with `RECEIVER_EXPORTED`, so `adb shell am broadcast` can reach it) and the manual-entry fallback path, confirming the end-to-end result matches the design: one queued event, correct `bagCode`, `pendingCount` behavior, and the "Queued ..." messaging.

- [ ] **Step 1: Install the freshly built debug APK**

```powershell
& "C:\Users\Jonathan\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "C:\Dev\PPNAM_Station_4_AA\app\build\outputs\apk\debug\app-debug.apk"
```

Expected: `Success`.

- [ ] **Step 2: Launch the app and get to the Waste Gathering screen**

If not already logged in from a prior session, log in first; then confirm the app is on `NavRoutes.WASTE_GATHERING` — take a screenshot to confirm:

```powershell
$adb = "C:\Users\Jonathan\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell screencap -p /sdcard/wizard_step1.png
& $adb pull /sdcard/wizard_step1.png "C:\Users\Jonathan\AppData\Local\Temp\claude\C--Dev-PPNAM-Station-4-AA\c977ff2f-5e47-4a57-80f9-ae430e8c1fcb\scratchpad\wizard_step1.png"
```

Expected: screenshot shows "Step 1 of 4 — Scan machine code".

- [ ] **Step 3: Simulate a machine-code barcode scan**

```powershell
& $adb shell am broadcast -a com.ppnam.station4aa.ACTION_SCAN --es com.symbol.datawedge.data_string "EXT-04" --es com.symbol.datawedge.label_type "LABEL-TYPE-CODE128"
```

Then screenshot again. Expected: now on "Step 2 of 4 — Scan machine operator code" — the machine scan advanced the wizard.

- [ ] **Step 4: Simulate a machine-operator barcode scan**

```powershell
& $adb shell am broadcast -a com.ppnam.station4aa.ACTION_SCAN --es com.symbol.datawedge.data_string "MO-00427" --es com.symbol.datawedge.label_type "LABEL-TYPE-CODE128"
```

Screenshot. Expected: now on "Step 3 of 4 — Select waste type".

- [ ] **Step 5: Confirm a stray scan during SELECT_WASTE_TYPE is ignored**

```powershell
& $adb shell am broadcast -a com.ppnam.station4aa.ACTION_SCAN --es com.symbol.datawedge.data_string "should-be-ignored" --es com.symbol.datawedge.label_type "LABEL-TYPE-CODE128"
```

Screenshot. Expected: still on "Step 3 of 4 — Select waste type", not advanced and not showing "should-be-ignored" anywhere — confirms `ScanDispatchResult.Ignored` behaves correctly on a real device, not just in the unit test.

- [ ] **Step 6: Select a waste type and confirm it via the UI** (manual tap — use `adb shell input tap` against the dropdown and the Confirm button, coordinates read off the screenshot from Step 5)

Screenshot after. Expected: now on "Step 4 of 4 — Scan bag code".

- [ ] **Step 7: Use the manual-entry fallback for the bag code** (exercises the non-scan path, not just DataWedge)

Use `adb shell input tap` to focus the "Manual entry" field, `adb shell input text "BAG-00931"`, then tap Submit.

Screenshot after. Expected: the review dialog appears, listing Machine `EXT-04`, Waste type (whichever was selected in Step 6), Wastage operator (the logged-in operator), Machine operator ID `MO-00427`, Bag code `BAG-00931`.

- [ ] **Step 8: Confirm the transaction**

Tap Confirm (`adb shell input tap` at the dialog's Confirm button coordinates).

Screenshot after. Expected: back on "Step 1 of 4 — Scan machine code" (full reset), with a "Queued WC-..." message visible.

- [ ] **Step 9: Pull logcat to confirm no unexpected errors during the run**

```powershell
& $adb logcat -d -v time | Select-String -Pattern "com.ppnam.station4aa" | Select-String -Pattern "FATAL|AndroidRuntime|Exception" 
```

Expected: no matches (no crash, no uncaught exception during the walkthrough).

- [ ] **Step 10: Report the result**

Summarize pass/fail for each step above. If any step didn't match its expected result, treat it as a bug — return to the relevant earlier task and fix it (do not patch around it only at the UI layer) before considering this plan complete.
