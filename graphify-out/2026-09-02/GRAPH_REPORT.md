# Graph Report - PPNAM_Station_4_AA  (2026-09-02)

## Corpus Check
- 90 files · ~61,236 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 759 nodes · 1218 edges · 58 communities (44 shown, 14 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 66 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `4a389e97`
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
- MqttTopicsTest
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
- PpnamApplication
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
- resolveConnectionStatus
- WasteCatalogueRepository
- Final wastage bag process, Phase 1 — design
- Global Constraints
- WasteGatheringScreen
- AppScaffold
- .sync
- .onCreate
- AppNavGraph
- SettingsViewModel
- LoginViewModel.kt
- WasteCatalogueSeedTest
- PPNAM Station 4 — Final Wastage Bag Process
- WasteTypeCatalog.kt

## God Nodes (most connected - your core abstractions)
1. `WasteCollectionValidatorTest` - 30 edges
2. `WasteGatheringViewModel` - 26 edges
3. `WasteWizardControllerTest` - 24 edges
4. `WasteCatalogueRepository` - 23 edges
5. `WasteWizardController` - 23 edges
6. `MqttConnectionManager` - 22 edges
7. `FakeWasteCatalogueDao` - 19 edges
8. `FakeWasteCatalogueDao` - 17 edges
9. `ScramCrypto` - 16 edges
10. `MqttTopicsTest` - 16 edges

## Surprising Connections (you probably didn't know these)
- `toEvent()` --references--> `WasteCollectionEvent`  [EXTRACTED]
  app/src/main/java/com/mitas/ppnam/station4aa/data/local/WasteOutboxEntity.kt → app/src/main/java/com/mitas/ppnam/station4aa/domain/model/WasteCollectionEvent.kt
- `AppNavGraph()` --calls--> `LoginViewModel`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginViewModel.kt
- `AppNavGraph()` --calls--> `SettingsScreen()`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsScreen.kt
- `AppNavGraph()` --calls--> `SettingsViewModel`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsViewModel.kt
- `AppNavGraph()` --calls--> `WasteGatheringScreen()`  [INFERRED]
  app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringScreen.kt

## Import Cycles
- None detected.

## Communities (58 total, 14 thin omitted)

### Community 0 - "LoginViewModel"
Cohesion: 0.30
Nodes (11): ConfigSection(), DiagnosticRow(), Boolean, String, SectionLabel(), SettingsScreen(), SettingsTextField(), SettingsToggleRow() (+3 more)

### Community 1 - "FakeWasteOutboxDao"
Cohesion: 0.09
Nodes (23): Long, Status, toEvent(), toOutboxEntity(), WasteOutboxEntity, WasteCollectionResultMessage, Accepted, evaluateOutcome() (+15 more)

### Community 2 - "MqttConnectionManager"
Cohesion: 0.09
Nodes (18): Mqtt5AsyncClient, MqttClientFactory, Boolean, ByteArray, Mqtt5AsyncClient, Result, StateFlow, String (+10 more)

### Community 3 - "WasteGatheringViewModel"
Cohesion: 0.07
Nodes (16): Boolean, List, WasteCatalogueSeed, toDomain(), CatalogueMeta, CatalogueSource, WasteCategory, WasteType (+8 more)

### Community 4 - "WasteWizardController"
Cohesion: 0.09
Nodes (7): Applied, Ignored, ScanDispatchResult, WasteTransactionDraft, String, WasteWizardController, WasteWizardControllerTest

### Community 5 - "ScramCrypto"
Cohesion: 0.36
Nodes (8): ApplyState, Failure, Idle, Locked, PinState, Success, Testing, Unlocked

### Community 6 - "WasteCollectionPublisher"
Cohesion: 0.22
Nodes (6): Boolean, ByteArray, Int, String, ScramCrypto, ScramProof

### Community 7 - ".request"
Cohesion: 0.10
Nodes (21): Accepted, describe(), FailureKind, String, T, MqttOutcome, NoResponse, Rejected (+13 more)

### Community 8 - "WasteCollectionValidatorTest"
Cohesion: 0.17
Nodes (10): WasteCollectionMessage, Flow, Int, SharedFlow, WasteCollectionPublisher, create(), generateCollectionId(), Instant (+2 more)

### Community 9 - "WasteOutboxDao"
Cohesion: 0.12
Nodes (10): Flow, Int, List, Long, String, WasteOutboxDao, create(), Context (+2 more)

### Community 11 - "SecureCredentialStore"
Cohesion: 0.24
Nodes (5): Boolean, ByteArray, String, SecureCredentialStore, SecretKey

### Community 12 - "AuthMessages.kt"
Cohesion: 0.10
Nodes (20): Result, String, ScramExchange, BadgeLoginPayload, OperatorContextResponse, ScramChallengeResponse, ScramProofPayload, ScramProofResponse (+12 more)

### Community 13 - "Scan-driven waste collection wizard — design"
Cohesion: 0.15
Nodes (12): 2026-08-05 addendum: contract bumped to schema v3 mid-implementation, Cancel-anywhere addition, Context, Error handling, Goals, Non-goals, Scan-driven waste collection wizard — design, State machine (+4 more)

### Community 14 - "MqttTopicsTest"
Cohesion: 0.31
Nodes (5): Flow, StateFlow, String, LoginViewModel, Job

### Community 15 - "Waste Collection Result Alignment Implementation Plan"
Cohesion: 0.17
Nodes (11): Global Constraints, Task 1: WasteCollectionResultMessage wire DTO, Task 2: Make the collection topic Settings-configurable, Task 3: Outbox schema — terminal ACCEPTED/REJECTED statuses and result fields, Task 4: WasteCollectionResultChannel — subscribe, correlate, apply outcome, Task 5: Wire WasteCollectionPublisher to the result channel and configurable topic, Task 6: Wire the new dependencies in AppContainer, Task 7: Surface rejected results to the operator (+3 more)

### Community 16 - ".create"
Cohesion: 0.20
Nodes (8): Gson, T, NullPruningTypeAdapterFactory, WireJson, JsonElement, TypeAdapter, TypeAdapterFactory, TypeToken

### Community 17 - "Scan-Driven Waste Collection Wizard Implementation Plan"
Cohesion: 0.18
Nodes (10): Global Constraints, Scan-Driven Waste Collection Wizard Implementation Plan, Task 1: Extend WasteCollectionValidator with machine-code and bag-code rules, Task 2: Pure wizard step-transition controller, Task 3: Correct WasteCollectionEvent/WasteCollectionMessage for the schema v3 contract update, Task 4: Carry bagCode/deviceId/operatorSessionId through the local outbox, Task 5: Rewrite WasteGatheringViewModel around the wizard controller, Task 6: Rewrite WasteGatheringScreen as a step wizard, remove MachineCatalog (+2 more)

### Community 18 - ".validateRequiredIdentity"
Cohesion: 0.07
Nodes (25): CatalogueMetaEntity, toEntity(), WasteCategoryEntity, WasteTypeEntity, Flow, Int, List, String (+17 more)

### Community 20 - "MqttTopics"
Cohesion: 0.35
Nodes (4): Boolean, Int, String, WasteCollectionValidator

### Community 22 - "ScramCryptoTest"
Cohesion: 0.29
Nodes (3): Instant, String, WasteCollectionEventTest

### Community 23 - ".build"
Cohesion: 0.54
Nodes (3): DeviceIdentity, Context, String

### Community 25 - ".onCreate"
Cohesion: 0.29
Nodes (5): EmptyPayload, Any, Gson, String, RequestEnvelope

### Community 26 - "PpnamApplication"
Cohesion: 0.33
Nodes (4): AppContainer, String, PpnamApplication, Application

### Community 27 - "MqttSchema"
Cohesion: 0.33
Nodes (4): Instant, String, MqttSchema, DateTimeFormatter

### Community 28 - "DataWedgeReceiver"
Cohesion: 0.33
Nodes (4): DataWedgeReceiver, Context, BroadcastReceiver, Intent

### Community 29 - "Repo Rules"
Cohesion: 0.40
Nodes (4): External directory: C:\Dev\PPNAM-Station-4, graphify, Operator login is mirrored from Station 2 AA, not Station 4's own contract, Repo Rules

### Community 30 - "WasteCollectionResultMessageTest"
Cohesion: 0.48
Nodes (5): Barcode, SharedFlow, RfidTag, ScanEvent, ScanEventBus

### Community 32 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 45 - "WasteCatalogueRepository"
Cohesion: 0.13
Nodes (10): Flow, List, String, WasteCatalogueRepository, FakeWasteCatalogueDao, Flow, Int, List (+2 more)

### Community 46 - "Final wastage bag process, Phase 1 — design"
Cohesion: 0.09
Nodes (21): A latent bug this work fixes, Catalogue fetch, Catalogue subsystem, Collection event, Context, Decisions, Edit-from-review, Event and outbox (+13 more)

### Community 47 - "Global Constraints"
Cohesion: 0.15
Nodes (12): Global Constraints, Phase 1 Wastage Bag Flow Implementation Plan, Task 1: Catalogue domain models and seed data, Task 2: Room storage for the catalogue, Task 3: Waste catalogue repository, Task 4: Catalogue sync over MQTT, Task 5: Validator support for the new fields, Task 6: Phase 1 wizard and schema v4, end to end (+4 more)

### Community 48 - "WasteGatheringScreen"
Cohesion: 0.36
Nodes (10): CatalogueStep(), ConfirmRow(), DropdownSelector(), List, String, T, Unit, ScanStep() (+2 more)

### Community 49 - "AppScaffold"
Cohesion: 0.22
Nodes (8): AppScaffold(), Boolean, String, Unit, ConnectionStatus, connectionStatusFlow(), Boolean, Flow

### Community 50 - ".sync"
Cohesion: 0.54
Nodes (5): CatalogueSyncResult, Failed, String, Replaced, SyncWasteCatalogueUseCase

### Community 51 - ".onCreate"
Cohesion: 0.29
Nodes (4): MainActivity, PPNAMStation4AATheme(), Bundle, ComponentActivity

### Community 52 - "AppNavGraph"
Cohesion: 0.29
Nodes (4): AppNavGraph(), LoginScreen(), SessionWatcher(), NavHostController

### Community 53 - "SettingsViewModel"
Cohesion: 0.29
Nodes (4): StateFlow, String, SettingsViewModel, ViewModel

### Community 54 - "LoginViewModel.kt"
Cohesion: 0.60
Nodes (5): Error, Idle, LoggedIn, LoggingIn, LoginUiState

### Community 56 - "PPNAM Station 4 — Final Wastage Bag Process"
Cohesion: 0.33
Nodes (5): Control points, Flow diagram, Phase 1 — Label and register the disposable wastage bag, Phase 2 — Scan and weigh the same bag at Station 4, PPNAM Station 4 — Final Wastage Bag Process

## Knowledge Gaps
- **73 isolated node(s):** `Status`, `FailureKind`, `EmptyPayload`, `ScramPurpose`, `ScramChallengeResponse` (+68 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **14 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `WasteGatheringViewModel` connect `WasteGatheringViewModel` to `WasteWizardController`, `AuthMessages.kt`, `WasteGatheringScreen`, `AppScaffold`, `AppNavGraph`, `SettingsViewModel`?**
  _High betweenness centrality (0.164) - this node is a cross-community bridge._
- **Why does `SettingsViewModel` connect `SettingsViewModel` to `LoginViewModel`, `MqttConnectionManager`, `ScramCrypto`, `AuthMessages.kt`, `AppScaffold`, `AppNavGraph`?**
  _High betweenness centrality (0.108) - this node is a cross-community bridge._
- **Why does `MqttConnectionManager` connect `MqttConnectionManager` to `FakeWasteOutboxDao`?**
  _High betweenness centrality (0.100) - this node is a cross-community bridge._
- **Are the 14 inferred relationships involving `WasteCatalogueRepository` (e.g. with `.`recordSyncFailure leaves the cached catalogue untouched`()` and `.`replaceWith is wholesale, not a merge`()`) actually correct?**
  _`WasteCatalogueRepository` has 14 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Status`, `FailureKind`, `EmptyPayload` to the rest of the system?**
  _73 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `FakeWasteOutboxDao` be split into smaller, more focused modules?**
  _Cohesion score 0.08974358974358974 - nodes in this community are weakly interconnected._
- **Should `MqttConnectionManager` be split into smaller, more focused modules?**
  _Cohesion score 0.09446693657219973 - nodes in this community are weakly interconnected._