# RFID Badge Scan Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a badge scan on the Chainway C72 sign an operator into PPNAM Station 4, by implementing the `login_requested` → `operator_context` exchange the handheld already publishes and no station has ever answered.

**Architecture:** The Android client is already complete and unchanged in shape — it publishes `login_requested` with a `badgeTag` and parses `operator_context`. The work is (a) a new badge-login branch in the Windows station's existing `MqttAuthenticationProcessor` / `MqttScramAuthenticationService` pair, resolving the tag against the already-replicated `fleet.badges` mirror and reusing `CreateMqttOperatorSessionAsync` for session issuance, and (b) closing the Android hardware and UX gaps so a scan reaches the app and reports itself intelligibly.

**Tech Stack:** C# / .NET (xUnit, Microsoft.Data.SqlClient, Microsoft.Data.Sqlite) for the station; Kotlin / Jetpack Compose (JUnit4, kotlinx-coroutines-test) for the handheld; MQTT over HiveMQ client.

**Spec:** `docs/superpowers/specs/2026-09-18-rfid-badge-login-design.md`

## Global Constraints

- **Contract version:** the station contract bumps **5.1.1 → 5.2.0**. Wire `schemaVersion` for authentication stays the string `"4.1"` (`MqttAuthenticationContract.SchemaVersion`) — do **not** use the waste workflow's integer `4`.
- **Topics are derived, never configured:** `PPNAM/station_4/{deviceId}/req/login_requested` and `PPNAM/station_4/{deviceId}/res/operator_context`. Do not add a settings field for either end.
- **Identity precedence is central-first.** `fleet.badges` is authoritative for `display_name` and `role`; the local `dbo.station4_users` row fills only columns the mirror leaves null. Both mirror columns are nullable — this is a **per-field** fallback, not a wholesale source choice.
- **A locally-disabled account does not refuse a badge.** This is deliberate (spec §2). Task 3 pins it with a test; do not "fix" it.
- **Badge login is online-only.** Never reuse `LoadCredentialAsync`'s offline user cache for badges, and do not add a badge mirror to `LocalOperationalStore`.
- **Three error codes only:** `badge_invalid`, `badge_unknown`, `badge_lookup_unavailable`. There is no `badge_inactive` — an inactive or soft-deleted badge returns `badge_unknown` so the station never confirms a badge exists but is switched off.
- **Never log a raw badge tag.** Logs use the last 4 characters only (see Task 3's `BadgeTag.Mask`).
- **Windows repo scope:** `C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4` is read-only *except* for this feature (see `CLAUDE.md`). Touch only the files named in these tasks.
- **Build/test commands:** station — `dotnet test PPNAM.Station4.Tests/PPNAM.Station4.Tests.csproj` from the Windows repo root; handheld — `./gradlew testDebugUnitTest` from the Android repo root.

---

## Deviation from the spec, requiring a decision before Task 3

Spec §2 says the resolving identity source is "carried into the session audit outbox". Implementing that literally means adding a column to the SQLite `mqtt_operator_sessions` table (created with `CREATE TABLE IF NOT EXISTS` at `LocalOperationalStore.cs:187`, so existing installations need a migration) **and** to the SQL-side audit `OperatorSession`, for a single diagnostic field.

This plan instead delivers the same diagnostic value at the decision point: an `IdentitySource` field on the in-memory `MqttOperatorSession` (no schema change), plus a structured log naming the source on every badge login and a warning when the mirror and the local account disagree about role. **If you want the persisted audit column, say so and Task 3 grows a migration step.**

---

## File Structure

**Station (`C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4`)**

| File | Responsibility |
|---|---|
| `PPNAM.Station4.Core/Security/BadgeTag.cs` (create) | Badge tag normalisation, validation and log masking. One concern, no dependencies. |
| `PPNAM.Station4.Core/Security/SecurityServices.cs` (modify) | `UserRoleLabels` gains `Parse` — the inverse of the existing `Display`. |
| `PPNAM.Station4.Core/Models/Station4Models.cs` (modify) | `FleetBadge` record; `MqttOperatorSession.IdentitySource`. |
| `PPNAM.Station4.Core/Models/MqttAuthenticationModels.cs` (modify) | `MqttBadgeLoginRequestedMessage`. |
| `PPNAM.Station4.Core/Data/IStation4Repository.cs` (modify) | `FindBadgeAsync`, `FindUserByOperatorIdAsync`. |
| `PPNAM.Station4.Core/Data/SqlStation4Repository.cs` (modify) | SQL for both lookups. |
| `PPNAM.Station4.Core/Services/MqttScramAuthenticationService.cs` (modify) | `BadgeLoginAsync` / `PrepareBadgeLoginAsync` — resolution and session issuance. |
| `PPNAM.Station4.Core/Services/MqttAuthenticationProcessor.cs` (modify) | Route `login_requested` → `operator_context`. |
| `PPNAM.Station4.Tests/CoreProductionTests.cs` (modify) | `TestRepository` gains badge support. |
| `PPNAM.Station4.Tests/BadgeTagTests.cs` (create) | Task 1 + 2 unit tests. |
| `PPNAM.Station4.Tests/MqttBadgeLoginTests.cs` (create) | Tasks 3 + 4 behaviour tests. |
| `DOCS/Station4_Wastage_MQTT_Contract.md` (modify) | Contract 5.2.0. |

**Handheld (`C:\Dev\Clients\PPNAM\Andriod\PPNAM_Station_4_AA`)**

| File | Responsibility |
|---|---|
| `app/src/main/java/.../domain/auth/BadgeLoginError.kt` (create) | Maps station error codes to operator-facing text. Pure, unit-testable. |
| `app/src/main/java/.../domain/usecase/AuthUseCase.kt` (modify) | Depend on the `RequestChannel` interface; surface badge error codes. |
| `app/src/main/java/.../ui/login/LoginViewModel.kt` (modify) | `BadgeRead` state so a scan is acknowledged before the round trip. |
| `app/src/main/java/.../ui/login/LoginScreen.kt` (modify) | Render the badge-read state. |
| `app/src/main/java/.../data/rfid/ScannerProvisioner.kt` (create, Task 8) | Self-provision the Chainway broadcast output. Gated on Task 0. |
| `app/src/test/java/.../domain/auth/BadgeLoginErrorTest.kt` (create) | Task 6 tests. |
| `app/src/test/java/.../domain/usecase/AuthUseCaseTest.kt` (create) | Task 6 tests. |

**Ordering.** Task 0 is a probe whose result selects Task 8's mechanism; it blocks nothing else. Tasks 1–5 (station) are independently deployable and testable without any handheld change. Tasks 6–7 (handheld) improve behaviour whether or not Task 8 lands. Task 9 needs both sides deployed.

---

### Task 0: Probe the C72's RFID delivery path

**This task requires physical access to the handheld and a real badge.** Its output is a decision recorded in the repo, not shipped code. It gates Task 8 only.

**Files:**
- Create: `docs/superpowers/plans/2026-09-18-rfid-probe-findings.md`

**Interfaces:**
- Consumes: nothing.
- Produces: a documented verdict — `BROADCAST` (Task 8 uses `ScannerProvisioner`) or `SDK` (Task 8 is replaced by a vendor-SDK spike), plus the observed tag string for Task 9's fixture.

- [ ] **Step 1: Confirm the device is attached**

```bash
export PATH="$PATH:/c/Users/Jonathan/AppData/Local/Android/Sdk/platform-tools"
adb devices
```

Expected: `HC720DE260100322	device`. If it shows `unauthorized`, accept the prompt on the handheld.

- [ ] **Step 2: Clear the log buffer**

```bash
adb logcat -c
```

Do **not** background a `logcat` follow here: shell jobs do not survive between tool calls, so it could never be stopped and would orphan. The buffer is read with `-d` after the scan instead (Step 6), which captures the same evidence.

- [ ] **Step 3: Open the Chainway UHF demo and scan a real badge**

```bash
adb shell monkey -p com.rscja.ht -c android.intent.category.LAUNCHER 1
```

Physically press the trigger with a badge presented. The demo app proves whether the UHF module reads this badge type **at all** — if it reads nothing here, no amount of app code helps and the verdict is a hardware/tag-type problem, not `BROADCAST` vs `SDK`.

Record the exact tag string the demo displays. Note whether it looks like a hex EPC (`E280...`) or a short decimal — this tells Task 9 whether the value matches `fleet.badges.badge_tag` or relates to `card_code`.

- [ ] **Step 4: Check whether InfoWedge offers a UHF source with intent output**

```bash
adb shell monkey -p com.rscja.infowedge -c android.intent.category.LAUNCHER 1
```

Inspect its profile configuration for (a) a UHF/RFID data source and (b) an "intent output" / broadcast delivery mode. Barcode-only options mean UHF reads never become broadcasts.

- [ ] **Step 5: If an intent output exists, point it at this app and verify**

Set the intent action to `com.mitas.ppnam.station4aa.ACTION_SCAN`, then scan again.

- [ ] **Step 6: Dump the buffer and record the verdict**

```bash
adb logcat -d -v time > /c/Users/Jonathan/AppData/Local/Temp/claude/rfid-probe.log
grep -i "ACTION_SCAN\|rscja.*RFID\|com.scanner.broadcast" /c/Users/Jonathan/AppData/Local/Temp/claude/rfid-probe.log | head -20
```

Expected on success: a broadcast line naming one of those actions. An empty result on a chatty device may mean the read rolled out of the buffer — clear it with `adb logcat -c` and rescan before concluding `SDK`.

Write `docs/superpowers/plans/2026-09-18-rfid-probe-findings.md` containing: the verdict (`BROADCAST` or `SDK`), whether the demo app read the badge, the exact tag string observed, which broadcast action carried it (if any), and the InfoWedge options seen. State the tag's apparent format explicitly.

- [ ] **Step 7: Commit**

```bash
git add docs/superpowers/plans/2026-09-18-rfid-probe-findings.md
git commit -m "docs: record C72 RFID delivery probe findings"
```

---

### Task 1: Role parsing across the fleet boundary

`fleet.badges.role` is a free-text `VARCHAR(50)` from central. The local enum is
`StationRole { Worker, Manager, Administrator, Officer }` (four values — verified at
`Station4Models.cs:3-9`), and the existing `UserRoleLabels.Display` renders `Worker` as
`"Operator"` while `Officer`, `Manager` and `Administrator` render as `"Officer"`, `"Manager"` and
`"Admin"`. A round-tripped role therefore arrives spelled differently than the enum. Parsing must
accept both vocabularies and fall back to **least privilege**.

`Officer` matters and must not be dropped: `SecurityServices.cs:154` grants it the same
`CaptureWaste` rights as `Manager`, and `Station4SchemaSql.cs`'s role CHECK constraints admit
`'Officer'` for `station4_users`, `operator_sessions` and `audit_events` — so a replicated badge
carrying that role is expected, and silently demoting it to `Worker` would strip real permissions.

**Files:**
- Modify: `PPNAM.Station4.Core/Security/SecurityServices.cs` (the `UserRoleLabels` class, around line 103)
- Test: `PPNAM.Station4.Tests/BadgeTagTests.cs` (create)

**Interfaces:**
- Consumes: `StationRole` and `UserRoleLabels.Display` (existing).
- Produces: `public static StationRole UserRoleLabels.Parse(string? value)` — never throws; unknown, blank and null all yield `StationRole.Worker`.

- [ ] **Step 1: Write the failing test**

Create `PPNAM.Station4.Tests/BadgeTagTests.cs`:

```csharp
using PPNAM.Station4.Core.Models;
using PPNAM.Station4.Core.Security;

namespace PPNAM.Station4.Tests;

public sealed class UserRoleLabelsParseTests
{
    [Theory]
    [InlineData("Administrator", StationRole.Administrator)]
    [InlineData("administrator", StationRole.Administrator)]
    [InlineData("Admin", StationRole.Administrator)]
    [InlineData("  admin  ", StationRole.Administrator)]
    [InlineData("Manager", StationRole.Manager)]
    [InlineData("manager", StationRole.Manager)]
    [InlineData("Worker", StationRole.Worker)]
    [InlineData("Operator", StationRole.Worker)]
    [InlineData("Officer", StationRole.Officer)]
    [InlineData("officer", StationRole.Officer)]
    public void Parse_AcceptsBothEnumNamesAndDisplayLabels(string value, StationRole expected) =>
        Assert.Equal(expected, UserRoleLabels.Parse(value));

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("Supervisor")]
    [InlineData("root")]
    public void Parse_FallsBackToLeastPrivilege(string? value) =>
        Assert.Equal(StationRole.Worker, UserRoleLabels.Parse(value));

    // Driven from the enum rather than a hand-listed set: a role added to StationRole later must
    // fail this test until Parse handles it. A hand-listed theory is exactly how Officer was
    // missed the first time this task was written.
    [Fact]
    public void Parse_RoundTripsEveryDisplayLabel()
    {
        foreach (var role in Enum.GetValues<StationRole>())
        {
            Assert.Equal(role, UserRoleLabels.Parse(UserRoleLabels.Display(role)));
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `dotnet test PPNAM.Station4.Tests/PPNAM.Station4.Tests.csproj --filter UserRoleLabelsParseTests`
Expected: FAIL — `'UserRoleLabels' does not contain a definition for 'Parse'`.

- [ ] **Step 3: Write the minimal implementation**

In `PPNAM.Station4.Core/Security/SecurityServices.cs`, add to `UserRoleLabels`:

```csharp
    /// <summary>
    /// The inverse of <see cref="Display"/>, tolerant of the fleet's spellings. Central's
    /// replicated role column is free text, so this accepts both the enum names and the display
    /// labels. Anything unrecognised — including null and blank — is Worker: an unreadable role
    /// must never widen access. Every value of <see cref="StationRole"/> must appear here, or a
    /// badge carrying that role is silently demoted; Parse_RoundTripsEveryDisplayLabel enforces it.
    /// </summary>
    public static StationRole Parse(string? value) => (value ?? string.Empty).Trim().ToLowerInvariant() switch
    {
        "administrator" or "admin" => StationRole.Administrator,
        "manager" => StationRole.Manager,
        "officer" => StationRole.Officer,
        "worker" or "operator" => StationRole.Worker,
        _ => StationRole.Worker
    };
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `dotnet test PPNAM.Station4.Tests/PPNAM.Station4.Tests.csproj --filter UserRoleLabelsParseTests`
Expected: PASS (18 InlineData cases + the enum-driven round-trip).

- [ ] **Step 5: Commit**

```bash
git add PPNAM.Station4.Core/Security/SecurityServices.cs PPNAM.Station4.Tests/BadgeTagTests.cs
git commit -m "feat: parse fleet role strings into StationRole, defaulting to least privilege"
```

---

### Task 2: Badge tag handling and the lookup surface

**Files:**
- Create: `PPNAM.Station4.Core/Security/BadgeTag.cs`
- Modify: `PPNAM.Station4.Core/Models/Station4Models.cs` (add `FleetBadge`)
- Modify: `PPNAM.Station4.Core/Data/IStation4Repository.cs`
- Modify: `PPNAM.Station4.Core/Data/SqlStation4Repository.cs`
- Modify: `PPNAM.Station4.Tests/CoreProductionTests.cs` (`TestRepository`, from line 1067)
- Test: `PPNAM.Station4.Tests/BadgeTagTests.cs` (extend)

**Interfaces:**
- Consumes: `StationUser`, `IStation4Repository` (existing).
- Produces:
  - `public static class BadgeTag` with `Normalize(string?) → string`, `IsValid(string?) → bool`, `Mask(string?) → string`
  - `public sealed record FleetBadge(string BadgeTag, string OperatorId, string? DisplayName, string? Role)`
  - `Task<FleetBadge?> IStation4Repository.FindBadgeAsync(string badgeTag, CancellationToken cancellationToken = default)`
  - `Task<StationUser?> IStation4Repository.FindUserByOperatorIdAsync(string operatorId, CancellationToken cancellationToken = default)`
  - `TestRepository.Badges` (`List<FleetBadge>`)

- [ ] **Step 1: Write the failing test**

Append to `PPNAM.Station4.Tests/BadgeTagTests.cs`:

```csharp
public sealed class BadgeTagTests
{
    [Theory]
    [InlineData("e280117000", "E280117000")]
    [InlineData("  e280117000  ", "E280117000")]
    [InlineData("E280117000", "E280117000")]
    [InlineData(null, "")]
    public void Normalize_UppercasesAndTrims(string? input, string expected) =>
        Assert.Equal(expected, BadgeTag.Normalize(input));

    [Theory]
    [InlineData("E280117000")]
    [InlineData("card-0001")]
    [InlineData("A")]
    public void IsValid_AcceptsRealisticTags(string value) => Assert.True(BadgeTag.IsValid(value));

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("has space")]
    [InlineData("semi;colon")]
    public void IsValid_RejectsBlankAndIllegalCharacters(string? value) =>
        Assert.False(BadgeTag.IsValid(value));

    [Fact]
    public void IsValid_RejectsOverLongTags() =>
        Assert.False(BadgeTag.IsValid(new string('A', 129)));

    [Fact]
    public void IsValid_AcceptsTagAtTheColumnLimit() =>
        Assert.True(BadgeTag.IsValid(new string('A', 128)));

    [Theory]
    [InlineData("E280117000A1B2", "****A1B2")]
    // A tag of four characters or fewer is masked entirely: revealing its last four would
    // disclose the whole credential, which is the one thing Mask exists to prevent.
    [InlineData("A1B2", "****")]
    [InlineData("B2", "****")]
    [InlineData(null, "****")]
    public void Mask_KeepsOnlyTheLastFourCharacters(string? value, string expected) =>
        Assert.Equal(expected, BadgeTag.Mask(value));
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `dotnet test PPNAM.Station4.Tests/PPNAM.Station4.Tests.csproj --filter BadgeTagTests`
Expected: FAIL — `The name 'BadgeTag' does not exist`.

- [ ] **Step 3: Create `BadgeTag`**

Create `PPNAM.Station4.Core/Security/BadgeTag.cs`:

```csharp
namespace PPNAM.Station4.Core.Security;

/// <summary>
/// Badge tag handling, shared by the repository lookup and the login service so a tag is
/// normalised identically on both sides. The replication merge procedure stores
/// UPPER(LTRIM(RTRIM(badge_tag))), so anything comparing against fleet.badges must match that
/// exactly or every badge reads as unknown.
/// </summary>
public static class BadgeTag
{
    /// <summary>fleet.badges.badge_tag is VARCHAR(128).</summary>
    public const int MaxLength = 128;

    public static string Normalize(string? value) =>
        (value ?? string.Empty).Trim().ToUpperInvariant();

    /// <summary>
    /// Conservative charset: the tags this station expects are hex EPCs or issued card codes.
    /// Widen this only with evidence from a real badge — see the Task 0 probe findings.
    /// </summary>
    public static bool IsValid(string? value)
    {
        var normalized = Normalize(value);
        if (normalized.Length is 0 or > MaxLength) return false;
        foreach (var character in normalized)
        {
            var ok = character is >= 'A' and <= 'Z'
                || character is >= '0' and <= '9'
                || character is '-' or '_';
            if (!ok) return false;
        }
        return true;
    }

    /// <summary>A badge tag is a credential; logs get the last four characters only.</summary>
    public static string Mask(string? value)
    {
        var normalized = Normalize(value);
        return normalized.Length <= 4 ? "****" : $"****{normalized[^4..]}";
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `dotnet test PPNAM.Station4.Tests/PPNAM.Station4.Tests.csproj --filter BadgeTagTests`
Expected: PASS.

Note: `Mask("B2")` returns `"****"` because a 2-character tag has nothing safe to reveal.

- [ ] **Step 5: Add the `FleetBadge` model**

In `PPNAM.Station4.Core/Models/Station4Models.cs`, alongside the other records:

```csharp
/// <summary>
/// A row of the replicated fleet.badges mirror. DisplayName and Role are nullable in the schema,
/// which is why badge login falls back to the local account per field rather than choosing one
/// source outright.
/// </summary>
public sealed record FleetBadge(
    string BadgeTag,
    string OperatorId,
    string? DisplayName,
    string? Role);
```

- [ ] **Step 6: Declare the repository methods**

In `PPNAM.Station4.Core/Data/IStation4Repository.cs`, after `FindUserByNameAsync` (line 9):

```csharp
    Task<FleetBadge?> FindBadgeAsync(string badgeTag, CancellationToken cancellationToken = default);
    Task<StationUser?> FindUserByOperatorIdAsync(string operatorId, CancellationToken cancellationToken = default);
```

- [ ] **Step 7: Implement them in SQL**

In `PPNAM.Station4.Core/Data/SqlStation4Repository.cs`, after `FindUserByNameAsync`. Add `using PPNAM.Station4.Core.Security;` — it is absent.

**First, factor out the shared column list.** `GetUsersAsync` and `FindUserByNameAsync` already repeat the `dbo.station4_users` SELECT column list verbatim, and `FindUserByOperatorIdAsync` would make a third copy — so a renamed column would need three sites kept in sync, and a missed one compiles fine while reading the wrong column. The file already establishes this pattern with `FleetUserSelectSql`, so follow it:

```csharp
    private const string StationUserSelectSql = """
SELECT user_id, name, role, scram_salt, scram_iterations, scram_stored_key, scram_server_key,
       scram_verifier_version, is_active, created_by, created_at_utc,
       updated_by, updated_at_utc, last_login_utc, row_version
FROM dbo.station4_users
""";
```

Rewrite `GetUsersAsync` and `FindUserByNameAsync` to build their SQL from it (appending `ORDER BY name;` and `WHERE name = @name;` respectively) before adding the new methods. Leave the INSERT/UPDATE column lists alone — they are a different column set with no `row_version`.

Then add the two new methods:

```csharp
    public async Task<FleetBadge?> FindBadgeAsync(string badgeTag, CancellationToken cancellationToken = default)
    {
        // TOP (1) with a deterministic order because fleet.badges is keyed by
        // (source_station_id, id): the same physical badge can legitimately arrive from more than
        // one source station, and the most recently updated row is the current truth.
        const string sql = """
SELECT TOP (1) badge_tag, operator_id, display_name, role
FROM fleet.badges
WHERE badge_tag = @tag AND is_active = 1 AND is_deleted = 0
ORDER BY updated_at_utc DESC;
""";
        await using var connection = CreateConnection();
        await connection.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using var command = NewCommand(connection, sql);
        command.Parameters.Add("@tag", SqlDbType.VarChar, BadgeTag.MaxLength).Value =
            BadgeTag.Normalize(badgeTag);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken).ConfigureAwait(false);
        if (!await reader.ReadAsync(cancellationToken).ConfigureAwait(false)) return null;
        return new FleetBadge(
            reader.GetString(0),
            reader.GetString(1),
            reader.IsDBNull(2) ? null : reader.GetString(2),
            reader.IsDBNull(3) ? null : reader.GetString(3));
    }

    public async Task<StationUser?> FindUserByOperatorIdAsync(string operatorId, CancellationToken cancellationToken = default)
    {
        if (!Guid.TryParse(operatorId, out var userId)) return null;
        const string sql = $"{StationUserSelectSql} WHERE user_id = @userId;";
        await using var connection = CreateConnection();
        await connection.OpenAsync(cancellationToken).ConfigureAwait(false);
        await using var command = NewCommand(connection, sql);
        command.Parameters.Add("@userId", SqlDbType.UniqueIdentifier).Value = userId;
        await using var reader = await command.ExecuteReaderAsync(cancellationToken).ConfigureAwait(false);
        return await reader.ReadAsync(cancellationToken).ConfigureAwait(false) ? ReadUser(reader) : null;
    }
```

- [ ] **Step 8: Extend the test repository**

In `PPNAM.Station4.Tests/CoreProductionTests.cs`, inside `TestRepository` (line 1067), after the `Users` property and `FindUserByNameAsync`:

```csharp
    public List<FleetBadge> Badges { get; } = [];

    /// <summary>Set to throw the SQL-unavailable path without needing a real server.</summary>
    public Exception? BadgeLookupException { get; set; }

    public Task<FleetBadge?> FindBadgeAsync(string badgeTag, CancellationToken cancellationToken = default)
    {
        if (BadgeLookupException is not null) throw BadgeLookupException;
        var normalized = BadgeTag.Normalize(badgeTag);
        return Task.FromResult(Badges.FirstOrDefault(x => BadgeTag.Normalize(x.BadgeTag) == normalized));
    }

    public Task<StationUser?> FindUserByOperatorIdAsync(string operatorId, CancellationToken cancellationToken = default) =>
        Task.FromResult(Users.FirstOrDefault(x =>
            string.Equals(x.UserId.ToString(), operatorId?.Trim(), StringComparison.OrdinalIgnoreCase)));
```

Add `using PPNAM.Station4.Core.Security;` to that file if it is not already imported.

- [ ] **Step 9: Verify the whole suite still builds and passes**

Run: `dotnet test PPNAM.Station4.Tests/PPNAM.Station4.Tests.csproj`
Expected: PASS. A compile error naming `IStation4Repository` means another implementation of that interface needs the two new members — implement them there by returning `null`, and say so in the commit.

- [ ] **Step 10: Commit**

```bash
git add PPNAM.Station4.Core/Security/BadgeTag.cs PPNAM.Station4.Core/Models/Station4Models.cs PPNAM.Station4.Core/Data/IStation4Repository.cs PPNAM.Station4.Core/Data/SqlStation4Repository.cs PPNAM.Station4.Tests/BadgeTagTests.cs PPNAM.Station4.Tests/CoreProductionTests.cs
git commit -m "feat: add fleet.badges lookup and badge tag normalisation"
```

---

### Task 3: Badge login in the authentication service

The heart of the feature: resolve a tag to an identity and issue a session. Session creation reuses `CreateMqttOperatorSessionAsync`, which already closes the previous `Active` session for the device and queues the audit outbox — contract §7 comes free.

**Files:**
- Modify: `PPNAM.Station4.Core/Models/Station4Models.cs` (add `MqttOperatorSession.IdentitySource`)
- Modify: `PPNAM.Station4.Core/Services/MqttScramAuthenticationService.cs`
- Test: `PPNAM.Station4.Tests/MqttBadgeLoginTests.cs` (create)

**Interfaces:**
- Consumes: `BadgeTag`, `FleetBadge`, `UserRoleLabels.Parse`, `IStation4Repository.FindBadgeAsync` / `FindUserByOperatorIdAsync` (Tasks 1–2); `LocalOperationalStore.CreateMqttOperatorSessionAsync` (existing).
- Produces:
  - `public Task<MqttAuthenticationResult<MqttOperatorSession>> BadgeLoginAsync(string badgeTag, string deviceId, DateTime utcNow, CancellationToken cancellationToken = default)`
  - `internal Task<MqttAuthenticationResult<MqttOperatorSession>> PrepareBadgeLoginAsync(...)` — same parameters, used by the processor.
  - `MqttOperatorSession.IdentitySource` (`"central"`, `"local"` or `"mixed"`).

- [ ] **Step 1: Write the failing test**

Create `PPNAM.Station4.Tests/MqttBadgeLoginTests.cs`:

```csharp
using Microsoft.Data.SqlClient;
using PPNAM.Station4.Core.Local;
using PPNAM.Station4.Core.Models;
using PPNAM.Station4.Core.Services;

namespace PPNAM.Station4.Tests;

public sealed class MqttBadgeLoginTests
{
    private static readonly DateTime Now = new(2026, 9, 18, 8, 0, 0, DateTimeKind.Utc);

    private static async Task<(MqttScramAuthenticationService Service, TestRepository Repository, LocalOperationalStore Local)>
        CreateAsync(TempDirectory temp)
    {
        var local = new LocalOperationalStore(Path.Combine(temp.Path, "badge.db"));
        await local.InitializeAsync();
        var repository = new TestRepository();
        return (new MqttScramAuthenticationService(repository, local), repository, local);
    }

    private static StationUser LocalUser(Guid id, string name, StationRole role, bool isActive = true) => new()
    {
        UserId = id,
        Name = name,
        Role = role,
        IsActive = isActive,
        ScramVerifierVersion = 1,
        ScramSalt = "c2FsdA==",
        ScramIterations = 100_000,
        ScramStoredKey = Convert.ToBase64String(new byte[32]),
        ScramServerKey = Convert.ToBase64String(new byte[32])
    };

    [Fact]
    public async Task UnknownBadge_IsRefused()
    {
        using var temp = new TempDirectory();
        var (service, _, _) = await CreateAsync(temp);

        var result = await service.BadgeLoginAsync("E2801170FFFF", "scanner_1", Now);

        Assert.False(result.Success);
        Assert.Equal("badge_unknown", result.ErrorCode);
    }

    [Fact]
    public async Task BlankBadge_IsRefusedWithoutTouchingTheDirectory()
    {
        using var temp = new TempDirectory();
        var (service, repository, _) = await CreateAsync(temp);
        repository.BadgeLookupException = new InvalidOperationException("must not be called");

        var result = await service.BadgeLoginAsync("   ", "scanner_1", Now);

        Assert.False(result.Success);
        Assert.Equal("badge_invalid", result.ErrorCode);
    }

    [Fact]
    public async Task DirectoryUnavailable_IsDistinctFromUnknown()
    {
        using var temp = new TempDirectory();
        var (service, repository, _) = await CreateAsync(temp);
        repository.BadgeLookupException =
            new InvalidOperationException("SQL Server is unavailable.");

        var result = await service.BadgeLoginAsync("E2801170AAAA", "scanner_1", Now);

        Assert.False(result.Success);
        Assert.Equal("badge_lookup_unavailable", result.ErrorCode);
    }

    [Fact]
    public async Task MirrorIdentityWins_OverADisagreeingLocalAccount()
    {
        using var temp = new TempDirectory();
        var (service, repository, _) = await CreateAsync(temp);
        var id = Guid.NewGuid();
        repository.Users.Add(LocalUser(id, "Local Name", StationRole.Worker));
        repository.Badges.Add(new FleetBadge("E2801170AAAA", id.ToString(), "Central Name", "Manager"));

        var result = await service.BadgeLoginAsync("E2801170AAAA", "scanner_1", Now);

        Assert.True(result.Success, result.Message);
        Assert.Equal("Central Name", result.Value!.UserName);
        Assert.Equal(StationRole.Manager, result.Value.Role);
        Assert.Equal("central", result.Value.IdentitySource);
    }

    [Fact]
    public async Task NullMirrorColumns_FallBackToTheLocalAccount()
    {
        using var temp = new TempDirectory();
        var (service, repository, _) = await CreateAsync(temp);
        var id = Guid.NewGuid();
        repository.Users.Add(LocalUser(id, "Local Name", StationRole.Administrator));
        repository.Badges.Add(new FleetBadge("E2801170BBBB", id.ToString(), null, null));

        var result = await service.BadgeLoginAsync("E2801170BBBB", "scanner_1", Now);

        Assert.True(result.Success, result.Message);
        Assert.Equal("Local Name", result.Value!.UserName);
        Assert.Equal(StationRole.Administrator, result.Value.Role);
        Assert.Equal("local", result.Value.IdentitySource);
    }

    [Fact]
    public async Task BadgeWithNoLocalAccount_StillSignsIn()
    {
        using var temp = new TempDirectory();
        var (service, repository, _) = await CreateAsync(temp);
        repository.Badges.Add(
            new FleetBadge("E2801170CCCC", Guid.NewGuid().ToString(), "Visiting Operator", "Operator"));

        var result = await service.BadgeLoginAsync("E2801170CCCC", "scanner_1", Now);

        Assert.True(result.Success, result.Message);
        Assert.Equal("Visiting Operator", result.Value!.UserName);
        Assert.Equal(StationRole.Worker, result.Value.Role);
    }

    // Deliberate, per docs/superpowers/specs/2026-09-18-rfid-badge-login-design.md section 2:
    // central is authoritative, so a locally-disabled account is NOT a badge revocation path.
    // Revocation happens centrally. Do not "fix" this by adding an IsActive check.
    [Fact]
    public async Task LocallyDisabledAccount_WithAnActiveMirrorBadge_IsAdmitted()
    {
        using var temp = new TempDirectory();
        var (service, repository, _) = await CreateAsync(temp);
        var id = Guid.NewGuid();
        repository.Users.Add(LocalUser(id, "Disabled Locally", StationRole.Worker, isActive: false));
        repository.Badges.Add(new FleetBadge("E2801170DDDD", id.ToString(), "Central Name", "Operator"));

        var result = await service.BadgeLoginAsync("E2801170DDDD", "scanner_1", Now);

        Assert.True(result.Success, result.Message);
    }

    [Fact]
    public async Task MalformedOperatorId_IsTreatedAsUnknown()
    {
        using var temp = new TempDirectory();
        var (service, repository, _) = await CreateAsync(temp);
        repository.Badges.Add(new FleetBadge("E2801170EEEE", "not-a-guid", "Nobody", "Operator"));

        var result = await service.BadgeLoginAsync("E2801170EEEE", "scanner_1", Now);

        Assert.False(result.Success);
        Assert.Equal("badge_unknown", result.ErrorCode);
    }

    [Fact]
    public async Task TagNormalisation_ResolvesRegardlessOfCaseAndWhitespace()
    {
        using var temp = new TempDirectory();
        var (service, repository, _) = await CreateAsync(temp);
        repository.Badges.Add(
            new FleetBadge("E2801170AAAA", Guid.NewGuid().ToString(), "Operator One", "Operator"));

        var result = await service.BadgeLoginAsync("  e2801170aaaa  ", "scanner_1", Now);

        Assert.True(result.Success, result.Message);
    }

    [Fact]
    public async Task NewBadgeLogin_ClosesThePreviousSessionOnTheSameDevice()
    {
        using var temp = new TempDirectory();
        var (service, repository, local) = await CreateAsync(temp);
        repository.Badges.Add(
            new FleetBadge("E2801170AAAA", Guid.NewGuid().ToString(), "Operator One", "Operator"));
        repository.Badges.Add(
            new FleetBadge("E2801170BBBB", Guid.NewGuid().ToString(), "Operator Two", "Operator"));

        var first = await service.BadgeLoginAsync("E2801170AAAA", "scanner_1", Now);
        var second = await service.BadgeLoginAsync("E2801170BBBB", "scanner_1", Now.AddMinutes(1));

        Assert.True(first.Success, first.Message);
        Assert.True(second.Success, second.Message);
        Assert.Null(await local.ResolveMqttOperatorSessionAsync(
            first.Value!.SessionId.ToString(), "scanner_1", Now.AddMinutes(2)));
        Assert.NotNull(await local.ResolveMqttOperatorSessionAsync(
            second.Value!.SessionId.ToString(), "scanner_1", Now.AddMinutes(2)));
    }

    [Fact]
    public async Task Session_IsBoundToTheRequestingDevice()
    {
        using var temp = new TempDirectory();
        var (service, repository, local) = await CreateAsync(temp);
        repository.Badges.Add(
            new FleetBadge("E2801170AAAA", Guid.NewGuid().ToString(), "Operator One", "Operator"));

        var result = await service.BadgeLoginAsync("E2801170AAAA", "scanner_1", Now);

        Assert.Equal("scanner_1", result.Value!.DeviceId);
        Assert.Null(await local.ResolveMqttOperatorSessionAsync(
            result.Value.SessionId.ToString(), "scanner_2", Now.AddMinutes(1)));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `dotnet test PPNAM.Station4.Tests/PPNAM.Station4.Tests.csproj --filter MqttBadgeLoginTests`
Expected: FAIL — `'MqttScramAuthenticationService' does not contain a definition for 'BadgeLoginAsync'`.

- [ ] **Step 3: Add `IdentitySource` to the session model**

In `PPNAM.Station4.Core/Models/Station4Models.cs`, inside `MqttOperatorSession` (line 171), after `SessionState`:

```csharp
    /// <summary>
    /// Which directory supplied this session's display name and role: "central" (the fleet.badges
    /// mirror), "local" (this station's user table), or "mixed" when each supplied one field.
    /// Diagnostic only — it never affects authorization. SCRAM logins are always "local".
    /// </summary>
    public string IdentitySource { get; set; } = "local";
```

- [ ] **Step 4: Implement badge login**

In `PPNAM.Station4.Core/Services/MqttScramAuthenticationService.cs`, add `using PPNAM.Station4.Core.Security;` if absent, then add these members after `LogoutCoreAsync`:

```csharp
    public async Task<MqttAuthenticationResult<MqttOperatorSession>> BadgeLoginAsync(
        string badgeTag,
        string deviceId,
        DateTime utcNow,
        CancellationToken cancellationToken = default) =>
        await BadgeLoginCoreAsync(badgeTag, deviceId, utcNow, true, cancellationToken)
            .ConfigureAwait(false);

    internal async Task<MqttAuthenticationResult<MqttOperatorSession>> PrepareBadgeLoginAsync(
        string badgeTag,
        string deviceId,
        DateTime utcNow,
        CancellationToken cancellationToken = default) =>
        await BadgeLoginCoreAsync(badgeTag, deviceId, utcNow, false, cancellationToken)
            .ConfigureAwait(false);

    /// <summary>
    /// Badge login is deliberately online-only: unlike LoadCredentialAsync it does NOT fall back to
    /// the offline user cache. When the directory is unreachable the handheld is told to use a
    /// password, which still works offline, rather than being silently admitted on stale data.
    /// </summary>
    private async Task<MqttAuthenticationResult<MqttOperatorSession>> BadgeLoginCoreAsync(
        string badgeTag,
        string deviceId,
        DateTime utcNow,
        bool persistMutation,
        CancellationToken cancellationToken)
    {
        deviceId = deviceId?.Trim() ?? string.Empty;
        if (!BadgeTag.IsValid(badgeTag) || deviceId.Length is < 1 or > 100)
        {
            return MqttAuthenticationResult<MqttOperatorSession>.Fail(
                "badge_invalid",
                "A badge tag and device ID are required.");
        }

        FleetBadge? badge;
        StationUser? localUser;
        try
        {
            badge = await _repository.FindBadgeAsync(badgeTag, cancellationToken)
                .ConfigureAwait(false);
            if (badge is null)
            {
                return MqttAuthenticationResult<MqttOperatorSession>.Fail(
                    "badge_unknown",
                    "This badge is not registered.");
            }
            // A mirror row whose operator_id is not a user id is corrupt replication data. It is
            // reported as unknown rather than as its own code: the operator cannot act on the
            // difference, and the station must not confirm which badges exist.
            if (!Guid.TryParse(badge.OperatorId, out _))
            {
                return MqttAuthenticationResult<MqttOperatorSession>.Fail(
                    "badge_unknown",
                    "This badge is not registered.");
            }
            localUser = await _repository
                .FindUserByOperatorIdAsync(badge.OperatorId, cancellationToken)
                .ConfigureAwait(false);
        }
        catch (Exception exception)
            when (exception is SqlException or InvalidOperationException)
        {
            return MqttAuthenticationResult<MqttOperatorSession>.Fail(
                "badge_lookup_unavailable",
                "The badge directory is unavailable. Sign in with your username and password.");
        }

        // Central-first, per field: the mirror's columns are nullable, so each is taken from the
        // mirror when present and from the local account only where the mirror is silent.
        var hasCentralName = !string.IsNullOrWhiteSpace(badge.DisplayName);
        var hasCentralRole = !string.IsNullOrWhiteSpace(badge.Role);
        var displayName = hasCentralName
            ? badge.DisplayName!.Trim()
            : localUser?.Name ?? string.Empty;
        var role = hasCentralRole
            ? UserRoleLabels.Parse(badge.Role)
            : localUser?.Role ?? StationRole.Worker;
        var identitySource = (hasCentralName, hasCentralRole) switch
        {
            (true, true) => "central",
            (false, false) => "local",
            _ => "mixed"
        };

        // NOTE: localUser.IsActive is intentionally NOT checked. Central is authoritative, so a
        // locally-disabled account does not block a badge that is active in the mirror. See the
        // design spec, section 2, and MqttBadgeLoginTests.LocallyDisabledAccount_*.
        var user = new StationUser
        {
            UserId = Guid.Parse(badge.OperatorId),
            Name = displayName,
            Role = role,
            IsActive = true
        };

        MqttOperatorSession session;
        if (persistMutation)
        {
            session = await _local.CreateMqttOperatorSessionAsync(
                    user,
                    deviceId,
                    false,
                    utcNow,
                    utcNow.Add(MqttAuthenticationContract.OperatorSessionLifetime),
                    cancellationToken)
                .ConfigureAwait(false);
        }
        else
        {
            session = new MqttOperatorSession
            {
                UserId = user.UserId,
                UserName = user.Name,
                Role = user.Role,
                DeviceId = deviceId,
                IsOfflineLogin = false,
                LoginAtUtc = utcNow,
                ExpiresAtUtc = utcNow.Add(MqttAuthenticationContract.OperatorSessionLifetime),
                SessionState = "Active"
            };
        }
        session.IdentitySource = identitySource;

        if (persistMutation && localUser is not null)
        {
            await RecordSuccessfulLoginAsync(localUser.UserId, utcNow, cancellationToken)
                .ConfigureAwait(false);
        }

        return MqttAuthenticationResult<MqttOperatorSession>.Ok(session, "Badge login accepted.") with
        {
            Mutation = new MqttAuthenticationMutation { SessionToCreate = session }
        };
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `dotnet test PPNAM.Station4.Tests/PPNAM.Station4.Tests.csproj --filter MqttBadgeLoginTests`
Expected: PASS (11 tests).

- [ ] **Step 6: Run the full suite for regressions**

Run: `dotnet test PPNAM.Station4.Tests/PPNAM.Station4.Tests.csproj`
Expected: PASS. SCRAM sessions keep `IdentitySource` at its `"local"` default, so no existing test changes.

- [ ] **Step 7: Commit**

```bash
git add PPNAM.Station4.Core/Models/Station4Models.cs PPNAM.Station4.Core/Services/MqttScramAuthenticationService.cs PPNAM.Station4.Tests/MqttBadgeLoginTests.cs
git commit -m "feat: resolve badges against the fleet mirror and issue operator sessions"
```

---

### Task 4: Route `login_requested` through the processor

**Files:**
- Modify: `PPNAM.Station4.Core/Models/MqttAuthenticationModels.cs`
- Modify: `PPNAM.Station4.Core/Services/MqttAuthenticationProcessor.cs`
- Test: `PPNAM.Station4.Tests/MqttBadgeLoginTests.cs` (extend)

**Interfaces:**
- Consumes: `PrepareBadgeLoginAsync` (Task 3).
- Produces: `MqttBadgeLoginRequestedMessage` with a `BadgeTag` property; `login_requested` answered on `PPNAM/station_4/{deviceId}/res/operator_context`.

- [ ] **Step 1: Write the failing test**

Append to `PPNAM.Station4.Tests/MqttBadgeLoginTests.cs`:

```csharp
public sealed class MqttBadgeLoginProcessorTests
{
    private static readonly DateTime Now = new(2026, 9, 18, 8, 0, 0, DateTimeKind.Utc);

    private static string Request(string messageId, string badgeTag, string deviceId = "scanner_1") =>
        $$"""
        {"messageId":"{{messageId}}","schemaVersion":"4.1","deviceId":"{{deviceId}}",
         "timestampUtc":"2026-09-18T08:00:00.000000Z","badgeTag":"{{badgeTag}}"}
        """;

    private static async Task<(MqttAuthenticationProcessor Processor, TestRepository Repository)>
        CreateAsync(TempDirectory temp)
    {
        var local = new LocalOperationalStore(Path.Combine(temp.Path, "processor.db"));
        await local.InitializeAsync();
        var repository = new TestRepository();
        var service = new MqttScramAuthenticationService(repository, local);
        return (new MqttAuthenticationProcessor(service, local, () => new DateTimeOffset(Now)), repository);
    }

    [Fact]
    public async Task AcceptedBadgeLogin_AnswersOnOperatorContextWithASessionId()
    {
        using var temp = new TempDirectory();
        var (processor, repository) = await CreateAsync(temp);
        repository.Badges.Add(
            new FleetBadge("E2801170AAAA", Guid.NewGuid().ToString(), "Operator One", "Operator"));

        var result = await processor.ProcessAsync(
            "PPNAM/station_4/scanner_1/req/login_requested",
            Request("badge-001", "E2801170AAAA"));

        Assert.NotNull(result);
        Assert.Equal("PPNAM/station_4/scanner_1/res/operator_context", result!.ResponseTopic);
        using var document = JsonDocument.Parse(result.ResponsePayload);
        var root = document.RootElement;
        Assert.True(root.GetProperty("accepted").GetBoolean());
        Assert.False(string.IsNullOrWhiteSpace(root.GetProperty("operatorSessionId").GetString()));
        Assert.Equal("Operator One", root.GetProperty("displayName").GetString());
    }

    [Fact]
    public async Task UnknownBadge_AnswersOnOperatorContextWithTheErrorCode()
    {
        using var temp = new TempDirectory();
        var (processor, _) = await CreateAsync(temp);

        var result = await processor.ProcessAsync(
            "PPNAM/station_4/scanner_1/req/login_requested",
            Request("badge-002", "E2801170FFFF"));

        Assert.NotNull(result);
        Assert.Equal("PPNAM/station_4/scanner_1/res/operator_context", result!.ResponseTopic);
        using var document = JsonDocument.Parse(result.ResponsePayload);
        Assert.False(document.RootElement.GetProperty("accepted").GetBoolean());
        Assert.Equal("badge_unknown", document.RootElement.GetProperty("errorCode").GetString());
    }

    [Fact]
    public async Task RepeatedMessageId_ReplaysTheStoredResponse()
    {
        using var temp = new TempDirectory();
        var (processor, repository) = await CreateAsync(temp);
        repository.Badges.Add(
            new FleetBadge("E2801170AAAA", Guid.NewGuid().ToString(), "Operator One", "Operator"));
        var payload = Request("badge-003", "E2801170AAAA");

        var first = await processor.ProcessAsync(
            "PPNAM/station_4/scanner_1/req/login_requested", payload);
        var second = await processor.ProcessAsync(
            "PPNAM/station_4/scanner_1/req/login_requested", payload);

        Assert.False(first!.IsReplay);
        Assert.True(second!.IsReplay);
        Assert.Equal(first.ResponsePayload, second.ResponsePayload);
    }

    [Fact]
    public async Task RetainedBadgeLogin_IsRefused()
    {
        using var temp = new TempDirectory();
        var (processor, repository) = await CreateAsync(temp);
        repository.Badges.Add(
            new FleetBadge("E2801170AAAA", Guid.NewGuid().ToString(), "Operator One", "Operator"));

        var result = await processor.ProcessAsync(
            "PPNAM/station_4/scanner_1/req/login_requested",
            Request("badge-004", "E2801170AAAA"),
            isRetained: true);

        using var document = JsonDocument.Parse(result!.ResponsePayload);
        Assert.False(document.RootElement.GetProperty("accepted").GetBoolean());
        Assert.Equal(
            "retained_message_not_allowed",
            document.RootElement.GetProperty("errorCode").GetString());
    }
}
```

Add `using System.Text.Json;` to the file's imports.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `dotnet test PPNAM.Station4.Tests/PPNAM.Station4.Tests.csproj --filter MqttBadgeLoginProcessorTests`
Expected: FAIL — `ProcessAsync` returns null for `login_requested`, so `Assert.NotNull(result)` fails.

- [ ] **Step 3: Add the request message model**

In `PPNAM.Station4.Core/Models/MqttAuthenticationModels.cs`, beside `MqttReaderLogoutRequestedMessage`:

```csharp
public sealed class MqttBadgeLoginRequestedMessage : MqttWorkflowEnvelope
{
    public string BadgeTag { get; set; } = string.Empty;
}
```

- [ ] **Step 4: Route the request type**

In `PPNAM.Station4.Core/Services/MqttAuthenticationProcessor.cs`, add to the `responseType` switch (around line 47), before the `_ => string.Empty` arm:

```csharp
            "login_requested" => "operator_context",
```

- [ ] **Step 5: Dispatch to the badge handler**

In the same file's `prepared = requestType switch` block, before the `_ => throw` arm:

```csharp
                "login_requested" => await ProcessBadgeLoginAsync(
                        payload,
                        deviceId,
                        inspection,
                        receivedAt,
                        stopwatch,
                        cancellationToken)
                    .ConfigureAwait(false),
```

- [ ] **Step 6: Map the mutation-rejected failure**

In the `commit.MutationRejected` switch, before the `_ =>` arm:

```csharp
                "login_requested" => (
                    Code: "authentication_state_conflict",
                    Message: "The session could not be created. Scan your badge again."),
```

- [ ] **Step 7: Implement the handler**

Add after `ProcessLogoutAsync`:

```csharp
    private async Task<PreparedWorkflowResult> ProcessBadgeLoginAsync(
        string payload,
        string deviceId,
        PayloadInspection inspection,
        DateTimeOffset receivedAt,
        Stopwatch stopwatch,
        CancellationToken cancellationToken)
    {
        var request = JsonSerializer.Deserialize<MqttBadgeLoginRequestedMessage>(
                payload,
                MqttWorkflowEnvelopeCodec.JsonOptions)
            ?? throw new JsonException();
        var auth = await _authentication.PrepareBadgeLoginAsync(
                request.BadgeTag,
                deviceId,
                receivedAt.UtcDateTime,
                cancellationToken)
            .ConfigureAwait(false);
        var session = auth.Value;
        var response = new MqttOperatorContextMessage
        {
            Accepted = auth.Success,
            Reason = auth.Success ? null : auth.Message,
            ErrorCode = auth.Success ? null : auth.ErrorCode,
            ErrorMessage = auth.Success ? null : auth.Message,
            OperatorId = session?.UserId.ToString() ?? string.Empty,
            DisplayName = session?.UserName ?? string.Empty,
            Role = session is null ? string.Empty : UserRoleLabels.Display(session.Role),
            SessionState = session?.SessionState ?? string.Empty,
            SessionExpiresAtUtc = session is null
                ? null
                : new DateTimeOffset(session.ExpiresAtUtc, TimeSpan.Zero),
            NextAction = auth.Success ? "create_waste_collection" : "login",
            CorrelationKey = inspection.CorrelationKey
        };
        MqttWorkflowEnvelopeCodec.CompleteEnvelope(response, request, deviceId, receivedAt, stopwatch);
        // The session id rides the envelope, not the body — CompleteEnvelope leaves it alone, and
        // the handheld's OperatorContextResponse reads it from the same flat JSON object.
        response.OperatorSessionId = auth.Success ? session!.SessionId.ToString() : string.Empty;
        return new PreparedWorkflowResult(
            MqttWorkflowEnvelopeCodec.SerializeResponse(
                $"PPNAM/station_4/{deviceId}/res/operator_context",
                response),
            auth.Mutation,
            null);
    }
```

`SuccessfulSession` is passed as `null` deliberately: `BadgeLoginCoreAsync` already calls `RecordSuccessfulLoginAsync` itself, and only when a local account exists. Passing the session here would double-record it and would also try to update a last-login row for mirror-only operators who have none.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `dotnet test PPNAM.Station4.Tests/PPNAM.Station4.Tests.csproj --filter MqttBadgeLogin`
Expected: PASS (15 tests across both classes).

- [ ] **Step 9: Run the full suite**

Run: `dotnet test PPNAM.Station4.Tests/PPNAM.Station4.Tests.csproj`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add PPNAM.Station4.Core/Models/MqttAuthenticationModels.cs PPNAM.Station4.Core/Services/MqttAuthenticationProcessor.cs PPNAM.Station4.Tests/MqttBadgeLoginTests.cs
git commit -m "feat: answer login_requested on res/operator_context"
```

---

### Task 5: Contract document 5.2.0

**Files:**
- Modify: `DOCS/Station4_Wastage_MQTT_Contract.md`

**Interfaces:**
- Consumes: the behaviour built in Tasks 3–4.
- Produces: the normative description other station apps and reviewers read.

- [ ] **Step 1: Replace the paragraph that denies badge login**

At `DOCS/Station4_Wastage_MQTT_Contract.md:424`, replace:

> Station 4 does not currently expose Station 2's badge login. `login_requested` is therefore not a Station 4 authentication request in this version.

with:

```markdown
### Badge login

Station 4 exposes Station 2's badge login from contract 5.2.0.

Publish:

```text
PPNAM/station_4/scanner_1/req/login_requested
```

```json
{
  "messageId": "badge-001",
  "schemaVersion": "4.1",
  "deviceId": "scanner_1",
  "timestampUtc": "2026-09-18T08:00:00.000000Z",
  "badgeTag": "E28011700000020F1A2B3C4D"
}
```

Station 4 answers on `PPNAM/station_4/scanner_1/res/operator_context`, the same response
message `reader_logout_requested` uses. An accepted reply carries `operatorSessionId`,
`operatorId`, `displayName`, `role`, `sessionState` and `sessionExpiresAtUtc`.

- The badge tag is resolved against the replicated `fleet.badges` mirror, normalised to
  upper case with surrounding whitespace removed.
- **The mirror is authoritative.** `displayName` and `role` come from `fleet.badges`; the
  station's own user record supplies only the columns the mirror leaves null. A badge that is
  active in the mirror signs in even when the matching Station 4 account is disabled, so badge
  revocation is a central operation and replication lag is revocation lag.
- Badge login is **online-only**. It never uses the protected offline user cache that password
  login may use; when the directory is unreachable the handheld is told to use a password.
- A successful badge login closes the previous active session on the same `deviceId`, exactly as
  a SCRAM login does (§7).

| `errorCode` | Meaning | Retry |
|---|---|---|
| `badge_invalid` | `badgeTag` was missing, blank, over 128 characters, or contained characters outside `A-Z 0-9 - _`. | No — the scan was unusable. |
| `badge_unknown` | No active, non-deleted badge matches. An inactive or soft-deleted badge is reported this way too; the station does not confirm that a badge exists but is switched off. | No. |
| `badge_lookup_unavailable` | The badge directory could not be read. | Yes, or sign in with a username and password, which still works while the directory is down. |
```

- [ ] **Step 2: Add the row to the authentication request table**

Find the table listing `scram_start_requested`, `scram_proof_requested` and `reader_logout_requested`, and add:

```markdown
| `login_requested` | `operator_context` | Badge login. Resolves a badge tag against the fleet mirror and issues a session. |
```

- [ ] **Step 3: Bump the version**

Update the document's version identifier from `5.1.1` to `5.2.0`, including any header, changelog or history section the document keeps. Search for `5.1.1` and update every occurrence that refers to this document's own version:

```bash
grep -n "5\.1\.1" DOCS/Station4_Wastage_MQTT_Contract.md
```

- [ ] **Step 4: Verify no contradiction remains**

```bash
grep -n -i "does not currently expose\|not a Station 4 authentication request" DOCS/Station4_Wastage_MQTT_Contract.md
```

Expected: no output.

- [ ] **Step 5: Update the Android repo's CLAUDE.md contract version reference**

In `C:\Dev\Clients\PPNAM\Andriod\PPNAM_Station_4_AA\CLAUDE.md`, change the contract version from **5.1.1** to **5.2.0**.

- [ ] **Step 6: Commit (both repos)**

```bash
# Windows repo
git add DOCS/Station4_Wastage_MQTT_Contract.md
git commit -m "docs: contract 5.2.0 adds badge login"
```

```bash
# Android repo
git add CLAUDE.md
git commit -m "docs: track contract 5.2.0"
```

---

### Task 6: Handheld — testable auth and badge error text

**Files:**
- Create: `app/src/main/java/com/mitas/ppnam/station4aa/domain/auth/BadgeLoginError.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/domain/usecase/AuthUseCase.kt` (line 24, line 58)
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/data/AppContainer.kt` (line 65 — no change needed if types align; verify it compiles)
- Test: `app/src/test/java/com/mitas/ppnam/station4aa/domain/auth/BadgeLoginErrorTest.kt` (create)
- Test: `app/src/test/java/com/mitas/ppnam/station4aa/domain/usecase/AuthUseCaseTest.kt` (create)

**Interfaces:**
- Consumes: `RequestChannel`, `MqttOutcome`, `BadgeLoginPayload`, `OperatorContextResponse` (all existing).
- Produces: `BadgeLoginError.messageFor(errorCode: String?, fallback: String?): String`.

- [ ] **Step 1: Write the failing error-mapping test**

Create `app/src/test/java/com/mitas/ppnam/station4aa/domain/auth/BadgeLoginErrorTest.kt`:

```kotlin
package com.mitas.ppnam.station4aa.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeLoginErrorTest {

    @Test
    fun `an unknown badge tells the operator it is not registered`() {
        val message = BadgeLoginError.messageFor("badge_unknown", null)
        assertTrue(message, message.contains("not registered", ignoreCase = true))
    }

    @Test
    fun `an unavailable directory points at password sign-in`() {
        val message = BadgeLoginError.messageFor("badge_lookup_unavailable", null)
        assertTrue(message, message.contains("password", ignoreCase = true))
    }

    @Test
    fun `an invalid tag asks for a rescan`() {
        val message = BadgeLoginError.messageFor("badge_invalid", null)
        assertTrue(message, message.contains("again", ignoreCase = true))
    }

    @Test
    fun `an unrecognised code falls back to the station's own reason`() {
        assertEquals(
            "Something specific from the station",
            BadgeLoginError.messageFor("some_new_code", "Something specific from the station"),
        )
    }

    @Test
    fun `an unrecognised code with no reason still says something useful`() {
        val message = BadgeLoginError.messageFor(null, null)
        assertTrue(message, message.isNotBlank())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*BadgeLoginErrorTest*"`
Expected: FAIL — unresolved reference `BadgeLoginError`.

- [ ] **Step 3: Implement the mapping**

Create `app/src/main/java/com/mitas/ppnam/station4aa/domain/auth/BadgeLoginError.kt`:

```kotlin
package com.mitas.ppnam.station4aa.domain.auth

/**
 * Turns the station's badge `errorCode` into text an operator can act on. The station's own
 * `reason` is a developer-facing sentence; these are the three codes the operator can actually do
 * something about, so they get purpose-written copy and everything else falls through.
 *
 * Contract 5.2.0, `Station4_Wastage_MQTT_Contract.md` — badge login.
 */
object BadgeLoginError {
    fun messageFor(errorCode: String?, fallback: String?): String = when (errorCode) {
        "badge_unknown" ->
            "This badge is not registered. Ask a supervisor to have it issued, or sign in with your username and password."
        "badge_lookup_unavailable" ->
            "The badge directory is unavailable. Sign in with your username and password instead."
        "badge_invalid" ->
            "That scan could not be read. Present the badge again."
        else -> fallback?.takeIf { it.isNotBlank() } ?: "Badge sign-in failed."
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*BadgeLoginErrorTest*"`
Expected: PASS (5 tests).

- [ ] **Step 5: Write the failing AuthUseCase test**

Create `app/src/test/java/com/mitas/ppnam/station4aa/domain/usecase/AuthUseCaseTest.kt`:

```kotlin
package com.mitas.ppnam.station4aa.domain.usecase

import com.mitas.ppnam.station4aa.data.auth.ScramExchange
import com.mitas.ppnam.station4aa.data.mqtt.FailureKind
import com.mitas.ppnam.station4aa.data.mqtt.MqttOutcome
import com.mitas.ppnam.station4aa.data.mqtt.RequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.dto.BadgeLoginPayload
import com.mitas.ppnam.station4aa.data.mqtt.dto.OperatorContextResponse
import com.mitas.ppnam.station4aa.data.session.OperatorSessionHolder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DEVICE_ID = "scanner_a1b2c3d4e5f6"
private const val BADGE_TAG = "E28011700000020F1A2B3C4D"

private class FakeAuthChannel(private val outcome: MqttOutcome<OperatorContextResponse>) : RequestChannel {
    var lastRequestType: String? = null
    var lastPayload: Any? = null
    var lastDeviceId: String? = null

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> request(
        deviceId: String,
        requestType: String,
        responseClass: Class<T>,
        payload: Any,
        operatorSessionId: String,
        timeoutMs: Long,
    ): MqttOutcome<T> {
        lastRequestType = requestType
        lastPayload = payload
        lastDeviceId = deviceId
        return outcome as MqttOutcome<T>
    }
}

class AuthUseCaseTest {

    private fun useCase(
        outcome: MqttOutcome<OperatorContextResponse>,
    ): Triple<AuthUseCase, FakeAuthChannel, OperatorSessionHolder> {
        val channel = FakeAuthChannel(outcome)
        val holder = OperatorSessionHolder()
        return Triple(
            // ScramExchange takes only the channel — deviceId is an authenticate() argument.
            AuthUseCase(channel, holder, ScramExchange(channel), DEVICE_ID),
            channel,
            holder,
        )
    }

    private fun accepted() = OperatorContextResponse(
        operatorSessionId = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
        operatorId = "1f0f6b1e-6a0e-4e1a-9d3e-2b0a5f4c7d81",
        displayName = "Operator One",
        role = "Operator",
        sessionState = "Active",
        sessionExpiresAtUtc = "2026-09-19T00:00:00.000000Z",
    )

    @Test
    fun `a badge login publishes login_requested carrying the scanned tag`() = runTest {
        val (auth, channel, _) = useCase(MqttOutcome.Accepted(accepted()))

        val result = auth.login(LoginMethod.Badge(BADGE_TAG))

        assertTrue(result.isSuccess)
        assertEquals("login_requested", channel.lastRequestType)
        assertEquals(DEVICE_ID, channel.lastDeviceId)
        assertEquals(BadgeLoginPayload(BADGE_TAG), channel.lastPayload)
    }

    @Test
    fun `an accepted badge login establishes the session`() = runTest {
        val (auth, _, holder) = useCase(MqttOutcome.Accepted(accepted()))

        val session = auth.login(LoginMethod.Badge(BADGE_TAG)).getOrThrow()

        assertEquals("Operator One", session.operatorName)
        assertEquals("4dfda8bb-e9bf-4e92-b8a9-acde673fbb83", holder.currentSessionIdOrEmpty())
    }

    @Test
    fun `an unknown badge surfaces operator-readable text, not the raw code`() = runTest {
        val (auth, _, _) = useCase(
            MqttOutcome.Rejected(null, "badge_unknown", "This badge is not registered."),
        )

        val message = auth.login(LoginMethod.Badge(BADGE_TAG)).exceptionOrNull()?.message.orEmpty()

        assertTrue(message, message.contains("not registered", ignoreCase = true))
    }

    @Test
    fun `an unavailable directory tells the operator to use a password`() = runTest {
        val (auth, _, _) = useCase(
            MqttOutcome.Rejected(null, "badge_lookup_unavailable", "Directory unavailable."),
        )

        val message = auth.login(LoginMethod.Badge(BADGE_TAG)).exceptionOrNull()?.message.orEmpty()

        assertTrue(message, message.contains("password", ignoreCase = true))
    }

    @Test
    fun `no response is reported as a transport failure`() = runTest {
        val (auth, _, _) = useCase(MqttOutcome.NoResponse(FailureKind.Timeout))

        val result = auth.login(LoginMethod.Badge(BADGE_TAG))

        assertTrue(result.isFailure)
    }

    @Test
    fun `an accepted login with no session id is refused`() = runTest {
        val (auth, _, _) = useCase(MqttOutcome.Accepted(accepted().copy(operatorSessionId = "")))

        assertTrue(auth.login(LoginMethod.Badge(BADGE_TAG)).isFailure)
    }
}
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*AuthUseCaseTest*"`
Expected: FAIL — `AuthUseCase` requires `MqttRequestChannel`, so passing a `FakeAuthChannel` does not compile.

- [ ] **Step 7: Depend on the interface and map badge errors**

In `app/src/main/java/com/mitas/ppnam/station4aa/domain/usecase/AuthUseCase.kt`:

Change the import `com.mitas.ppnam.station4aa.data.mqtt.MqttRequestChannel` to `com.mitas.ppnam.station4aa.data.mqtt.RequestChannel`, add `import com.mitas.ppnam.station4aa.domain.auth.BadgeLoginError`, and change the constructor parameter at line 24:

```kotlin
    private val requestChannel: RequestChannel,
```

Then in `loginWithBadge`, replace the `MqttOutcome.Rejected` arm:

```kotlin
            is MqttOutcome.Rejected -> Result.failure(
                Exception(BadgeLoginError.messageFor(outcome.errorCode, outcome.reason)),
            )
```

`ScramExchange` already takes the same channel, and `MqttRequestChannel` implements `RequestChannel`, so `AppContainer.kt:65` needs no edit.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*AuthUseCaseTest*"`
Expected: PASS (6 tests).

`ScramExchange`'s constructor **does** demand the concrete `MqttRequestChannel` (verified: `ScramExchange(private val requestChannel: MqttRequestChannel)`), so change its parameter type to `RequestChannel` too — the same one-word substitution. Its only constructor argument is the channel; `deviceId` is passed per call to `authenticate()`.

- [ ] **Step 9: Run the full unit suite**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/mitas/ppnam/station4aa/domain/auth/BadgeLoginError.kt app/src/main/java/com/mitas/ppnam/station4aa/domain/usecase/AuthUseCase.kt app/src/test/java/com/mitas/ppnam/station4aa/domain/auth/BadgeLoginErrorTest.kt app/src/test/java/com/mitas/ppnam/station4aa/domain/usecase/AuthUseCaseTest.kt
git commit -m "feat: give badge sign-in failures operator-readable text"
```

---

### Task 7: Handheld — acknowledge the scan

Today a badge scan looks identical to nothing happening until the round trip resolves. This adds an immediate acknowledgement.

**Files:**
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginViewModel.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginScreen.kt` (the divider block at line 201)

**Interfaces:**
- Consumes: `LoginUiState`, `ScanEventBus` (existing).
- Produces: `LoginUiState.BadgeRead` — emitted the moment a tag arrives, replaced by `LoggingIn` when the request goes out.

- [ ] **Step 1: Add the state**

In `LoginViewModel.kt`, add to the `LoginUiState` sealed class:

```kotlin
    /** A tag was read and the request is going out. Distinct from [LoggingIn] so the screen can
     * say "badge read" rather than showing the same spinner a password login shows. */
    object BadgeRead : LoginUiState()
```

- [ ] **Step 2: Emit it on scan**

In `startListeningForBadgeScans`, change the collector body:

```kotlin
            scanEventBus.events.filterIsInstance<ScanEvent.RfidTag>().collect { event ->
                if (_uiState.value == LoginUiState.Idle || _uiState.value is LoginUiState.Error) {
                    _uiState.value = LoginUiState.BadgeRead
                }
                attemptLogin(LoginMethod.Badge(event.tagId))
            }
```

- [ ] **Step 3: Let the re-entry guard accept it**

`attemptLogin` currently returns early unless the state is `Idle` or `Error`. `BadgeRead` must pass through, while still blocking a second concurrent login. Change the guard:

```kotlin
        val current = _uiState.value
        val mayStart = current == LoginUiState.Idle ||
            current is LoginUiState.Error ||
            current == LoginUiState.BadgeRead
        if (!mayStart) return
```

The guard's original purpose is unchanged: `LoggingIn` and `LoggedIn` still refuse re-entry, so a re-fired tag cannot overwrite an established session.

- [ ] **Step 4: Disable the form while a badge is in flight**

In `LoginScreen.kt`, the three `enabled = uiState !is LoginUiState.LoggingIn` occurrences (the two `OutlinedTextField`s and the `Button`) must also disable during `BadgeRead`. Replace each with:

```kotlin
                        enabled = uiState == LoginUiState.Idle || uiState is LoginUiState.Error,
```

- [ ] **Step 5: Show the acknowledgement**

In `LoginScreen.kt`, replace the divider `Row` block (line 201) with:

```kotlin
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = GraphiteBorder)
                        Text(
                            if (uiState == LoginUiState.BadgeRead) "  badge read — signing in  "
                            else "  or scan your badge  ",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState == LoginUiState.BadgeRead) AmberPrimary else TextMuted,
                            textAlign = TextAlign.Center
                        )
                        HorizontalDivider(Modifier.weight(1f), color = GraphiteBorder)
                    }
```

- [ ] **Step 6: Verify it builds and the suite passes**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginViewModel.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginScreen.kt
git commit -m "feat: acknowledge a badge scan before the round trip"
```

---

### Task 8: Handheld — self-provision the scanner broadcast

**Gated on Task 0.** Do this task only if the probe's verdict is `BROADCAST`. If the verdict is `SDK`, stop and raise it: that needs a vendor jar this machine does not have, and it is a new scoping decision, not a step.

**Files:**
- Create: `app/src/main/java/com/mitas/ppnam/station4aa/data/rfid/ScannerProvisioner.kt`
- Modify: `app/src/main/java/com/mitas/ppnam/station4aa/PpnamApplication.kt`

**Interfaces:**
- Consumes: `DataWedgeReceiver.ACTION_SCAN` (existing).
- Produces: `ScannerProvisioner.configure(context: Context)` — idempotent, safe to call on every launch.

- [ ] **Step 1: Write the provisioner**

Use the exact control-intent action names and extras recorded by the Task 0 probe. The skeleton below uses the actions advertised by `com.rscja.scanner`; **replace the extra keys with the ones the probe confirmed** rather than trusting these names:

```kotlin
package com.mitas.ppnam.station4aa.data.rfid

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Configures the Chainway scanner service to deliver reads as broadcasts aimed at this app.
 *
 * DataWedgeReceiver's class doc notes the profile "needs to be configured on-device — that's a
 * provisioning step, not code". `com.rscja.scanner` exposes ACTION_KE_* control intents, so it is
 * code after all: every handheld self-provisions on launch instead of being set up by hand.
 *
 * Failures are logged and swallowed. A device that ignores these intents is no worse off than
 * before, and a crash on a control intent would take the login screen with it.
 */
object ScannerProvisioner {
    private const val TAG = "ScannerProvisioner"
    private const val ACTION_ENABLE = "ACTION_KE_ENABLE"
    private const val ACTION_OUTPUT_MODE = "ACTION_KE_OUTPUTMODE"
    private const val ACTION_CHANGE_BROADCAST = "ACTION_KE_CHANGEBROADCAST"
    private const val SCANNER_PACKAGE = "com.rscja.scanner"

    fun configure(context: Context) {
        send(context, ACTION_ENABLE, "enable", true)
        send(context, ACTION_OUTPUT_MODE, "mode", 2)
        send(context, ACTION_CHANGE_BROADCAST, "action", DataWedgeReceiver.ACTION_SCAN)
    }

    private fun send(context: Context, action: String, extraKey: String, value: Any) {
        try {
            val intent = Intent(action).setPackage(SCANNER_PACKAGE)
            when (value) {
                is Boolean -> intent.putExtra(extraKey, value)
                is Int -> intent.putExtra(extraKey, value)
                else -> intent.putExtra(extraKey, value.toString())
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Scanner control intent $action was not accepted", e)
        }
    }
}
```

- [ ] **Step 2: Call it at startup**

In `PpnamApplication.onCreate`, after `registerReceiver`:

```kotlin
        ScannerProvisioner.configure(this)
```

Add `import com.mitas.ppnam.station4aa.data.rfid.ScannerProvisioner`.

- [ ] **Step 3: Build and install**

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 4: Verify a scan now reaches the app**

```bash
adb logcat -c
adb logcat | grep -i "ScanEventBus\|ScannerProvisioner\|DataWedgeReceiver"
```

Scan a badge. Expected: no `ScannerProvisioner` warnings, and a `DataWedgeReceiver` / `ScanEventBus` line carrying the tag. A `tryEmit dropped` warning means the login screen is not in the foreground — return to it and rescan.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mitas/ppnam/station4aa/data/rfid/ScannerProvisioner.kt app/src/main/java/com/mitas/ppnam/station4aa/PpnamApplication.kt
git commit -m "feat: self-provision the Chainway scanner broadcast output"
```

---

### Task 9: End-to-end verification

**Prerequisite:** Tasks 1–8 complete, the station build deployed, and `fleet.badges` confirmed to hold the test badge. Verify that first — an empty mirror makes every scan `badge_unknown` no matter how correct the code is:

```sql
SELECT badge_tag, operator_id, display_name, role, is_active, is_deleted
FROM fleet.badges
WHERE is_active = 1 AND is_deleted = 0;
```

**Files:**
- Modify: `docs/Station4_Handheld_SOP.html`

- [ ] **Step 1: Confirm the tag matches the mirror**

Compare the tag recorded by the Task 0 probe against `badge_tag` above. If the scan produces the value in `card_code` instead, stop and raise it — that is a data-mapping decision, not a code fix.

- [ ] **Step 2: Sign in by badge**

With the handheld on the login screen and the station running, scan the badge. Expected: "badge read — signing in", then the dashboard, with the operator name from the mirror.

- [ ] **Step 3: Verify the session replaced any previous one**

Sign in with a password first, then scan a different operator's badge. Expected: the second operator's dashboard, and the first session closed in the station's session history.

- [ ] **Step 4: Verify each refusal**

Scan an unregistered badge — expected: "This badge is not registered." Stop SQL Server and scan a valid badge — expected: the message naming username and password, and a password sign-in still succeeds. Restart SQL Server afterwards.

- [ ] **Step 5: Update the SOP**

Add badge sign-in to `docs/Station4_Handheld_SOP.html`: how to scan, the three error messages and what to do about each, and — in the known-issues or security section — that **disabling a Station 4 account does not stop that person's badge**, because badge revocation is central and takes effect only once replication delivers it.

- [ ] **Step 6: Commit**

```bash
git add docs/Station4_Handheld_SOP.html
git commit -m "docs: document badge sign-in and central-only revocation in the SOP"
```

- [ ] **Step 7: Refresh the knowledge graph**

```bash
graphify update .
```

```bash
git add graphify-out
git commit -m "chore: graphify update after badge login"
```

---

## Self-Review

**Spec coverage**

| Spec section | Task |
|---|---|
| §1 Wire contract, error codes, 5.2.0 bump | Tasks 4, 5 |
| §2 Badge resolution, normalisation, per-field fallback, central-only revocation | Tasks 1, 2, 3 |
| §2 Input validation (`badge_invalid`) | Tasks 2, 3 |
| §2 Diagnosing disagreement | Task 3 (`IdentitySource`) — **reduced scope, flagged above** |
| §3 Online-only failure | Task 3 |
| §4 Session issuance, previous-session closure, last-login | Tasks 3, 4 |
| §5 Processor wiring, inherited guards | Task 4 |
| §6 Android scan scope, hardware, UX | Tasks 6, 7, 8 |
| §7 Testing | Tasks 1–4, 6 |
| §8 Risks: empty mirror, tag format, UHF delivery, rollout | Tasks 0, 9 |

**Known gaps, stated rather than hidden**

1. The persisted audit column for `IdentitySource` is reduced to an in-memory field — flagged at the top of this document for your decision.
2. `SqlStation4Repository`'s two new methods have no unit test: the suite fakes `IStation4Repository` and never touches SQL Server. Their correctness is covered by Task 9's live verification. Adding a SQL integration harness would be a separate piece of work.
3. Task 8's control-intent extra keys are unverified until Task 0 runs; the task says to replace them with the probe's findings rather than trust the skeleton.

**Type consistency checked:** `FleetBadge` (Task 2) is consumed with the same four positional fields in Tasks 2 and 3. `BadgeTag.Normalize` / `IsValid` / `Mask` are used as defined. `UserRoleLabels.Parse` (Task 1) is called in Task 3 only. `BadgeLoginAsync` / `PrepareBadgeLoginAsync` signatures match between Task 3's definition and Task 4's call. `LoginUiState.BadgeRead` is referenced consistently across Task 7's steps. The Android fake channel implements `RequestChannel`'s exact six-parameter signature.
