# Graph Report - s4  (2026-10-05)

## Corpus Check
- 140 files · ~109,937 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1275 nodes · 1790 edges · 239 communities (53 shown, 186 thin omitted)
- Extraction: 90% EXTRACTED · 10% INFERRED · 0% AMBIGUOUS · INFERRED: 176 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `739193ff`
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
- build.gradle.kts
- settings.gradle.kts
- Type.kt
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
- build.gradle.kts
- settings.gradle.kts
- WasteOutboxDao
- WasteOutboxEntity
- WasteType
- WasteCategory
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
- Boolean
- String
- Unit
- Boolean
- Flow
- StateFlow
- android
- Boolean
- Modifier
- Color
- Int
- Modifier
- String
- StateFlow
- String
- Throwable
- Flow
- Job
- List
- StateFlow
- String
- String
- Boolean
- Color
- String
- Unit
- Boolean
- Int
- String
- Throwable
- Boolean
- Job
- StateFlow
- String
- List
- String
- T
- Unit
- Boolean
- Int
- List
- StateFlow
- String
- String
- Unit
- Boolean
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
- String
- Long
- Pair
- Runnable

## God Nodes (most connected - your core abstractions)
1. `WasteWizardController` - 30 edges
2. `WasteWizardControllerTest` - 30 edges
3. `WasteCatalogueRepository` - 27 edges
4. `MqttConnectionManager` - 24 edges
5. `WasteGatheringViewModel` - 24 edges
6. `OperatorEntry` - 22 edges
7. `FakeWasteOutboxDao` - 20 edges
8. `FakeWasteCatalogueDao` - 20 edges
9. `toEvent()` - 19 edges
10. `FakeWasteCatalogueDao` - 19 edges

## Surprising Connections (you probably didn't know these)
- `WasteGatheringScreen()` --calls--> `WasteTransactionDraft`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringScreen.kt → app/src/main/java/com/mitas/ppnam/station4aa/domain/wizard/WasteTransactionDraft.kt
- `AppNavGraph()` --calls--> `SessionWatcher()`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/session/SessionWatcher.kt
- `AppNavGraph()` --calls--> `HomeScreen()`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/home/HomeScreen.kt
- `AppNavGraph()` --calls--> `LoginScreen()`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginScreen.kt
- `AppNavGraph()` --calls--> `LoginViewModel`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginViewModel.kt

## Import Cycles
- None detected.

## Communities (239 total, 186 thin omitted)

### Community 0 - "LoginViewModel"
Cohesion: 0.08
Nodes (10): WasteCatalogueRepository, WasteCatalogueRequestPayload, WasteCatalogueResponse, WasteCategoryDto, WasteTypeDto, FakeWasteCatalogueDao, WasteCatalogueRepositoryTest, FakeRequestChannel (+2 more)

### Community 1 - "FakeWasteOutboxDao"
Cohesion: 0.08
Nodes (23): WasteCollectionResultMessage, Boolean, ByteArray, Mqtt5AsyncClient, Result, StateFlow, String, Unit (+15 more)

### Community 2 - "MqttConnectionManager"
Cohesion: 0.10
Nodes (18): WasteCapturePayload, WasteCaptureResultMessage, Failed, InvalidBagCode, String, Refused, RequestWasteCaptureUseCase, WasteCaptureOutcome (+10 more)

### Community 3 - "WasteGatheringViewModel"
Cohesion: 0.24
Nodes (12): ApplyStatusRow(), ConfigSection(), DiagnosticRow(), DiagnosticValueRow(), SectionLabel(), SettingsScreen(), SettingsTextField(), SettingsToggleRow() (+4 more)

### Community 4 - "WasteWizardController"
Cohesion: 0.33
Nodes (4): String, Applied, Ignored, ScanDispatchResult

### Community 5 - "ScramCrypto"
Cohesion: 0.18
Nodes (4): AppNavGraph(), HomeViewModel, SettingsViewModel, ViewModel

### Community 6 - "WasteCollectionPublisher"
Cohesion: 0.13
Nodes (14): describeConnectFailure(), parsePort(), savedButNotConnectedMessage(), SettingsFieldErrors, validateSettingsDraft(), ApplyState, Failure, Idle (+6 more)

