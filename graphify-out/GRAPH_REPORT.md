# Graph Report - PPNAM_Station_4_AA  (2026-11-06)

## Corpus Check
- 156 files · ~631,448 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1484 nodes · 2130 edges · 261 communities (53 shown, 208 thin omitted)
- Extraction: 90% EXTRACTED · 10% INFERRED · 0% AMBIGUOUS · INFERRED: 207 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `f1c4f100`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- LoginViewModel
- FakeWasteOutboxDao
- MqttConnectionManager
- WasteGatheringViewModel
- WasteWizardController
- ScramCrypto
- WasteCollectionPublisher
- .request
- WasteCollectionValidatorTest
- WasteOutboxDao
- OperatorSession
- SecureCredentialStore
- AuthMessages.kt
- Scan-driven waste collection wizard — design
- FakeWasteCatalogueDao
- Waste Collection Result Alignment Implementation Plan
- .create
- Scan-Driven Waste Collection Wizard Implementation Plan
- .validateRequiredIdentity
- SettingsViewModel.kt
- MqttTopics
- WasteCollectionEventTest
- ScramCryptoTest
- .build
- ScanEvent
- .onCreate
- .request
- MqttSchema
- DataWedgeReceiver
- Repo Rules
- WasteCollectionResultMessageTest
- SessionStateTest
- gradlew
- ExampleInstrumentedTest
- ResponseEnvelope
- ExampleUnitTest
- SessionState.kt
- NavRoutes.kt
- Color.kt
- Type.kt
- ScanSoundsTest
- WasteWizardControllerTest
- AppNavGraph
- LoginViewModel
- WasteCatalogueRepository
- Final wastage bag process, Phase 1 — design
- Global Constraints
- WasteGatheringViewModel
- WasteType
- .sync
- CaptureRefusalTest
- CaptureRefusals
- .advanceTo
- ScanDispatchResult
- WasteCatalogueSeedTest
- PPNAM Station 4 — Final Wastage Bag Process
- HomeScreenTest
- WasteGatheringScreen
- WeighBagViewModel
- ScanFeedback
- WasteGatheringScreen
- WasteTransactionDraft
- .describe
- AutoSignOutTest
- RequestEnvelope.kt
- WasteCollectionResultMessageTest
- SessionStateTest
- gradlew
- ForegroundTracker
- ExampleUnitTest
- ResponseEnvelope.kt
- SessionState.kt
- Boolean
- ByteArray
- Int
- String
- Boolean
- Flow
- List
- String
- List
- Context
- String
- Flow
- Int
- List
- String
- Context
- String
- Boolean
- ByteArray
- Mqtt5AsyncClient
- Result
- StateFlow
- String
- Unit
- Any
- Class
- Long
- String
- T
- Instant
- String
- Any
- Gson
- String
- SharedFlow
- String
- Gson
- T
- SharedFlow
- Boolean
- ByteArray
- String
- String
- Instant
- String
- String
- String
- Boolean
- Int
- String
- Flow
- Int
- List
- String
- Throwable
- Flow
- Int
- List
- String
- Instant
- String
- Any
- Class
- Long
- AppScaffold
- T
- Any
- Class
- Flow
- Int
- List
- Long
- Pair
- String
- T
- Throwable
- build.gradle.kts
- settings.gradle.kts
- WasteOutboxDao
- WasteOutboxEntity
- WasteType
- ConnectionStatus
- AppScaffold
- .observeByCollectionId
- CatalogueEntities.kt
- CatalogueMeta.kt
- Int
- Long
- String
- Boolean
- Long
- Runnable
- Int
- String
- Result
- String
- android
- Boolean
- ScanDispatchResult
- MainActivity
- brandTextButtonColors
- Boolean
- Flow
- StateFlow
- android
- Boolean
- Modifier
- HomeScreenTest
- WeighBagScreen
- WasteType
- content.md
- StateFlow
- PPNAM handheld SOP template (1.0)
- Flow
- Job
- List
- StateFlow
- String
- String
- Boolean
- Int
- String
- Throwable
- Boolean
- Job
- StateFlow
- String
- Type.kt
- List
- String
- T
- Unit
- Boolean
- Int
- List
- StateFlow
- String
- Flow
- Int
- List
- Long
- String
- Boolean
- Flow
- Int
- List
- Long
- String
- String
- Long
- Pair
- Runnable
- settings.gradle.kts
- SharedFlow
- String
- Gson
- T
- SharedFlow
- Boolean
- ByteArray
- String
- String
- String
- Any
- Class
- Long
- Pair
- String
- T
- Boolean
- String
- List
- String
- List
- List
- List
- String
- List
- Any
- Class
- List
- Long
- String
- T

