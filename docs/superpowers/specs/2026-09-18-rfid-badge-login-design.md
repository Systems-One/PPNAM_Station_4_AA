# RFID badge scan login — design

## Context

The handheld's badge-login path already exists in this app, ported verbatim from Station 2 AA. It is
**wired but dead-ended**: every client-side piece is present and correct, and nothing on the station
answers.

Present on the Android side today:

- `data/rfid/ScanEventBus.kt` — `ScanEvent.RfidTag(tagId, timestamp)`.
- `data/rfid/DataWedgeReceiver.kt` — listens for `com.mitas.ppnam.station4aa.ACTION_SCAN`,
  `com.scanner.broadcast` and `com.rscja.scanner.action.scanner.RFID`; registered dynamically in
  `PpnamApplication.onCreate` (not the manifest — see its class doc for the Android 11+
  package-visibility reason).
- `ui/login/LoginViewModel.kt:60` — collects `RfidTag` events and calls
  `attemptLogin(LoginMethod.Badge(event.tagId))`, with a documented re-entry guard covering the
  whole `LoggingIn -> LoggedIn` span so a re-fired tag cannot start a second login.
- `domain/usecase/AuthUseCase.kt:58` — `loginWithBadge` publishes `login_requested` carrying
  `BadgeLoginPayload(badgeTag)` and reads `res/operator_context`.
- `ui/login/LoginScreen.kt:201` — an "or scan your badge" divider.

Three gaps make that path non-functional:

1. **The station never answers.** `PPNAM.Station4.Core` implements `scram_start_requested`,
   `scram_proof_requested`, `reader_logout_requested`, `waste_catalogue_requested`,
   `waste_collection_requested` and `waste_capture_requested`. There is no `login_requested`
   handler. The contract says so deliberately — `Station4_Wastage_MQTT_Contract.md:424`: *"Station 4
   does not currently expose Station 2's badge login. `login_requested` is therefore not a Station 4
   authentication request in this version."* A badge scan today publishes and times out.
2. **Nothing reads the RFID hardware.** No `com.rscja` / UHF SDK dependency, no `app/libs`, no
   trigger-key handling. The app is a passive broadcast listener that assumes an on-device
   Chainway/InfoWedge profile has been provisioned to target its action.
3. **No badge UX.** A scan produces a spinner and eventually a generic timeout, with nothing
   indicating a badge was involved.

The backend is not hostile to this change. `fleet.badges` is already replicated and indexed
(`Station4SchemaSql.cs:609`), carrying `badge_tag`, `operator_id`, `display_name`, `role`,
`is_active`, `card_code` and `is_deleted`, with the comment *"Station 4 has no badge reader today;
the badge mirror lands so the route is the same shape at every station and nothing upstream changes
the day it gains one."* This design is that day.

## Decisions taken

Four questions were settled before design, recorded here because each removes a whole branch of
work:

| Question | Decision |
|---|---|
| What does a badge scan need to sign someone in? | **Badge tag alone**, exactly as Station 2 AA does it. Keeps schema 4.1 identical fleet-wide and adds no payload fields. |
| Badge login while SQL Server is down? | **No — online only, fail clearly.** Password login still works offline via the existing user cache, so nobody is locked out; they fall back. |
| Where is a badge scan live? | **Login screen only** (Station 2 parity). No app-wide listener, no mid-workflow operator switching, no data-loss questions. |
| Who may badge in? | **Prefer the central replica, fall back to local.** The replicated `fleet.badges` mirror is authoritative for identity and role; the local Station 4 account only supplies fields the mirror leaves null. An active badge in the mirror signs in even when the local account is disabled. |

The fourth decision is the only permissive one, and it carries a risk accepted knowingly: two
identity sources can disagree about the same person's role. Mitigation is in §2.

## 1. Wire contract

A new request/response pair, additive to the existing authentication set:

```text
PPNAM/station_4/{deviceId}/req/login_requested
PPNAM/station_4/{deviceId}/res/operator_context
```

Request body is the standard envelope plus one field:

