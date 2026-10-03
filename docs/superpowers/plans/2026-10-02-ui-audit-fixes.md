# Station 4 UI Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close every Station 4 finding from the 2026-10-01 PPNAM handheld UI audit (S4-01..S4-18 plus the static rows that name S4) so the app behaves like the fleet standard on the Chainway C72: keyboard never hides the primary action, Enter submits, the pill and dialogs use the shared vocabulary, errors are operator-facing, drafts are never silently discarded, the station's collection result is shown, and the session has the same inactivity policy as Station 1.

**Architecture:** All changes stay inside the existing Compose M3 / Navigation-Compose / Room outbox / manual-DI shape. Pure decision logic that the audit found missing (PIN lockout, settings validation, error-string mapping, inactivity timer, rejection copy, badge-at-step-3 routing) is added as small dependency-free Kotlin classes next to the existing `WasteWizardController` / `CaptureRefusals` pattern so it is JUnit-testable; the screens consume it. No MQTT topic, payload shape, schema version, SCRAM step or broker/credential default changes.

**Tech Stack:** Kotlin 2.0, Jetpack Compose BOM 2024.12.01 (material3 1.3.1, foundation 1.7.6), Navigation-Compose 2.7.7, Room 2.6.1, DataStore 1.1.1, HiveMQ client 1.3.3, JUnit 4 + kotlinx-coroutines-test for JVM tests, compose-ui-test-junit4 for the one instrumented test.

**Spec:** `C:\Users\Jonathan\AppData\Local\Temp\claude\C--Dev-Clients-PPNAM\ba7a1680-4205-4b04-bcb6-1b1f23c94914\scratchpad\audit\CONSOLIDATED_REPORT.md` (sections 3, 4 "Station 4" + "Static consistency audit", 5, 6, 7), with the per-app evidence in `...\audit\station4.md` and `...\audit\static_consistency.md`. Screenshot paths below are relative to `...\audit\shots\station4\`.

## Global Constraints

- **Repo:** `C:\Dev\Clients\PPNAM\Station 4\PPNAM_Station_4_AA`. Package `com.mitas.ppnam.station4aa`. Source root `app\src\main\java\com\mitas\ppnam\station4aa\` (every path below that starts with `ui/`, `data/`, `domain/`, `navigation/` is relative to it).
- **Branch:** `fix/ui-audit-2026-10-02` off `master` (Task 1). Commit after every task. Never `git add -A`; add only the task's files.
- **Commit trailer (verbatim, last two lines of every commit body):**
  ```
  Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q
  ```
- **Build:** `.\gradlew.bat --offline :app:assembleDebug` from the repo root (drop `--offline` only if it fails to resolve something). APK: `app\build\outputs\apk\debug\app-debug.apk`.
- **JVM tests:** `.\gradlew.bat --offline :app:testDebugUnitTest` (baseline on 2026-10-02: passes, exit 0). One class: `.\gradlew.bat --offline :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.domain.pin.PinGateTest"`.
- **Instrumented tests:** only `app/src/androidTest/.../ui/home/HomeScreenTest.kt` exists (compose-ui-test). There is no Robolectric. Screen-level behaviour is therefore verified manually on the emulator; every such task says so explicitly and gives the adb recipe.
- **Emulator:** serial `emulator-5558`. adb = `C:\Users\Jonathan\AppData\Local\Android\Sdk\platform-tools\adb.exe`. Install: `adb -s emulator-5558 install -r -g app\build\outputs\apk\debug\app-debug.apk` (if "signatures do not match": `adb -s emulator-5558 uninstall com.mitas.ppnam.station4aa` first). Fake backend settings: host `10.0.2.2`, port `9001`, WebSocket ON, TLS OFF, broker user/pass `test`/`test`. PIN `079545`. Logins `operator1`/`pass`, `manager1`/`secret`; badges `BADGE000000000000000001` / `...002`. Error/timeout modes: `python <SP>\fake_stations\set_mode.py --device <deviceId from Settings > Diagnostics> --mode error|timeout|clear` where `<SP>` = `C:\Users\Jonathan\AppData\Local\Temp\claude\C--Dev-Clients-PPNAM\ba7a1680-4205-4b04-bcb6-1b1f23c94914\scratchpad`.
- **Scan broadcasts:** barcode `adb -s emulator-5558 shell am broadcast -a com.scanner.broadcast --es data <CODE>`; RFID `adb -s emulator-5558 shell am broadcast -a com.rscja.scanner.action.scanner.RFID --es data <EPC>`.
- **Keyboard check recipe (used by several tasks):** tap the field, then `adb -s emulator-5558 shell dumpsys window | findstr ITYPE_IME` (IME top ≈ 1023 px for the text keyboard, ≈ 1155 px for the numeric pad), then `adb -s emulator-5558 shell uiautomator dump /sdcard/ui.xml && adb -s emulator-5558 pull /sdcard/ui.xml <SP>\ui.xml` and confirm the primary button's `bounds` bottom y < IME top, or that swiping up scrolls it above the IME. Screenshot: `cmd /c "adb -s emulator-5558 exec-out screencap -p > <SP>\shot.png"`.
- **Copy glossary (exact strings):** pill `Offline` / `Reconnecting` / `Connected` / `Station 4 offline`; Diagnostics broker row uses the same three words, station row `Online` / `Offline` / `Unknown`; timeout `Station 4 did not respond. Check the station and retry.` with a `Retry` button; `Close the app?` [Stay | Close]; `Log out?` [Cancel | Log out]; `Please fill in all fields`; `Incorrect username or password`; `Could not reach the broker (15 s)`; `Host required`; `Invalid port (1–65535)`; `Enter 0–1440`; `Signed out after N minute(s) of inactivity.`; `Log out` (never `Log Out`/`LOG OUT`).
- **Colours:** primary = launcher icon violet `#55368C`; failures use `DangerRed #E25C5C`; `WarningOrange` only for warnings/pending states. Primary buttons are 56 dp high.
- **Timeouts:** one request timeout of 10 s, single attempt (`RequestChannel.DEFAULT_TIMEOUT_MS`). The broker connect timeout stays 15 s.
- **OUT OF SCOPE (do not touch):** `AppSettings` broker defaults (`mqtt.sysone.co.za:443`, `admin`/`admin`); any MQTT topic, payload, schema version or SCRAM code; the station-offline overlay; session-persistence-across-restart; the pre-existing untracked `docs/Station4_Handheld_SOP.html`.
- **Pre-existing dirty files (leave untouched, never add):** `docs/Station4_Handheld_SOP.html` (untracked on 2026-10-02).

## Review Focus

1. A blank PIN submitted with Enter or Unlock must not count as a failed attempt and must not start a lockout — pinned by `PinGateTest` (Task 9).
2. Clearing the Port field entirely (or typing `70000`) must leave the field empty/invalid and block Test & Apply with `Invalid port (1–65535)` rather than snapping back to the old value — pinned by `SettingsValidationTest` (Task 10).
3. A replayed `operator_session_invalid` result that belongs to a previous session must not sign out the operator who just logged in — pinned by `CollectionResultFilterTest` (Task 14).
4. A badge scan on the bag or job step must say `Scan a barcode, not a badge.` and must not advance or change the draft — pinned by `WasteWizardControllerTest` (Task 15).
5. Auto sign-out `0` must never sign out, and changing the minutes mid-session must restart the timer with the new value — pinned by `InactivityMonitorTest` (Task 13).

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | modify | portrait lock, `stateHidden\|adjustResize` |
| `ui/theme/Color.kt`, `ui/theme/Type.kt` | modify | violet primary, S2 tracking/leading |
| `ui/home/HomeScreen.kt` | modify | tiles keep S1 blue via `InfoBlue`; Close-the-app dialog |
| `data/mqtt/RequestChannel.kt`, `data/mqtt/MqttOutcome.kt` | modify | 10 s default, S3 wording |
| `ui/components/ConnectionStatus.kt` | modify | `connectionStatusStateFlow()` with current value (pill flash) |
| `ui/components/AppScaffold.kt` | modify | pill wording, chip truncation, non-focusable icons, reserved progress height |
| `ui/components/AppDialogs.kt` | create | `ExitAppDialog`, `DiscardDraftDialog` |
| `ui/waste/WasteGatheringScreen.kt` | modify | scroll, Enter, 56 dp, red, dropdown, back policy, Retry now, badge hint |
| `ui/waste/WasteGatheringViewModel.kt` | modify | results consumed, retry with session, badge at step 3, review dismiss |
| `domain/wizard/WasteWizardController.kt` | modify | `dismissReview()`, `handleScannedBadge()`, `hasDraft` |
| `domain/collection/CollectionRejections.kt` | create | operator copy for `waste_collection_result` rejections |
| `domain/collection/CollectionResultFilter.kt` | create | "is this result for the current session" |
| `data/local/WasteOutboxDao.kt`, `data/mqtt/WasteCollectionPublisher.kt` | modify | re-stamp PENDING rows to the current session before replay |
| `ui/weigh/WeighBagScreen.kt`, `ui/weigh/WeighBagViewModel.kt` | modify | scroll, Enter, 56 dp, red problem card + Retry, title casing, sign-out reason |
| `ui/login/LoginScreen.kt`, `ui/login/LoginViewModel.kt` | modify | saveable fields, toggle, fill-all-fields, error above, reason text |
| `ui/login/LoginErrorMessage.kt` | create | maps auth failures to operator strings |
| `domain/usecase/LoginFailures.kt` | create | typed login exceptions |
| `domain/usecase/AuthUseCase.kt`, `data/auth/ScramExchange.kt` | modify | throw the typed exceptions; `logout(reason)` |
| `domain/pin/PinGate.kt` | create | persisted attempts/lockout logic + messages |
| `data/settings/SharedPrefsPinLockoutStore.kt` | create | SharedPreferences backing for `PinGate` |
| `domain/model/AutoSignOut.kt` | create | minutes parsing (S1 `AutoLogout` port) |
| `domain/model/AppSettings.kt`, `data/settings/SettingsRepository.kt` | modify | `autoSignOutMinutes` |
| `ui/settings/SettingsValidation.kt` | create | host/port/minutes validation + connect-failure copy |
| `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt` | modify | PIN gate, string drafts, validation, toggle, Done=apply, confirmation, Diagnostics rows |
| `ui/settings/CatalogueStatus.kt` | modify | local-time minute-precision timestamps |
| `domain/session/InactivityMonitor.kt`, `domain/session/SessionMessages.kt` | create | S1 inactivity timer port + reason copy |
| `data/session/SessionGuard.kt` | create | wires timer to session/settings/scans |
| `data/session/OperatorSessionHolder.kt` | modify | `signedOutReason` |
| `data/rfid/ForegroundTracker.kt` | create | RESUMED flag for the receiver |
| `data/rfid/DataWedgeReceiver.kt` | modify | ignore scans while not resumed |
| `data/AppContainer.kt`, `navigation/AppNavGraph.kt`, `MainActivity.kt`, `ui/session/SessionWatcher.kt` | modify | wiring |

---

## Tier 1 — one-line / manifest fixes

### Task 1: Branch setup

**Files:** none modified.

- [x] **Step 1: Record the dirty tree**

Run from the repo root:
```powershell
git status --short
```
Expected: exactly `?? docs/Station4_Handheld_SOP.html`. Record anything else that appears as "pre-existing, untouched" in your final report and never `git add` it.

- [x] **Step 2: Create the branch**

```powershell
git switch -c fix/ui-audit-2026-10-02
git branch --show-current
```
Expected: `fix/ui-audit-2026-10-02`.

- [x] **Step 3: Confirm the baseline builds and tests pass**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`. (A "SDK XML versions up to 3" warning is harmless.)

---

### Task 2: Portrait lock + soft-input mode (S4-08 part, S4-13 part; group e)

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:18-23`

- [x] **Step 1: Edit the activity element**

Replace lines 18–23 of `AndroidManifest.xml`:
```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.PPNAMStation4AA"
            android:windowSoftInputMode="stateHidden|adjustResize">
```

- [x] **Step 2: Compile**

```powershell
.\gradlew.bat --offline :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Manual verification**

Install the APK, launch, then rotate: `adb -s emulator-5558 shell settings put system accelerometer_rotation 0` and `adb -s emulator-5558 shell settings put system user_rotation 1`. Screenshot. Expected: the Login screen stays portrait (compare `23_login_landscape.png`, which must no longer be reproducible). Restore: `user_rotation 0`.

- [x] **Step 4: Commit**

```powershell
git add app/src/main/AndroidManifest.xml
git commit -m "fix(ui): lock Station 4 to portrait and resize for the keyboard

Closes S4-08 (rotation wiped typed credentials) and S4-13 (landscape
layouts with controls off the bottom) at the manifest, per the audit's
group (e) one-fix pattern.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

### Task 3: Accent = icon violet, typography tracking (static-02, static-23, S4 parity note)

**Files:**
- Modify: `ui/theme/Color.kt:15-17`
- Modify: `ui/theme/Type.kt` (whole file)
- Modify: `ui/home/HomeScreen.kt:29, 42-43, 77`
- Modify test: `app/src/androidTest/java/com/mitas/ppnam/station4aa/ui/home/HomeScreenTest.kt:13, 47, 86-91`

**Interfaces:**
- Produces: `AmberPrimary` now `Color(0xFF55368C)`; `InfoBlue` (unchanged `0xFF2E77F5`) is the Waste Collection tile colour.

- [x] **Step 1: Update the instrumented test first (it documents the tile colours)**

In `HomeScreenTest.kt` change line 13 to `import com.mitas.ppnam.station4aa.ui.theme.InfoBlue`, line 47 to `containerColor = InfoBlue,`, and lines 86–91 to:
```kotlin
    @Test
    fun theTwoTilesUseStation1sDashboardColours() {
        // Station 1 AA's activity_main.xml paints its two tiles tile_blue (#2E77F5) and tile_teal
        // (#25C7DA). The app's *primary* is now the launcher-icon violet (static-02), so the
        // dashboard keeps Station 1's blue through InfoBlue, not through the primary.
        assertEquals(0xFF2E77F5.toInt(), InfoBlue.toArgb())
        assertEquals(0xFF25C7DA.toInt(), CyanAccent.toArgb())
    }
```

- [x] **Step 2: Change the primary**

`Color.kt` lines 15–17 become:
```kotlin
// Primary accent — the launcher icon's violet (res/values/ic_launcher_background.xml), so the
// app carries its icon identity on screen the way Stations 1/3/5 do (audit static-02).
val AmberPrimary           = Color(0xFF55368C)
val AmberDark              = Color(0xFFFFFFFF)   // on-primary (white text on violet buttons)
```

- [x] **Step 3: Copy Station 2's tracking/leading into `Type.kt`**

Replace the whole file:
```kotlin
package com.mitas.ppnam.station4aa.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tracking is size-specific, never one value for all sizes: large text tightens as it grows,
// small text opens up for glanceability on the handheld's screen; body stays at 0. Leading runs
// the other way — tight on headings, comfortable on body copy. Everything is sp, so the
// operator's system text-size setting scales all of it together. Identical to Station 2's Type.kt.
val Typography = Typography(
    displaySmall  = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Normal,
        letterSpacing = (-0.4).sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp, lineHeight = 28.sp),
    headlineSmall  = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp, lineHeight = 24.sp),
    titleLarge     = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp, lineHeight = 24.sp),
    bodyLarge      = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp, lineHeight = 20.sp),
    labelSmall     = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp, lineHeight = 14.sp)
)
```

- [x] **Step 4: Keep the dashboard tile blue**

`HomeScreen.kt`: change line 29 `import com.mitas.ppnam.station4aa.ui.theme.AmberPrimary` to `import com.mitas.ppnam.station4aa.ui.theme.InfoBlue`; line 77 `containerColor = AmberPrimary,` to `containerColor = InfoBlue,`; and lines 42–43 of the doc comment to `* The tile colours are Station 1's `tile_blue` and `tile_teal`, which this app's palette carries as * [InfoBlue] and [CyanAccent] (the primary itself is the icon violet).`

- [ ] **Step 5: Compile and run the instrumented test**

```powershell
.\gradlew.bat --offline :app:assembleDebug
.\gradlew.bat --offline :app:connectedDebugAndroidTest
```
Expected: both `BUILD SUCCESSFUL` (the second needs `emulator-5558` running; if gradle picks another device, set `ANDROID_SERIAL=emulator-5558` in the environment first).

- [ ] **Step 6: Manual verification**

Install, screenshot Login. Expected: Log In button, focused field border and links are violet; compare `01_login_initial.png` (blue).

- [x] **Step 7: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/ui/theme/Color.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/theme/Type.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/home/HomeScreen.kt app/src/androidTest/java/com/mitas/ppnam/station4aa/ui/home/HomeScreenTest.kt
git commit -m "fix(theme): use the launcher-icon violet as primary and Station 2's tracking