## God Nodes (most connected - your core abstractions)
1. `WasteWizardController` - 46 edges
2. `WasteGatheringViewModel` - 36 edges
3. `WasteWizardControllerTest` - 36 edges
4. `WasteCatalogueRepository` - 27 edges
5. `MqttConnectionManager` - 21 edges
6. `FakeWasteOutboxDao` - 20 edges
7. `FakeWasteCatalogueDao` - 20 edges
8. `FakeWasteCatalogueDao` - 19 edges
9. `WasteCollectionValidatorTest` - 19 edges
10. `FakeOutboxDao` - 18 edges

## Surprising Connections (you probably didn't know these)
- `WasteGatheringScreen()` --calls--> `WasteTransactionDraft`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringScreen.kt → app/src/main/java/com/mitas/ppnam/station4aa/domain/wizard/WasteTransactionDraft.kt
- `AppNavGraph()` --calls--> `LoginScreen()`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginScreen.kt
- `AppNavGraph()` --calls--> `LoginViewModel`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginViewModel.kt
- `AppNavGraph()` --calls--> `SettingsScreen()`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsScreen.kt
- `AppNavGraph()` --calls--> `SettingsViewModel`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsViewModel.kt

## Import Cycles
- None detected.

## Communities (261 total, 208 thin omitted)

### Community 0 - "LoginViewModel"
Cohesion: 0.07
Nodes (15): WasteCatalogueRepository, WasteCatalogueRequestPayload, WasteCatalogueResponse, WasteCategoryDto, WasteTypeDto, CatalogueSource, CatalogueSyncResult, Failed (+7 more)

### Community 1 - "FakeWasteOutboxDao"
Cohesion: 0.06
Nodes (17): MqttClientFactory, Keys, SettingsRepository, AppSettings, FakeWasteOutboxDao, WasteCollectionResultChannelHandleIncomingTest, WasteCollectionResultChannelTest, WasteCollectionResultMessage (+9 more)

### Community 2 - "MqttConnectionManager"
Cohesion: 0.11
Nodes (18): WasteOperatorPayload, WasteOperatorResultMessage, Failed, Invalid, String, LookupWasteOperatorUseCase, Refused, Resolved (+10 more)

### Community 3 - "WasteGatheringViewModel"
Cohesion: 0.06
Nodes (27): SharedPrefsPinLockoutStore, Blank, InMemoryPinLockoutStore, LockedOut, lockoutMessage(), PinGate, PinGateResult, PinLockoutStore (+19 more)

### Community 4 - "WasteWizardController"
Cohesion: 0.21
Nodes (3): WasteTransactionDraft, Boolean, WasteWizardController

### Community 5 - "ScramCrypto"
Cohesion: 0.20
Nodes (9): Dimension ratings, False positives dropped, Overall assessment, P1: Critical, P2: Important, P3: Polish, Resolution (branch `fix/skill-critique-2026-11-06`), verified on the emulator. See `after/`, Suite-level (left alone) (+1 more)

### Community 6 - "WasteCollectionPublisher"
Cohesion: 0.17
Nodes (17): attr(), build_html(), BuildError, check_order(), figure(), fonts_css(), Images, inline() (+9 more)

### Community 7 - ".request"
Cohesion: 0.07
Nodes (19): Accepted, FailureKind, MqttOutcome, NoResponse, Rejected, RequestChannel, WasteCapturePayload, WasteCaptureResultMessage (+11 more)

### Community 8 - "WasteCollectionValidatorTest"
Cohesion: 0.06
Nodes (11): WasteOutboxDao, Status, toEvent(), WasteCollectionPublisher, FakeOutboxDao, WasteCollectionPublisherTest, WasteCollectionMessage, create() (+3 more)

### Community 9 - "WasteOutboxDao"
Cohesion: 0.07
Nodes (11): AppContainer, String, seedCatalogueSafely(), Application, WasteCatalogueDao, create(), WasteOutboxDatabase, PpnamApplication (+3 more)