```json
{
  "messageId": "...",
  "schemaVersion": "4.1",
  "deviceId": "scanner_1",
  "timestampUtc": "2026-09-18T07:15:00.000000Z",
  "badgeTag": "E28011700000020F1A2B3C4D"
}
```

The response is the **existing** `MqttOperatorContextMessage` — already the `reader_logout_requested`
response, and already the shape `OperatorContextResponse` on the Android side parses. No new
response model is introduced.

New error codes, all returned with `accepted: false`:

| Code | Meaning |
|---|---|
| `badge_invalid` | The `badgeTag` is missing, blank, or outside the accepted length/charset. |
| `badge_unknown` | No matching active, non-deleted row in `fleet.badges`. A row that exists but is inactive or soft-deleted also returns this, deliberately — see §2. |
| `badge_lookup_unavailable` | The badge directory could not be read (SQL Server down). Distinct from `badge_unknown` on purpose — see §3. |

The contract document bumps **5.1.1 → 5.2.0** (additive feature, minor). Two edits:
`Station4_Wastage_MQTT_Contract.md:424`'s "does not currently expose" paragraph is replaced with the
exchange's specification, and the authentication request table gains the `login_requested` row.

`C:\Dev\Clients\PPNAM\Andriod\MQTT_BASE_README.md` needs **no change** — its §5 schema 4.1 table
already lists `login_requested` → `operator_context` as "Badge login". This work brings Station 4
into line with a fleet standard that already anticipated it, rather than extending that standard.

## 2. Badge resolution

Two new repository methods on `IStation4Repository`:

- `FindBadgeAsync(badgeTag, ct)` → a badge registration record (`operator_id`, `display_name`,
  `role`, `is_active`), selecting from `fleet.badges` filtered on `is_active = 1 AND is_deleted = 0`
  and hitting the existing `IX_fleet_badges_tag` index.
- `FindUserByOperatorIdAsync(operatorId, ct)` → the Station 4 account for that operator, if any.
  Only `FindUserByNameAsync` exists today; SCRAM resolves by name, badge login must resolve by id.

**Tag normalisation.** The replication merge proc stores `UPPER(LTRIM(RTRIM(badge_tag)))`, so the
lookup applies identical normalisation to the scanned value before comparing. A mismatch here would
present as "every badge is unknown", so it gets a dedicated test.

**Input validation.** `badgeTag` is checked before any lookup — non-blank, within length bounds, and
free of characters the column cannot hold. A failure returns `badge_invalid` without touching SQL,
mirroring how `StartCoreAsync` validates username and nonce lengths up front.

**One "no" answer for an unusable badge.** The query filters on `is_active = 1 AND is_deleted = 0`,
so a badge that is absent, inactive or soft-deleted is indistinguishable in the response: all three
return `badge_unknown`. The station does not confirm that a badge exists but is switched off.

**Identity resolution**, implementing the "prefer the central replica, fall back to local" decision.
The mirror is authoritative; the local account is consulted only to fill gaps:

1. Resolve `badgeTag` → `fleet.badges` row. No row → `badge_unknown`. The mirror is the sole
   gatekeeper: this is the only check that can refuse a badge.
2. Look up `operator_id` in Station 4's own user table. A miss is normal, not an error.
3. Build the session identity **field by field**, mirror first: `display_name` and `role` are taken
   from `fleet.badges` when non-null, and from the local `StationUser` only where the mirror's column
   is null. Both columns are nullable in the schema, which is precisely why this is a per-field
   fallback and not a wholesale choice of one source.
4. If neither source supplies a field, it is empty — an absent role is not invented.

**The local account cannot refuse a badge.** Per the decision above, an active mirror badge signs its
operator in even when the Station 4 account it maps to is disabled. This is a deliberate consequence
of making central authoritative, and it means disabling a local account is **not** a revocation path
for badge login — revocation happens centrally. §8 records the operational consequence; a dedicated
test pins the behaviour so it cannot be "fixed" by accident later.

**Diagnosing disagreement.** Each session records which source supplied its identity fields, and
that value is carried into the session audit outbox. Where the local account and the mirror disagree
about the same person's role, the session shows which one was applied. This is a record, not an
enforcement mechanism — the decision was to let central win.

