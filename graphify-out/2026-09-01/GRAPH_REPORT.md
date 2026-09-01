# Graph Report - PPNAM_Station_4_AA  (2026-08-17)

## Corpus Check
- 71 files · ~38,131 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 528 nodes · 817 edges · 43 communities (32 shown, 11 thin omitted)
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 49 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `5c9a52f0`
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

## God Nodes (most connected - your core abstractions)
1. `MqttConnectionManager` - 22 edges
2. `WasteGatheringViewModel` - 21 edges
3. `WasteWizardController` - 20 edges
4. `WasteCollectionValidatorTest` - 20 edges
5. `ScramCrypto` - 16 edges
6. `FakeWasteOutboxDao` - 15 edges
7. `LoginViewModel` - 14 edges
8. `SecureCredentialStore` - 13 edges
9. `SettingsViewModel` - 13 edges
10. `WasteWizardControllerTest` - 12 edges

## Surprising Connections (you probably didn't know these)
- `AppNavGraph()` --calls--> `WasteGatheringScreen()`  [INFERRED]
  app/src/main/java/com/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/ppnam/station4aa/ui/waste/WasteGatheringScreen.kt
- `AppNavGraph()` --calls--> `WasteGatheringViewModel`  [INFERRED]
  app/src/main/java/com/ppnam/station4aa/navigation/AppNavGraph.kt → app/src/main/java/com/ppnam/station4aa/ui/waste/WasteGatheringViewModel.kt
- `WasteGatheringScreen()` --calls--> `AppScaffold()`  [INFERRED]
  app/src/main/java/com/ppnam/station4aa/ui/waste/WasteGatheringScreen.kt → app/src/main/java/com/ppnam/station4aa/ui/components/AppScaffold.kt
- `FakeWasteOutboxDao` --implements--> `WasteOutboxDao`  [EXTRACTED]
  app/src/test/java/com/ppnam/station4aa/data/mqtt/WasteCollectionResultChannelTest.kt → app/src/main/java/com/ppnam/station4aa/data/local/WasteOutboxDao.kt
- `toEvent()` --references--> `WasteCollectionEvent`  [EXTRACTED]
  app/src/main/java/com/ppnam/station4aa/data/local/WasteOutboxEntity.kt → app/src/main/java/com/ppnam/station4aa/domain/model/WasteCollectionEvent.kt

## Import Cycles
- None detected.

## Communities (43 total, 11 thin omitted)

### Community 0 - "LoginViewModel"
Cohesion: 0.06
Nodes (38): MqttConnectionState, AppNavGraph(), AppScaffold(), Boolean, String, Unit, ConnectionStatus, connectionStatusFlow() (+30 more)

### Community 1 - "FakeWasteOutboxDao"
Cohesion: 0.11
Nodes (19): WasteOutboxEntity, WasteCollectionResultMessage, Accepted, evaluateOutcome(), IdentityMismatch, SharedFlow, String, Rejected (+11 more)

### Community 2 - "MqttConnectionManager"
Cohesion: 0.11
Nodes (16): Mqtt5AsyncClient, MqttClientFactory, ByteArray, Mqtt5AsyncClient, Result, StateFlow, String, Unit (+8 more)

### Community 3 - "WasteGatheringViewModel"
Cohesion: 0.11
Nodes (16): WasteTypeCatalog, WizardStep, ConfirmRow(), EnumDropdownSelector(), List, String, T, ScanStep() (+8 more)

### Community 4 - "WasteWizardController"
Cohesion: 0.14
Nodes (7): Applied, Ignored, ScanDispatchResult, WasteTransactionDraft, String, WasteWizardController, WasteWizardControllerTest

### Community 5 - "ScramCrypto"
Cohesion: 0.22
Nodes (6): Boolean, ByteArray, Int, String, ScramCrypto, ScramProof

### Community 6 - "WasteCollectionPublisher"
Cohesion: 0.12
Nodes (14): Long, Status, toEvent(), toOutboxEntity(), WasteCollectionMessage, Flow, Int, SharedFlow (+6 more)

### Community 7 - ".request"
Cohesion: 0.16
Nodes (15): Accepted, describe(), FailureKind, String, T, MqttOutcome, NoResponse, Rejected (+7 more)