### Community 10 - "OperatorSession"
Cohesion: 0.35
Nodes (3): isResultForSession(), ShownResultTracker, CollectionResultFilterTest

### Community 12 - "AuthMessages.kt"
Cohesion: 0.07
Nodes (25): ScramExchange, BadgeLoginPayload, OperatorContextResponse, ScramChallengeResponse, ScramProofPayload, ScramProofResponse, ScramPurpose, ScramStartPayload (+17 more)

### Community 13 - "Scan-driven waste collection wizard — design"
Cohesion: 0.07
Nodes (26): Coverage, File Structure, Global Constraints, Review Focus, Self-review notes, Station 4 UI Audit Fixes Implementation Plan, Task 10: Settings form — string drafts, host/port validation, password toggle (blank = keep), auto sign-out minutes, Done applies, Uri keyboard, visible confirmation, friendly connect failure, "Log out" casing (static-06, static-20, S4-07 settings, S4-14 settings, S4-17 confirmation; §5 "Settings action & field set"), Task 11: Diagnostics — Station 4 row, Station 1 order, pill vocabulary, readable timestamps (static-17, S4-07 catalogue, §5 "Diagnostics rows") (+18 more)

### Community 14 - "FakeWasteCatalogueDao"
Cohesion: 0.08
Nodes (14): OperatorEntryDto, OperatorListResponse, OperatorDirectoryCodec, toEntry(), SharedPrefsOperatorDirectoryStore, InMemoryOperatorDirectoryStore, OperatorDirectoryStore, normalize() (+6 more)

### Community 15 - "Waste Collection Result Alignment Implementation Plan"
Cohesion: 0.09
Nodes (21): A latent bug this work fixes, Catalogue fetch, Catalogue subsystem, Collection event, Context, Decisions, Edit-from-review, Event and outbox (+13 more)

### Community 16 - ".create"
Cohesion: 0.22
Nodes (6): NullPruningTypeAdapterFactory, WireJson, JsonElement, TypeAdapter, TypeAdapterFactory, TypeToken

### Community 17 - "Scan-Driven Waste Collection Wizard Implementation Plan"
Cohesion: 0.17
Nodes (13): toOutboxEntity(), WasteOutboxEntity, CollectionBanner, collectionBannerFlow(), collectionBannerFor(), CollectionBannerTracker, Flow, StateFlow (+5 more)

### Community 20 - "MqttTopics"
Cohesion: 0.11
Nodes (18): Deviation from the spec, requiring a decision before Task 3, File Structure, Global Constraints, RFID Badge Scan Login Implementation Plan, Self-Review, Station-half remediation (Tasks 10–11), Task 0: Probe the C72's RFID delivery path, Task 10: Shadow-provision badge operators, and let the newest badge row decide (+10 more)

### Community 23 - ".build"
Cohesion: 0.26
Nodes (8): Boolean, SharedFlow, StateFlow, String, Problem, WeighBagViewModel, Weighed, WeighFeedback

### Community 24 - "ScanEvent"
Cohesion: 0.15
Nodes (12): Global Constraints, Phase 1 Wastage Bag Flow Implementation Plan, Task 1: Catalogue domain models and seed data, Task 2: Room storage for the catalogue, Task 3: Waste catalogue repository, Task 4: Catalogue sync over MQTT, Task 5: Validator support for the new fields, Task 6: Phase 1 wizard and schema v4, end to end (+4 more)

### Community 25 - ".onCreate"
Cohesion: 0.15
Nodes (12): 2026-08-05 addendum: contract bumped to schema v3 mid-implementation, Cancel-anywhere addition, Context, Error handling, Goals, Non-goals, Scan-driven waste collection wizard — design, State machine (+4 more)

### Community 26 - ".request"
Cohesion: 0.17
Nodes (11): Global Constraints, Task 1: WasteCollectionResultMessage wire DTO, Task 2: Make the collection topic Settings-configurable, Task 3: Outbox schema — terminal ACCEPTED/REJECTED statuses and result fields, Task 4: WasteCollectionResultChannel — subscribe, correlate, apply outcome, Task 5: Wire WasteCollectionPublisher to the result channel and configurable topic, Task 6: Wire the new dependencies in AppContainer, Task 7: Surface rejected results to the operator (+3 more)