> **Implementation note (2026-09-18).** Planning found that persisting this costs a column on the
> SQLite `mqtt_operator_sessions` table — created with `CREATE TABLE IF NOT EXISTS`, so existing
> installations need a migration — *and* one on the SQL audit schema, for a single diagnostic field.
> `docs/superpowers/plans/2026-09-18-rfid-badge-login.md` therefore delivers it as an in-memory
> `MqttOperatorSession.IdentitySource` plus structured logging at the decision point, and flags the
> reduction for a decision. If the persisted audit trail is wanted, that plan's Task 3 grows a
> migration step and this paragraph stands as written.

## 3. Online-only failure

`MqttScramAuthenticationService.LoadCredentialAsync` falls back to the local protected user cache
(`FindOfflineUserAsync`, bounded by `AuthenticationService.OfflineValidity` at 24h) when SQL throws.
Badge login deliberately **does not** reuse that path, and no badge mirror is added to
`LocalOperationalStore`.

A `SqlException` or `InvalidOperationException` during badge lookup returns
`badge_lookup_unavailable`, and the handheld tells the operator to sign in with username and
password instead. Password login continues to work offline through the existing cache, so a database
outage degrades badge login to a fallback rather than locking anyone out. This is why
`badge_lookup_unavailable` must be distinct from `badge_unknown`: the operator's next action differs
(try again with a password vs. your badge is not registered).

## 4. Session issuance

`PrepareBadgeLoginAsync` on `MqttScramAuthenticationService` mirrors the structure of
`CompleteCoreAsync`'s tail: build the `MqttOperatorSession`, return it as an
`MqttAuthenticationResult` carrying a `SessionToCreate` mutation, and let the processor commit it.
The `persistMutation` split that `StartCoreAsync`/`CompleteCoreAsync` use is followed exactly, so
the prepared and persisted paths stay consistent with the rest of the service.

Session creation reuses `LocalOperationalStore.CreateMqttOperatorSessionAsync` unchanged. That
method already selects every `Active` session for the device, closes them, and queues each into the
session audit outbox inside one transaction — so contract §7's "a new successful login closes the
previous active session on the same handheld" is satisfied **for free**, with no new code and no
second implementation to keep in step.

`RecordSuccessfulLoginAsync` fires whenever a matching local Station 4 account exists, independently
of whether the mirror or that account supplied the session's display name and role — last-login is
telemetry about the account, not about which source won. When the badge resolves to an operator with
no local account at all there is no row to update, and none is created: the mirror is the directory,
and materialising shadow accounts from it would put Station 4 back in the business of owning identity
that the central-first decision just moved away.

Session lifetime, state values (`Active` / `Closed`) and device binding are unchanged — badge login
produces a session indistinguishable from a SCRAM one apart from how it was authenticated.

## 5. Processor wiring

`MqttAuthenticationProcessor` changes are small because the badge exchange fits its existing seams:

- `responseType` switch gains `"login_requested" => "operator_context"`.
- The dispatch switch gains a `ProcessBadgeLoginAsync` case, following `ProcessLogoutAsync`'s shape
  (deserialise, call the prepare method, populate `MqttOperatorContextMessage`, complete the
  envelope, serialise).
- The `commit.MutationRejected` mapping gains a `login_requested` entry.
- New model `MqttBadgeLoginRequestedMessage` in `MqttAuthenticationModels.cs`.

Inherited unchanged, and worth stating because they are the parts most easily got wrong: envelope
validation, `deviceId` topic/payload agreement, retained-message rejection, the plaintext-credential
guard, and `messageId` replay/idempotency including the same-id-different-body conflict.
`BuildFailure`'s `_ =>` branch already emits `MqttOperatorContextMessage`, so badge failures
serialise correctly with no edit to that method.

## 6. Android

**Scan scope is unchanged.** Badge listening stays in `LoginViewModel` only. `WasteGatheringViewModel`
and `WeighBagViewModel` already filter to `ScanEvent.Barcode`, so RFID and barcode streams stay
cleanly separated and no new listeners are introduced.