### Community 7 - ".request"
Cohesion: 0.18
Nodes (8): MqttRequestChannel, Accepted, FailureKind, MqttOutcome, NoResponse, Rejected, RequestChannel, Nothing

### Community 8 - "WasteCollectionValidatorTest"
Cohesion: 0.06
Nodes (10): WasteCollectionMessage, create(), generateCollectionId(), WasteCollectionEvent, WasteOutboxDao, toEvent(), WasteCollectionPublisher, FakeOutboxDao (+2 more)

### Community 9 - "WasteOutboxDao"
Cohesion: 0.08
Nodes (7): WasteCatalogueDao, create(), WasteOutboxDatabase, AppContainerTest, FakeWasteCatalogueDao, seedCatalogueSafely(), RoomDatabase

### Community 10 - "OperatorSession"
Cohesion: 0.07
Nodes (26): Coverage, File Structure, Global Constraints, Review Focus, Self-review notes, Station 4 UI Audit Fixes Implementation Plan, Task 10: Settings form — string drafts, host/port validation, password toggle (blank = keep), auto sign-out minutes, Done applies, Uri keyboard, visible confirmation, friendly connect failure, "Log out" casing (static-06, static-20, S4-07 settings, S4-14 settings, S4-17 confirmation; §5 "Settings action & field set"), Task 11: Diagnostics — Station 4 row, Station 1 order, pill vocabulary, readable timestamps (static-17, S4-07 catalogue, §5 "Diagnostics rows") (+18 more)

### Community 11 - "SecureCredentialStore"
Cohesion: 0.26
Nodes (5): Boolean, ByteArray, String, SecureCredentialStore, SecretKey

### Community 12 - "AuthMessages.kt"
Cohesion: 0.07
Nodes (23): BadgeLoginPayload, OperatorContextResponse, ScramChallengeResponse, ScramProofPayload, ScramProofResponse, ScramPurpose, ScramStartPayload, ScramExchange (+15 more)

### Community 13 - "Scan-driven waste collection wizard — design"
Cohesion: 0.13
Nodes (12): SharedPrefsPinLockoutStore, Blank, InMemoryPinLockoutStore, LockedOut, lockoutMessage(), PinGate, PinGateResult, PinLockoutStore (+4 more)

### Community 14 - "FakeWasteCatalogueDao"
Cohesion: 0.05
Nodes (30): OperatorEntryDto, OperatorListResponse, List, String, OperatorDirectoryCodec, toEntry(), List, SharedPrefsOperatorDirectoryStore (+22 more)

### Community 15 - "Waste Collection Result Alignment Implementation Plan"
Cohesion: 0.22
Nodes (6): Boolean, ByteArray, Int, String, ScramCrypto, ScramProof

### Community 16 - ".create"
Cohesion: 0.20
Nodes (8): Gson, T, NullPruningTypeAdapterFactory, WireJson, JsonElement, TypeAdapter, TypeAdapterFactory, TypeToken

### Community 17 - "Scan-Driven Waste Collection Wizard Implementation Plan"
Cohesion: 0.19
Nodes (10): Status, toOutboxEntity(), WasteOutboxEntity, CollectionBanner, collectionBannerFlow(), collectionBannerFor(), CollectionBannerTracker, TrackedCollection (+2 more)

### Community 18 - ".validateRequiredIdentity"
Cohesion: 0.09
Nodes (21): A latent bug this work fixes, Catalogue fetch, Catalogue subsystem, Collection event, Context, Decisions, Edit-from-review, Event and outbox (+13 more)

### Community 20 - "MqttTopics"
Cohesion: 0.11
Nodes (18): Deviation from the spec, requiring a decision before Task 3, File Structure, Global Constraints, RFID Badge Scan Login Implementation Plan, Self-Review, Station-half remediation (Tasks 10–11), Task 0: Probe the C72's RFID delivery path, Task 10: Shadow-provision badge operators, and let the newest badge row decide (+10 more)

### Community 22 - "ScramCryptoTest"
Cohesion: 0.10
Nodes (3): Boolean, WasteWizardController, WasteTransactionDraft

### Community 25 - ".onCreate"
Cohesion: 0.29
Nodes (3): isResultForSession(), ShownResultTracker, CollectionResultFilterTest

