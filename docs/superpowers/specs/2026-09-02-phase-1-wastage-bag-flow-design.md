# Final wastage bag process, Phase 1 — design

Source: `docs/README-Wastage-Bag-Process-Flow.md` (converted from
`PPNAM-Station-4-Final-Wastage-Bag-Process-Flow.pdf`). This design covers **Phase 1 only** — the
handheld's label-and-register flow. Phase 2 (scan and weigh at the Station 4 scale terminal) is
the WPF app's work and is out of scope for this repo.

## Context

The current wizard captures a machine code, a machine-operator code, a waste type and a bag code,
then publishes one schema v3 collection event. The new process removes the machine entirely and
adds a job number and a two-level category → type selection:

| Step | Today (v3) | Phase 1 (v4) |
|---|---|---|
| 1 | Scan machine code | Scan bag barcode |
| 2 | Scan machine-operator code | Scan or enter Job Number |
| 3 | Select waste type (4 placeholders) | Scan or enter Operator ID |
| 4 | Scan bag code | Select Waste Category |
| 5 | Review and confirm | Select Waste Type (18 real types) |
| 6 | — | Review and confirm |

The bag is single-use and its customer-created barcode has no business meaning and no allow-list;
it is the temporary key linking Phase 1 registration to the Phase 2 weight.

### A latent bug this work fixes

`WasteTypeCatalog` is a four-item invention (`WT-01` = "General", `WT-02` = "Recyclable",
`WT-03` = "Organic", `WT-04` = "Hazardous"). Station 4's real seed
(`PPNAM.Station4.Core/Data/Station4SchemaSql.cs:234-252`) defines `WT-01` = "Bubble breaks",
`WT-02` = "Startup", `WT-03` = "Technical", `WT-04` = "Winding". Against a real station, selecting
"Recyclable" today publishes `WT-02` and Station 4 records **Startup** — silent mislabelling, not
a rejection. Replacing this catalogue is required regardless of the rest of this design.

## Decisions

Recorded with their reasoning so a later reader can tell a choice from an accident.

1. **This app leads with schema v4.** Station 4's contract still specifies v3 with machine fields
   and a `bagCode` allow-list. This repo defines and publishes v4 ahead of the consumer, exactly
   as it did for v2 and v3. Updating `Station4_Wastage_MQTT_Contract.md` and the Station 4
   consumer is someone else's work.
2. **The catalogue is owned by the WPF app and synced to the handheld**, so a customer-requested
   change is maintained in one place. It is not hardcoded here.
3. **`wasteCategoryCode` is not on the wire.** The category is a local navigation aid only. Station
   4 derives the category from `wasteTypeCode`. Consequence, explicitly accepted: point-in-time
   category reporting must be solved on the Station 4 side (by snapshotting the allocation when it
   changes), because the capture-time category is not transmitted.
4. **Operator ID is the production operator** — a different person from the logged-in wastage
   operator. It is today's `machineOperatorUserId` renamed, now that the machine is not scanned.
   It is free-standing and is *not* checked against the active session.
5. **Job Number is an opaque required string.** No format rule and no list to validate against.
6. **Edit-from-review, but no mid-capture step-back.** A scan cannot be undone by another scan
   during capture; the review screen can jump to any single field and return.

## Wire contract — schema v4

### Collection event

Topic `PPNAM/station_4/waste/collection` (Settings-configurable), QoS 1, retain false — unchanged.

| Field | v3 | v4 |
|---|---|---|
| `schemaVersion` | `3` | `4` |
| `messageId` | ✓ | unchanged |
| `deviceId` | ✓ | unchanged |
| `operatorSessionId` | ✓ | unchanged |
| `collectionId` | ✓ | unchanged |
| `bagCode` | ✓ | unchanged |
| `collectedBy` | ✓ | unchanged (from the session) |
| `collectedAtUtc` | ✓ | unchanged |
| `machineCode` | ✓ | **removed** |
| `machineName` | ✓ | **removed** |
| `machineOperatorUserId` | ✓ | **renamed** → `operatorId` |
| `jobNumber` | — | **new**, required, opaque |
| `wasteTypeCode` | ✓ | unchanged |

`waste_collection_result` correlation is unchanged: `inResponseToMessageId` matched against the
outbox row, with the echoed identity check on `deviceId`/`operatorSessionId`/`collectionId`/
`bagCode` (all four still present in v4).

### Catalogue fetch

A new request/response pair on the existing login channel, using the same schema 4.1 auth envelope
handled by `MqttRequestChannel`. No new topic family, no fleet-standard change.

**Request** `PPNAM/station_4/{deviceId}/req/waste_catalogue_requested` — envelope plus
`operatorSessionId`. No other fields.

**Response** `PPNAM/station_4/{deviceId}/res/waste_catalogue`:

```json
{
  "messageId": "response-<request messageId>",
  "inResponseToMessageId": "<request messageId>",
  "schemaVersion": "4.1",
  "deviceId": "scanner_5c64df8d86a8",
  "timestampUtc": "2026-09-02T07:00:00.000000Z",
  "accepted": true,
  "catalogueVersion": "<opaque server-assigned string>",
  "categories": [
    { "code": "CAT-01", "name": "Process", "sortOrder": 1 }
  ],
  "wasteTypes": [
    { "code": "WT-01", "name": "Bubble breaks", "categoryCode": "CAT-01", "sortOrder": 1 }
  ]
}
```

- Station 4 sends **active types only**. The handheld renders exactly what it receives and never
  filters.
- `catalogueVersion` is opaque to the handheld: stored and displayed in Diagnostics so a support
  call can compare it against the station. It carries no ordering or comparison semantics here.
- No conditional or delta fetch. The whole catalogue is roughly 1 KB; always sending it in full
  removes a class of cache-invalidation bugs at negligible cost.
- Rejections use the existing shape (`accepted: false` plus a `lowercase_snake_case` `errorCode`),
  so `MqttOutcome` covers them with no new error handling.

## Catalogue subsystem

### Storage

Three new entities in the existing `WasteOutboxDatabase` (version 3 → 4):

- `WasteCategoryEntity` — `code` (PK), `name`, `sortOrder`
- `WasteTypeEntity` — `code` (PK), `name`, `categoryCode`, `sortOrder`
- `CatalogueMetaEntity` — single row: `catalogueVersion`, `syncedAtUtc`, `source`
  (`SEED` | `SYNCED`), `lastFailedAtUtc`

All three are replaced together inside one `@Transaction`, so the UI never observes a
half-applied catalogue.

### Seed

On first launch the tables are empty and `WasteCatalogueSeed` inserts the real `WT-01`…`WT-18`
codes and names taken from `Station4SchemaSql.cs:234-252`, all under a single provisional category
with code `CAT-00` and name `Uncategorised`, with `source = SEED`. `CAT-00` is a seed-only value
and carries no meaning to Station 4; the first successful sync replaces it wholesale along with
everything else.

`WT-01` Bubble breaks · `WT-02` Startup · `WT-03` Technical · `WT-04` Winding ·
`WT-05` Sticking & folding · `WT-06` Treat · `WT-07` Microns · `WT-08` Registration ·
`WT-09` Trimmings · `WT-10` Handles · `WT-11` Gusset & layflat · `WT-12` Color variation ·
`WT-13` Wrong size · `WT-14` Ghost prints · `WT-15` Setting/product change ·
`WT-16` Sample waste · `WT-17` Sweepings · `WT-18` Customer complaints

The `WasteTypeCatalog` enum is deleted.

### Sync

`SyncWasteCatalogueUseCase` runs after a successful login, on reconnect while a session is active,
and from a manual "Refresh catalogue" action in Settings → Diagnostics. It is fire-and-forget with
respect to the UI: a failed sync never blocks or interrupts a collection.

Two rules that matter more than they look:

- **An accepted-but-empty catalogue is treated as a rejection.** A response carrying zero waste
  types never replaces a working catalogue. Otherwise one bad server-side query silently leaves
  every handheld in the plant unable to select a waste type, with nothing on screen explaining why.
- **Replacement is wholesale, never merged.** The catalogue Station 4 sends *is* the catalogue.
  Merging would let a type deleted at the station live on indefinitely in the handheld's cache.

### Visibility

Settings → Diagnostics shows one line distinguishing three states:

- `Catalogue: built-in seed — never synced`
- `Catalogue: v<version> — synced <timestamp>`
- `Catalogue: v<version> — synced <timestamp>, last refresh failed <timestamp>`

Without this, a handheld quietly running the seed against a real station looks identical to a
correctly synced one — the exact failure mode the current four-item catalogue would have caused.

### Exposure

`WasteCatalogueRepository` gives the wizard `categories: Flow<List<Category>>` and
`typesFor(categoryCode): Flow<List<Type>>`, both ordered by `sortOrder`, plus `meta: Flow<CatalogueMeta>`
for Diagnostics. The wizard never touches Room or MQTT directly.

## Wizard, validation and event

### Step machine

```
SCAN_BAG → SCAN_JOB → SCAN_OPERATOR → SELECT_CATEGORY → SELECT_WASTE_TYPE → REVIEW
```

`WasteTransactionDraft` holds `bagCode`, `jobNumber`, `operatorId`, `category`, `wasteType`. The
last two are catalogue objects supplied by the ViewModel, not enum values.

`WasteWizardController` stays **pure** — no Room, no MQTT, no Android — which is what keeps its
tests fast and fake-free. Catalogue values reach it only as already-resolved `Category`/`Type`
objects, so it never validates a code against a list.

### Edit-from-review

```kotlin
fun editField(target: WizardStep)   // legal only from REVIEW
private var returnToReview = false
```

`editField` is callable only while `step == REVIEW`, and only with one of the five capture steps
as its target. Both are `check`ed and throw otherwise — the UI cannot produce either case by
construction, since edit affordances are rendered only on the review dialog's own rows.

Each `submit*`/`confirm*` gains one check on its existing success path: if `returnToReview`, go
straight back to `REVIEW` and clear the flag; otherwise advance normally. Two consequent rules:

- **Editing the category invalidates the type.** Changing category from A to B leaves a selected
  type belonging to A, so editing `SELECT_CATEGORY` clears `wasteType` and continues to
  `SELECT_WASTE_TYPE` rather than returning to review — the one deliberate override of
  `returnToReview`. Keeping the old type would put a contradiction on the review screen that the
  operator has no reason to notice.
- **Scans stay ignored at `REVIEW`.** Unchanged from today. A stray trigger-pull while the
  operator reads the confirmation must not quietly rewrite a field they are about to approve.

`cancel()` resets to `SCAN_BAG` and clears `returnToReview`.

### Validation

| Function | Change |
|---|---|
| `validateMachineCode` | deleted |
| `validateMachineOperatorUserId` | renamed `validateOperatorId`, rules identical (max 100, placeholder-rejecting) |
| `validateJobNumber` | **new** — max 100, placeholder-rejecting |
| `validateBagCode` | unchanged |
| `validateCollectedBy` | unchanged |

`validateJobNumber` rejects placeholders (`N/A`, `UNKNOWN`) where `validateBagCode` does not:
a bag code is always a scanned customer barcode, whereas a job number can be hand-typed by
someone who does not have one to hand.

### Event and outbox

`WasteCollectionEvent.create()` swaps `machineCode`/`machineName`/`machineOperatorUserId` for
`jobNumber`/`operatorId`; `SCHEMA_VERSION` becomes 4. `WasteOutboxEntity` changes identically.
`messageId`, `collectionId` and `collectedAtUtc` are still minted exactly once in `create()` and
never regenerated on retry.

The database version bump discards any events queued in the outbox at upgrade time, through the
`fallbackToDestructiveMigration()` already configured. This is deliberate rather than incidental:
a queued v3 event carries `machineCode` and no `jobNumber`, so once Station 4 is on v4 that event
can never be accepted — a migration would preserve only messages guaranteed to be rejected.

**Rollout consequence:** the upgrade must be timed for a moment when no handheld holds unsent
collections. This belongs in the deployment runbook.

## UI

The existing structure largely survives.

- `ScanStep` is already a reusable composable used three times; it stays used three times, now for
  bag / job / operator, with different labels and validators.
- `StepIndicator`'s step→number map grows to five capture steps.
- `WasteTypeStep` and a new `CategoryStep` render from `WasteCatalogueRepository` flows.
  `EnumDropdownSelector` is already generic over `<T>`; it is renamed `DropdownSelector` and takes
  a list plus a label lambda instead of enum values. The type step shows only types in the chosen
  category, ordered by `sortOrder`.
- The review `AlertDialog`'s `ConfirmRow` gains an optional `onEdit` lambda rendering a per-row
  edit affordance that calls `editField(step)`.
- Settings → Diagnostics gains the catalogue state line and a "Refresh catalogue" button, added to
  the existing card that already shows the read-only device id.

## Testing

The layers carrying the risk are pure or fakeable, and that is where the tests go. Written before
the corresponding implementation, per the repo's usual flow.

- `WasteWizardControllerTest` — extend the existing 11 cases to the six-step order; add
  edit-from-review round-tripping for each field, and specifically that editing the category clears
  the type and routes to `SELECT_WASTE_TYPE` rather than back to review.
- `WasteCollectionValidatorTest` — drop machine-code cases; add `validateJobNumber`, including its
  placeholder rejections.
- `WasteCollectionEventTest` — the v4 field set, `SCHEMA_VERSION == 4`, and that `create()` mints
  its three generated fields exactly once.
- `WasteCatalogueSyncTest` (new, against a fake request channel) — accepted-but-empty is rejected;
  replacement is wholesale not merged; a failed sync leaves the cached catalogue untouched; the
  seed applies only when the tables are empty.
- `WasteCatalogueSeedTest` (new) — all 18 codes present with names matching
  `Station4SchemaSql.cs`. This is the guard against the seed drifting from the station's list the
  way the current four-item catalogue did.

## Out of scope and dependencies

Phase 2 (scan and weigh at the scale terminal) is entirely the WPF app's work.

Three things must land on the Station 4 side before any of this functions against a real station.
None are in scope for this repo:

1. A category table plus the type→category allocation, and a maintenance screen for it. Nothing
   exists today — there are zero matches for "categor" anywhere in `C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4`.
2. An MQTT handler for `waste_catalogue_requested`.
3. A v4 collection consumer — `jobNumber`/`operatorId`, no machine fields, no bag allow-list.

Per `CLAUDE.md`, the Station 4 consumer is not yet even v2-compliant, so this widens an existing
gap rather than creating a new one. Until these land, the handheld runs on its seeded catalogue and
its publishes are rejected or quarantined — the same forward-looking posture the repo already took
for v2 and v3.

Also unresolved, and correctly so: the customer has not confirmed category names or the allocation
of the 18 types. That is precisely why the allocation is served from Station 4 rather than built
into this app — when it is confirmed, no handheld release is required.
