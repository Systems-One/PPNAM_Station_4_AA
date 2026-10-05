# Graph Report - PPNAM_Station_4_AA  (2026-10-04)

## Corpus Check
- 139 files · ~114,332 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1263 nodes · 1895 edges · 161 communities (56 shown, 105 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 174 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `915bc1cc`
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
- build.gradle.kts
- settings.gradle.kts
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
- AppScaffold
- WeighBagScreen
- WasteTransactionDraft
- .describe
- AutoSignOutTest
- RequestEnvelope.kt
- WasteCollectionResultMessageTest
- SessionStateTest
- gradlew
- ForegroundTracker
- ExampleInstrumentedTest
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
- Pair
- String
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
- WasteOutboxDao
- WasteOutboxEntity
- WasteType
- WasteCategory
- AppScaffold
- .observeByCollectionId
- CatalogueEntities.kt
- CatalogueMeta.kt

## God Nodes (most connected - your core abstractions)
1. `WasteWizardController` - 30 edges
2. `WasteGatheringViewModel` - 30 edges
3. `WasteWizardControllerTest` - 30 edges
4. `WasteCatalogueRepository` - 27 edges
5. `OperatorEntry` - 23 edges
6. `MqttConnectionManager` - 21 edges
7. `WasteOutboxEntity` - 20 edges
8. `SettingsViewModel` - 20 edges
9. `FakeWasteOutboxDao` - 20 edges
10. `FakeWasteCatalogueDao` - 20 edges

## Surprising Connections (you probably didn't know these)
- `toEvent()` --references--> `WasteCollectionEvent`  [EXTRACTED]
  app/src/main/java/com/mitas/ppnam/station4aa/data/local/WasteOutboxEntity.kt → app/src/main/java/com/mitas/ppnam/station4aa/domain/model/WasteCollectionEvent.kt
- `WasteGatheringScreen()` --calls--> `WasteTransactionDraft`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringScreen.kt → app/src/main/java/com/mitas/ppnam/station4aa/domain/wizard/WasteTransactionDraft.kt
- `AppNavGraph()` --calls--> `HomeScreen()`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/home/HomeScreen.kt
- `AppNavGraph()` --calls--> `HomeViewModel`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/home/HomeViewModel.kt
- `AppNavGraph()` --calls--> `LoginScreen()`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginScreen.kt

## Import Cycles
- None detected.

## Communities (161 total, 105 thin omitted)

### Community 0 - "LoginViewModel"
Cohesion: 0.07
Nodes (14): WasteCatalogueRepository, WasteCatalogueRequestPayload, WasteCatalogueResponse, WasteCategoryDto, WasteTypeDto, CatalogueSyncResult, Failed, Replaced (+6 more)

### Community 1 - "FakeWasteOutboxDao"
Cohesion: 0.08
Nodes (19): FakeWasteOutboxDao, Boolean, Flow, Int, Long, String, WasteCollectionResultChannelHandleIncomingTest, WasteCollectionResultChannelTest (+11 more)

### Community 2 - "MqttConnectionManager"
Cohesion: 0.15
Nodes (8): Mqtt5AsyncClient, MqttClientFactory, Keys, Boolean, Flow, SettingsRepository, AppSettings, Boolean

### Community 3 - "WasteGatheringViewModel"
Cohesion: 0.06
Nodes (39): ApplyStatusRow(), ConfigSection(), DiagnosticRow(), DiagnosticValueRow(), Boolean, Color, String, Unit (+31 more)

### Community 4 - "WasteWizardController"
Cohesion: 0.08
Nodes (8): Boolean, String, WasteWizardController, WasteWizardControllerTest, Applied, Ignored, ScanDispatchResult, WasteTransactionDraft

### Community 5 - "ScramCrypto"
Cohesion: 0.18
Nodes (9): DashboardTile(), HomeScreen(), Color, Int, Modifier, String, HomeViewModel, StateFlow (+1 more)

### Community 6 - "WasteCollectionPublisher"
Cohesion: 0.12
Nodes (11): WasteCapturePayload, WasteCaptureResultMessage, Failed, InvalidBagCode, Refused, RequestWasteCaptureUseCase, WasteCaptureOutcome, Weighed (+3 more)

### Community 7 - ".request"
Cohesion: 0.12
Nodes (16): Accepted, describe(), FailureKind, String, T, MqttOutcome, NoResponse, Rejected (+8 more)

### Community 8 - "WasteCollectionValidatorTest"
Cohesion: 0.16
Nodes (9): Flow, Int, SharedFlow, String, WasteCollectionPublisher, WasteCollectionMessage, create(), generateCollectionId() (+1 more)

### Community 9 - "WasteOutboxDao"
Cohesion: 0.08
Nodes (7): seedCatalogueSafely(), WasteCatalogueDao, create(), WasteOutboxDatabase, AppContainerTest, FakeWasteCatalogueDao, RoomDatabase

### Community 10 - "OperatorSession"
Cohesion: 0.12
Nodes (18): Int, Long, SharedPrefsPinLockoutStore, Blank, InMemoryPinLockoutStore, Boolean, Int, Long (+10 more)

### Community 12 - "AuthMessages.kt"
Cohesion: 0.07
Nodes (27): Result, String, ScramExchange, BadgeLoginPayload, OperatorContextResponse, ScramChallengeResponse, ScramProofPayload, ScramProofResponse (+19 more)

### Community 13 - "Scan-driven waste collection wizard — design"
Cohesion: 0.07
Nodes (26): Coverage, File Structure, Global Constraints, Review Focus, Self-review notes, Station 4 UI Audit Fixes Implementation Plan, Task 10: Settings form — string drafts, host/port validation, password toggle (blank = keep), auto sign-out minutes, Done applies, Uri keyboard, visible confirmation, friendly connect failure, "Log out" casing (static-06, static-20, S4-07 settings, S4-14 settings, S4-17 confirmation; §5 "Settings action & field set"), Task 11: Diagnostics — Station 4 row, Station 1 order, pill vocabulary, readable timestamps (static-17, S4-07 catalogue, §5 "Diagnostics rows") (+18 more)

### Community 14 - "FakeWasteCatalogueDao"
Cohesion: 0.05
Nodes (31): AppContainer, String, OperatorEntryDto, OperatorListResponse, List, String, OperatorDirectoryCodec, toEntry() (+23 more)

### Community 15 - "Waste Collection Result Alignment Implementation Plan"
Cohesion: 0.25
Nodes (4): FakeOutboxDao, Long, String, WasteCollectionPublisherTest

### Community 16 - ".create"
Cohesion: 0.22
Nodes (6): NullPruningTypeAdapterFactory, WireJson, JsonElement, TypeAdapter, TypeAdapterFactory, TypeToken

### Community 17 - "Scan-Driven Waste Collection Wizard Implementation Plan"
Cohesion: 0.19
Nodes (11): CollectionBanner, collectionBannerFlow(), collectionBannerFor(), CollectionBannerTracker, Flow, StateFlow, String, TrackedCollection (+3 more)

### Community 18 - ".validateRequiredIdentity"
Cohesion: 0.09
Nodes (21): A latent bug this work fixes, Catalogue fetch, Catalogue subsystem, Collection event, Context, Decisions, Edit-from-review, Event and outbox (+13 more)

### Community 20 - "MqttTopics"
Cohesion: 0.11
Nodes (18): Deviation from the spec, requiring a decision before Task 3, File Structure, Global Constraints, RFID Badge Scan Login Implementation Plan, Self-Review, Station-half remediation (Tasks 10–11), Task 0: Probe the C72's RFID delivery path, Task 10: Shadow-provision badge operators, and let the newest badge row decide (+10 more)

### Community 23 - ".build"
Cohesion: 0.23
Nodes (6): isResultForSession(), Boolean, String, ShownResultTracker, CollectionResultFilterTest, String

### Community 25 - ".onCreate"
Cohesion: 0.18
Nodes (4): InactivityMonitorTest, Long, Pair, Runnable

### Community 26 - ".request"
Cohesion: 0.17
Nodes (5): SessionGuard, Int, String, signedOutAfterMinutes(), SessionMessagesTest

### Community 28 - "DataWedgeReceiver"
Cohesion: 0.33
Nodes (4): DataWedgeReceiver, Context, BroadcastReceiver, Intent

### Community 29 - "Repo Rules"
Cohesion: 0.29
Nodes (6): External directory: C:\Dev\Clients\PPNAM\Station 4\PPNAM-Station-4, graphify, Handheld-triggered weight capture (contract 5.1.0 §9.2), No topic is configurable, at either end, Operator login is mirrored from Station 2 AA — and Station 4's backend has now caught up, Repo Rules

### Community 30 - "WasteCollectionResultMessageTest"
Cohesion: 0.15
Nodes (12): Global Constraints, Phase 1 Wastage Bag Flow Implementation Plan, Task 1: Catalogue domain models and seed data, Task 2: Room storage for the catalogue, Task 3: Waste catalogue repository, Task 4: Catalogue sync over MQTT, Task 5: Validator support for the new fields, Task 6: Phase 1 wizard and schema v4, end to end (+4 more)

### Community 31 - "SessionStateTest"
Cohesion: 0.15
Nodes (12): 2026-08-05 addendum: contract bumped to schema v3 mid-implementation, Cancel-anywhere addition, Context, Error handling, Goals, Non-goals, Scan-driven waste collection wizard — design, State machine (+4 more)

### Community 32 - "gradlew"
Cohesion: 0.15
Nodes (12): 1. Wire contract, 2. Badge resolution, 3. Online-only failure, 4. Session issuance, 5. Processor wiring, 6. Android, 7. Testing, 8. Risks and rollout (+4 more)

### Community 33 - "ExampleInstrumentedTest"
Cohesion: 0.17
Nodes (11): Global Constraints, Task 1: WasteCollectionResultMessage wire DTO, Task 2: Make the collection topic Settings-configurable, Task 3: Outbox schema — terminal ACCEPTED/REJECTED statuses and result fields, Task 4: WasteCollectionResultChannel — subscribe, correlate, apply outcome, Task 5: Wire WasteCollectionPublisher to the result channel and configurable topic, Task 6: Wire the new dependencies in AppContainer, Task 7: Surface rejected results to the operator (+3 more)

### Community 35 - "ExampleUnitTest"
Cohesion: 0.18
Nodes (10): Global Constraints, Scan-Driven Waste Collection Wizard Implementation Plan, Task 1: Extend WasteCollectionValidator with machine-code and bag-code rules, Task 2: Pure wizard step-transition controller, Task 3: Correct WasteCollectionEvent/WasteCollectionMessage for the schema v3 contract update, Task 4: Carry bagCode/deviceId/operatorSessionId through the local outbox, Task 5: Rewrite WasteGatheringViewModel around the wizard controller, Task 6: Rewrite WasteGatheringScreen as a step wizard, remove MachineCatalog (+2 more)

### Community 36 - "SessionState.kt"
Cohesion: 0.33
Nodes (4): InactivityMonitor, Boolean, Long, Runnable

### Community 41 - "build.gradle.kts"
Cohesion: 0.22
Nodes (4): android, Boolean, MainActivity, ComponentActivity

### Community 42 - "settings.gradle.kts"
Cohesion: 0.29
Nodes (4): AppNavGraph(), Bundle, SessionWatcher(), NavHostController

### Community 44 - "LoginViewModel"
Cohesion: 0.09
Nodes (20): ConnectionStatus, connectionStatusFlow(), connectionStatusStateFlow(), Boolean, Flow, StateFlow, resolveConnectionStatus(), Error (+12 more)

### Community 45 - "WasteCatalogueRepository"
Cohesion: 0.22
Nodes (6): describeCatalogue(), formatTimestampForDisplay(), String, CatalogueStatusTest, CatalogueMeta, ZoneId

### Community 46 - "Final wastage bag process, Phase 1 — design"
Cohesion: 0.25
Nodes (5): consumeEnterKeyUp(), EnterKeyGuard, android, Boolean, Modifier

### Community 48 - "WasteGatheringViewModel"
Cohesion: 0.14
Nodes (7): Boolean, Int, List, StateFlow, String, WasteGatheringViewModel, WizardStep

### Community 49 - "WasteType"
Cohesion: 0.33
Nodes (4): AutoSignOut, Int, Long, String

### Community 52 - "CaptureRefusals"
Cohesion: 0.47
Nodes (4): CaptureRefusal, CaptureRefusals, Entry, Map

### Community 55 - "WasteCatalogueSeedTest"
Cohesion: 0.60
Nodes (4): Barcode, RfidTag, ScanEvent, ScanEventBus

### Community 58 - "WasteGatheringScreen"
Cohesion: 0.36
Nodes (10): CatalogueStep(), ConfirmRow(), DropdownSelector(), List, String, T, Unit, ScanStep() (+2 more)

### Community 59 - "WeighBagViewModel"
Cohesion: 0.33
Nodes (7): Boolean, StateFlow, String, Problem, WeighBagViewModel, Weighed, WeighFeedback

### Community 60 - "AppScaffold"
Cohesion: 0.32
Nodes (6): DiscardDraftDialog(), ExitAppDialog(), brandOutlinedButtonColors(), brandTextButtonColors(), PPNAMStation4AATheme(), ButtonColors

### Community 61 - "WeighBagScreen"
Cohesion: 0.36
Nodes (7): formatKilograms(), String, Unit, ProblemCard(), WeighBagScreen(), WeighedCard(), Double

### Community 62 - "WasteTransactionDraft"
Cohesion: 0.33
Nodes (5): Control points, Flow diagram, Phase 1 — Label and register the disposable wastage bag, Phase 2 — Scan and weigh the same bag at Station 4, PPNAM Station 4 — Final Wastage Bag Process

### Community 63 - ".describe"
Cohesion: 0.50
Nodes (3): CollectionRejection, CollectionRejections, String

### Community 68 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 153 - "WasteOutboxDao"
Cohesion: 0.21
Nodes (5): Flow, Int, Long, String, WasteOutboxDao

### Community 154 - "WasteOutboxEntity"
Cohesion: 0.14
Nodes (8): List, Long, Status, toEvent(), toOutboxEntity(), WasteOutboxEntity, List, List

### Community 155 - "WasteType"
Cohesion: 0.22
Nodes (3): WasteCatalogueSeed, toDomain(), WasteType

### Community 157 - "AppScaffold"
Cohesion: 0.29
Nodes (5): AppScaffold(), Boolean, String, Unit, LoginScreen()

### Community 159 - "CatalogueEntities.kt"
Cohesion: 0.70
Nodes (4): CatalogueMetaEntity, toEntity(), WasteCategoryEntity, WasteTypeEntity

## Knowledge Gaps
- **125 isolated node(s):** `Status`, `FailureKind`, `EmptyPayload`, `WireJson`, `ScramPurpose` (+120 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **105 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `WasteGatheringViewModel` connect `WasteGatheringViewModel` to `WasteWizardController`, `ScramCrypto`, `settings.gradle.kts`, `AuthMessages.kt`, `LoginViewModel`, `Scan-Driven Waste Collection Wizard Implementation Plan`, `WasteGatheringScreen`, `WasteType`, `WasteCategory`?**
  _High betweenness centrality (0.151) - this node is a cross-community bridge._
- **Why does `SettingsViewModel` connect `WasteGatheringViewModel` to `FakeWasteOutboxDao`, `MqttConnectionManager`, `ScramCrypto`, `settings.gradle.kts`, `AuthMessages.kt`, `LoginViewModel`?**
  _High betweenness centrality (0.091) - this node is a cross-community bridge._
- **Why does `AppNavGraph()` connect `settings.gradle.kts` to `WasteGatheringViewModel`, `ScramCrypto`, `LoginViewModel`, `WasteGatheringViewModel`, `WeighBagScreen`, `WasteGatheringScreen`, `WeighBagViewModel`, `AppScaffold`?**
  _High betweenness centrality (0.078) - this node is a cross-community bridge._
- **Are the 19 inferred relationships involving `WasteCatalogueRepository` (e.g. with `.`a seed failure is swallowed rather than propagating`()` and `.`a successful seed still populates the catalogue`()`) actually correct?**
  _`WasteCatalogueRepository` has 19 INFERRED edges - model-reasoned connections that need verification._
- **Are the 2 inferred relationships involving `OperatorEntry` (e.g. with `.`decode drops blank usernames and falls back displayName to username`()` and `.`encodes exactly username and displayName per entry`()`) actually correct?**
  _`OperatorEntry` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Status`, `FailureKind`, `EmptyPayload` to the rest of the system?**
  _125 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `LoginViewModel` be split into smaller, more focused modules?**
  _Cohesion score 0.07305669199298656 - nodes in this community are weakly interconnected._