### Community 26 - ".request"
Cohesion: 0.15
Nodes (12): Global Constraints, Phase 1 Wastage Bag Flow Implementation Plan, Task 1: Catalogue domain models and seed data, Task 2: Room storage for the catalogue, Task 3: Waste catalogue repository, Task 4: Catalogue sync over MQTT, Task 5: Validator support for the new fields, Task 6: Phase 1 wizard and schema v4, end to end (+4 more)

### Community 27 - "MqttSchema"
Cohesion: 0.33
Nodes (4): Instant, String, MqttSchema, DateTimeFormatter

### Community 28 - "DataWedgeReceiver"
Cohesion: 0.40
Nodes (3): BroadcastReceiver, DataWedgeReceiver, Intent

### Community 29 - "Repo Rules"
Cohesion: 0.29
Nodes (6): External directory: C:\Dev\Clients\PPNAM\Station 4\PPNAM-Station-4, graphify, Handheld-triggered weight capture (contract 5.1.0 §9.2), No topic is configurable, at either end, Operator login is mirrored from Station 2 AA — and Station 4's backend has now caught up, Repo Rules

### Community 30 - "WasteCollectionResultMessageTest"
Cohesion: 0.15
Nodes (12): 2026-08-05 addendum: contract bumped to schema v3 mid-implementation, Cancel-anywhere addition, Context, Error handling, Goals, Non-goals, Scan-driven waste collection wizard — design, State machine (+4 more)

### Community 31 - "SessionStateTest"
Cohesion: 0.17
Nodes (11): Global Constraints, Task 1: WasteCollectionResultMessage wire DTO, Task 2: Make the collection topic Settings-configurable, Task 3: Outbox schema — terminal ACCEPTED/REJECTED statuses and result fields, Task 4: WasteCollectionResultChannel — subscribe, correlate, apply outcome, Task 5: Wire WasteCollectionPublisher to the result channel and configurable topic, Task 6: Wire the new dependencies in AppContainer, Task 7: Surface rejected results to the operator (+3 more)

### Community 32 - "gradlew"
Cohesion: 0.15
Nodes (12): 1. Wire contract, 2. Badge resolution, 3. Online-only failure, 4. Session issuance, 5. Processor wiring, 6. Android, 7. Testing, 8. Risks and rollout (+4 more)

### Community 33 - "ExampleInstrumentedTest"
Cohesion: 0.24
Nodes (3): MqttClientFactory, Keys, SettingsRepository

### Community 35 - "ExampleUnitTest"
Cohesion: 0.20
Nodes (3): SessionGuard, signedOutAfterMinutes(), SessionMessagesTest

### Community 37 - "NavRoutes.kt"
Cohesion: 0.18
Nodes (10): Global Constraints, Scan-Driven Waste Collection Wizard Implementation Plan, Task 1: Extend WasteCollectionValidator with machine-code and bag-code rules, Task 2: Pure wizard step-transition controller, Task 3: Correct WasteCollectionEvent/WasteCollectionMessage for the schema v3 contract update, Task 4: Carry bagCode/deviceId/operatorSessionId through the local outbox, Task 5: Rewrite WasteGatheringViewModel around the wizard controller, Task 6: Rewrite WasteGatheringScreen as a step wizard, remove MachineCatalog (+2 more)

### Community 40 - "Type.kt"
Cohesion: 0.54
Nodes (3): DeviceIdentity, Context, String

### Community 41 - "build.gradle.kts"
Cohesion: 0.20
Nodes (4): Bundle, MainActivity, PPNAMStation4AATheme(), ComponentActivity

### Community 42 - "settings.gradle.kts"
Cohesion: 0.24
Nodes (4): SessionWatcher(), OperatorSession, OperatorSessionHolder, NavHostController

### Community 44 - "LoginViewModel"
Cohesion: 0.29
Nodes (6): ConnectionStatusTest, ConnectionStatus, connectionStatusFlow(), connectionStatusStateFlow(), resolveConnectionStatus(), CoroutineScope

### Community 45 - "WasteCatalogueRepository"
Cohesion: 0.29
Nodes (5): EmptyPayload, Any, Gson, String, RequestEnvelope