### Community 9 - "WasteOutboxDao"
Cohesion: 0.12
Nodes (10): Flow, Int, List, Long, String, WasteOutboxDao, create(), Context (+2 more)

### Community 10 - "OperatorSession"
Cohesion: 0.20
Nodes (10): StateFlow, String, OperatorSession, OperatorSessionHolder, AuthUseCase, Badge, Credentials, Result (+2 more)

### Community 11 - "SecureCredentialStore"
Cohesion: 0.24
Nodes (5): Boolean, ByteArray, String, SecureCredentialStore, SecretKey

### Community 12 - "AuthMessages.kt"
Cohesion: 0.18
Nodes (10): Result, String, ScramExchange, BadgeLoginPayload, OperatorContextResponse, ScramChallengeResponse, ScramProofPayload, ScramProofResponse (+2 more)

### Community 13 - "Scan-driven waste collection wizard — design"
Cohesion: 0.15
Nodes (12): 2026-08-05 addendum: contract bumped to schema v3 mid-implementation, Cancel-anywhere addition, Context, Error handling, Goals, Non-goals, Scan-driven waste collection wizard — design, State machine (+4 more)

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
Cohesion: 0.38
Nodes (4): Boolean, Int, String, WasteCollectionValidator

### Community 19 - "SettingsViewModel.kt"
Cohesion: 0.36
Nodes (8): ApplyState, Failure, Idle, Locked, PinState, Success, Testing, Unlocked

### Community 21 - "WasteCollectionEventTest"
Cohesion: 0.36
Nodes (3): Instant, String, WasteCollectionEventTest

### Community 23 - ".build"
Cohesion: 0.29
Nodes (5): EmptyPayload, Any, Gson, String, RequestEnvelope

### Community 24 - "ScanEvent"
Cohesion: 0.48
Nodes (5): Barcode, SharedFlow, RfidTag, ScanEvent, ScanEventBus

### Community 25 - ".onCreate"
Cohesion: 0.29
Nodes (4): MainActivity, PPNAMStation4AATheme(), Bundle, ComponentActivity

### Community 26 - "PpnamApplication"
Cohesion: 0.40
Nodes (3): AppContainer, PpnamApplication, Application

### Community 27 - "MqttSchema"
Cohesion: 0.33
Nodes (4): Instant, String, MqttSchema, DateTimeFormatter

### Community 28 - "DataWedgeReceiver"
Cohesion: 0.33
Nodes (4): DataWedgeReceiver, Context, BroadcastReceiver, Intent

### Community 29 - "Repo Rules"
Cohesion: 0.40
Nodes (4): External directory: C:\Dev\PPNAM-Station-4, graphify, Operator login is mirrored from Station 2 AA, not Station 4's own contract, Repo Rules

### Community 32 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **41 isolated node(s):** `Status`, `FailureKind`, `EmptyPayload`, `ScramPurpose`, `ScramChallengeResponse` (+36 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **11 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MqttConnectionManager` connect `MqttConnectionManager` to `LoginViewModel`, `FakeWasteOutboxDao`?**
  _High betweenness centrality (0.143) - this node is a cross-community bridge._
- **Why does `SettingsViewModel` connect `LoginViewModel` to `MqttConnectionManager`, `OperatorSession`, `SettingsViewModel.kt`?**
  _High betweenness centrality (0.142) - this node is a cross-community bridge._
- **Why does `MqttConnectionState` connect `LoginViewModel` to `MqttConnectionManager`?**
  _High betweenness centrality (0.122) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `MqttConnectionManager` (e.g. with `.`a result for an already-ACCEPTED row is a no-op, status stays ACCEPTED, no emit`()` and `.`a well-formed accepted result marks the stored PENDING row ACCEPTED and emits`()`) actually correct?**
  _`MqttConnectionManager` has 6 INFERRED edges - model-reasoned connections that need verification._
- **Are the 11 inferred relationships involving `WasteWizardController` (e.g. with `.`an invalid scanned value is applied as an error and does not advance`()` and `.`cancel from REVIEW also fully resets`()`) actually correct?**
  _`WasteWizardController` has 11 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Status`, `FailureKind`, `EmptyPayload` to the rest of the system?**
  _41 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `LoginViewModel` be split into smaller, more focused modules?**
  _Cohesion score 0.058823529411764705 - nodes in this community are weakly interconnected._