static-02: Station 4 looked identical to Station 2 (both #2E77F5). The
dashboard tiles keep Station 1's blue via InfoBlue. static-23: Type.kt
gains the same letterSpacing/lineHeight as Station 2.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

### Task 4: One 10 s timeout and the Station 3 wording (static-08, S4-07 part)

**Files:**
- Modify: `data/mqtt/RequestChannel.kt`
- Modify: `data/mqtt/MqttOutcome.kt:27-31`
- Create test: `app/src/test/java/com/mitas/ppnam/station4aa/data/mqtt/MqttOutcomeTest.kt`

**Interfaces:**
- Produces: `RequestChannel.DEFAULT_TIMEOUT_MS = 10_000L`; `FailureKind.describe()` returns the glossary strings (used by login, catalogue sync, weigh).

- [x] **Step 1: Write the failing test**

```kotlin
package com.mitas.ppnam.station4aa.data.mqtt

import org.junit.Assert.assertEquals
import org.junit.Test

/** The fleet standard (audit §5 "Timeout seconds / wording"): 10 s, single attempt, Station 3's
 * wording, and the operator is told to retry. */
class MqttOutcomeTest {

    @Test
    fun `the default request timeout is ten seconds`() {
        assertEquals(10_000L, RequestChannel.DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun `a timeout reads as the station not responding`() {
        assertEquals("Station 4 did not respond. Check the station and retry.", FailureKind.Timeout.describe())
    }

    @Test
    fun `not connected tells the operator where to look`() {
        assertEquals("Not connected to the broker. Check Settings and retry.", FailureKind.NotConnected.describe())
    }

    @Test
    fun `an unreadable reply is still a retry`() {
        assertEquals("Station 4 sent an unreadable reply. Retry.", FailureKind.MalformedResponse.describe())
    }
}
```

- [x] **Step 2: Run it to see it fail**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.data.mqtt.MqttOutcomeTest"
```
Expected: compilation error `Unresolved reference: DEFAULT_TIMEOUT_MS`.

- [x] **Step 3: Implement**

`RequestChannel.kt` whole file:
```kotlin
package com.mitas.ppnam.station4aa.data.mqtt

/**
 * The request/response round trip [MqttRequestChannel] performs, as an interface so use cases can
 * be tested without a broker. Defaults live here; implementations must not repeat them.
 */
interface RequestChannel {
    suspend fun <T : Any> request(
        deviceId: String,
        requestType: String,
        responseClass: Class<T>,
        payload: Any,
        operatorSessionId: String = "",
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): MqttOutcome<T>

    companion object {
        /** Fleet standard: 10 s, one attempt (Stations 1–3 already use 10 s; 15 s was Station 4's
         * own number). Weigh/login/catalogue all inherit this. */
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
```

`MqttOutcome.kt` lines 27–31:
```kotlin
internal fun FailureKind.describe(): String = when (this) {
    FailureKind.NotConnected -> "Not connected to the broker. Check Settings and retry."
    FailureKind.Timeout -> "Station 4 did not respond. Check the station and retry."
    FailureKind.MalformedResponse -> "Station 4 sent an unreadable reply. Retry."
}
```

- [x] **Step 4: Run the whole JVM suite**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL` (no existing test asserts the old strings — verified by `grep -rn "No response received" app/src/test` returning nothing).

- [x] **Step 5: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/data/mqtt/RequestChannel.kt app/src/main/java/com/mitas/ppnam/station4aa/data/mqtt/MqttOutcome.kt app/src/test/java/com/mitas/ppnam/station4aa/data/mqtt/MqttOutcomeTest.kt
git commit -m "fix(mqtt): 10 s single-attempt request timeout with the fleet wording

static-08: Station 4 waited 15 s and said 'No response received'; the
fleet standard is 10 s and Station 3's 'did not respond ... retry' copy.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

### Task 5: AppScaffold — pill wording, no Offline flash, chip truncation, non-focusable icons, steady progress bar (S4-05, S4-11, S4-15 part, S4-17 part, static-18)

**Files:**
- Modify: `ui/components/ConnectionStatus.kt`
- Modify: `ui/components/AppScaffold.kt`
- Modify: `ui/home/HomeViewModel.kt:27-30`, `ui/login/LoginViewModel.kt:47-50`, `ui/settings/SettingsViewModel.kt:99-102`, `ui/waste/WasteGatheringViewModel.kt:66-69`, `ui/weigh/WeighBagViewModel.kt:55-58`

**Interfaces:**
- Produces: `fun MqttConnectionManager.connectionStatusStateFlow(scope: CoroutineScope): StateFlow<ConnectionStatus>` — every ViewModel's `connectionStatus` becomes this one-liner.

- [x] **Step 1: Add the helper to `ConnectionStatus.kt`**

Append after `connectionStatusFlow` (and add the imports `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.flow.SharingStarted`, `kotlinx.coroutines.flow.StateFlow`, `kotlinx.coroutines.flow.stateIn`, `com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionManager`):
```kotlin
/**
 * The per-screen StateFlow every ViewModel exposes to [AppScaffold]. The initial value is resolved
 * from the manager's *current* state, not a hard-coded [ConnectionStatus.Offline]: with the
 * 1.5 s debounce above, a hard-coded initial painted a red "Offline" pill on every screen entry
 * while the broker was in fact connected (audit S4-05).
 */
fun MqttConnectionManager.connectionStatusStateFlow(scope: CoroutineScope): StateFlow<ConnectionStatus> =
    connectionStatusFlow(connectionState, stationOnline).stateIn(
        scope,
        SharingStarted.WhileSubscribed(5_000),
        resolveConnectionStatus(connectionState.value, stationOnline.value),
    )
```

- [x] **Step 2: Use it in all five ViewModels**

In each file replace the four-line `val connectionStatus: StateFlow<ConnectionStatus> = connectionStatusFlow(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionStatus.Offline)` block with:
```kotlin
    val connectionStatus: StateFlow<ConnectionStatus> = connectionManager.connectionStatusStateFlow(viewModelScope)
```
and change the import `com.mitas.ppnam.station4aa.ui.components.connectionStatusFlow` to `com.mitas.ppnam.station4aa.ui.components.connectionStatusStateFlow`. In `HomeViewModel.kt` and `LoginViewModel.kt` the imports `kotlinx.coroutines.flow.SharingStarted` and `kotlinx.coroutines.flow.stateIn` become unused — delete them (the other three ViewModels still use `stateIn` elsewhere).

- [x] **Step 3: AppScaffold edits**

(a) Line 48: `ConnectionStatus.StationOffline -> WarningOrange to "Station 4 offline"`.

(b) Add import `androidx.compose.ui.focus.focusProperties`. Give every `IconButton` in the file (back ×2, settings ×2) `modifier = Modifier.focusProperties { canFocus = false }` so a hardware Enter after a submit cannot land focus on them (S4-15). Example for the first one (lines 118–124):
```kotlin
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.focusProperties { canFocus = false },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = AmberPrimary
                                )
                            }
```

(c) Chip truncation (S4-11): replace line 135 `text = if (!operatorRole.isNullOrBlank()) "$operatorName · $operatorRole" else operatorName,` with
```kotlin
                                    // With a back arrow in the row there is no room for the role
                                    // ("Operator One · Op…" on every sub-screen, audit S4-11).
                                    text = if (onBack == null && !operatorRole.isNullOrBlank()) "$operatorName · $operatorRole" else operatorName,
```

(d) Reserved progress height (S4-17 "12 px jump"): replace lines 211–216 with
```kotlin
                // Always reserve the bar's 4 dp so content does not jump when a request starts.
                Box(Modifier.fillMaxWidth().height(4.dp)) {
                    if (loading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = AmberPrimary,
                            trackColor = GraphiteBorder
                        )
                    }
                }
```

(e) Fix the stale class doc (lines 25–28) to: `Shared top-bar chrome, mirroring Station 2's AppScaffold. With operatorName null the bar collapses to a plain TopAppBar with the title, optional Settings icon and the connection pill.`

- [x] **Step 4: Compile + JVM tests**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
.\gradlew.bat --offline :app:assembleDebug
```
Expected: both succeed.

- [ ] **Step 5: Manual verification**

Install; configure Settings for the fake broker (PIN 079545, host 10.0.2.2, port 9001, WS on, TLS off, test/test, Test & Apply). Log in as operator1 and open Waste Collection, then Weigh Bag, then Settings, screenshotting the first frame each time (`exec-out screencap` immediately after `input tap`). Expected: no red "Offline" pill on entry (was `40_wc_initial.png`); chip reads `Operator One` on sub-screens (was `Operator One · Op…`); stop the fake backend and wait: pill reads `Station 4 offline`.

- [x] **Step 6: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/ui/components/ConnectionStatus.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/components/AppScaffold.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/home/HomeViewModel.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginViewModel.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsViewModel.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringViewModel.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/weigh/WeighBagViewModel.kt
git commit -m "fix(scaffold): stop the Offline flash, name the station in the pill, keep the chip readable

S4-05: initial pill state is resolved from the live connection manager.
static-18: 'Station 4 offline'. S4-11: role dropped when a back arrow
is shown. S4-15: toolbar icons are not focusable. S4-17: progress bar
height is reserved so content no longer jumps.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

## Tier 2 — one pattern per screen

### Task 6: Waste Collection steps — scroll, Enter submits, 56 dp, red errors, outlined dropdowns (S4-01, S4-13 part, S4-14 part, S4-15 part, S4-16, static-01, static-19)

**Files:**
- Modify: `ui/waste/WasteGatheringScreen.kt`

No JVM/UI test is feasible for a Compose layout here (no Robolectric; the screen needs a live ViewModel graph), so the test for this task is the documented emulator check in Step 6 plus the compile.

- [x] **Step 1: Make the step column scroll**

Replace lines 112–118 (the `Column(` opening inside `AppScaffold`) with:
```kotlin
        // `padding` already carries the IME inset (AppScaffold uses WindowInsets.safeDrawing), so
        // the content area shrinks above the keyboard; what was missing was a scroll container —
        // without one, Submit was half-clipped and Cancel transaction unreachable (audit S4-01).
        // Deliberately no extra imePadding(): it would double the IME inset.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
```
Add imports `androidx.compose.foundation.rememberScrollState`, `androidx.compose.foundation.verticalScroll`.

- [x] **Step 2: Danger red for failures, orange only for pending**

- Line 87 (review-dialog `stepError`): `color = WarningOrange` → `color = DangerRed`.
- Line 134: `color = if (lastMessageIsError) WarningOrange else TextMuted,` → `color = if (lastMessageIsError) DangerRed else TextMuted,`.
- Line 183: `Text("Cancel transaction", color = WarningOrange)` → `Text("Cancel transaction", color = DangerRed)`.
- Lines 121–123 (pending count line) keep `WarningOrange` — pending state, not a failure.
- Line 294 (empty-catalogue message) keeps `WarningOrange` — a warning.
- Add import `com.mitas.ppnam.station4aa.ui.theme.DangerRed`.

- [x] **Step 3: Rewrite `ScanStep` (Enter submits, error above the field, code keyboard, 56 dp, clear focus)**

Replace lines 215–257 with:
```kotlin
@Composable
private fun ScanStep(
    label: String,
    hint: String,
    errorMessage: String?,
    onSubmit: (String) -> Unit,
) {
    var manualValue by rememberSaveable(label) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val submit: () -> Unit = {
        if (manualValue.isNotBlank()) {
            // Clearing focus first stops a hardware Enter from landing on the toolbar icons
            // (audit S4-15) and closes the keyboard so the next step is fully visible.
            focusManager.clearFocus()
            onSubmit(manualValue)
            manualValue = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(hint, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        // Above the field, not below it: text under the field lands exactly under the keyboard
        // (audit group (a) sub-cause 4).
        if (errorMessage != null) {
            Text(errorMessage, style = MaterialTheme.typography.labelMedium, color = DangerRed)
        }
        OutlinedTextField(
            value = manualValue,
            onValueChange = { manualValue = it },
            label = { Text("Manual entry") },
            singleLine = true,
            isError = errorMessage != null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmberPrimary,
                focusedLabelColor = AmberPrimary,
                cursorColor = AmberPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = submit,
            enabled = manualValue.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Submit")
        }
    }
}
```
Imports to add: `androidx.compose.foundation.layout.height`, `androidx.compose.foundation.text.KeyboardActions`, `androidx.compose.foundation.text.KeyboardOptions`, `androidx.compose.runtime.saveable.rememberSaveable`, `androidx.compose.ui.platform.LocalFocusManager`, `androidx.compose.ui.text.input.ImeAction`, `androidx.compose.ui.text.input.KeyboardCapitalization`, `androidx.compose.ui.text.input.KeyboardType`.

Update the three call sites (lines 146–160) to pass the new `hint` parameter:
```kotlin
                WizardStep.SCAN_BAG -> ScanStep(
                    label = "Scan bag code",
                    hint = "Scan the barcode, or enter it manually below.",
                    errorMessage = stepError,
                    onSubmit = viewModel::onBagCodeSubmitted,
                )
                WizardStep.SCAN_JOB -> ScanStep(
                    label = "Scan or enter the job number",
                    hint = "Scan the barcode, or enter it manually below.",
                    errorMessage = stepError,
                    onSubmit = viewModel::onJobNumberSubmitted,
                )
                WizardStep.SCAN_OPERATOR -> ScanStep(
                    label = "Scan or enter the operator ID",
                    hint = "Scan the operator's barcode or badge, or enter the ID manually below.",
                    errorMessage = stepError,
                    onSubmit = viewModel::onOperatorIdSubmitted,
                )
```
(The badge itself is accepted at step 3 in Task 15; the hint lands now so the copy lives in one place.)

- [x] **Step 4: 56 dp Confirm and an outlined dropdown with a bounded menu**

In `CatalogueStep` (lines 308–314) make the button `modifier = Modifier.fillMaxWidth().height(56.dp)`.

Replace `DropdownSelector` (lines 330–376) with:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownSelector(
    label: String,
    options: List<T>,
    selected: T,
    display: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    // Outlined like every other field in the app (audit S4-16); no Card wrapper.
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmberPrimary,
                focusedLabelColor = AmberPrimary,
            ),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Five 48 dp rows exactly: the menu never ends in a clipped sliver of a sixth item
            // (audit S4-16); longer lists scroll inside the menu.
            modifier = Modifier.heightIn(max = 240.dp),
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
```
Add imports `androidx.compose.foundation.layout.heightIn`, `androidx.compose.material3.MenuAnchorType`; remove the now-unused `androidx.compose.material3.TextField`, `androidx.compose.material3.Card`, `androidx.compose.material3.CardDefaults`, `androidx.compose.foundation.BorderStroke`, `androidx.compose.foundation.layout.Box`, `com.mitas.ppnam.station4aa.ui.theme.GraphiteBorder` (`GraphiteSurface` is still used by the dialog).

- [x] **Step 5: Compile**

```powershell
.\gradlew.bat --offline :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Manual verification (keyboard matrix row "S4 Waste Collection steps 1–3")**

Install, log in, Home > Waste Collection, tap Manual entry, type `BAG-77`. Run the keyboard check recipe. Expected: Submit's `bounds` bottom < 1023 or the column scrolls to reveal it; `Cancel transaction` reachable by swiping up with the keyboard open (was `41_wc_manual_kb.png` / `42_wc_manual_kb_swiped.png`). `adb -s emulator-5558 shell input keyevent KEYCODE_ENTER`: step advances to "Step 2 of 5" and the keyboard closes (was `43_wc_after_enter.png`). Clear the field, type a space, Enter: red `Required.` appears *above* the field. Steps 4–5: dropdown fields are outlined; the waste-type menu shows five whole rows and scrolls (was `50_wc_wastetype_dropdown.png`).

- [x] **Step 7: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringScreen.kt
git commit -m "fix(waste): scroll the wizard, submit on Enter, 56 dp buttons, red errors, outlined dropdowns

S4-01/static-01: Submit and Cancel transaction were under the keyboard.
S4-14: code keyboard with Done. S4-15: focus cleared on submit.
S4-16: dropdowns outlined, menu bounded to whole rows. static-19:
failures in danger red, primary buttons 56 dp.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

### Task 7: Weigh Bag — scroll, Enter requests, 56 dp, red problem card with Retry, title casing (S4-02, S4-13 part, S4-14 part, S4-15 part, S4-17 part, static-08 Retry, static-19)

**Files:**
- Modify: `ui/weigh/WeighBagScreen.kt`

Compose-only; verification is the emulator check in Step 3 plus the compile.

- [x] **Step 1: Rewrite `WeighBagScreen` and `ProblemCard`**

Replace lines 39–108 with:
```kotlin
@Composable
fun WeighBagScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    viewModel: WeighBagViewModel,
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val session by viewModel.session.collectAsState()
    val operatorName by viewModel.operatorName.collectAsState()
    val bagCode by viewModel.bagCode.collectAsState()
    val feedback by viewModel.feedback.collectAsState()
    val isWeighing by viewModel.isWeighing.collectAsState()
    val focusManager = LocalFocusManager.current

    val requestWeight: () -> Unit = {
        if (bagCode.isNotBlank() && !isWeighing) {
            focusManager.clearFocus()
            viewModel.onRequestWeight()
        }
    }

    AppScaffold(
        title = "Weigh Bag",
        status = connectionStatus,
        onBack = onBack,
        onSettings = onSettings,
        operatorName = operatorName.takeIf { it.isNotBlank() },
        operatorRole = session?.role,
        loading = isWeighing,
    ) { padding ->
        // Scrollable so the field, the button and the result card are all reachable with the
        // keyboard up (audit S4-02). `padding` already includes the IME inset.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Place the bag on the Station 4 scale, then scan its code.",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                "Nobody needs to be signed in at the station — the weight is recorded against you.",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )

            OutlinedTextField(
                value = bagCode,
                onValueChange = viewModel::onBagCodeChanged,
                label = { Text("Bag code") },
                singleLine = true,
                enabled = !isWeighing,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { requestWeight() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AmberPrimary,
                    focusedLabelColor = AmberPrimary,
                    cursorColor = AmberPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = requestWeight,
                enabled = bagCode.isNotBlank() && !isWeighing,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(if (isWeighing) "Weighing…" else "Request weight")
            }

            when (val current = feedback) {
                is WeighFeedback.Weighed -> WeighedCard(current)
                is WeighFeedback.Problem -> ProblemCard(
                    problem = current,
                    onRetry = if (current.canRetrySameBag) requestWeight else null,
                )
                null -> Unit
            }
        }
    }
}
```
and replace `ProblemCard` (lines 145–167) with:
```kotlin
@Composable
private fun ProblemCard(problem: WeighFeedback.Problem, onRetry: (() -> Unit)?) {
    // Danger red, not orange: this is a failure (audit static-19).
    Card(
        colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(problem.message, style = MaterialTheme.typography.bodyMedium, color = DangerRed)
            if (onRetry != null) {
                // The bag code is still in the field precisely so this is one tap away.
                Text(
                    "The bag code is still here — fix the problem and tap Retry.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Retry") }
            }
        }
    }
}
```
Imports to add: `androidx.compose.foundation.layout.height`, `androidx.compose.foundation.rememberScrollState`, `androidx.compose.foundation.text.KeyboardActions`, `androidx.compose.foundation.text.KeyboardOptions`, `androidx.compose.foundation.verticalScroll`, `androidx.compose.material3.OutlinedButton`, `androidx.compose.ui.platform.LocalFocusManager`, `androidx.compose.ui.text.input.ImeAction`, `androidx.compose.ui.text.input.KeyboardCapitalization`, `androidx.compose.ui.text.input.KeyboardType`, `com.mitas.ppnam.station4aa.ui.theme.DangerRed`. Remove `com.mitas.ppnam.station4aa.ui.theme.WarningOrange` (no longer used).

- [x] **Step 2: Compile**

```powershell
.\gradlew.bat --offline :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Manual verification (keyboard matrix row "S4 Weigh Bag")**

Home > Weigh Bag. Title reads `Weigh Bag` (matches the tile). Tap Bag code, type `BAG-77`, run the keyboard recipe: `Request weight` bottom < 1023 or scrollable into view (was a 34 px sliver, `71_weigh_kb.png`). `KEYCODE_ENTER` → progress bar, then green `12.34 kg` card (was `72_weigh_after_enter.png`: nothing). `set_mode.py --mode timeout`, request again: after ~10 s a red-bordered card `Station 4 did not respond. Check the station and retry.` with a `Retry` button; tap Retry → a new request. `--mode error`: red card `This bag is not waiting to be weighed. Register the collection first.` with no Retry (bag code cleared). `--mode clear` afterwards.

- [x] **Step 4: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/ui/weigh/WeighBagScreen.kt
git commit -m "fix(weigh): scroll above the keyboard, Enter requests, red problem card with Retry

S4-02/static-01: Request weight was clipped to a sliver under the IME.
S4-14/S4-15: Done requests and clears focus. static-08: explicit Retry.
static-19: 56 dp button, danger red. S4-17: title 'Weigh Bag'.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

### Task 8: Login — saveable fields, password toggle, empty-field check, error above fields, operator-facing auth errors (S4-07 login, S4-08, S4-15 part, static-20 toggle, static-25; group f)

**Files:**
- Create: `domain/usecase/LoginFailures.kt`
- Modify: `data/auth/ScramExchange.kt:36-37, 85-86`
- Modify: `domain/usecase/AuthUseCase.kt:77-78`
- Create: `ui/login/LoginErrorMessage.kt`
- Modify: `ui/login/LoginViewModel.kt:68-70, 86-88`
- Modify: `ui/login/LoginScreen.kt`
- Create test: `app/src/test/java/com/mitas/ppnam/station4aa/ui/login/LoginErrorMessageTest.kt`

**Interfaces:**
- Produces: `class LoginRejectedException(val errorCode: String?, val reason: String?)`, `class LoginTransportException(val kind: FailureKind)` in `domain.usecase`; `fun loginErrorMessage(failure: Throwable): String` in `ui.login`.

- [x] **Step 1: Write the failing test**

```kotlin
package com.mitas.ppnam.station4aa.ui.login

import com.mitas.ppnam.station4aa.data.mqtt.FailureKind
import com.mitas.ppnam.station4aa.domain.usecase.LoginRejectedException
import com.mitas.ppnam.station4aa.domain.usecase.LoginTransportException
import org.junit.Assert.assertEquals
import org.junit.Test

/** Audit S4-07 / group (f): the operator never sees backend or library text on the login line. */
class LoginErrorMessageTest {

    @Test
    fun `a rejected password never echoes the backend reason`() {
        assertEquals(
            "Incorrect username or password",
            loginErrorMessage(LoginRejectedException("authentication_failed", "SCRAM proof rejected.")),
        )
    }

    @Test
    fun `a rejected badge is named as a badge problem`() {
        assertEquals(
            "Badge not recognised. Ask a manager.",
            loginErrorMessage(LoginRejectedException("badge_unknown", "No such badge")),
        )
    }

    @Test
    fun `a timeout uses the fleet timeout wording`() {
        assertEquals(
            "Station 4 did not respond. Check the station and retry.",
            loginErrorMessage(LoginTransportException(FailureKind.Timeout)),
        )
    }

    @Test
    fun `anything else is a generic retry, not an exception message`() {
        assertEquals("Login failed. Try again.", loginErrorMessage(IllegalStateException("java.lang.Whatever: boom")))
    }
}
```

- [x] **Step 2: Run it to see it fail**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.ui.login.LoginErrorMessageTest"
```
Expected: `Unresolved reference: LoginRejectedException`.

- [x] **Step 3: Typed failures**

Create `domain/usecase/LoginFailures.kt`:
```kotlin
package com.mitas.ppnam.station4aa.domain.usecase

import com.mitas.ppnam.station4aa.data.mqtt.FailureKind
import com.mitas.ppnam.station4aa.data.mqtt.describe

/** The station answered and said no. [reason] is backend text — kept for logs, never shown. */
class LoginRejectedException(val errorCode: String?, val reason: String?) :
    Exception(reason ?: errorCode ?: "Login rejected")

/** Nothing usable came back from the station. */
class LoginTransportException(val kind: FailureKind) : Exception(kind.describe())
```

`ScramExchange.kt`: line 36 becomes `is MqttOutcome.Rejected -> return Result.failure(LoginRejectedException(startOutcome.errorCode, startOutcome.reason))`, line 37 `is MqttOutcome.NoResponse -> return Result.failure(LoginTransportException(startOutcome.kind))`; lines 85–86 likewise with `proofOutcome`. Add imports `com.mitas.ppnam.station4aa.domain.usecase.LoginRejectedException`, `com.mitas.ppnam.station4aa.domain.usecase.LoginTransportException`; remove the now-unused `describe` import.

`AuthUseCase.kt` lines 77–78:
```kotlin
            is MqttOutcome.Rejected -> Result.failure(LoginRejectedException(outcome.errorCode, outcome.reason))
            is MqttOutcome.NoResponse -> Result.failure(LoginTransportException(outcome.kind))
```
(remove the unused `describe` import).

- [x] **Step 4: The mapping**

Create `ui/login/LoginErrorMessage.kt`:
```kotlin
package com.mitas.ppnam.station4aa.ui.login

import com.mitas.ppnam.station4aa.data.mqtt.describe
import com.mitas.ppnam.station4aa.domain.usecase.LoginRejectedException
import com.mitas.ppnam.station4aa.domain.usecase.LoginTransportException

/** One place that turns an auth failure into the line above the login fields. Pure. */
fun loginErrorMessage(failure: Throwable): String = when (failure) {
    is LoginRejectedException ->
        if (failure.errorCode.orEmpty().startsWith("badge")) "Badge not recognised. Ask a manager."
        else "Incorrect username or password"
    is LoginTransportException -> failure.kind.describe()
    else -> "Login failed. Try again."
}
```

`LoginViewModel.kt`: replace lines 68–70 with
```kotlin
    fun submitCredentials(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Please fill in all fields")
            return
        }
        attemptLogin(LoginMethod.Credentials(username.trim(), password))
    }
```
and line 87 `_uiState.value = LoginUiState.Error(e.message ?: "Login failed")` with `_uiState.value = LoginUiState.Error(loginErrorMessage(e))`.

- [x] **Step 5: Run the test**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.ui.login.LoginErrorMessageTest"
```
Expected: PASS.

- [x] **Step 6: LoginScreen**

Edit `LoginScreen.kt`:

(a) Lines 69–70 → saveable, plus a toggle flag:
```kotlin
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
```
(b) After `val focusManager = LocalFocusManager.current` add:
```kotlin
    val submit: () -> Unit = {
        focusManager.clearFocus()
        viewModel.submitCredentials(username, password)
    }
```
(c) Move the error block (lines 173–179) to sit *before* the Username field, inside the card column:
```kotlin
                    if (uiState is LoginUiState.Error) {
                        Text(
                            text = (uiState as LoginUiState.Error).message,
                            color = DangerRed,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
```
(d) Password field: `visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),`, `keyboardActions = KeyboardActions(onDone = { submit() }),` and add
```kotlin
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                    tint = TextMuted,
                                )
                            }
                        },
```
(e) Log In button `onClick = submit`.
(f) Badge row (lines 199–208): replace the literal padding spaces:
```kotlin
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = GraphiteBorder)
                        Text(
                            "or scan your badge",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        HorizontalDivider(Modifier.weight(1f), color = GraphiteBorder)
                    }
```
Imports to add: `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.filled.Visibility`, `androidx.compose.material.icons.filled.VisibilityOff`, `androidx.compose.material3.Icon`, `androidx.compose.material3.IconButton`, `androidx.compose.ui.text.input.VisualTransformation`. Remove the unused `androidx.compose.runtime.remember`.

- [x] **Step 7: Compile + full JVM suite**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
.\gradlew.bat --offline :app:assembleDebug
```
Expected: both succeed.

- [ ] **Step 8: Manual verification**

Login: tap Log In with both fields empty → `Please fill in all fields` above the Username field, no request. Type `operator1` / `wrong`, Enter → `Incorrect username or password` (was `SCRAM proof rejected.`, `21_login_wrong_creds.png`); no grey focus halo on the gear icon. Eye icon reveals/hides the password. Configuration-change survival: with text typed in both fields, change the system font scale (`adb -s emulator-5558 shell settings put system font_scale 1.15`, then back to `1.0`) — the typed values are still there.

- [x] **Step 9: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/domain/usecase/LoginFailures.kt app/src/main/java/com/mitas/ppnam/station4aa/data/auth/ScramExchange.kt app/src/main/java/com/mitas/ppnam/station4aa/domain/usecase/AuthUseCase.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginErrorMessage.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginViewModel.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginScreen.kt app/src/test/java/com/mitas/ppnam/station4aa/ui/login/LoginErrorMessageTest.kt
git commit -m "fix(login): operator-facing auth errors, empty-field check, password toggle, saveable fields

S4-07: 'SCRAM proof rejected.' becomes 'Incorrect username or password'
via typed login failures. static-25: 'Please fill in all fields'.
static-20: password visibility toggle. S4-08: rememberSaveable.
S4-15: focus cleared on submit. Error line now sits above the fields.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

### Task 9: Settings PIN gate — persisted attempts and lockout, blank guard, ticker, error above the field (S4-06, static-21 pattern; group c)

**Files:**
- Create: `domain/pin/PinGate.kt`
- Create: `data/settings/SharedPrefsPinLockoutStore.kt`
- Modify: `data/AppContainer.kt` (add `pinLockoutStore`)
- Modify: `navigation/AppNavGraph.kt:120-135`
- Modify: `ui/settings/SettingsViewModel.kt:61-95, 133-178`
- Modify: `ui/settings/SettingsScreen.kt:195-248`
- Create test: `app/src/test/java/com/mitas/ppnam/station4aa/domain/pin/PinGateTest.kt`

**Interfaces:**
- Produces: `interface PinLockoutStore { var failedAttempts: Int; var lockedOutUntilMs: Long }`, `class InMemoryPinLockoutStore`, `class PinGate(correctPin, store, now)` with `submit(pin): PinGateResult`, `remainingLockoutMs()`, `isLockedOut`; `wrongPinMessage(attemptsLeft)`, `lockoutMessage(remainingMs)`; `SettingsViewModel` gains constructor param `pinLockoutStore: PinLockoutStore` and state `pinLockedOut: State<Boolean>`.

- [x] **Step 1: Write the failing test**

```kotlin
package com.mitas.ppnam.station4aa.domain.pin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit group (c): the lockout must survive leaving the screen, a blank submit must not count,
 * and the countdown must be derived from a persisted deadline. */
class PinGateTest {

    private var now = 1_000_000L
    private val store = InMemoryPinLockoutStore()
    private fun gate() = PinGate(correctPin = "079545", store = store, now = { now })

    @Test
    fun `the correct PIN unlocks and resets the counter`() {
        val gate = gate()
        gate.submit("000000")
        assertEquals(PinGateResult.Unlocked, gate.submit("079545"))
        assertEquals(0, store.failedAttempts)
    }

    @Test
    fun `a wrong PIN reports attempts left`() {
        assertEquals(PinGateResult.Wrong(attemptsLeft = 4), gate().submit("000000"))
        assertEquals(PinGateResult.Wrong(attemptsLeft = 3), gate().submit("000000"))
    }

    @Test
    fun `a blank PIN is not an attempt`() {
        val gate = gate()
        assertEquals(PinGateResult.Blank, gate.submit(""))
        assertEquals(PinGateResult.Blank, gate.submit("   "))
        assertEquals(0, store.failedAttempts)
        assertEquals(PinGateResult.Wrong(attemptsLeft = 4), gate.submit("1"))
    }

    @Test
    fun `five wrong attempts lock the gate for thirty seconds`() {
        val gate = gate()
        repeat(4) { gate.submit("000000") }
        assertEquals(PinGateResult.LockedOut(remainingMs = 30_000L), gate.submit("000000"))
        assertTrue(gate.isLockedOut)
        now += 29_000
        assertEquals(PinGateResult.LockedOut(remainingMs = 1_000L), gate.submit("079545"))
        now += 1_000
        assertFalse(gate.isLockedOut)
        assertEquals(PinGateResult.Unlocked, gate.submit("079545"))
    }

    @Test
    fun `the lockout survives a new gate instance because it lives in the store`() {
        repeat(5) { gate().submit("000000") }
        // A fresh ViewModel (new screen, or process restart with the same prefs) must still be locked.
        assertTrue(gate().isLockedOut)
        assertEquals(PinGateResult.LockedOut(remainingMs = 30_000L), gate().submit("079545"))
    }

    @Test
    fun `messages read the way the audit glossary says`() {
        assertEquals("Incorrect PIN. 1 attempt left before lockout.", wrongPinMessage(1))
        assertEquals("Incorrect PIN. 4 attempts left before lockout.", wrongPinMessage(4))
        assertEquals("Too many attempts. Try again in 30s.", lockoutMessage(30_000L))
        assertEquals("Too many attempts. Try again in 1s.", lockoutMessage(1L))
    }
}
```

- [x] **Step 2: Run it to see it fail**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.domain.pin.PinGateTest"
```
Expected: `Unresolved reference: PinGate`.

- [x] **Step 3: Implement `PinGate.kt`**

```kotlin
package com.mitas.ppnam.station4aa.domain.pin

/** Where the attempt counter and lockout deadline live. Persisted in production (SharedPreferences)
 * so leaving Settings, or restarting the process, cannot reset the counter (audit group (c)). */
interface PinLockoutStore {
    var failedAttempts: Int
    /** Wall-clock epoch ms; 0 = not locked out. */
    var lockedOutUntilMs: Long
}

class InMemoryPinLockoutStore : PinLockoutStore {
    override var failedAttempts: Int = 0
    override var lockedOutUntilMs: Long = 0L
}

sealed interface PinGateResult {
    object Blank : PinGateResult
    object Unlocked : PinGateResult
    data class Wrong(val attemptsLeft: Int) : PinGateResult
    data class LockedOut(val remainingMs: Long) : PinGateResult
}

/** Pure supervisor-PIN gate: same five-attempt / 30 s policy every PPNAM app uses. */
class PinGate(
    private val correctPin: String,
    private val store: PinLockoutStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L
    }

    fun remainingLockoutMs(): Long = (store.lockedOutUntilMs - now()).coerceAtLeast(0L)

    val isLockedOut: Boolean get() = remainingLockoutMs() > 0L

    fun submit(pin: String): PinGateResult {
        val remaining = remainingLockoutMs()
        if (remaining > 0L) return PinGateResult.LockedOut(remaining)
        if (pin.isBlank()) return PinGateResult.Blank
        if (pin == correctPin) {
            store.failedAttempts = 0
            store.lockedOutUntilMs = 0L
            return PinGateResult.Unlocked
        }
        val failed = store.failedAttempts + 1
        if (failed >= MAX_ATTEMPTS) {
            store.failedAttempts = 0
            store.lockedOutUntilMs = now() + LOCKOUT_MS
            return PinGateResult.LockedOut(LOCKOUT_MS)
        }
        store.failedAttempts = failed
        return PinGateResult.Wrong(MAX_ATTEMPTS - failed)
    }
}

fun wrongPinMessage(attemptsLeft: Int): String =
    "Incorrect PIN. $attemptsLeft ${if (attemptsLeft == 1) "attempt" else "attempts"} left before lockout."

fun lockoutMessage(remainingMs: Long): String =
    "Too many attempts. Try again in ${(remainingMs + 999) / 1_000}s."
```

- [x] **Step 4: Run the test**

Same command as Step 2. Expected: PASS (6 tests).

- [x] **Step 5: SharedPreferences store + wiring**

Create `data/settings/SharedPrefsPinLockoutStore.kt`:
```kotlin
package com.mitas.ppnam.station4aa.data.settings

import android.content.Context
import com.mitas.ppnam.station4aa.domain.pin.PinLockoutStore

/** Process- and screen-independent backing for the supervisor PIN gate. Plain prefs: the values
 * are a counter and a deadline, nothing secret. */
class SharedPrefsPinLockoutStore(context: Context) : PinLockoutStore {
    private val prefs = context.applicationContext.getSharedPreferences("pin_gate", Context.MODE_PRIVATE)

    override var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED, 0)
        set(value) = prefs.edit().putInt(KEY_FAILED, value).apply()

    override var lockedOutUntilMs: Long
        get() = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_LOCKED_UNTIL, value).apply()

    private companion object {
        const val KEY_FAILED = "failed_attempts"
        const val KEY_LOCKED_UNTIL = "locked_out_until_ms"
    }
}
```
`AppContainer.kt`: after line 45 (`val settingsRepository = ...`) add
```kotlin
    val pinLockoutStore: PinLockoutStore = SharedPrefsPinLockoutStore(appContext)
```
with imports `com.mitas.ppnam.station4aa.data.settings.SharedPrefsPinLockoutStore`, `com.mitas.ppnam.station4aa.domain.pin.PinLockoutStore`.
`AppNavGraph.kt` lines 124–132: add `pinLockoutStore = container.pinLockoutStore,` to the `SettingsViewModel(...)` call.

- [x] **Step 6: SettingsViewModel**

Add constructor parameter (after `syncCatalogue`): `private val pinLockoutStore: PinLockoutStore,`. Replace lines 61–67 with:
```kotlin
    private val pinGate = PinGate(correctPin = "079545", store = pinLockoutStore)

    /** True while the persisted lockout deadline is in the future; the field and Unlock are
     * disabled and [pinLockoutMessage] counts down once a second. */
    var pinLockedOut = mutableStateOf(false)
        private set
    private var lockoutTicker: Job? = null
```
Replace `submitPin()` and the companion (lines 141–178) with:
```kotlin
    fun submitPin() {
        when (val result = pinGate.submit(pinInput.value)) {
            PinGateResult.Blank -> {
                pinError.value = true
                pinErrorMessage.value = "Enter the supervisor PIN."
            }
            PinGateResult.Unlocked -> {
                pinInput.value = ""
                pinError.value = false
                pinErrorMessage.value = null
                pinLockoutMessage.value = null
                pinState.value = PinState.Unlocked
            }
            is PinGateResult.Wrong -> {
                pinInput.value = ""
                pinError.value = true
                pinErrorMessage.value = wrongPinMessage(result.attemptsLeft)
                pinLockoutMessage.value = null
            }
            is PinGateResult.LockedOut -> {
                pinInput.value = ""
                pinError.value = true
                pinErrorMessage.value = null
                startLockoutTicker()
            }
        }
    }

    /** Re-derives the countdown from the persisted deadline every second until it passes, so the
     * message is never a static "30s" and a lockout started on a previous visit still shows. */
    private fun startLockoutTicker() {
        lockoutTicker?.cancel()
        lockoutTicker = viewModelScope.launch {
            while (true) {
                val remaining = pinGate.remainingLockoutMs()
                if (remaining <= 0L) {
                    pinLockedOut.value = false
                    pinLockoutMessage.value = null
                    pinError.value = false
                    break
                }
                pinLockedOut.value = true
                pinLockoutMessage.value = lockoutMessage(remaining)
                delay(1_000)
            }
        }
    }
```
In `init` (line 127) add `if (pinGate.isLockedOut) startLockoutTicker()` as the first statement. Imports: `com.mitas.ppnam.station4aa.domain.pin.PinGate`, `PinGateResult`, `PinLockoutStore`, `lockoutMessage`, `wrongPinMessage`, `kotlinx.coroutines.Job`. Delete the removed `failedPinAttempts`/`lockedOutUntilMs`/`correctPin` fields and the old `companion object`.

- [x] **Step 7: SettingsScreen PIN card**

Replace lines 195–248 (`PinState.Locked -> { ... }`) with:
```kotlin
                PinState.Locked -> {
                    val pinLockedOut = viewModel.pinLockedOut.value
                    val focusManager = LocalFocusManager.current
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
                        border = BorderStroke(1.dp, GraphiteBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Enter supervisor PIN to edit settings",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                            // Above the field: the numeric pad's top edge sits exactly on the
                            // field's bottom, so anything below it was invisible (audit S4-06).
                            pinErrorMessage?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = DangerRed)
                            }
                            pinLockoutMessage?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = DangerRed)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = pinInput,
                                    onValueChange = viewModel::onPinChange,
                                    label = { Text("PIN") },
                                    singleLine = true,
                                    enabled = !pinLockedOut,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.NumberPassword,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(onDone = {
                                        focusManager.clearFocus()
                                        viewModel.submitPin()
                                    }),
                                    isError = pinError,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AmberPrimary,
                                        focusedLabelColor = AmberPrimary,
                                        cursorColor = AmberPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.submitPin()
                                    },
                                    enabled = !pinLockedOut,
                                    modifier = Modifier.height(56.dp)
                                ) { Text("Unlock") }
                            }
                        }
                    }
                }
```
Add import `androidx.compose.ui.platform.LocalFocusManager`.

- [x] **Step 8: Compile + JVM suite**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
.\gradlew.bat --offline :app:assembleDebug
```

- [ ] **Step 9: Manual verification**

Login > gear. Tap Unlock with the field empty → `Enter the supervisor PIN.` above the field, counter untouched (type one wrong PIN: `4 attempts left`, not 3). Wrong PIN with the numeric pad up: the red message is visible above the field (was hidden, `04_settings_pin_wrong.png`). Five wrong → `Too many attempts. Try again in 30s.` counting down each second; field and Unlock greyed. Press Back, reopen Settings: still locked and counting (bypass closed). After it reaches 0 the field re-enables; `079545` unlocks.

- [x] **Step 10: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/domain/pin/PinGate.kt app/src/main/java/com/mitas/ppnam/station4aa/data/settings/SharedPrefsPinLockoutStore.kt app/src/main/java/com/mitas/ppnam/station4aa/data/AppContainer.kt app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsViewModel.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsScreen.kt app/src/test/java/com/mitas/ppnam/station4aa/domain/pin/PinGateTest.kt
git commit -m "fix(settings): persist the PIN lockout, ignore blank submits, tick the countdown

Audit group (c): the counter lived in a per-screen ViewModel so leaving
Settings reset it; a blank Unlock counted as an attempt; the 30 s
message never ticked. S4-06: the error now sits above the field where
the numeric pad cannot hide it.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

### Task 10: Settings form — string drafts, host/port validation, password toggle (blank = keep), auto sign-out minutes, Done applies, Uri keyboard, visible confirmation, friendly connect failure, "Log out" casing (static-06, static-20, S4-07 settings, S4-14 settings, S4-17 confirmation; §5 "Settings action & field set")

**Files:**
- Create: `domain/model/AutoSignOut.kt`
- Modify: `domain/model/AppSettings.kt:35-42`
- Modify: `data/settings/SettingsRepository.kt:33-39, 41-57, 61-74`
- Create: `ui/settings/SettingsValidation.kt`
- Modify: `ui/settings/SettingsViewModel.kt` (drafts, `testAndApply`, `onPinChange`)
- Modify: `ui/settings/SettingsScreen.kt` (unlocked branch, apply feedback placement, `SettingsTextField`, Log out)
- Create tests: `app/src/test/java/com/mitas/ppnam/station4aa/domain/model/AutoSignOutTest.kt`, `app/src/test/java/com/mitas/ppnam/station4aa/ui/settings/SettingsValidationTest.kt`

**Interfaces:**
- Produces: `AppSettings.autoSignOutMinutes: Int` (default 15, persisted by `SettingsRepository`); `object AutoSignOut { DEFAULT_MINUTES, MAX_MINUTES, parseMinutes(text): Int?, timeoutMs(minutes): Long }`; `data class SettingsFieldErrors(host, port, autoSignOut)`, `fun parsePort(text): Int?`, `fun validateSettingsDraft(host, portText, autoSignOutText): SettingsFieldErrors`, `fun describeConnectFailure(failure: Throwable?): String`. Task 13 consumes `autoSignOutMinutes` and `AutoSignOut.timeoutMs`.

- [x] **Step 1: Write the failing tests**

`AutoSignOutTest.kt`:
```kotlin
package com.mitas.ppnam.station4aa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Station 1's AutoLogout rules, ported: whole minutes, 0 = never, max one day. */
class AutoSignOutTest {

    @Test
    fun `parses whole minutes in range`() {
        assertEquals(0, AutoSignOut.parseMinutes("0"))
        assertEquals(15, AutoSignOut.parseMinutes(" 15 "))
        assertEquals(1440, AutoSignOut.parseMinutes("1440"))
    }

    @Test
    fun `rejects blanks, negatives, decimals and more than a day`() {
        assertNull(AutoSignOut.parseMinutes(""))
        assertNull(AutoSignOut.parseMinutes("-1"))
        assertNull(AutoSignOut.parseMinutes("1.5"))
        assertNull(AutoSignOut.parseMinutes("1441"))
    }

    @Test
    fun `zero means never`() {
        assertEquals(0L, AutoSignOut.timeoutMs(0))
        assertEquals(900_000L, AutoSignOut.timeoutMs(15))
    }
}
```

`SettingsValidationTest.kt`:
```kotlin
package com.mitas.ppnam.station4aa.ui.settings

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit §5 "Settings action": Test & Apply keeps Station 4's inline-test behaviour but gains
 * Station 1's host/port validation; S4-07: the timeout is never a library string. */
class SettingsValidationTest {

    @Test
    fun `a complete draft has no errors`() {
        val errors = validateSettingsDraft(host = "10.0.2.2", portText = "9001", autoSignOutText = "15")
        assertFalse(errors.hasErrors)
        assertNull(errors.host)
        assertNull(errors.port)
        assertNull(errors.autoSignOut)
    }

    @Test
    fun `a blank host is required`() {
        assertEquals("Host required", validateSettingsDraft("   ", "9001", "15").host)
    }

    @Test
    fun `an emptied or out-of-range port is invalid rather than silently kept`() {
        assertEquals("Invalid port (1–65535)", validateSettingsDraft("h", "", "15").port)
        assertEquals("Invalid port (1–65535)", validateSettingsDraft("h", "70000", "15").port)
        assertEquals("Invalid port (1–65535)", validateSettingsDraft("h", "0", "15").port)
        assertNull(validateSettingsDraft("h", "443", "15").port)
    }

    @Test
    fun `auto sign-out minutes use Station 1's range`() {
        assertEquals("Enter 0–1440", validateSettingsDraft("h", "9001", "").autoSignOut)
        assertEquals("Enter 0–1440", validateSettingsDraft("h", "9001", "2000").autoSignOut)
        assertNull(validateSettingsDraft("h", "9001", "0").autoSignOut)
    }

    @Test
    fun `parsePort mirrors Station 1`() {
        assertEquals(9001, parsePort(" 9001 "))
        assertNull(parsePort("abc"))
        assertNull(parsePort("65536"))
    }

    @Test
    fun `a connect timeout is described without the library text`() = runTest {
        val failure = try {
            withTimeout(1) { delay(1_000) }
            null
        } catch (e: TimeoutCancellationException) {
            e
        }
        assertTrue(failure is TimeoutCancellationException)
        assertEquals("Could not reach the broker (15 s)", describeConnectFailure(failure))
    }

    @Test
    fun `any other connect failure is generic and actionable`() {
        assertEquals(
            "Could not connect to the broker. Check the host, port and credentials.",
            describeConnectFailure(IllegalStateException("Timed out waiting for 15000 ms")),
        )
        assertEquals(
            "Could not connect to the broker. Check the host, port and credentials.",
            describeConnectFailure(null),
        )
    }
}
```

- [x] **Step 2: Run them to see them fail**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.domain.model.AutoSignOutTest" --tests "com.mitas.ppnam.station4aa.ui.settings.SettingsValidationTest"
```
Expected: unresolved references.

- [x] **Step 3: Model + repository**

Create `domain/model/AutoSignOut.kt`:
```kotlin
package com.mitas.ppnam.station4aa.domain.model

/** Inactivity auto sign-out rules, ported from Station 1's AutoLogout: whole minutes, 0 = never,
 * max one day. */
object AutoSignOut {
    const val DEFAULT_MINUTES = 15
    const val MAX_MINUTES = 1440

    fun parseMinutes(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in 0..MAX_MINUTES }

    fun timeoutMs(minutes: Int): Long = if (minutes <= 0) 0L else minutes * 60_000L
}
```
`AppSettings.kt`: add a last constructor parameter
```kotlin
    /** Inactivity auto sign-out in minutes, 0 = never (Station 1's policy, audit static-05). */
    val autoSignOutMinutes: Int = AutoSignOut.DEFAULT_MINUTES,
```
`SettingsRepository.kt`: add `val AUTO_SIGN_OUT_MINUTES = intPreferencesKey("auto_sign_out_minutes")` to `Keys`; in `settingsFlow` add `autoSignOutMinutes = prefs[Keys.AUTO_SIGN_OUT_MINUTES] ?: defaults.autoSignOutMinutes,`; in `save` add `prefs[Keys.AUTO_SIGN_OUT_MINUTES] = settings.autoSignOutMinutes.coerceIn(0, AutoSignOut.MAX_MINUTES)` (import `com.mitas.ppnam.station4aa.domain.model.AutoSignOut`).

- [x] **Step 4: Validation file**

Create `ui/settings/SettingsValidation.kt`:
```kotlin
package com.mitas.ppnam.station4aa.ui.settings

import com.mitas.ppnam.station4aa.domain.model.AutoSignOut
import kotlinx.coroutines.TimeoutCancellationException

/** Per-field messages for the Connection card; null = valid. Pure, so it is unit-tested. */
data class SettingsFieldErrors(
    val host: String? = null,
    val port: String? = null,
    val autoSignOut: String? = null,
) {
    val hasErrors: Boolean get() = host != null || port != null || autoSignOut != null
}

/** Station 1's `BrokerSettings.parsePort`. */
fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..65535 }

fun validateSettingsDraft(host: String, portText: String, autoSignOutText: String): SettingsFieldErrors =
    SettingsFieldErrors(
        host = if (host.isBlank()) "Host required" else null,
        port = if (parsePort(portText) == null) "Invalid port (1–65535)" else null,
        autoSignOut = if (AutoSignOut.parseMinutes(autoSignOutText) == null) "Enter 0–1440" else null,
    )

/** Test & Apply failure line. The 15 s is MqttConnectionManager.CONNECT_TIMEOUT_MS; the previous
 * text was HiveMQ's "Timed out waiting for 15000 ms" (audit S4-07). */
fun describeConnectFailure(failure: Throwable?): String = when (failure) {
    is TimeoutCancellationException -> "Could not reach the broker (15 s)"
    else -> "Could not connect to the broker. Check the host, port and credentials."
}
```

- [x] **Step 5: Run the tests**

Same command as Step 2. Expected: PASS.

- [x] **Step 6: SettingsViewModel drafts and apply**

Replace `var draftSettings = mutableStateOf(AppSettings())` (lines 94–95) with:
```kotlin
    /** Host / WebSocket / TLS / username live here; password is blank = "keep the stored one". */
    var draftSettings = mutableStateOf(AppSettings())
        private set
    /** Port and minutes are kept as typed text so the operator can clear the field; they are
     * parsed and validated only on Test & Apply (audit S2-03 pattern, S4-14). */
    var portText = mutableStateOf("")
        private set
    var autoSignOutText = mutableStateOf("")
        private set
    var fieldErrors = mutableStateOf(SettingsFieldErrors())
        private set
    private var storedPassword = ""
```
Replace the `init` body that loads the draft (lines 128–130) with:
```kotlin
        viewModelScope.launch {
            val current = settingsRepository.current()
            storedPassword = current.mqttPassword
            draftSettings.value = current.copy(mqttPassword = "")
            portText.value = current.mqttPort.toString()
            autoSignOutText.value = current.autoSignOutMinutes.toString()
        }
```
Add two update functions next to `updateDraft`:
```kotlin
    fun updatePortText(value: String) { portText.value = value; fieldErrors.value = fieldErrors.value.copy(port = null) }
    fun updateAutoSignOutText(value: String) { autoSignOutText.value = value; fieldErrors.value = fieldErrors.value.copy(autoSignOut = null) }
```
and make `updateDraft` also clear `fieldErrors.value = fieldErrors.value.copy(host = null)`.
Replace `testAndApply()` (lines 184–199) with:
```kotlin
    fun testAndApply() {
        val errors = validateSettingsDraft(draftSettings.value.mqttHost, portText.value, autoSignOutText.value)
        fieldErrors.value = errors
        if (errors.hasErrors) {
            applyState.value = ApplyState.Failure("Fix the highlighted fields.")
            return
        }
        val effective = draftSettings.value.copy(
            mqttHost = draftSettings.value.mqttHost.trim(),
            mqttPort = parsePort(portText.value)!!,
            mqttUsername = draftSettings.value.mqttUsername.trim(),
            // Blank keeps the already-provisioned password (Station 1's rule, audit static-20).
            mqttPassword = draftSettings.value.mqttPassword.ifBlank { storedPassword },
            autoSignOutMinutes = AutoSignOut.parseMinutes(autoSignOutText.value)!!,
        )
        applyState.value = ApplyState.Testing
        viewModelScope.launch {
            val result = connectionManager.reconnectWith(effective)
            if (result.isSuccess) {
                settingsRepository.save(effective)
                storedPassword = effective.mqttPassword
                draftSettings.value = effective.copy(mqttPassword = "")
                // The Success row is rendered outside the PIN card (see SettingsScreen) so it
                // stays visible after the re-lock below — the audit found the old one vanished
                // with the card (S4-17).
                applyState.value = ApplyState.Success("Connected — settings saved")
                delay(2_000)
                pinState.value = PinState.Locked
                pinInput.value = ""
            } else {
                applyState.value = ApplyState.Failure(describeConnectFailure(result.exceptionOrNull()))
            }
        }
    }
```
In `onPinChange` add `applyState.value = ApplyState.Idle` as the first line (typing a PIN again clears the last confirmation). Import `com.mitas.ppnam.station4aa.domain.model.AutoSignOut`.

- [x] **Step 7: SettingsScreen unlocked branch and helpers**

(a) Move the `when (val state = applyState) { ... }` block (lines 293–326) out of the `PinState.Unlocked` branch to just *before* `when (pinState) {` (line 195), so it renders in both states.

(b) Replace the `ConfigSection(title = "Connection") { ... }` body (lines 255–291) with:
```kotlin
                    val focusManager = LocalFocusManager.current
                    val fieldErrors = viewModel.fieldErrors.value
                    var showPassword by rememberSaveable { mutableStateOf(false) }
                    ConfigSection(title = "Connection") {
                        SettingsTextField(
                            value = draft.mqttHost,
                            label = "Host",
                            keyboardType = KeyboardType.Uri,
                            errorMessage = fieldErrors.host,
                            onValueChange = { viewModel.updateDraft(draft.copy(mqttHost = it)) }
                        )
                        SettingsTextField(
                            value = viewModel.portText.value,
                            label = "Port",
                            keyboardType = KeyboardType.Number,
                            errorMessage = fieldErrors.port,
                            onValueChange = viewModel::updatePortText
                        )
                        SettingsToggleRow(
                            label = "WebSocket",
                            checked = draft.mqttUseWebSocket,
                            onCheckedChange = { viewModel.updateDraft(draft.copy(mqttUseWebSocket = it)) }
                        )
                        SettingsToggleRow(
                            label = "TLS",
                            checked = draft.mqttUseTls,
                            onCheckedChange = { viewModel.updateDraft(draft.copy(mqttUseTls = it)) }
                        )
                        SettingsTextField(
                            value = draft.mqttUsername,
                            label = "Username",
                            onValueChange = { viewModel.updateDraft(draft.copy(mqttUsername = it)) }
                        )
                        SettingsTextField(
                            value = draft.mqttPassword,
                            label = "Password (blank = keep current)",
                            keyboardType = KeyboardType.Password,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showPassword) "Hide password" else "Show password",
                                        tint = TextMuted
                                    )
                                }
                            },
                            onValueChange = { viewModel.updateDraft(draft.copy(mqttPassword = it)) }
                        )
                    }
                    ConfigSection(title = "Session") {
                        SettingsTextField(
                            value = viewModel.autoSignOutText.value,
                            label = "Auto sign-out after (minutes, 0 = never)",
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                            errorMessage = fieldErrors.autoSignOut,
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.testAndApply()
                            },
                            onValueChange = viewModel::updateAutoSignOutText
                        )
                    }
```
and make the Test & Apply button's `onClick = { focusManager.clearFocus(); viewModel.testAndApply() }`.

(c) Replace `SettingsTextField` (lines 445–467) with:
```kotlin
@Composable
private fun SettingsTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    errorMessage: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = errorMessage != null,
        // supportingText is part of the field's own layout, so bringing the field into view
        // above the keyboard brings the message with it.
        supportingText = errorMessage?.let { { Text(it, color = DangerRed) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AmberPrimary,
            focusedLabelColor = AmberPrimary,
            cursorColor = AmberPrimary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
```
(d) Line 376: `{ Text("Log Out") }` → `{ Text("Log out") }`.

Imports to add: `androidx.compose.material.icons.filled.Visibility`, `androidx.compose.material.icons.filled.VisibilityOff` (the `Icons`, `Icon`, `IconButton`, `LocalFocusManager`, `rememberSaveable` imports are already present or added in Task 9; `androidx.compose.material3.*` covers `IconButton`).

- [x] **Step 8: Compile + JVM suite**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
.\gradlew.bat --offline :app:assembleDebug
```

- [ ] **Step 9: Manual verification**

Unlock Settings. Clear Port entirely, tap Test & Apply → `Invalid port (1–65535)` under Port, `Fix the highlighted fields.` red line, nothing connects. Port `9001`, Host `10.0.2.2`, blank the Host → `Host required`. Restore. Password field is empty with an eye icon; leave it blank, Auto sign-out `15`, press Done on that field → `Testing connection…` then green `Connected — settings saved`, and after 2 s the card re-locks **with the green row still visible** (was `14_settings_after_apply.png`, no confirmation). Port `9002` → after 15 s red `Could not reach the broker (15 s)` (was `Timed out waiting for 15000 ms`, `97_settings_badport_result.png`). Host field opens the URL keyboard. Session card button reads `Log out`.

- [x] **Step 10: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/domain/model/AutoSignOut.kt app/src/main/java/com/mitas/ppnam/station4aa/domain/model/AppSettings.kt app/src/main/java/com/mitas/ppnam/station4aa/data/settings/SettingsRepository.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsValidation.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsViewModel.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsScreen.kt app/src/test/java/com/mitas/ppnam/station4aa/domain/model/AutoSignOutTest.kt app/src/test/java/com/mitas/ppnam/station4aa/ui/settings/SettingsValidationTest.kt
git commit -m "feat(settings): validate host/port, add auto sign-out minutes, password toggle, visible confirmation

static-06: Test & Apply now validates like Station 1 before testing.
static-20: blank password keeps the stored one, with a visibility
toggle. S4-14: Done applies, Host uses the URI keyboard, port is a
text draft. S4-07: connect timeout described in operator terms.
S4-17: success row survives the re-lock. 'Log out' casing.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

### Task 11: Diagnostics — Station 4 row, Station 1 order, pill vocabulary, readable timestamps (static-17, S4-07 catalogue, §5 "Diagnostics rows")

**Files:**
- Modify: `ui/settings/CatalogueStatus.kt`
- Modify test: `app/src/test/java/com/mitas/ppnam/station4aa/ui/settings/CatalogueStatusTest.kt`
- Modify: `ui/settings/SettingsViewModel.kt:97, 104-106`
- Modify: `ui/settings/SettingsScreen.kt:86-148`

**Interfaces:**
- Produces: `fun describeCatalogue(meta: CatalogueMeta?, zone: ZoneId = ZoneId.systemDefault()): String`; `SettingsViewModel.stationOnline: StateFlow<Boolean?>`.

- [x] **Step 1: Update the test for the new format**

Replace `CatalogueStatusTest.kt` entirely:
```kotlin
package com.mitas.ppnam.station4aa.ui.settings

import com.mitas.ppnam.station4aa.domain.model.CatalogueMeta
import com.mitas.ppnam.station4aa.domain.model.CatalogueSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

/**
 * A handheld quietly running the built-in seed against a real station must not look identical to a
 * correctly synced one — that is the whole point of this line. Timestamps are shown in local time
 * at minute precision (audit S4-07): the raw ISO string with microseconds wrapped to three lines.
 */
class CatalogueStatusTest {

    private val utc = ZoneOffset.UTC

    @Test
    fun `no cached catalogue at all reads as not loaded`() {
        assertEquals("Catalogue: not loaded", describeCatalogue(null, utc))
    }

    @Test
    fun `the built-in seed says so explicitly`() {
        val meta = CatalogueMeta(
            catalogueVersion = "",
            syncedAtUtc = null,
            source = CatalogueSource.SEED,
            lastFailedAtUtc = null,
        )
        assertEquals("Catalogue: built-in seed — never synced", describeCatalogue(meta, utc))
    }

    @Test
    fun `a synced catalogue shows its version and a readable local timestamp`() {
        val meta = CatalogueMeta(
            catalogueVersion = "v7",
            syncedAtUtc = "2026-09-02T07:00:00Z",
            source = CatalogueSource.SYNCED,
            lastFailedAtUtc = null,
        )
        assertEquals("Catalogue: v7 — synced 2 Sep 2026, 07:00", describeCatalogue(meta, utc))
    }

    @Test
    fun `microsecond timestamps from the station are parsed and shortened`() {
        val meta = CatalogueMeta(
            catalogueVersion = "b1b6841add35",
            syncedAtUtc = "2026-10-01T16:12:37.942434Z",
            source = CatalogueSource.SYNCED,
            lastFailedAtUtc = null,
        )
        assertEquals("Catalogue: b1b6841add35 — synced 1 Oct 2026, 16:12", describeCatalogue(meta, utc))
    }

    @Test
    fun `the zone is applied`() {
        val meta = CatalogueMeta(
            catalogueVersion = "v7",
            syncedAtUtc = "2026-09-02T07:00:00Z",
            source = CatalogueSource.SYNCED,
            lastFailedAtUtc = null,
        )
        assertEquals("Catalogue: v7 — synced 2 Sep 2026, 09:00", describeCatalogue(meta, ZoneOffset.ofHours(2)))
    }

    @Test
    fun `a later failed refresh is appended without hiding the good sync`() {
        val meta = CatalogueMeta(
            catalogueVersion = "v7",
            syncedAtUtc = "2026-09-02T07:00:00Z",
            source = CatalogueSource.SYNCED,
            lastFailedAtUtc = "2026-09-02T09:30:00Z",
        )
        assertEquals(
            "Catalogue: v7 — synced 2 Sep 2026, 07:00, last refresh failed 2 Sep 2026, 09:30",
            describeCatalogue(meta, utc),
        )
    }

    @Test
    fun `a seed whose refresh failed reports both facts`() {
        val meta = CatalogueMeta(
            catalogueVersion = "",
            syncedAtUtc = null,
            source = CatalogueSource.SEED,
            lastFailedAtUtc = "2026-09-02T09:30:00Z",
        )
        assertEquals(
            "Catalogue: built-in seed — never synced, last refresh failed 2 Sep 2026, 09:30",
            describeCatalogue(meta, utc),
        )
    }

    @Test
    fun `an unparseable timestamp is shown as-is rather than crashing`() {
        val meta = CatalogueMeta(
            catalogueVersion = "v7",
            syncedAtUtc = "not-a-date",
            source = CatalogueSource.SYNCED,
            lastFailedAtUtc = null,
        )
        assertEquals("Catalogue: v7 — synced not-a-date", describeCatalogue(meta, utc))
    }
}
```

- [x] **Step 2: Run it to see it fail**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.ui.settings.CatalogueStatusTest"
```
Expected: compile error (no `zone` parameter).

- [x] **Step 3: Implement**

Replace `CatalogueStatus.kt`:
```kotlin
package com.mitas.ppnam.station4aa.ui.settings

import com.mitas.ppnam.station4aa.domain.model.CatalogueMeta
import com.mitas.ppnam.station4aa.domain.model.CatalogueSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK)

/** "2026-10-01T16:12:37.942434Z" → "1 Oct 2026, 18:12" in [zone]; unparseable input is returned
 * untouched so a surprising station value is still visible to support. */
internal fun formatTimestampForDisplay(isoUtc: String, zone: ZoneId): String = try {
    DISPLAY_FORMAT.withZone(zone).format(Instant.parse(isoUtc))
} catch (e: DateTimeParseException) {
    isoUtc
}

/**
 * One honest line about where the cached catalogue came from, for Settings → Diagnostics. Pure, so
 * it is tested without Android; [zone] defaults to the device zone and is injected by tests.
 */
fun describeCatalogue(meta: CatalogueMeta?, zone: ZoneId = ZoneId.systemDefault()): String {
    if (meta == null) return "Catalogue: not loaded"
    val base = when (meta.source) {
        CatalogueSource.SEED -> "Catalogue: built-in seed — never synced"
        CatalogueSource.SYNCED ->
            "Catalogue: ${meta.catalogueVersion} — synced ${meta.syncedAtUtc?.let { formatTimestampForDisplay(it, zone) }}"
    }
    return meta.lastFailedAtUtc
        ?.let { "$base, last refresh failed ${formatTimestampForDisplay(it, zone)}" }
        ?: base
}
```
`SettingsViewModel.kt` line 105: `.map(::describeCatalogue)` → `.map { describeCatalogue(it) }`. After line 97 add `val stationOnline: StateFlow<Boolean?> = connectionManager.stationOnline`.

- [x] **Step 4: Run the test**

Same command. Expected: PASS (8 tests).

- [x] **Step 5: Diagnostics card rows in Station 1 order**

Replace lines 86–148 of `SettingsScreen.kt` (the Diagnostics `Column` body up to and including the `Refresh catalogue` button) with:
```kotlin
                Column(modifier = Modifier.padding(16.dp)) {
                    // Same three words as the top-bar pill (audit static-17/18).
                    val (brokerColor, brokerLabel) = when (connectionState) {
                        MqttConnectionState.CONNECTED    -> SuccessGreen to "Connected"
                        MqttConnectionState.RECONNECTING -> WarningOrange to "Reconnecting"
                        MqttConnectionState.DISCONNECTED -> DangerRed to "Offline"
                    }
                    DiagnosticRow("MQTT BROKER", brokerColor, brokerLabel)

                    HorizontalDivider(color = GraphiteBorder, modifier = Modifier.padding(vertical = 10.dp))

                    // Station 4's own retained presence — the row every other station shows.
                    val stationOnline by viewModel.stationOnline.collectAsState()
                    val (stationColor, stationLabel) = when (stationOnline) {
                        true -> SuccessGreen to "Online"
                        false -> DangerRed to "Offline"
                        null -> TextMuted to "Unknown"
                    }
                    DiagnosticRow("STATION 4", stationColor, stationLabel)

                    HorizontalDivider(color = GraphiteBorder, modifier = Modifier.padding(vertical = 10.dp))

                    DiagnosticValueRow("VERSION", "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

                    HorizontalDivider(color = GraphiteBorder, modifier = Modifier.padding(vertical = 10.dp))

                    // Read-only by design (base standard §2): the device id is derived on-device
                    // and immutable — this row exists so it can be read off for enrolment.
                    DiagnosticValueRow("DEVICE ID", viewModel.deviceId)

                    HorizontalDivider(color = GraphiteBorder, modifier = Modifier.padding(vertical = 10.dp))

                    val catalogueStatus by viewModel.catalogueStatus.collectAsState()
                    Text(
                        catalogueStatus,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                    TextButton(onClick = { viewModel.refreshCatalogue() }) {
                        Text("Refresh catalogue", color = AmberPrimary)
                    }
```
and add this helper next to `DiagnosticRow`:
```kotlin
/** A labelled monospace value line of the Diagnostics card (Version, Device ID). */
@Composable
private fun DiagnosticValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = TextMuted
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = TextPrimary
        )
    }
}
```

- [x] **Step 6: Compile + JVM suite**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
.\gradlew.bat --offline :app:assembleDebug
```

- [ ] **Step 7: Manual verification**

Settings: rows read MQTT BROKER · STATION 4 · VERSION · DEVICE ID then the catalogue line (was `02_settings_pingate.png`: no station row, Device ID before Version). With the fake backend up, STATION 4 = `Online`; stop it → `Offline`. Log in, Refresh catalogue: the line reads e.g. `Catalogue: b1b6841add35 — synced 2 Oct 2026, 14:03` on one line (was `33_settings_refresh_result.png`). Disconnect the broker: MQTT BROKER reads `Offline` (was `Disconnected`).

- [x] **Step 8: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/CatalogueStatus.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsViewModel.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/settings/SettingsScreen.kt app/src/test/java/com/mitas/ppnam/station4aa/ui/settings/CatalogueStatusTest.kt
git commit -m "fix(settings): Station 4 row and Station 1 order in Diagnostics, readable catalogue timestamps

static-17: MQTT Broker, Station 4, Version, Device ID. static-18: broker
row uses the pill vocabulary. S4-07: ISO microsecond timestamps become
local time at minute precision.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

### Task 12: Back policy — shared dialogs, "Close the app?" on Home, review dialog only hides, drafts need confirmation (S4-03, S4-09, static-04, static-15; group d)

**Files:**
- Create: `ui/components/AppDialogs.kt`
- Modify: `ui/login/LoginScreen.kt:95-111`
- Modify: `ui/home/HomeScreen.kt` (signature + `BackHandler` + dialog)
- Modify: `navigation/AppNavGraph.kt:70-75`
- Modify: `domain/wizard/WasteWizardController.kt`
- Modify test: `app/src/test/java/com/mitas/ppnam/station4aa/domain/wizard/WasteWizardControllerTest.kt`
- Modify: `ui/waste/WasteGatheringViewModel.kt` (add `onReviewDismissed`)
- Modify: `ui/waste/WasteGatheringScreen.kt` (dialog, BackHandler, cancel paths)

**Interfaces:**
- Produces: `@Composable fun ExitAppDialog(onStay, onClose)`, `@Composable fun DiscardDraftDialog(onKeep, onDiscard)`; `WasteWizardController.dismissReview()`, `WasteWizardController.hasDraft: Boolean`; `WasteGatheringViewModel.onReviewDismissed()`; `HomeScreen(onExitApp: () -> Unit)`.

- [x] **Step 1: Write the failing controller tests**

Append to `WasteWizardControllerTest.kt`:
```kotlin
    @Test
    fun `dismissing the review returns to the waste-type step with the draft intact`() {
        val controller = completedController()
        controller.dismissReview()
        assertEquals(WizardStep.SELECT_WASTE_TYPE, controller.step)
        assertEquals("BAG-01", controller.draft.bagCode)
        assertEquals(bubbleBreaks, controller.draft.wasteType)
        // Re-confirming the type goes straight back to review.
        controller.confirmWasteType(bubbleBreaks)
        assertEquals(WizardStep.REVIEW, controller.step)
    }

    @Test
    fun `dismissReview is only legal from review`() {
        assertThrows(IllegalStateException::class.java) { WasteWizardController().dismissReview() }
    }

    @Test
    fun `hasDraft is false until something is captured and false again after cancel`() {
        val controller = WasteWizardController()
        assertFalse(controller.hasDraft)
        controller.submitBagCode("BAG-01")
        assertTrue(controller.hasDraft)
        controller.cancel()
        assertFalse(controller.hasDraft)
    }
```
Add imports `org.junit.Assert.assertFalse`, `org.junit.Assert.assertTrue`.

- [x] **Step 2: Run to see them fail**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.domain.wizard.WasteWizardControllerTest"
```
Expected: `Unresolved reference: dismissReview`.

- [x] **Step 3: Controller**

Add to `WasteWizardController.kt` after `editField`:
```kotlin
    /** Back / scrim / "Back" on the review dialog: hide the review and land on the last capture
     * step with every value kept. Previously this was [cancel], which silently threw away the
     * whole five-step transaction (audit S4-03). */
    fun dismissReview() {
        check(step == WizardStep.REVIEW) { "dismissReview called outside REVIEW (was $step)" }
        returnToReview = false
        step = WizardStep.SELECT_WASTE_TYPE
    }

    /** True once any value has been captured — the screen asks before discarding such a draft. */
    val hasDraft: Boolean get() = draft != WasteTransactionDraft()
```

- [x] **Step 4: Run the tests**

Same command. Expected: PASS.

- [x] **Step 5: Shared dialogs**

Create `ui/components/AppDialogs.kt`:
```kotlin
package com.mitas.ppnam.station4aa.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.mitas.ppnam.station4aa.ui.theme.DangerRed
import com.mitas.ppnam.station4aa.ui.theme.GraphiteSurface
import com.mitas.ppnam.station4aa.ui.theme.TextMuted
import com.mitas.ppnam.station4aa.ui.theme.TextPrimary

/** The fleet's "Close the app?" [Stay | Close] confirmation (Station 2's wording), used on both
 * Login and Home so Back behaves the same on every root screen (audit static-04). M3 default
 * rounded shape; neutral dismiss, red destructive confirm. */
@Composable
fun ExitAppDialog(onStay: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text("Close the app?", color = TextPrimary) },
        text = { Text("You'll leave PPNAM Station 4 and return to the home screen.", color = TextMuted) },
        confirmButton = { TextButton(onClick = onClose) { Text("Close", color = DangerRed) } },
        dismissButton = { TextButton(onClick = onStay) { Text("Stay") } },
        containerColor = GraphiteSurface,
    )
}

/** Asked before any path that would throw away a partly captured collection (audit S4-09). */
@Composable
fun DiscardDraftDialog(onKeep: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Discard this collection?", color = TextPrimary) },
        text = { Text("The values captured so far will be lost.", color = TextMuted) },
        confirmButton = { TextButton(onClick = onDiscard) { Text("Discard", color = DangerRed) } },
        dismissButton = { TextButton(onClick = onKeep) { Text("Keep editing") } },
        containerColor = GraphiteSurface,
    )
}
```

- [x] **Step 6: Login uses the shared dialog**

`LoginScreen.kt` lines 95–111 become:
```kotlin
    if (showExitDialog) {
        ExitAppDialog(
            onStay = { showExitDialog = false },
            onClose = {
                showExitDialog = false
                onExitApp()
            },
        )
    }
```
Add import `com.mitas.ppnam.station4aa.ui.components.ExitAppDialog`; remove the now-unused `androidx.compose.material3.AlertDialog`, `androidx.compose.material3.TextButton` and `GraphiteSurface` imports if nothing else uses them (`TextButton` is no longer used in this file once this dialog is extracted).

- [x] **Step 7: Home asks before leaving**

`HomeScreen.kt`: add parameter `onExitApp: () -> Unit,` after `onSettings`, and before `AppScaffold(` insert:
```kotlin
    // System Back on the post-login root used to background the app with no prompt while Login
    // asked "Close the app?" (audit S4-09 / static-04). Same dialog on both now.
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    BackHandler { showExitDialog = true }
    if (showExitDialog) {
        ExitAppDialog(
            onStay = { showExitDialog = false },
            onClose = {
                showExitDialog = false
                onExitApp()
            },
        )
    }
```
Imports: `androidx.activity.compose.BackHandler`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.saveable.rememberSaveable`, `androidx.compose.runtime.setValue`, `com.mitas.ppnam.station4aa.ui.components.ExitAppDialog`.
`AppNavGraph.kt` lines 70–75: add `onExitApp = { (context as? Activity)?.finish() },` to the `HomeScreen(` call.

- [x] **Step 8: Wizard — review only hides, drafts need confirmation**

`WasteGatheringViewModel.kt`: after `onEditField` add
```kotlin
    /** Back, scrim or "Back" on the review dialog — keeps the draft (audit S4-03). */
    fun onReviewDismissed() {
        wizardController.dismissReview()
        syncFromController(null)
    }
```
`WasteGatheringScreen.kt`:

(a) After the `wasteTypes` collect line add:
```kotlin
    val hasDraft = draft != WasteTransactionDraft()
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var leaveAfterDiscard by rememberSaveable { mutableStateOf(false) }
    val requestLeave: () -> Unit = {
        if (hasDraft) {
            leaveAfterDiscard = true
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    // Mid-wizard Back with values captured asks first; the review AlertDialog has its own Back
    // handling (it is a separate window), so this only runs on steps 1–5.
    BackHandler(enabled = hasDraft && step != WizardStep.REVIEW) { requestLeave() }

    if (showDiscardDialog) {
        DiscardDraftDialog(
            onKeep = {
                showDiscardDialog = false
                leaveAfterDiscard = false
            },
            onDiscard = {
                showDiscardDialog = false
                viewModel.onCancelTransaction()
                if (leaveAfterDiscard) {
                    leaveAfterDiscard = false
                    onBack()
                }
            },
        )
    }
```
(b) Review dialog (lines 64–101): `onDismissRequest = { viewModel.onReviewDismissed() }`, and the dismiss button becomes `TextButton(onClick = { viewModel.onReviewDismissed() }) { Text("Back") }`.
(c) `AppScaffold(... onBack = onBack, ...)` → `onBack = requestLeave,`.
(d) The bottom `TextButton(onClick = { viewModel.onCancelTransaction() })` → `TextButton(onClick = { if (hasDraft) showDiscardDialog = true })` (nothing to discard on a fresh step 1, so the tap is a no-op there).
Imports: `androidx.activity.compose.BackHandler`, `com.mitas.ppnam.station4aa.domain.wizard.WasteTransactionDraft`, `com.mitas.ppnam.station4aa.ui.components.DiscardDraftDialog` (`rememberSaveable`/`setValue` already imported in Task 6).

- [x] **Step 9: Compile + JVM suite**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
.\gradlew.bat --offline :app:assembleDebug
```

- [ ] **Step 10: Manual verification**

Home: system Back → `Close the app?` [Stay | Close]; Stay keeps the session (was `29_home_back.png`: backgrounded silently). Wizard: scan `BAG-1`, `JOB-1`, `MO-1`, pick category and type, Confirm → review; press Back → dialog closes, screen shows Step 5 with the type still selected; Confirm → review again with all five values (was `53_wc_review_back.png`: reset to Step 1). Top-bar back arrow with a draft → `Discard this collection?`; Keep editing stays; Discard → Home. `Cancel transaction` → same dialog; Discard → Step 1. On a fresh Step 1, back arrow goes straight to Home.

- [x] **Step 11: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/ui/components/AppDialogs.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginScreen.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/home/HomeScreen.kt app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt app/src/main/java/com/mitas/ppnam/station4aa/domain/wizard/WasteWizardController.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringViewModel.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringScreen.kt app/src/test/java/com/mitas/ppnam/station4aa/domain/wizard/WasteWizardControllerTest.kt
git commit -m "fix(nav): one Back policy — confirm app exit on Home, never discard a draft silently

S4-03: Back/scrim on the review dialog hides it and keeps the draft.
S4-09/static-04: Home shows the same 'Close the app?' as Login; the
back arrow and Cancel transaction confirm before discarding. static-15:
dialogs share one M3 style with a red destructive action.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

## Tier 3 — behaviour and consistency work

### Task 13: Inactivity auto sign-out with a reason on Login (static-05, S4-09 reason, §5 "Session"; group j)

**Files:**
- Create: `domain/session/InactivityMonitor.kt`, `domain/session/SessionMessages.kt`
- Create tests: `app/src/test/java/com/mitas/ppnam/station4aa/domain/session/InactivityMonitorTest.kt`, `.../SessionMessagesTest.kt`
- Modify: `data/session/OperatorSessionHolder.kt`
- Modify: `domain/usecase/AuthUseCase.kt:115-127`
- Create: `data/session/SessionGuard.kt`
- Modify: `data/AppContainer.kt`, `MainActivity.kt`
- Modify: `ui/login/LoginViewModel.kt` (init), `ui/weigh/WeighBagViewModel.kt:121`

**Interfaces:**
- Produces: `class InactivityMonitor(now, schedule, cancel, onExpired)` with `start(timeoutMs)`, `touch()`, `checkNow()`, `stop()`, `isRunning`; `fun signedOutAfterMinutes(minutes: Int): String`; `OperatorSessionHolder.clear(reason: String? = null)`, `signedOutReason: StateFlow<String?>`, `consumeSignedOutReason(): String?`; `AuthUseCase.logout(reason: String? = null)`; `SessionGuard.touch()`, `SessionGuard.checkNow()`.

- [x] **Step 1: Write the failing tests**

`InactivityMonitorTest.kt` (Station 1's test, package changed):
```kotlin
package com.mitas.ppnam.station4aa.domain.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Inactivity auto sign-out timer. Time and scheduling are injected so the tests are
 * deterministic: `scheduled` holds the pending runnable (at most one) and `fireScheduled()`
 * advances the clock to its due time and runs it. Ported from Station 1.
 */
class InactivityMonitorTest {

    private var now = 1_000_000L
    private var scheduled: Pair<Long, Runnable>? = null
    private var expired = 0

    private val monitor = InactivityMonitor(
        now = { now },
        schedule = { delay, r -> scheduled = (now + delay) to r },
        cancel = { r -> if (scheduled?.second === r) scheduled = null },
        onExpired = { expired++ },
    )

    private fun fireScheduled() {
        val (due, r) = scheduled ?: error("nothing scheduled")
        scheduled = null
        now = maxOf(now, due)
        r.run()
    }

    @Test
    fun `expires once the timeout elapses without activity`() {
        monitor.start(60_000)
        assertTrue(monitor.isRunning)
        fireScheduled()
        assertEquals(1, expired)
        assertFalse(monitor.isRunning)
    }

    @Test
    fun `touch defers the deadline`() {
        monitor.start(60_000)
        now += 40_000
        monitor.touch()
        fireScheduled()
        assertEquals(0, expired)
        assertTrue(monitor.isRunning)
        assertEquals(now + 40_000, scheduled!!.first)
        fireScheduled()
        assertEquals(1, expired)
    }

    @Test
    fun `stop cancels the pending deadline and never fires`() {
        monitor.start(60_000)
        monitor.stop()
        assertFalse(monitor.isRunning)
        assertNull(scheduled)
        assertEquals(0, expired)
    }

    @Test
    fun `checkNow after a long gap fires immediately`() {
        monitor.start(60_000)
        now += 3_600_000
        monitor.checkNow()
        assertEquals(1, expired)
        assertNull(scheduled)
    }

    @Test
    fun `checkNow before the deadline does nothing`() {
        monitor.start(60_000)
        now += 10_000
        monitor.checkNow()
        assertEquals(0, expired)
        assertTrue(monitor.isRunning)
    }

    @Test
    fun `zero or negative timeout disables the monitor`() {
        monitor.start(0)
        assertFalse(monitor.isRunning)
        assertNull(scheduled)
        monitor.touch()
        monitor.checkNow()
        assertEquals(0, expired)
    }

    @Test
    fun `touch and checkNow are no-ops when stopped`() {
        monitor.touch()
        monitor.checkNow()
        assertEquals(0, expired)
        assertNull(scheduled)
    }

    @Test
    fun `restart replaces the previous timeout`() {
        monitor.start(60_000)
        monitor.start(5_000)
        assertEquals(now + 5_000, scheduled!!.first)
        fireScheduled()
        assertEquals(1, expired)
    }
}
```
`SessionMessagesTest.kt`:
```kotlin
package com.mitas.ppnam.station4aa.domain.session

import org.junit.Assert.assertEquals
import org.junit.Test

/** Audit group (f)/(i): the sign-out reason is a sentence with a correct plural, never
 * "Signed out after 1 minutes". */
class SessionMessagesTest {
    @Test
    fun `plural minutes`() {
        assertEquals("Signed out after 15 minutes of inactivity.", signedOutAfterMinutes(15))
    }

    @Test
    fun `singular minute`() {
        assertEquals("Signed out after 1 minute of inactivity.", signedOutAfterMinutes(1))
    }
}
```

- [x] **Step 2: Run to see them fail**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.domain.session.*"
```
Expected: unresolved references.

- [x] **Step 3: Pure pieces**

`domain/session/InactivityMonitor.kt` (Station 1's class verbatim, package changed):
```kotlin
package com.mitas.ppnam.station4aa.domain.session

/**
 * Inactivity auto sign-out timer, ported from Station 1. Pure Kotlin: the caller supplies a
 * monotonic clock and a scheduler, so production uses SystemClock.elapsedRealtime plus a
 * main-thread Handler while tests drive time by hand.
 *
 * The deadline is wall-clock from the last activity, so time spent in the background still
 * counts; hosts call [checkNow] on resume to catch a deadline that passed while no Handler was
 * running. [onExpired] fires at most once per [start].
 */
class InactivityMonitor(
    private val now: () -> Long,
    private val schedule: (Long, Runnable) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val onExpired: () -> Unit,
) {
    private var timeoutMs = 0L
    private var lastActivity = 0L
    private var pending: Runnable? = null

    val isRunning: Boolean get() = timeoutMs > 0

    fun start(timeoutMs: Long) {
        stop()
        if (timeoutMs <= 0) return
        this.timeoutMs = timeoutMs
        lastActivity = now()
        scheduleCheck(timeoutMs)
    }

    fun touch() {
        if (!isRunning) return
        lastActivity = now()
    }

    fun checkNow() {
        if (!isRunning) return
        val remaining = timeoutMs - (now() - lastActivity)
        if (remaining <= 0) {
            stop()
            onExpired()
        } else {
            scheduleCheck(remaining)
        }
    }

    fun stop() {
        timeoutMs = 0
        pending?.let(cancel)
        pending = null
    }

    private fun scheduleCheck(delayMs: Long) {
        pending?.let(cancel)
        val r = Runnable {
            pending = null
            checkNow()
        }
        pending = r
        schedule(delayMs, r)
    }
}
```
`domain/session/SessionMessages.kt`:
```kotlin
package com.mitas.ppnam.station4aa.domain.session

/** Shown on Login after an inactivity sign-out (Station 1's `signed_out_inactivity`). */
fun signedOutAfterMinutes(minutes: Int): String =
    "Signed out after $minutes ${if (minutes == 1) "minute" else "minutes"} of inactivity."

/** Shown on Login after the station refused the session (`operator_session_invalid`). */
const val SIGNED_OUT_SESSION_ENDED = "Your session has ended. Sign in again."
```

- [x] **Step 4: Run the tests**

Same command. Expected: PASS (10 tests).

- [x] **Step 5: Session holder carries a reason**

Replace `OperatorSessionHolder` class (lines 26–41):
```kotlin
/** In-memory only, like Station 2's — rebuilt from a fresh login/badge scan on process restart.
 * No password or session token is ever persisted to disk. [signedOutReason] is the one-shot
 * sentence Login shows when a session was dropped for the operator (audit static-05). */
class OperatorSessionHolder {
    private val _session = MutableStateFlow<OperatorSession?>(null)
    val session: StateFlow<OperatorSession?> = _session.asStateFlow()

    private val _signedOutReason = MutableStateFlow<String?>(null)
    val signedOutReason: StateFlow<String?> = _signedOutReason.asStateFlow()

    fun set(session: OperatorSession) {
        _signedOutReason.value = null
        _session.value = session
    }

    /** [reason] is null for a deliberate Log out (nothing to explain). */
    fun clear(reason: String? = null) {
        _signedOutReason.value = reason
        _session.value = null
    }

    /** Login reads the reason once and clears it so it does not reappear on the next visit. */
    fun consumeSignedOutReason(): String? {
        val reason = _signedOutReason.value
        _signedOutReason.value = null
        return reason
    }

    fun currentSessionIdOrEmpty(): String = _session.value?.operatorSessionId ?: ""
}
```
`AuthUseCase.kt` lines 118–127: signature `suspend fun logout(reason: String? = null)` and last line `sessionHolder.clear(reason)`.
`WeighBagViewModel.kt` line 121: `if (refusal.requiresLogin) sessionHolder.clear(SIGNED_OUT_SESSION_ENDED)` (import `com.mitas.ppnam.station4aa.domain.session.SIGNED_OUT_SESSION_ENDED`).
`LoginViewModel.kt` `init` (line 54): add as the first statement
```kotlin
        // A dropped session (inactivity, station refusal) explains itself on the login line.
        sessionHolder.consumeSignedOutReason()?.let { _uiState.value = LoginUiState.Error(it) }
```
and add constructor parameter `private val sessionHolder: OperatorSessionHolder,` (import `com.mitas.ppnam.station4aa.data.session.OperatorSessionHolder`); in `AppNavGraph.kt` lines 38–43 pass `sessionHolder = container.operatorSessionHolder,`.

- [x] **Step 6: SessionGuard**

Create `data/session/SessionGuard.kt`:
```kotlin
package com.mitas.ppnam.station4aa.data.session

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mitas.ppnam.station4aa.data.rfid.ScanEventBus
import com.mitas.ppnam.station4aa.data.settings.SettingsRepository
import com.mitas.ppnam.station4aa.domain.model.AutoSignOut
import com.mitas.ppnam.station4aa.domain.session.InactivityMonitor
import com.mitas.ppnam.station4aa.domain.session.signedOutAfterMinutes
import com.mitas.ppnam.station4aa.domain.usecase.AuthUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Process-wide inactivity sign-out, the Compose-app shape of Station 1's SessionGuard: the timer
 * starts with a session and the configured minutes, restarts when Settings changes the minutes,
 * stops when the session ends, is touched by every user interaction (MainActivity.onUserInteraction)
 * and every scan, and signs out with a reason Login shows. 0 minutes = never.
 */
class SessionGuard(
    private val sessionHolder: OperatorSessionHolder,
    settingsRepository: SettingsRepository,
    private val authUseCase: AuthUseCase,
    scanEventBus: ScanEventBus,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var currentMinutes = AutoSignOut.DEFAULT_MINUTES

    private val monitor = InactivityMonitor(
        now = { SystemClock.elapsedRealtime() },
        schedule = { delayMs, r -> handler.postDelayed(r, delayMs) },
        cancel = { r -> handler.removeCallbacks(r) },
        onExpired = { expire() },
    )

    init {
        scope.launch {
            combine(
                sessionHolder.session,
                settingsRepository.settingsFlow.map { it.autoSignOutMinutes }.distinctUntilChanged(),
            ) { session, minutes -> session to minutes }
                .collect { (session, minutes) ->
                    currentMinutes = minutes
                    if (session == null) monitor.stop() else monitor.start(AutoSignOut.timeoutMs(minutes))
                }
        }
        scope.launch { scanEventBus.events.collect { monitor.touch() } }
    }

    /** Any operator interaction. Safe from any thread. */
    fun touch() {
        handler.post { monitor.touch() }
    }

    /** Catches a deadline that passed while the app was in the background. */
    fun checkNow() {
        handler.post { monitor.checkNow() }
    }

    private fun expire() {
        if (sessionHolder.session.value == null) return
        val reason = signedOutAfterMinutes(currentMinutes)
        scope.launch { authUseCase.logout(reason) }
    }
}
```
`AppContainer.kt`: after `val dataWedgeReceiver = ...` (line 80) add
```kotlin
    /** Inactivity sign-out (Station 1 policy). Constructed last: it needs the session holder,
     * settings, auth and the scan bus. */
    val sessionGuard = SessionGuard(
        sessionHolder = operatorSessionHolder,
        settingsRepository = settingsRepository,
        authUseCase = authUseCase,
        scanEventBus = scanEventBus,
    )
```
(import `com.mitas.ppnam.station4aa.data.session.SessionGuard`).
`MainActivity.kt`: add
```kotlin
    private val container get() = (application as PpnamApplication).container

    override fun onResume() {
        super.onResume()
        container.sessionGuard.checkNow()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.sessionGuard.touch()
    }
```

- [x] **Step 7: Compile + JVM suite**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
.\gradlew.bat --offline :app:assembleDebug
```

- [ ] **Step 8: Manual verification**

Settings: unlock, set Auto sign-out to `1`, Test & Apply. Log in, go to Home, do not touch for 60 s. Expected: Login screen with `Signed out after 1 minute of inactivity.` in the error slot (compare S1 `station1/78_auto_logout.png`). Log in again: the line is gone. Set it to `0`, log in, wait 90 s: still signed in. Weigh in `--mode error` with `operator_session_invalid` is not something the fake backend emits for weigh, so verify the session-ended wording through Task 14's replay path instead.

- [x] **Step 9: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/domain/session/InactivityMonitor.kt app/src/main/java/com/mitas/ppnam/station4aa/domain/session/SessionMessages.kt app/src/main/java/com/mitas/ppnam/station4aa/data/session/OperatorSessionHolder.kt app/src/main/java/com/mitas/ppnam/station4aa/data/session/SessionGuard.kt app/src/main/java/com/mitas/ppnam/station4aa/domain/usecase/AuthUseCase.kt app/src/main/java/com/mitas/ppnam/station4aa/data/AppContainer.kt app/src/main/java/com/mitas/ppnam/station4aa/MainActivity.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/login/LoginViewModel.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/weigh/WeighBagViewModel.kt app/src/main/java/com/mitas/ppnam/station4aa/navigation/AppNavGraph.kt app/src/test/java/com/mitas/ppnam/station4aa/domain/session/InactivityMonitorTest.kt app/src/test/java/com/mitas/ppnam/station4aa/domain/session/SessionMessagesTest.kt
git commit -m "feat(session): Station 1's inactivity auto sign-out with a reason on Login

static-05 / S4-09: configurable minutes (0 = never), touched by every
interaction and scan, and every dropped session now explains itself on
the login line instead of navigating silently.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

### Task 14: Collection results shown, outbox re-stamped to the current session, "Retry now" (S4-04; groups f, j)

**Files:**
- Create: `domain/collection/CollectionRejections.kt`, `domain/collection/CollectionResultFilter.kt`
- Create tests: `app/src/test/java/com/mitas/ppnam/station4aa/domain/collection/CollectionRejectionsTest.kt`, `.../CollectionResultFilterTest.kt`, `app/src/test/java/com/mitas/ppnam/station4aa/data/mqtt/WasteCollectionPublisherTest.kt`
- Modify: `data/local/WasteOutboxDao.kt`, `data/mqtt/WasteCollectionPublisher.kt:46-52`
- Modify: `ui/waste/WasteGatheringViewModel.kt:125-170`
- Modify: `ui/waste/WasteGatheringScreen.kt:119-125`

**Interfaces:**
- Produces: `data class CollectionRejection(val message: String, val requiresLogin: Boolean)`, `object CollectionRejections { fun describe(bagCode: String, errorCode: String?): CollectionRejection }`; `fun isResultForSession(result: WasteCollectionResultMessage, currentSessionId: String): Boolean`; `WasteOutboxDao.restampSession(messageId, operatorSessionId)`; `WasteCollectionPublisher.retryPending(currentSessionId: String)` (replaces the no-arg version); `WasteGatheringViewModel.retryNow()`.

**Design note (recorded for the reviewer):** the contract says a retry republishes the exact queued event, but Station 4 rejects any session it did not issue, so a row queued under a session that no longer exists can never be accepted as-is (audit S4-04, shot `100_wc_after_restart.png`). The brief allows "re-stamp with the current session or drop + notify"; this plan re-stamps PENDING rows (no operator work lost; payload *shape* unchanged). Known limitation: if the station had already stored the original attempt and answers the re-stamped retry with `isDuplicate: true` echoing the *old* session id, `evaluateOutcome` reports `IdentityMismatch` and the row stays PENDING — visible in the "queued" line with Retry now, and reconcilable by a manager. `evaluateOutcome` is deliberately not loosened (contract criterion 29).

- [x] **Step 1: Write the failing tests**

`CollectionRejectionsTest.kt`:
```kotlin
package com.mitas.ppnam.station4aa.domain.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit S4-04 / group (f): the rejection line never shows the backend sentence or the
 * nextAction token ("Session is malformed, inactive, expired, or wrong-device. (login)"). */
class CollectionRejectionsTest {

    @Test
    fun `bag already in use`() {
        val r = CollectionRejections.describe("BAG-TO-2", "bag_code_in_use")
        assertEquals("Bag BAG-TO-2 is already waiting to be weighed. Weigh it before registering it again.", r.message)
        assertFalse(r.requiresLogin)
    }

    @Test
    fun `session invalid asks for a new login`() {
        val r = CollectionRejections.describe("BAG-TO-2", "operator_session_invalid")
        assertEquals("Your session ended before bag BAG-TO-2 was delivered. Sign in again and register it again.", r.message)
        assertTrue(r.requiresLogin)
    }

    @Test
    fun `payload problems say register again`() {
        for (code in listOf("invalid_payload", "validation_failed", "required_field_missing")) {
            assertEquals(
                "Station 4 could not read the collection for bag BAG-1. Register it again.",
                CollectionRejections.describe("BAG-1", code).message,
            )
        }
    }

    @Test
    fun `an unknown code names itself and points at a manager`() {
        assertEquals(
            "Station 4 rejected bag BAG-1 (future_code). Ask a manager.",
            CollectionRejections.describe("BAG-1", "future_code").message,
        )
        assertEquals(
            "Station 4 rejected bag BAG-1. Register it again.",
            CollectionRejections.describe("BAG-1", null).message,
        )
    }
}
```
`CollectionResultFilterTest.kt`:
```kotlin
package com.mitas.ppnam.station4aa.domain.collection

import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review focus 3: the result channel replays its last result to every new collector, so a
 * stale `operator_session_invalid` from the previous session must not sign out the operator
 * who just logged in. */
class CollectionResultFilterTest {

    private fun result(session: String) = WasteCollectionResultMessage(
        inResponseToMessageId = "msg-1",
        deviceId = "HH-01",
        operatorSessionId = session,
        collectionId = "COL-1",
        bagCode = "BAG-01",
        accepted = false,
        errorCode = "operator_session_invalid",
        nextAction = "login",
    )

    @Test
    fun `a result for the current session is applied`() {
        assertTrue(isResultForSession(result("sess-new"), currentSessionId = "sess-new"))
    }

    @Test
    fun `a result for a previous session is ignored`() {
        assertFalse(isResultForSession(result("sess-old"), currentSessionId = "sess-new"))
    }

    @Test
    fun `no session at all ignores everything`() {
        assertFalse(isResultForSession(result("sess-old"), currentSessionId = ""))
    }
}
```
`WasteCollectionPublisherTest.kt`:
```kotlin
package com.mitas.ppnam.station4aa.data.mqtt

import com.mitas.ppnam.station4aa.data.local.WasteOutboxDao
import com.mitas.ppnam.station4aa.data.local.WasteOutboxEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeOutboxDao : WasteOutboxDao {
    val rows = mutableMapOf<String, WasteOutboxEntity>()
    override suspend fun insert(entity: WasteOutboxEntity) { rows.putIfAbsent(entity.messageId, entity) }
    override suspend fun getPending(): List<WasteOutboxEntity> =
        rows.values.filter { it.status == WasteOutboxEntity.Status.PENDING }.sortedBy { it.createdAtEpochMs }
    override fun pendingCount(): Flow<Int> = flowOf(0)
    override suspend fun findByMessageId(messageId: String): WasteOutboxEntity? = rows[messageId]
    override suspend fun recordAttempt(messageId: String, nowEpochMs: Long) {
        rows[messageId]?.let { rows[messageId] = it.copy(attemptCount = it.attemptCount + 1, lastAttemptEpochMs = nowEpochMs) }
    }
    override suspend fun markAccepted(messageId: String) {
        rows[messageId]?.let { if (it.status == WasteOutboxEntity.Status.PENDING) rows[messageId] = it.copy(status = WasteOutboxEntity.Status.ACCEPTED) }
    }
    override suspend fun markRejected(messageId: String, errorCode: String?, reason: String?, nextAction: String?) {
        rows[messageId]?.let { if (it.status == WasteOutboxEntity.Status.PENDING) rows[messageId] = it.copy(status = WasteOutboxEntity.Status.REJECTED, errorCode = errorCode, reason = reason, nextAction = nextAction) }
    }
    override suspend fun restampSession(messageId: String, operatorSessionId: String) {
        rows[messageId]?.let { if (it.status == WasteOutboxEntity.Status.PENDING) rows[messageId] = it.copy(operatorSessionId = operatorSessionId) }
    }
}

/** Audit S4-04: a PENDING row queued under a session that no longer exists is re-stamped to the
 * current session before replay, so Station 4 can accept it; terminal rows are untouched. */
class WasteCollectionPublisherTest {

    private fun row(messageId: String, session: String, status: String = WasteOutboxEntity.Status.PENDING) = WasteOutboxEntity(
        messageId = messageId, deviceId = "HH-01", operatorSessionId = session, collectionId = "COL-$messageId",
        bagCode = "BAG-$messageId", jobNumber = "JOB-1", operatorId = "MO-1", wasteTypeCode = "WT-01",
        collectedBy = "Operator One", collectedAtUtc = "2026-10-01T10:00:00.000Z", status = status,
        createdAtEpochMs = 0L, lastAttemptEpochMs = null, attemptCount = 0, errorCode = null, reason = null, nextAction = null,
    )

    private fun publisher(dao: FakeOutboxDao): WasteCollectionPublisher {
        val manager = MqttConnectionManager(deviceId = "HH-01")
        return WasteCollectionPublisher(dao, manager, WasteCollectionResultChannel(dao, manager))
    }

    @Test
    fun `pending rows from an old session are re-stamped and attempted`() = runTest {
        val dao = FakeOutboxDao()
        dao.rows["m1"] = row("m1", "sess-old")
        dao.rows["m2"] = row("m2", "sess-new")

        publisher(dao).retryPending(currentSessionId = "sess-new")

        assertEquals("sess-new", dao.rows["m1"]!!.operatorSessionId)
        assertEquals("sess-new", dao.rows["m2"]!!.operatorSessionId)
        assertEquals(1, dao.rows["m1"]!!.attemptCount)
        assertEquals(1, dao.rows["m2"]!!.attemptCount)
    }

    @Test
    fun `terminal rows are never re-stamped or attempted`() = runTest {
        val dao = FakeOutboxDao()
        dao.rows["m1"] = row("m1", "sess-old", status = WasteOutboxEntity.Status.REJECTED)

        publisher(dao).retryPending(currentSessionId = "sess-new")

        assertEquals("sess-old", dao.rows["m1"]!!.operatorSessionId)
        assertEquals(0, dao.rows["m1"]!!.attemptCount)
    }

    @Test
    fun `without a session nothing is replayed`() = runTest {
        val dao = FakeOutboxDao()
        dao.rows["m1"] = row("m1", "sess-old")

        publisher(dao).retryPending(currentSessionId = "")

        assertEquals("sess-old", dao.rows["m1"]!!.operatorSessionId)
        assertEquals(0, dao.rows["m1"]!!.attemptCount)
    }
}
```
Also update the private `FakeWasteOutboxDao` in `WasteCollectionResultChannelTest.kt` with the same `restampSession` override (it will not compile otherwise once the DAO gains the method).

- [x] **Step 2: Run to see them fail**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.domain.collection.*" --tests "com.mitas.ppnam.station4aa.data.mqtt.WasteCollectionPublisherTest"
```
Expected: unresolved references.

- [x] **Step 3: Pure pieces**

`domain/collection/CollectionRejections.kt`:
```kotlin
package com.mitas.ppnam.station4aa.domain.collection

/** One `waste_collection_result` rejection, phrased for the operator. */
data class CollectionRejection(val message: String, val requiresLogin: Boolean)

/** The operator-facing table for collection rejections — the collection-side twin of
 * `CaptureRefusals`. The station's free-text `reason` and `nextAction` are never shown. */
object CollectionRejections {
    fun describe(bagCode: String, errorCode: String?): CollectionRejection = when (errorCode) {
        "bag_code_in_use" -> CollectionRejection(
            "Bag $bagCode is already waiting to be weighed. Weigh it before registering it again.",
            requiresLogin = false,
        )
        "operator_session_invalid" -> CollectionRejection(
            "Your session ended before bag $bagCode was delivered. Sign in again and register it again.",
            requiresLogin = true,
        )
        "invalid_payload", "validation_failed", "required_field_missing" -> CollectionRejection(
            "Station 4 could not read the collection for bag $bagCode. Register it again.",
            requiresLogin = false,
        )
        null, "" -> CollectionRejection("Station 4 rejected bag $bagCode. Register it again.", requiresLogin = false)
        else -> CollectionRejection("Station 4 rejected bag $bagCode ($errorCode). Ask a manager.", requiresLogin = false)
    }
}
```
`domain/collection/CollectionResultFilter.kt`:
```kotlin
package com.mitas.ppnam.station4aa.domain.collection

import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage

/** `WasteCollectionResultChannel.results` replays its last value to every new collector. Only a
 * result echoing the *current* session may change what the operator sees or end their session. */
fun isResultForSession(result: WasteCollectionResultMessage, currentSessionId: String): Boolean =
    currentSessionId.isNotBlank() && result.operatorSessionId == currentSessionId
```

- [x] **Step 4: DAO + publisher**

`WasteOutboxDao.kt`: append
```kotlin
    // Only a PENDING row may change session: terminal rows are history. Column set unchanged,
    // so no Room schema version bump.
    @Query("UPDATE waste_outbox SET operatorSessionId = :operatorSessionId WHERE messageId = :messageId AND status = 'PENDING'")
    suspend fun restampSession(messageId: String, operatorSessionId: String)
```
`WasteCollectionPublisher.kt` lines 46–52 become:
```kotlin
    /**
     * Retries every durably-queued row still awaiting a result — call after a reconnect or a login
     * so anything queued while offline gets flushed. A row queued under a session Station 4 no
     * longer recognises is re-stamped to [currentSessionId] first (audit S4-04: replaying the old
     * session only ever produced "Session is malformed..."); the event payload shape is
     * unchanged. With no session nothing is sent — Station 4 would refuse it anyway.
     */
    suspend fun retryPending(currentSessionId: String) {
        if (currentSessionId.isBlank()) return
        outboxDao.getPending().forEach { row ->
            val toSend = if (row.operatorSessionId == currentSessionId) {
                row
            } else {
                outboxDao.restampSession(row.messageId, currentSessionId)
                row.copy(operatorSessionId = currentSessionId)
            }
            attemptPublish(toSend.toEvent())
        }
    }
```

- [x] **Step 5: Run the tests**

Same command as Step 2, then the full suite. Expected: PASS.

- [x] **Step 6: ViewModel**

In `WasteGatheringViewModel.kt` replace everything in the `init` block after the `connectionManager.connect(...)` line (lines 127–170: the reconnect-retry, barcode, results and catalogue-sync collectors) with:
```kotlin
        // Whenever we have both a session and a live broker link (first login and every
        // reconnect): refresh the catalogue and flush the outbox under the current session.
        // Sync failure is deliberately silent here — the cached catalogue stays usable and
        // Settings → Diagnostics is where staleness shows.
        viewModelScope.launch {
            combine(
                connectionManager.connectionState,
                sessionHolder.session,
            ) { state, activeSession -> state to activeSession }
                .filter { (state, activeSession) ->
                    state == MqttConnectionState.CONNECTED && activeSession != null
                }
                .collect { (_, activeSession) ->
                    syncCatalogue.sync(activeSession!!.operatorSessionId)
                    publisher.retryPending(activeSession.operatorSessionId)
                }
        }
        viewModelScope.launch {
            scanEventBus.events.filterIsInstance<ScanEvent.Barcode>().collect { event ->
                when (val result = wizardController.handleScannedValue(event.value)) {
                    is ScanDispatchResult.Applied -> syncFromController(result.error)
                    ScanDispatchResult.Ignored -> Unit
                }
            }
        }
        viewModelScope.launch {
            publisher.results.collect { result ->
                // The channel replays its last result; one from a previous session is history.
                if (!isResultForSession(result, sessionHolder.currentSessionIdOrEmpty())) return@collect
                if (result.accepted) {
                    _lastQueuedMessage.value = "Collection ${result.collectionId} accepted by Station 4."
                    _lastMessageIsError.value = false
                } else {
                    val rejection = CollectionRejections.describe(result.bagCode, result.errorCode)
                    _lastQueuedMessage.value = rejection.message
                    _lastMessageIsError.value = true
                    if (rejection.requiresLogin) sessionHolder.clear(SIGNED_OUT_SESSION_ENDED)
                }
            }
        }
```
Add:
```kotlin
    /** The "Retry now" affordance on the queued line. */
    fun retryNow() {
        viewModelScope.launch { publisher.retryPending(sessionHolder.currentSessionIdOrEmpty()) }
    }
```
Imports: `com.mitas.ppnam.station4aa.domain.collection.CollectionRejections`, `com.mitas.ppnam.station4aa.domain.collection.isResultForSession`, `com.mitas.ppnam.station4aa.domain.session.SIGNED_OUT_SESSION_ENDED`. Remove the now-unused `WasteCollectionResultMessage` import.

- [x] **Step 7: Screen — Retry now**

`WasteGatheringScreen.kt` lines 119–125 become:
```kotlin
            if (pendingCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "$pendingCount collection${if (pendingCount == 1) "" else "s"} queued, awaiting delivery",
                        style = MaterialTheme.typography.labelMedium,
                        color = WarningOrange,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.retryNow() }) {
                        Text("Retry now")
                    }
                }
            }
```

- [x] **Step 8: Compile + JVM suite**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
.\gradlew.bat --offline :app:assembleDebug
```

- [ ] **Step 9: Manual verification**

Happy mode: complete a collection → `Queued WC-… for delivery`, then within a second `Collection WC-… accepted by Station 4.` (was `57_wc_result.png`: "Queued" forever). `--mode error`: → red `Bag BAG-… is already waiting to be weighed. Weigh it before registering it again.` (was `84_wc_error_result.png`). `--mode timeout`: → orange `1 collection queued, awaiting delivery` with `Retry now`; `--mode clear`, tap Retry now → the line disappears and the accepted message shows (was `93_wc_outbox_after_clear.png`: stuck). Stale session: `--mode timeout`, submit, force-stop the app (`adb -s emulator-5558 shell am force-stop com.mitas.ppnam.station4aa`), `--mode clear`, relaunch, log in, open Waste Collection → the queued row is delivered and accepted under the new session (was `100_wc_after_restart.png`: "Session is malformed…(login)").

- [x] **Step 10: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/domain/collection/CollectionRejections.kt app/src/main/java/com/mitas/ppnam/station4aa/domain/collection/CollectionResultFilter.kt app/src/main/java/com/mitas/ppnam/station4aa/data/local/WasteOutboxDao.kt app/src/main/java/com/mitas/ppnam/station4aa/data/mqtt/WasteCollectionPublisher.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringViewModel.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringScreen.kt app/src/test/java/com/mitas/ppnam/station4aa/domain/collection/CollectionRejectionsTest.kt app/src/test/java/com/mitas/ppnam/station4aa/domain/collection/CollectionResultFilterTest.kt app/src/test/java/com/mitas/ppnam/station4aa/data/mqtt/WasteCollectionPublisherTest.kt app/src/test/java/com/mitas/ppnam/station4aa/data/mqtt/WasteCollectionResultChannelTest.kt
git commit -m "feat(waste): show the station's collection result, replay the outbox under the current session

S4-04: accepted/rejected results update the banner in operator copy
(no raw reason or nextAction); PENDING rows are re-stamped to the
current session before replay; 'Retry now' on the queued line; a
result from a previous session can no longer sign the operator out.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

### Task 15: Accept an RFID badge at the operator-ID step (S4-10)

**Files:**
- Modify: `domain/wizard/WasteWizardController.kt`
- Modify test: `app/src/test/java/com/mitas/ppnam/station4aa/domain/wizard/WasteWizardControllerTest.kt`
- Modify: `ui/waste/WasteGatheringViewModel.kt` (badge collector)

**Interfaces:**
- Produces: `WasteWizardController.handleScannedBadge(tagId: String): ScanDispatchResult`.

- [x] **Step 1: Write the failing tests**

Append to `WasteWizardControllerTest.kt`:
```kotlin
    @Test
    fun `a badge scan fills the operator id step`() {
        val controller = WasteWizardController()
        controller.submitBagCode("BAG-01")
        controller.submitJobNumber("JOB-1")
        assertEquals(ScanDispatchResult.Applied(null), controller.handleScannedBadge("BADGE000000000000000001"))
        assertEquals("BADGE000000000000000001", controller.draft.operatorId)
        assertEquals(WizardStep.SELECT_CATEGORY, controller.step)
    }

    @Test
    fun `a badge scan on the bag or job step is refused with a hint and changes nothing`() {
        val controller = WasteWizardController()
        assertEquals(ScanDispatchResult.Applied("Scan a barcode, not a badge."), controller.handleScannedBadge("BADGE000000000000000001"))
        assertEquals(WizardStep.SCAN_BAG, controller.step)
        assertNull(controller.draft.bagCode)
        controller.submitBagCode("BAG-01")
        assertEquals(ScanDispatchResult.Applied("Scan a barcode, not a badge."), controller.handleScannedBadge("BADGE000000000000000001"))
        assertEquals(WizardStep.SCAN_JOB, controller.step)
        assertNull(controller.draft.jobNumber)
    }

    @Test
    fun `a badge scan is ignored on selection and review steps`() {
        val controller = completedController()
        assertEquals(ScanDispatchResult.Ignored, controller.handleScannedBadge("BADGE000000000000000001"))
        assertEquals(WizardStep.REVIEW, controller.step)
    }
```

- [x] **Step 2: Run to see them fail**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest --tests "com.mitas.ppnam.station4aa.domain.wizard.WasteWizardControllerTest"
```
Expected: `Unresolved reference: handleScannedBadge`.

- [x] **Step 3: Implement**

Add to `WasteWizardController.kt` after `handleScannedValue`:
```kotlin
    /** An RFID badge read. Operators try their badge at the operator-ID step (audit S4-10), so it
     * is accepted there; on the two barcode steps it is refused with a hint rather than silently
     * dropped; elsewhere it is ignored like any stray scan. */
    fun handleScannedBadge(tagId: String): ScanDispatchResult = when (step) {
        WizardStep.SCAN_OPERATOR -> ScanDispatchResult.Applied(submitOperatorId(tagId))
        WizardStep.SCAN_BAG,
        WizardStep.SCAN_JOB -> ScanDispatchResult.Applied(BADGE_NOT_BARCODE)
        WizardStep.SELECT_CATEGORY,
        WizardStep.SELECT_WASTE_TYPE,
        WizardStep.REVIEW -> ScanDispatchResult.Ignored
    }

    private companion object {
        const val BADGE_NOT_BARCODE = "Scan a barcode, not a badge."
    }
```
`WasteGatheringViewModel.kt`: next to the barcode collector add
```kotlin
        viewModelScope.launch {
            scanEventBus.events.filterIsInstance<ScanEvent.RfidTag>().collect { event ->
                when (val result = wizardController.handleScannedBadge(event.tagId)) {
                    is ScanDispatchResult.Applied -> syncFromController(result.error)
                    ScanDispatchResult.Ignored -> Unit
                }
            }
        }
```

- [x] **Step 4: Run the tests, then the suite and build**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
.\gradlew.bat --offline :app:assembleDebug
```
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 5: Manual verification**

Wizard at Step 3: `adb -s emulator-5558 shell am broadcast -a com.rscja.scanner.action.scanner.RFID --es data BADGE000000000000000001` → advances to Step 4 with Operator ID `BADGE000000000000000001` on the review card (was `47_wc_step4.png`: ignored). At Step 1 the same broadcast → red `Scan a barcode, not a badge.` above the field, still Step 1.

- [x] **Step 6: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/domain/wizard/WasteWizardController.kt app/src/main/java/com/mitas/ppnam/station4aa/ui/waste/WasteGatheringViewModel.kt app/src/test/java/com/mitas/ppnam/station4aa/domain/wizard/WasteWizardControllerTest.kt
git commit -m "feat(waste): accept an RFID badge at the operator-ID step

S4-10: a badge read at step 3 fills the operator ID; on the barcode
steps it says 'Scan a barcode, not a badge.' instead of doing nothing.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

### Task 16: Ignore scan broadcasts while the app is not in the foreground (S4-18; group l)

**Files:**
- Create: `data/rfid/ForegroundTracker.kt`
- Modify: `data/rfid/DataWedgeReceiver.kt:23-28`
- Modify: `data/AppContainer.kt:79-80`, `MainActivity.kt`

`BroadcastReceiver`/`Intent` need Android, so there is no JVM test; verification is the emulator check in Step 3.

- [x] **Step 1: Implement**

`data/rfid/ForegroundTracker.kt`:
```kotlin
package com.mitas.ppnam.station4aa.data.rfid

/** Set by MainActivity.onResume/onPause. The scan receiver drops broadcasts while false: the
 * audit saw Station 4 log in from the background on a badge meant for Station 5 (S4-18). */
class ForegroundTracker {
    @Volatile var isResumed: Boolean = false
}
```
`DataWedgeReceiver.kt`: constructor becomes
```kotlin
class DataWedgeReceiver(
    private val scanEventBus: ScanEventBus,
    private val isForeground: () -> Boolean = { true },
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!isForeground()) {
            Log.i(TAG, "Ignoring ${intent.action} while not in the foreground")
            return
        }
        when (intent.action) {
```
(the rest of `onReceive` unchanged).
`AppContainer.kt` lines 79–80:
```kotlin
    val scanEventBus = ScanEventBus()
    val foregroundTracker = ForegroundTracker()
    val dataWedgeReceiver = DataWedgeReceiver(scanEventBus) { foregroundTracker.isResumed }
```
(import `com.mitas.ppnam.station4aa.data.rfid.ForegroundTracker`).
`MainActivity.kt`: in `onResume` (added in Task 13) add `container.foregroundTracker.isResumed = true` before `checkNow()`, and add
```kotlin
    override fun onPause() {
        container.foregroundTracker.isResumed = false
        super.onPause()
    }
```

- [x] **Step 2: Compile + JVM suite**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
.\gradlew.bat --offline :app:assembleDebug
```

- [ ] **Step 3: Manual verification**

On Login, press Home (`adb -s emulator-5558 shell input keyevent KEYCODE_HOME`), then broadcast the RFID badge `BADGE000000000000000002`. Reopen the app: still on Login, not logged in as Manager One (was `station5/32_main_back_reveals_station4_task.png`). Bring the app forward and broadcast again: logs in.

- [x] **Step 4: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/station4aa/data/rfid/ForegroundTracker.kt app/src/main/java/com/mitas/ppnam/station4aa/data/rfid/DataWedgeReceiver.kt app/src/main/java/com/mitas/ppnam/station4aa/data/AppContainer.kt app/src/main/java/com/mitas/ppnam/station4aa/MainActivity.kt
git commit -m "fix(scan): ignore DataWedge/Chainway broadcasts while the activity is not resumed

S4-18: a badge meant for another station logged Station 4 in from the
background.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q"
```

---

### Task 17: Whole-branch verification pass

**Files:** none (verification only; fix-ups go in their own `fix:` commits).

- [x] **Step 1: Full test and build**

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
.\gradlew.bat --offline :app:assembleDebug
.\gradlew.bat --offline :app:connectedDebugAndroidTest
```
Expected: all `BUILD SUCCESSFUL`.

- [ ] **Step 2: Install and run the audit's keyboard matrix for S4**

Install the APK on `emulator-5558`. For each row — Login (username/password), Settings PIN, Settings form (Host/Port/Username/Password/Auto sign-out), Waste steps 1–3, Weigh Bag — run the keyboard check recipe and record `primary button bottom < IME top` (or scrolls) and `Enter submits`. Every row must be Yes/Yes (section 6 of the report had four No rows for S4).

- [ ] **Step 3: Run the finding checklist**

Walk S4-01 … S4-18 and the static rows using the per-task "Manual verification" steps above, taking a screenshot for each into `<SP>\audit\shots\station4_after\`. Confirm `adb -s emulator-5558 shell logcat -d -s AndroidRuntime:E` is empty at the end.

- [ ] **Step 4: Refresh the knowledge graph (repo rule in `CLAUDE.md`)**

```powershell
graphify update .
```
Commit the `graphify-out/` changes on their own: `git add graphify-out && git commit -m "chore: graphify update after the UI audit fixes"` (with the standard trailer).

- [x] **Step 5: Report**

List any finding whose verification failed together with the task that owns it; do not merge until each is fixed in a follow-up commit on this branch.

---

## Coverage

**Findings closed by this plan:** S4-01 (T6), S4-02 (T7), S4-03 (T12), S4-04 (T14), S4-05 (T5), S4-06 (T9), S4-07 (T4, T8, T10, T11), S4-08 (T2, T8), S4-09 (T12, T13), S4-10 (T15), S4-11 (T5), S4-13 (T2, T6, T7), S4-14 (T6, T7, T10), S4-15 (T5, T6, T7, T8, T9), S4-16 (T6), S4-17 (T5, T7, T10), S4-18 (T16); static-01 (T6, T7), static-02 (T3), static-04 (T12), static-05 (T13), static-06 (T10), static-08 (T4, T7), static-15 (T12), static-17 (T11), static-18 (T5, T11), static-19 (T6, T7), static-20 (T8, T10), static-23 (T3), static-25 (T8); §5 rows: gear icon (already `Icons.Filled.Settings`, same tint on every screen — no change needed), accent (T3), login layout (T8; see below for the dropdown), pill vocabulary (T5, T11), Settings action & field set (T10), Diagnostics rows (T11), Back on Login/Home (T12), session (T13), timeout (T4, T7), dialog style (T12), error colour (T6, T7).

**Not covered, and why:**
- **S4-12** (production broker defaults, `admin`/`admin`) — explicitly out of scope per the brief; the "PUBLISHES TO" row the static audit expected does not exist and nothing is required.
- **static-24** (operator username dropdown on Login) — Station 4 has no local user list to populate it from (S1 caches one from its own backend); adding one needs a backend/contract decision. Badge row, toggle and empty-field check from the same §5 row are done in T8.
- **static-10** (status-bar strip `#102233` vs `#07101A`), **static-11** (corner radii / tile elevation), **static-13** (Home chrome, hard `\n` in tile labels), **static-16** (motion/transitions), **static-22** (toolbar title 22 sp vs 18 sp) — cross-stack theme decisions with no "Recommended standard" value in §5; the only S4-specific items in those rows (56 dp buttons, danger red) are done in T6/T7.
- **static-27** (Toast usage; Settings RFID shortcut tag) — S2/S4 have no RFID settings shortcut by design; adding one is a feature, not a consistency fix.
- **static-07** (station-offline overlay behaviour) — out of scope per the brief ("station-offline overlay redesign").
- **static-03, static-09, static-12, static-14, static-21, static-26** — do not apply to Station 4 (S2 badge login; XML night themes; wrench icon in XML apps; S3/S5 empty state; Launcher; XML layouts).

## Self-review notes

- Spec coverage: every S4 row in §4 and every §5 "Recommended standard" that applies to S4 maps to a task above; gaps are listed under "Not covered" with a reason.
- Type consistency: `connectionStatusStateFlow` (T5) is used by name in T5 only; `AutoSignOut`/`autoSignOutMinutes` (T10) are consumed by T13's `SessionGuard`; `OperatorSessionHolder.clear(reason)` and `SIGNED_OUT_SESSION_ENDED` (T13) are consumed by T14; `restampSession` (T14) appears in both fake DAOs; `handleScannedBadge` (T15) returns the existing `ScanDispatchResult`; `ForegroundTracker` (T16) hangs off `AppContainer` next to `scanEventBus`.
- Review Focus 1–5 are pinned by `PinGateTest` (T9), `SettingsValidationTest` (T10), `CollectionResultFilterTest` (T14), `WasteWizardControllerTest` (T15), `InactivityMonitorTest` (T13).