### Community 46 - "Final wastage bag process, Phase 1 — design"
Cohesion: 0.48
Nodes (5): Barcode, SharedFlow, RfidTag, ScanEvent, ScanEventBus

### Community 49 - "WasteType"
Cohesion: 0.62
Nodes (4): CatalogueSyncResult, Failed, Replaced, SyncWasteCatalogueUseCase

### Community 52 - "CaptureRefusals"
Cohesion: 0.43
Nodes (5): CaptureRefusal, CaptureRefusals, Entry, String, Map

### Community 53 - ".advanceTo"
Cohesion: 0.52
Nodes (6): CatalogueStep(), ConfirmRow(), DropdownSelector(), ScanStep(), StepIndicator(), WasteGatheringScreen()

### Community 58 - "WasteGatheringScreen"
Cohesion: 0.40
Nodes (3): Boolean, String, UserTagPolicy

### Community 60 - "AppScaffold"
Cohesion: 0.18
Nodes (9): ButtonColors, DiscardDraftDialog(), ExitAppDialog(), AppScaffold(), DashboardTile(), HomeScreen(), LoginScreen(), brandOutlinedButtonColors() (+1 more)

### Community 61 - "WeighBagScreen"
Cohesion: 0.28
Nodes (9): formatKilograms(), ProblemCard(), WeighBagScreen(), WeighedCard(), Problem, WeighBagViewModel, Weighed, WeighFeedback (+1 more)

### Community 62 - "WasteTransactionDraft"
Cohesion: 0.33
Nodes (5): Control points, Flow diagram, Phase 1 — Label and register the disposable wastage bag, Phase 2 — Scan and weigh the same bag at Station 4, PPNAM Station 4 — Final Wastage Bag Process

### Community 68 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 155 - "WasteType"
Cohesion: 0.18
Nodes (4): WasteCatalogueSeed, toDomain(), WasteCategory, WasteType

### Community 156 - "WasteCategory"
Cohesion: 0.14
Nodes (7): CatalogueMeta, CatalogueSource, CatalogueEntitiesTest, describeCatalogue(), formatTimestampForDisplay(), CatalogueStatusTest, ZoneId

### Community 159 - "CatalogueEntities.kt"
Cohesion: 0.70
Nodes (4): CatalogueMetaEntity, toEntity(), WasteCategoryEntity, WasteTypeEntity

## Knowledge Gaps
- **123 isolated node(s):** `Status`, `FailureKind`, `EmptyPayload`, `ScramPurpose`, `ScramChallengeResponse` (+118 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **186 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `WasteGatheringViewModel` connect `WasteGatheringViewModel` to `ScramCrypto`, `LoginViewModel`, `Scan-Driven Waste Collection Wizard Implementation Plan`, `.advanceTo`, `ScramCryptoTest`, `WasteType`?**
  _High betweenness centrality (0.130) - this node is a cross-community bridge._
- **Why does `WasteCatalogueRepository` connect `LoginViewModel` to `WasteOutboxDao`, `WasteType`, `WasteCategory`?**
  _High betweenness centrality (0.081) - this node is a cross-community bridge._
- **Why does `LoginViewModel` connect `AuthMessages.kt` to `AppScaffold`, `ScramCrypto`, `LoginViewModel`, `FakeWasteCatalogueDao`?**
  _High betweenness centrality (0.074) - this node is a cross-community bridge._
- **Are the 19 inferred relationships involving `WasteCatalogueRepository` (e.g. with `.`a seed failure is swallowed rather than propagating`()` and `.`a successful seed still populates the catalogue`()`) actually correct?**
  _`WasteCatalogueRepository` has 19 INFERRED edges - model-reasoned connections that need verification._
- **Are the 8 inferred relationships involving `MqttConnectionManager` (e.g. with `.publisher()` and `.`a result for a row queued under an earlier sign-in is still applied and emitted`()`) actually correct?**
  _`MqttConnectionManager` has 8 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Status`, `FailureKind`, `EmptyPayload` to the rest of the system?**
  _123 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `LoginViewModel` be split into smaller, more focused modules?**
  _Cohesion score 0.08295625942684766 - nodes in this community are weakly interconnected._