### Community 28 - "DataWedgeReceiver"
Cohesion: 0.40
Nodes (3): DataWedgeReceiver, BroadcastReceiver, Intent

### Community 29 - "Repo Rules"
Cohesion: 0.25
Nodes (7): External directory: C:\Dev\Clients\PPNAM\Station 4\PPNAM-Station-4, graphify, Handheld-triggered weight capture (contract 5.1.0 §9.2), No topic is configurable, at either end, Operator login is mirrored from Station 2 AA — and Station 4's backend has now caught up, Production-operator name lookup (contract 5.4.0 §9.3), Repo Rules

### Community 30 - "WasteCollectionResultMessageTest"
Cohesion: 0.20
Nodes (3): SessionGuard, signedOutAfterMinutes(), SessionMessagesTest

### Community 31 - "SessionStateTest"
Cohesion: 0.18
Nodes (6): describeCatalogue(), formatTimestampForDisplay(), CatalogueStatusTest, CatalogueMeta, CatalogueEntitiesTest, ZoneId

### Community 32 - "gradlew"
Cohesion: 0.15
Nodes (12): 1. Wire contract, 2. Badge resolution, 3. Online-only failure, 4. Session issuance, 5. Processor wiring, 6. Android, 7. Testing, 8. Risks and rollout (+4 more)

### Community 35 - "ExampleUnitTest"
Cohesion: 0.18
Nodes (10): Global Constraints, Scan-Driven Waste Collection Wizard Implementation Plan, Task 1: Extend WasteCollectionValidator with machine-code and bag-code rules, Task 2: Pure wizard step-transition controller, Task 3: Correct WasteCollectionEvent/WasteCollectionMessage for the schema v3 contract update, Task 4: Carry bagCode/deviceId/operatorSessionId through the local outbox, Task 5: Rewrite WasteGatheringViewModel around the wizard controller, Task 6: Rewrite WasteGatheringScreen as a step wizard, remove MachineCatalog (+2 more)

### Community 41 - "ScanSoundsTest"
Cohesion: 0.10
Nodes (4): CollectionVerdictSounds, Boolean, String, ScanSoundsTest

### Community 43 - "AppNavGraph"
Cohesion: 0.18
Nodes (9): AppNavGraph(), DashboardTile(), HomeScreen(), Color, Int, Modifier, String, HomeViewModel (+1 more)

### Community 48 - "WasteGatheringViewModel"
Cohesion: 0.60
Nodes (4): Barcode, RfidTag, ScanEvent, ScanEventBus

### Community 49 - "WasteType"
Cohesion: 0.50
Nodes (3): CollectionRejection, CollectionRejections, String

### Community 52 - "CaptureRefusals"
Cohesion: 0.47
Nodes (4): CaptureRefusal, CaptureRefusals, Entry, Map

### Community 60 - "ScanFeedback"
Cohesion: 0.18
Nodes (8): String, LocalRefusalDebounce, ScanSound, ScanSounds, Context, ScanFeedback, SoundPool, Vibrator

### Community 61 - "WasteGatheringScreen"
Cohesion: 0.32
Nodes (12): BannerTone, CatalogueStep(), ConfirmRow(), DropdownSelector(), List, String, T, Unit (+4 more)

### Community 62 - "WasteTransactionDraft"
Cohesion: 0.33
Nodes (5): Control points, Flow diagram, Phase 1 — Label and register the disposable wastage bag, Phase 2 — Scan and weigh the same bag at Station 4, PPNAM Station 4 — Final Wastage Bag Process

### Community 68 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 137 - "Long"
Cohesion: 0.29
Nodes (6): /critique-screen — Station 4 handheld (v1.2.4), 2026-11-06, Overall assessment, P1 — Critical, P2 — Important, P3 — Polish, Resolution (branch `fix/visual-critique-2026-11-06`), verified on the emulator. See `after/`

### Community 138 - "AppScaffold"
Cohesion: 0.19
Nodes (11): AppScaffold(), ConnectionPill(), Boolean, Modifier, String, Unit, LogoutConfirmDialog(), HomeHeaderCard() (+3 more)

