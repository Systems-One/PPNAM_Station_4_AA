# Scan-driven waste collection wizard — design

## 2026-08-05 addendum: contract bumped to schema v3 mid-implementation

This design was originally written against contract document version 2.1.0, where `bagCode` was
planned as an *additive*, ignorable JSON property and the wire `schemaVersion` stayed `2`. The
contract was updated the same day to document version 3.0.0. The rest of this document is left as
originally written (it's still an accurate record of the wizard's UI/state-machine design, which
the contract bump doesn't touch), **except the two points below, which the plan
(`docs/superpowers/plans/2026-08-05-scan-driven-waste-wizard.md`, Task 3) now implements
correctly and this doc's "Wire/domain changes" section (below) does not — read Task 3, not this
section, for the actual field list:**

- `schemaVersion` is now `3`, not `2`.
- `bagCode` is a **required** contract field, validated by Station4's own
  `WastageBagCodePolicy`/`MqttMessageValidator` (`C:\Dev\PPNAM-Station-4\PPNAM.Station4.Core\
  Services\`) — not additive/ignorable, and not placeholder-checked (see the `WasteCollectionValidator`
  note below). It remains a value distinct from `collectionId`, which stays the handheld's own
  generated transaction ID.
- Separately, and unrelated to the bump: reading the actual Station4 validator while implementing
  this revealed `deviceId` and `operatorSessionId` have always been required contract fields that
  this app's wire payload has never included. Task 3 fixes this alongside the schema v3 change.

The contract document itself (`Station4_Wastage_MQTT_Contract.md`) is internally inconsistent as of
this bump: its §1 intro and identity table were updated to v3 language, but its §9 payload
example/JSON Schema/field table were left showing v2's stale integer `2` and no `bagCode`. Treat
the real Station4 code as authoritative over that stale section — see the plan's Global Constraints
for the exact citation.

## Context

The current Waste Gathering screen (`WasteGatheringScreen`/`WasteGatheringViewModel`) is a
single-page form: machine and waste type are picked from dropdowns (`MachineCatalog`,
`WasteTypeCatalog`), the machine-operator ID is typed into a text field, and a Submit button opens
a confirmation dialog before publishing one MQTT event.

The operator workflow is changing to a sequential scan-driven capture: scan a machine code, scan
a machine-operator code, select a waste type (with its own confirmation), then scan a bag code.
All three scan steps are 2D barcodes read via the existing `ScanEventBus`/`DataWedgeReceiver`
pipeline (the same mechanism `LoginViewModel` already uses for RFID badge scans, filtered here to
`ScanEvent.Barcode` instead).

An initial version of this request asked for the MQTT payload to be published after every field
update, with unpopulated fields sent as `null` and progressively filled in on later publishes.
That was evaluated against `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`, which
explicitly prohibits it:

- "It MUST NOT publish a partial event and enrich it later." (Required handheld workflow, step
  after 10)
- Reusing a `messageId` with changed content is defined as a conflicting attempt that Station 4
  MUST quarantine (QoS 1, retries, and idempotency table).
- "Corrections are not delivery retries... changing its machine operator by republishing is
  prohibited."

Decision: **stay contract-compliant.** The new scan sequence is built as local wizard state only.
Exactly one MQTT publish happens, at the end, identical in spirit to today's single Submit — the
wizard just changes how the operator builds up the draft before that one publish.

## Goals

- Replace the single-page form with a step-by-step wizard: `SCAN_MACHINE → SCAN_OPERATOR →
  SELECT_WASTE_TYPE → SCAN_BAG → REVIEW`.
- Each scan step accepts a 2D barcode scan or manual text entry as a fallback.
- The waste-type step keeps today's on-screen list selection, plus a step-local Confirm action.
- Add a `bagCode` field, captured on the handheld and published as an additive JSON property on
  the existing schema v2 payload (contract-legal: `additionalProperties: true`), but not one of
  the contract's 9 required fields.
- No step-back navigation. A "Cancel transaction" action is available on every step (not just at
  final review) and resets the wizard to `SCAN_MACHINE` with an empty draft.
- Preserve every existing publish-path guarantee unchanged: durable outbox write before first
  publish attempt, PUBACK-gated "delivered" status, "Queued" (never "Submitted"/"Accepted")
  messaging, retry-with-unchanged-payload on reconnect.

## Non-goals

- No incremental/partial MQTT publishing. Confirmed explicitly with the user after the contract
  conflict was raised.
- No change to the login flow, RFID badge scanning, settings, or MQTT connection handling.
- No real machine or waste-type directory integration — `WasteTypeCatalog` remains the same
  hardcoded placeholder list it is today. `MachineCatalog` is removed outright (see below), not
  replaced with a new lookup source.

## State machine

```kotlin
enum class WizardStep { SCAN_MACHINE, SCAN_OPERATOR, SELECT_WASTE_TYPE, SCAN_BAG, REVIEW }

data class WasteTransactionDraft(
    val machineCode: String? = null,
    val machineOperatorUserId: String? = null,
    val wasteType: WasteTypeCatalog? = null,
    val bagCode: String? = null,
)
```

`WasteGatheringViewModel` holds `_step: MutableStateFlow<WizardStep>` and
`_draft: MutableStateFlow<WasteTransactionDraft>`.

A single scan-collection job subscribes to `scanEventBus.events.filterIsInstance<ScanEvent.Barcode>()`
for the lifetime of the screen. On each event, it dispatches based on the *current* step value at
the time the event arrives:

- `SCAN_MACHINE` → treat as machine code input.
- `SCAN_OPERATOR` → treat as machine-operator ID input.
- `SCAN_BAG` → treat as bag code input.
- Any other step (`SELECT_WASTE_TYPE`, `REVIEW`) → ignored. A barcode scanned while on an
  unrelated step is dropped, not queued for later — this matches "no going back" and avoids a
  stray scan silently overwriting a step the operator has already moved past.

Manual text entry reuses the same per-step handling function as a scan would, so validation and
advancement logic is identical regardless of input source.

### Step transitions

1. **SCAN_MACHINE**: barcode or manual entry → validate (new `WasteCollectionValidator` rule:
   non-blank after trim, no control characters, ≤100 characters — no placeholder-value check,
   consistent with how `collectedBy` is treated today) → `draft.machineCode` set, `machineName`
   mirrors `machineCode` (no separate name is scanned or looked up — confirmed with user: "the
   machine code identifies it") → advance to `SCAN_OPERATOR`.
2. **SCAN_OPERATOR**: barcode or manual entry → existing `validateMachineOperatorUserId` (blank,
   control chars, length, placeholder-value rejection) → `draft.machineOperatorUserId` set →
   advance to `SELECT_WASTE_TYPE`.
3. **SELECT_WASTE_TYPE**: operator picks from the existing `WasteTypeCatalog` dropdown/list →
   taps a step-local **Confirm** button → `draft.wasteType` set → advance to `SCAN_BAG`.
4. **SCAN_BAG**: barcode or manual entry → new `validateBagCode` (blank/control-char/length checks
   only, max 100 characters — **not** placeholder-value rejection: see the 2026-08-05 addendum
   above, `WastageBagCodePolicy.TryNormalize` never rejects placeholders for bag codes) →
   `draft.bagCode` set → advance to `REVIEW`.
5. **REVIEW**: dialog (same visual style as today's `AlertDialog`) listing Machine, Waste type,
   Wastage operator (`collectedBy`, from session, unchanged), Machine operator ID, Bag code.
   - **Confirm** → calls `submit()`, which builds the event from the draft plus `collectedBy` and
     follows the exact same durable-write-then-publish path as today
     (`WasteCollectionPublisher.submit`) → wizard resets to `SCAN_MACHINE` with an empty draft.
   - **Cancel** → wizard resets to `SCAN_MACHINE` with an empty draft. No partial state is
     retained.

A validation failure at any scan/manual-entry step shows the existing inline red error text under
the field and does **not** advance the step — the operator re-scans or retypes.

## Cancel-anywhere addition

Beyond what was originally scoped, a **"Cancel transaction"** text action is shown on every step
(scan steps, the waste-type step, and the review dialog), not only at final review. Rationale: with
no step-back, a mis-scan on step 1 would otherwise force the operator through three more steps
just to reach a way to start over. Tapping it at any point resets to `SCAN_MACHINE` with an empty
draft, identical to Cancel at REVIEW.

## Wire/domain changes

- `WasteCollectionEvent` (`domain/model/WasteCollectionEvent.kt`): add required `bagCode`,
  `deviceId`, `operatorSessionId: String` fields. `create()` gains matching required parameters
  (trimmed like the other fields, no defaults). `toWireMessage()` includes all three. See the
  plan's Task 3 for the exact field order and why `deviceId`/`operatorSessionId` are included too.
- `WasteCollectionMessage` (`data/mqtt/dto/WasteCollectionMessage.kt`): add `bagCode`, `deviceId`,
  `operatorSessionId` as **required** wire properties — schema v3 validates all three (see the
  2026-08-05 addendum above); this is not the additive/ignorable field originally planned.
- `WasteOutboxEntity` (`data/local/WasteOutboxEntity.kt`): add `bagCode`, `deviceId`,
  `operatorSessionId: String` columns, plus `toEvent()`/`toOutboxEntity()` mapping updates.
- `WasteOutboxDatabase`: bump `version` 1 → 2. No `exportSchema`/migration infrastructure exists
  yet (`exportSchema = false`, no `Migration` classes anywhere in the codebase) and the outbox is
  a transient in-flight queue, not a permanent record — `fallbackToDestructiveMigration()` is
  acceptable here rather than authoring a real migration.
- `WasteCollectionValidator` (`domain/validation/WasteCollectionValidator.kt`): add
  `validateBagCode()`, reusing the existing `validateRequiredIdentity` helper with
  `rejectPlaceholders = false` (see the 2026-08-05 addendum — Station4's own
  `WastageBagCodePolicy.TryNormalize` never rejects placeholders for bag codes, unlike
  `machineOperatorUserId`) and a 100-character max (matching `WastageBagCodePolicy.MaximumCodeLength`).
  Also add a machine-code validation rule (non-blank/control-char/length, no placeholder check) for
  step 1.
- `MachineCatalog` (`domain/model/MachineCatalog.kt`): **deleted**. Confirmed via repo-wide grep
  that it's referenced only from `WasteGatheringScreen.kt`/`WasteGatheringViewModel.kt`, both of
  which are being rewritten by this change.
- `WasteTypeCatalog`: unchanged, still backs the on-screen list in `SELECT_WASTE_TYPE`.

## UI (`WasteGatheringScreen`)

Replaces the current `Column` of dropdowns/fields with:

- A step indicator (e.g. "Step 2 of 4 — Scan machine operator").
- The active step's content:
  - Scan steps: a prompt, a manual-entry `OutlinedTextField` + submit affordance as the fallback
    path, fed by the same handler the scan listener calls.
  - Select step: the existing `EnumDropdownSelector` for `WasteTypeCatalog`, plus a Confirm button.
  - Review step: the existing `AlertDialog` styling, now with 5 rows instead of 4.
- A "Cancel transaction" text button, present on every step.
- The existing `connectionStatus`/`pendingCount`/`lastQueuedMessage` banners stay at the top,
  unchanged.

## Error handling

- Inline validation errors: unchanged pattern from today (red `labelSmall` text under the field),
  now driven by the new `validateBagCode`/machine-code rules in addition to the existing operator
  ID rule.
- Cancel is always available and always performs a full reset — there is no partial-edit recovery
  path, consistent with the "no going back" decision.
- Everything downstream of `submit()` — durable write before publish, PUBACK-gated delivered
  status, "Queued ${collectionId}" messaging (never "Submitted"/"Accepted"), retry-on-reconnect
  with the unchanged original payload — is completely unchanged by this design.

## Testing

- `WasteCollectionValidatorTest` (or equivalent): cases for `validateBagCode` and the new
  machine-code rule (blank, control characters, over-length, placeholder values where applicable,
  valid).
- `WasteCollectionEvent`/wire-message test: confirm `bagCode` is present and correctly named in
  the serialized JSON.
- `WasteGatheringViewModel` tests: a barcode scanned during an unrelated step is dropped, not
  applied to any field; invalid input blocks step advancement; Cancel from any step resets fully
  to `SCAN_MACHINE`; a full happy-path walk from `SCAN_MACHINE` through `REVIEW` → Confirm produces
  exactly one queued event with all fields (`machineCode`, `machineName`, `wasteTypeCode`,
  `collectedBy`, `machineOperatorUserId`, `bagCode`, `collectedAtUtc`) populated correctly.
- Existing suites (`MqttTopicsTest`, etc.) are unaffected by this change.