**Hardware.** The C72 (`HC720DE260100322`) ships `com.rscja.infowedge`, `com.rscja.scanner` and
`com.rscja.scanservice`, and `com.rscja.scanner` exposes `ACTION_KE_CHANGEBROADCAST` and
`ACTION_KE_OUTPUTMODE` — the scanner service can be configured **by intent**. The preferred
mechanism is therefore for the app to self-provision its broadcast output at startup rather than
depend on manual per-device InfoWedge configuration. That closes the provisioning gap named in
`DataWedgeReceiver`'s class doc and adds no dependency.

This is **probe-gated**. Every action those packages advertise is barcode-oriented (`BARCODE_SCAN`,
`2DS`, `KE`); none advertises UHF, and static inspection cannot settle whether UHF reads surface as
broadcasts at all. The implementation plan opens with an on-device probe against a real badge. If
broadcasts carry UHF reads, the self-provisioning approach proceeds; if they do not, the fallback is
the vendor `com.rscja` SDK — which is **not** present on this machine and would need procurement, so
discovering that early matters more than any other single step here.

**UX.** `LoginUiState` gains a badge-read acknowledgement so a scan is visibly received before the
network round-trip, and the three new error codes map to operator-readable text. In particular
`badge_lookup_unavailable` must say to sign in with username and password, since that is the action
that will actually work. Today all of these present as an undifferentiated timeout.

## 7. Testing

Backend, alongside `MqttAuthenticationTests`: unknown tag, inactive badge and soft-deleted badge all
returning `badge_unknown`, tag normalisation (mixed case and surrounding whitespace resolve), SQL
unavailable → `badge_lookup_unavailable` and *not* `badge_unknown`, `messageId` replay returning the
stored response, same id with different body → `message_id_reused`, and previous active session
closed and audited on a new badge login.

Identity resolution gets its own set, because precedence is the part most likely to be quietly
reversed by a later change: the mirror's `display_name`/`role` win over a local account that
disagrees; a null mirror column falls back to the local account's value; a badge with no local
account at all still signs in; neither source supplying a role yields an empty role rather than a
guessed one; and — pinned deliberately — **a locally-disabled account with an active mirror badge is
admitted**, with a comment pointing at this spec so the behaviour reads as intended rather than as a
missing check.

Android: `LoginViewModel` tests for badge success, each badge failure code, and that the scan
listener re-arms after a failure so a second attempt is possible without restarting the app.

On-device: a real badge on the C72, end to end, once `fleet.badges` is confirmed populated.

## 8. Risks and rollout

- **Badge revocation is central-only, by design.** Because the central replica is authoritative, a
  station administrator disabling a Station 4 account does **not** stop that operator badging in —
  only deactivating or deleting the badge centrally does, and then only once replication has
  delivered it. Station 4's own user administration remains the revocation path for *password*
  login, so the two methods revoke through different systems. This is a deliberate consequence of
  the central-first decision, not an oversight, but it is the fact most likely to surprise whoever
  operates the station, so it needs to reach the SOP and not just this spec. Replication lag is
  therefore also revocation lag.
- **`fleet.badges` may be empty in the target deployment.** The table exists and is indexed, but it
  is populated by central replication. If nothing has been delivered, the feature is untestable
  end-to-end however correct the code is. Verify before implementation reaches the device stage.
- **Tag format is unverified.** `fleet.badges` carries both `badge_tag` and a nullable `card_code`.
  Which one a scanned UHF read corresponds to is unknown until a real badge is read; the probe in §6
  answers this at the same time as the broadcast question.
- **UHF may not reach the app by broadcast.** Fallback is the vendor SDK, with a procurement cost.
- **Contract ordering.** The document bump to 5.2.0 and the station implementation ship together. An
  app build that publishes `login_requested` to a station that cannot answer it behaves exactly as
  today (timeout), so there is no unsafe intermediate state — but the handheld should not be told
  badge login works until the station side is deployed.

## Cross-repo note

This design is implemented across two repositories. `CLAUDE.md`'s rule marking
`C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4` read-only was explicitly lifted for this work, and
`CLAUDE.md` is updated as part of it so the change in scope is recorded rather than rediscovered
each session.