### Community 155 - "WasteType"
Cohesion: 0.06
Nodes (21): OperatorSession, OperatorSessionHolder, Boolean, Int, Job, List, SharedFlow, StateFlow (+13 more)

### Community 156 - "ConnectionStatus"
Cohesion: 0.29
Nodes (6): ConnectionStatus, connectionStatusFlow(), connectionStatusStateFlow(), resolveConnectionStatus(), ConnectionStatusTest, CoroutineScope

### Community 173 - "ScanDispatchResult"
Cohesion: 0.35
Nodes (5): Applied, Ignored, LookupOperator, ScanDispatchResult, String

### Community 174 - "MainActivity"
Cohesion: 0.20
Nodes (4): MainActivity, PPNAMStation4AATheme(), Bundle, ComponentActivity

### Community 175 - "brandTextButtonColors"
Cohesion: 0.31
Nodes (7): DiscardDraftDialog(), ExitAppDialog(), LoginScreen(), brandOutlinedButtonColors(), brandTextButtonColors(), neutralTextButtonColors(), ButtonColors

### Community 183 - "WeighBagScreen"
Cohesion: 0.24
Nodes (9): Flow, ScanSoundEffect(), formatKilograms(), String, Unit, ProblemCard(), WeighBagScreen(), WeighedCard() (+1 more)

### Community 184 - "WasteType"
Cohesion: 0.21
Nodes (16): ApplyStatusRow(), ConfigSection(), DiagnosticRow(), DiagnosticValueRow(), Boolean, Color, String, Unit (+8 more)

### Community 185 - "content.md"
Cohesion: 0.12
Nodes (15): Automatic sign-out, Before you start {#before}, Finishing up {#finishing}, Messages and what to do {#messages}, Purpose and scope {#purpose}, Scan sounds and vibration {#sounds}, Settings {#settings}, Signing in {#signin} (+7 more)

### Community 187 - "PPNAM handheld SOP template (1.0)"
Cohesion: 0.20
Nodes (9): Blocks, Checking the result, content.md, Files, How a repo uses it (the rule), Per-app identity, PPNAM handheld SOP template (1.0), Sections: fixed order (+1 more)

## Knowledge Gaps
- **159 isolated node(s):** `Status`, `FailureKind`, `EmptyPayload`, `WireJson`, `ScramPurpose` (+154 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **208 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `WasteGatheringViewModel` connect `WasteType` to `WasteWizardController`, `AppNavGraph`, `Scan-Driven Waste Collection Wizard Implementation Plan`, `ConnectionStatus`, `ScanFeedback`, `WasteGatheringScreen`?**
  _High betweenness centrality (0.142) - this node is a cross-community bridge._
- **Why does `AppNavGraph()` connect `AppNavGraph` to `WasteGatheringViewModel`, `AuthMessages.kt`, `MainActivity`, `brandTextButtonColors`, `.build`, `WeighBagScreen`, `WasteType`, `WasteType`, `WasteGatheringScreen`?**
  _High betweenness centrality (0.079) - this node is a cross-community bridge._
- **Why does `LoginViewModel` connect `AuthMessages.kt` to `AppNavGraph`, `ConnectionStatus`, `FakeWasteCatalogueDao`, `brandTextButtonColors`?**
  _High betweenness centrality (0.077) - this node is a cross-community bridge._
- **Are the 12 inferred relationships involving `WasteWizardController` (e.g. with `.`a badge read on the bag step repeated by one trigger buzzes once`()` and `.`a bag code refused by validation and read again within 1_5 s buzzes once`()`) actually correct?**
  _`WasteWizardController` has 12 INFERRED edges - model-reasoned connections that need verification._
- **Are the 19 inferred relationships involving `WasteCatalogueRepository` (e.g. with `.`a seed failure is swallowed rather than propagating`()` and `.`a successful seed still populates the catalogue`()`) actually correct?**
  _`WasteCatalogueRepository` has 19 INFERRED edges - model-reasoned connections that need verification._
- **Are the 8 inferred relationships involving `MqttConnectionManager` (e.g. with `.publisher()` and `.`a result for a row queued under an earlier sign-in is still applied and emitted`()`) actually correct?**
  _`MqttConnectionManager` has 8 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Status`, `FailureKind`, `EmptyPayload` to the rest of the system?**
  _160 weakly-connected nodes found - possible documentation gaps or missing edges._