---
app_name: Station 4
file_stem: Station_4
station: Station 4 wastage
mark: "4"
accent: "#55368C"
accent_tint: "#BCAAD9"
icon: images/icon.png
version: 1.5.0
date: 2026-11-06
device: Chainway C72
standfirst: >-
  How to register a waste collection, ask Station 4 to weigh a bag, and what
  to do when the station answers with a problem.
description: Standard operating procedure for the PPNAM Station 4 wastage handheld app 1.5.0 on the Chainway C72.
crop: {top: 72, bottom: 126}
revisions:
  - version: 1.0
    date: 2026-09-10
    change: First handheld SOP (`docs/Station4_Handheld_SOP.html`).
  - version: 1.5.0
    date: 2026-11-06
    change: "Suite release: visual refresh, consistency pass, scan sounds."
---

<!-- Screenshots: v1.5.0 (7) debug build of master on the c72_clone emulator, against a
     throwaway Station 4 mock (Mosquitto on 1887 plus a Node stand-in for the station).
     Names, bag codes and job numbers are demo data. -->

# Purpose and scope {#purpose}

The Station 4 app runs on the Chainway C72 handheld at the wastage station. Wastage operators use it for two separate jobs: **registering a waste collection** where the bag is filled, and **asking Station 4 to weigh the bag** when it reaches the station's scale.

## What it does

- Records each wastage bag with its bag code, job number, the production operator who filled it, a waste category and a waste type.
- Keeps registered collections on the handheld until Station 4 confirms it has accepted them, so a network gap does not lose a bag.
- Asks Station 4 to weigh a registered bag on the station's scale, and shows the weight it recorded against you.

## What it does not do

- It does not weigh anything itself. Only the Station 4 PC is wired to the scale.
- It cannot edit or delete a collection after Station 4 has accepted it. Corrections are made on the Station 4 desktop.
- It does not print labels or reports.

> [!NOTE] Who this is for
> Wastage operators at Station 4. Sections marked **Supervisor** are for the shift supervisor, who holds the settings PIN.

# Before you start {#before}

- [ ] The handheld is charged. Put it back on its charger at the end of the shift.
- [ ] You are on the plant Wi-Fi, in coverage at the bag-filling point and at the station.
- [ ] You know your Station 4 username and password, or you have your RFID badge.
- [ ] The Station 4 PC is running. The status pill shows **Connected** (section 3).
- [ ] For a weigh: the bag is on the Station 4 scale before you scan it.

::: step Opening the app
shot: 01_login_empty.png
caption: The Log In screen, ready for a username or a badge.
alt: Station 4 Log In screen with Username and Password fields, a violet Log In button and "or scan your badge"
The handheld starts in the **PPNAM Station Launcher**. Tap the violet **Station 4** tile.

The app opens on **Log In**. Wait a few seconds for the status pill in the top-right corner to settle on **Connected** before you sign in.
:::

# The status pill and header {#status}

The coloured pill in the top-right corner is on every screen. It reports two links: the **network link** (the broker that carries messages) and **Station 4 itself** (the PC at the station). It waits 1.5 seconds before it changes, so a short blip does not make it flicker.

::: pills
go | Connected | The network is up and Station 4 is running. Everything works. | Carry on.
warn | Reconnecting | The handheld lost the network link and is getting it back. It retries by itself, from every second up to every 30 seconds. | Wait. If it does not clear, move into Wi-Fi coverage.
warn | Station 4 offline | The network is fine, but the Station 4 PC is not running or not reachable. | Collections still queue on the handheld and send later. Weighs and operator look-ups will not work. Tell your supervisor.
stop | Offline | The handheld could not reach the network at all, for example straight after it was switched on outside coverage. | Move into Wi-Fi coverage. If it stays red, ask your supervisor to check Settings.
:::

::: shots
14_home_reconnecting.png | Reconnecting: the link dropped and is coming back. | Home screen with an amber Reconnecting pill
31_home_station_offline.png | Station 4 offline: the network is fine, the station PC is not. | Home screen with an amber Station 4 offline pill
02_login_offline.png | Offline: no network link since the app started. | Log In screen with a red Offline pill
:::

::: step The header
shot: 13_home.png
caption: Home: your name and role, Settings, the pill, and the two jobs.
alt: Station 4 home screen with the operator Sam Collector, a Connected pill and violet Waste Collection and Weigh Bag tiles
- **Your name and role** sit under the logo. Tap them to log out (section 10).
- The **gear** opens Settings.
- On the Waste Collection and Weigh Bag screens the header also has a **back arrow**, and the screen name sits next to the pill.

The home screen has two tiles: **Waste Collection** and **Weigh Bag**. Scans do nothing on the home screen.
:::

# Signing in {#signin}

Everything you record is saved against your name, so the app does nothing until you sign in. You can sign in with your username and password, or with your badge.

::: step By username and password
shot: 04_login_dropdown.png
caption: Tap the arrow to pick your username from the station's list.
alt: Username dropdown open, listing "manager — Mandla Manager" and "sim — Sam Collector"
1. Tap **Username**. Pick your name from the list (each row reads *username — Your Name*) or type your username.
2. Tap **Password** and type your password. The eye icon shows what you typed.
3. Tap **Log In**. The button reads **Logging in…** while Station 4 checks your password.

The list comes from Station 4 and is kept on the handheld, so it is there even while the station is offline. A username that is not in the list still works.
:::

::: step By badge
shot: 05_login_logging_in.png
caption: Logging in… The fields are locked until Station 4 answers.
alt: Log In button showing a spinner and "Logging in…"
While the Log In screen is showing, **scan your RFID badge**. You do not need to tap anything first.

A second badge read while a sign-in is already running is ignored, so a double read cannot sign in the wrong person. Signing in makes no scan sound.
:::

::: step If sign-in fails
shot: 06_login_error.png
caption: The reason appears in red above the fields.
alt: Log In screen with the red message "Incorrect username or password."
The reason appears in red at the top of the card. The common ones:

- **Incorrect username or password.** Retype them carefully.
- **Badge not recognised. Ask a manager.** Your badge is not registered at Station 4.
- **Station 4 did not respond. Check the station and retry.** The station did not answer within 10 seconds. Check the pill, then tap **Log In** again.
- **Please fill in all fields** Both boxes are needed.

Every sign-in message is listed in section 8.
:::

## Automatic sign-out

If nobody touches the handheld or scans for **15 minutes**, the app signs you out and shows *Signed out after 15 minutes of inactivity.* on the Log In screen. Your supervisor can change the time in Settings. Time with the app in the background counts too.

If Station 4 ends your session, you return to Log In with *Your session has ended. Sign in again.*

# Waste Collection {#workflow-collection}

Register one bag in five steps, then review and confirm. On steps 1 to 3 you can **scan** the barcode or type into **Manual entry** and tap **Submit**. Scanning is faster and cannot mistype. Typed values are capitalised for you.

> [!NOTE] Backing out
> **Cancel transaction** at the bottom of each step asks *Discard this collection?* before it throws the bag's values away and returns you to Step 1. The **back arrow** (or the Back key) leaves for the home screen and asks the same question if you have captured anything. Nothing is sent until you confirm the review.

::: step Scan bag code
label: Step 1
shot: 17_waste_step1.png
caption: Step 1. Submit stays grey until there is something to submit.
alt: Waste Collection, Step 1 of 5, "Scan bag code" with a Manual entry field and a disabled Submit button
Scan the barcode on the wastage bag. This code identifies the bag for the rest of its life, including when you weigh it.

A badge read here is refused with *Scan a barcode, not a badge.*
:::

::: step Scan or enter the job number
label: Step 2
shot: 18_waste_step2.png
caption: Step 2. The job the waste came off.
alt: Waste Collection, Step 2 of 5, "Scan or enter the job number"
Scan the job card or type the job number. A stand-in value such as `N/A`, `NA`, `NONE` or `UNKNOWN` is refused: enter the real job.
:::

::: step Scan the production operator's card
label: Step 3
shot: 20_waste_step3_lookup.png
caption: Station 4 looks the card up and returns the operator's name.
alt: Step 3 of 5 with a spinner and "Looking up the operator at Station 4…"
Scan the badge or card barcode of the operator who **filled** the bag, or type their card number. This is usually not you: you are recorded separately as the wastage operator.

The app asks Station 4 for the operator's name and shows *Looking up the operator at Station 4…* while it waits. It moves on only when the station returns a name, so this step needs Station 4 online.
:::

::: step If the card is refused
shot: 21_waste_step3_refused.png
caption: A refused card. Scan a valid card to carry on.
alt: Step 3 with the red message "That card is not registered to an active operator. Scan a valid card."
The reason appears in red above the field, for example *That card is not registered to an active operator. Scan a valid card.* Scan the right card. If the station is busy or offline, wait for **Connected** and scan again. Section 8 lists every message.
:::

::: step Select waste category
label: Step 4
shot: 23_waste_step4_menu.png
caption: Step 4. Pick the category from the list.
alt: Step 4 of 5 with the Waste Category list open: Production waste, Packaging, Hazardous
Tap **Waste Category**, pick from the list, then tap **Confirm**. Scans are ignored on this step.

If you see *No waste categories available. Refresh the catalogue in Settings.*, open Settings and tap **Refresh catalogue** (section 9).
:::

::: step Select waste type
label: Step 5
shot: 24_waste_step5_menu.png
caption: Step 5. Only the types in your category are listed.
alt: Step 5 of 5 with the Waste Type list open, showing types such as "TRIM — Rubber trimmings"
Tap **Waste Type**. Each row reads *CODE — Name*, filtered to the category you picked. Choose one and tap **Confirm**.

An empty list means the category has no types loaded: refresh the catalogue in Settings.
:::

::: step Review and confirm
label: Review
shot: 25_waste_review.png
caption: The last chance to fix a mis-scan.
alt: Confirm waste collection dialog listing bag code, job number, production operator, waste category, waste type and wastage operator, with Edit buttons, Back and Confirm
Everything you entered, on one card. **Read it.** Each row has an **Edit** button that takes you back to that step and then returns you here.

- **Bag code**, **Job number**: what you scanned.
- **Production operator**: the name Station 4 returned for the card.
- **Waste category**, **Waste type**: what you picked.
- **Wastage operator**: you, from your sign-in. It has no Edit.

Tap **Confirm** to send the collection. **Back** closes the review and keeps every value.
:::

::: step After Confirm
shot: 26_waste_accepted.png
caption: Accepted, and already back at Step 1 for the next bag.
alt: Green banner "Collection WC-20261106-682934 accepted by Station 4." above a fresh Step 1
The wizard resets to Step 1 straight away, ready for the next bag. The collection is saved on the handheld first, then sent. A banner shows where it is:

- **Queued WC-… for delivery**: saved and on its way. `WC-…` is the collection's reference.
- **Collection WC-… accepted by Station 4.** (green): done. You hear the success chirp.
- A red message: Station 4 refused the bag (below). You hear the fail buzz.

**Dismiss** hides a banner. It does not cancel anything.
:::

::: step When the station is offline
shot: 28_waste_queued_offline.png
caption: Queued while Station 4 is offline. It sends itself when the station is back.
alt: Station 4 offline pill, amber banner "1 collection queued, awaiting delivery" with Retry now, and "Queued WC-20261106-579402 for delivery"
Keep working. Each confirmed collection waits on the handheld, and the amber banner counts them: *1 collection queued, awaiting delivery*. They are sent again each time the link comes back. **Retry now** sends them at once.

Only step 3 needs the station live, because it looks up the operator's name.
:::

> [!WARNING] Deliver the queue before you log out
> Collections still queued when you log out are **not** sent under the next person's sign-in. The next person sees *1 collection from a previous sign-in was not delivered. Re-capture it.* and the bag must be registered again. Before you log out, wait until the queued banner has gone.

::: step If a bag is rejected
shot: 27_waste_rejected.png
caption: A rejected bag did not register. Read the reason.
alt: Red banner "Bag BAG-240612 is already waiting to be weighed. Weigh it before registering it again."
A red banner such as *Bag BAG-240612 is already waiting to be weighed. Weigh it before registering it again.* means Station 4 refused that bag. It is **not** registered. Do what the message says, then register the bag again if needed. If the reason names a code you do not recognise, take the bag code to your supervisor.
:::

# Weigh Bag {#workflow-weigh}

You do not weigh the bag on the handheld: you ask Station 4 to weigh it on the station's scale. The weight is recorded against **your** sign-in, so nobody needs to be signed in at the Station 4 PC.

::: step Request a weight
label: Steps
shot: 33_weigh_filled.png
caption: Bag on the scale, code scanned, ready to request.
alt: Weigh Bag screen with bag code BAG-240611 entered and a Request weight button
1. **Put the bag on the Station 4 scale.** Physically, first.
2. Scan the bag's barcode, or type it into **Bag code**. A scan only fills the box; it does not start the weigh.
3. Tap **Request weight**. The button reads **Weighing…** while the station measures.
4. Read the result, take the bag off and scan the next one.

> [!WARNING] Register the bag first
> A weigh only works for a bag that has a Waste Collection. Otherwise Station 4 answers *This bag is not waiting to be weighed. Register the collection first.*
:::

::: step A successful weigh
shot: 35_weigh_result.png
caption: The weight, the bag, and who captured it.
alt: Weigh Bag result card reading 12.345 kg, BAG-240611, Captured by Sam Collector, Scan the next bag.
The card shows the weight in large green figures, the bag code, *Captured by* your name and *Scan the next bag.* You hear the success chirp and the box clears for the next bag.

Weights show up to three decimals without padding: 8.5 kg reads `8.5 kg`.
:::

::: step A refused weigh
shot: 36_weigh_refused_retry.png
caption: Refused, but the code is kept: fix the problem and tap Retry.
alt: Red card "The scale is empty. Put the bag on it and try again." with "The bag code is still here — fix the problem and tap Retry." and a Retry button
A red card says what went wrong, and you hear the fail buzz. What matters is whether the bag code stays in the box:

- **The code is kept** and the card adds *The bag code is still here — fix the problem and tap Retry.* Fix the physical problem (bag on the pan, bag steadied, scale free) and tap **Retry**. This happens when the scale is busy, empty or did not settle, and when Station 4 did not answer in time.
- **The box is cleared.** That weigh is finished. Do what the message says before you scan the bag again.
:::

> [!WARNING] A weigh is never queued
> Unlike a collection, a weigh is not saved for later. It only means something while the bag is on the pan. If it fails, do it again there and then.

# Scan sounds and vibration {#sounds}

Every PPNAM handheld uses the same two sounds. The Chainway scanner beeps by itself when it reads anything; the app's sound comes **after** that, when the answer is known.

- **Success chirp** (a bright rising two-note chirp) with one short 40 ms vibration: the scan was **accepted**.
- **Fail buzz** (a low falling double buzz) with two 200 ms vibrations: the scan **failed**. Read the screen.

The sounds play at the **media volume**. If you cannot hear them, turn the media volume up; the vibration still tells you.

Table: What you hear in the Station 4 app
| When | Outcome | Sound |
|---|---|---|
| Waste Collection, steps 1 and 2 | Bag code or job number accepted | {ok} |
| Waste Collection, steps 1 and 2 | Value refused on the handheld (blank, placeholder, too long), or a badge instead of a barcode | {fail} |
| Waste Collection, step 3 | Station 4 returns the operator's name | {ok} |
| Waste Collection, step 3 | Card refused, Station 4 offline, or no answer in 10 seconds | {fail} |
| Waste Collection, after Confirm | Station 4 accepts the collection | {ok} |
| Waste Collection, after Confirm | Station 4 rejects the collection | {fail} |
| Weigh Bag | Weight recorded | {ok} |
| Weigh Bag | Weigh refused, Station 4 offline, or no answer in 10 seconds | {fail} |
| Weigh Bag | A scan fills the Bag code box | {none} |
| Steps 4 and 5, the review, the home screen | Any scan (ignored) | {none} |
| Log In, Settings, buttons, menus | Signing in, tapping, choosing from a list | {none} |

## The repeat-read rule

One pull of the trigger can read the same code several times. A repeat of a value that was **just refused on the handheld**, within 1.5 seconds, makes no sound, so holding the trigger never buzzes twice. A different value plays its own sound. Answers from Station 4 always sound once.

Queuing a collection is silent: the chirp or buzz comes when Station 4 answers. Answers that arrive after you have left the screen, or for collections queued earlier, are silent.

# Messages and what to do {#messages}

Every message the app shows, word for word. *Ask a manager* or *Ask a supervisor* means the problem cannot be fixed on the handheld.

Table: Signing in
| Message | What it means | What to do |
|---|---|---|
| Please fill in all fields | The username or password box is empty. | Fill in both. |
| Incorrect username or password. | Station 4 did not accept the password. | Retype carefully. Ask your supervisor if you are locked out. |
| Badge not recognised. Ask a manager. | The badge is not registered at Station 4. | Sign in with your username, and ask a manager to register the badge. |
| Login was refused by the station (*code*). Ask a supervisor. | Station 4 refused for another reason. | Tell your supervisor the code. |
| Not connected to the broker. Check Settings and retry. | The handheld has no network link. | Check the pill and Wi-Fi, then retry. |
| Station 4 did not respond. Check the station and retry. | No answer within 10 seconds. | Check that the Station 4 PC is running, then retry. |
| Station 4 sent an unreadable reply. Retry. | The answer was damaged. | Retry. Report it if it repeats. |
| Login failed. Try again. | Sign-in failed for another reason. | Try again. Report it if it repeats. |
| Signed out after *N* minutes of inactivity. | The automatic sign-out ran. | Sign in again. |
| Your session has ended. Sign in again. | Station 4 ended your session. | Sign in again and repeat the last action. |

Table: Waste Collection steps
| Message | What it means | What to do |
|---|---|---|
| Required. | The box is empty. | Scan or type the value. |
| Must not contain control characters. | The scan contained hidden characters. | Scan again, or type the value. |
| Must be 100 characters or fewer. | The value is too long to be a real code. | Scan the right label. |
| Enter a real ID — "*value*" looks like a placeholder value. | `N/A`, `NA`, `NONE` or `UNKNOWN` was entered. | Enter the real job number or card. |
| Scan a barcode, not a badge. | An RFID read on step 1 or 2. | Scan the barcode. |
| Bag code: *reason* | The bag code failed a check during the operator look-up. | Cancel and scan a valid bag. |
| Station 4 could not read that card. Scan it again. | The card did not read cleanly. | Scan it again. |
| The bag code is not valid. Cancel and scan a valid bag. | Station 4 refused the bag code. | Cancel transaction and start with a valid bag. |
| That is not a valid operator card. Scan the card again. | The card number is malformed. | Scan the card again. |
| That card is not registered to an active operator. Scan a valid card. | The card is unknown or inactive. | Scan the right operator's card. |
| That card has no operator name on record. Scan a different card or ask a manager. | The operator has no name at Station 4. | Use another card, or ask a manager. |
| Station 4 cannot look up operators right now. Try again shortly. | The look-up service is busy. | Wait, then scan again. |
| Station 4 refused the operator card. Scan it again. | Refused without a reason. | Scan again. |
| Station 4 refused the operator card: *code*. Ask a manager. | Refused with a code this app does not know. | Tell a manager the code. |
| Station 4's answer did not match the card you scanned. Scan it again. | The answer was for another card. | Scan again. |
| Station 4 accepted the card but sent no name. | The station has no name to show. | Scan again; report it if it repeats. |
| Could not ask Station 4 for the operator's name. | The request could not be sent. | Check the pill, then scan again. |
| No waste categories available. Refresh the catalogue in Settings. | The category list is empty. | Settings, then **Refresh catalogue**. |
| No waste types in this category. Refresh the catalogue in Settings. | The type list for this category is empty. | Settings, then **Refresh catalogue**. |

Table: Collection banners
| Message | What it means | What to do |
|---|---|---|
| *N* collection(s) queued, awaiting delivery | Collections are waiting on the handheld. | Nothing; they send when the link is back. **Retry now** sends at once. |
| *N* collection(s) from a previous sign-in was/were not delivered. Re-capture it/them. | Collections were still queued when the last person logged out. | Register those bags again, then **Dismiss**. |
| Bag *bag* is already waiting to be weighed. Weigh it before registering it again. | The bag is already registered. | Weigh it; do not register it twice. |
| Your session ended before bag *bag* was delivered. Sign in again and register it again. | The session ended first. | Sign in and register the bag again. |
| Station 4 could not read the collection for bag *bag*. Register it again. | The collection was damaged or incomplete. | Register the bag again. |
| Station 4 could not accept the operator card for bag *bag*. Register it again with a valid card. | The operator card was refused. | Register again with the right card. |
| Station 4 could not look up the operator for bag *bag*. Register it again shortly. | The look-up service was busy. | Wait, then register again. |
| Station 4 rejected bag *bag*. Register it again. | Refused without a reason. | Register again. |
| Station 4 rejected bag *bag* (*code*). Ask a manager. | Refused with a code this app does not know. | Set the bag aside and tell a manager. |

Table: Weigh Bag (✓ = the bag code stays in the box for **Retry**)
| Message | What it means | What to do |
|---|---|---|
| The scale is in use at the station. Try again in a moment. ✓ | Someone else is using the scale. | Wait, then **Retry**. |
| The scale is empty. Put the bag on it and try again. ✓ | The bag is not on the pan. | Put it on, then **Retry**. |
| The scale did not settle. Steady the bag and try again. ✓ | The bag is swinging or leaning. | Steady it, then **Retry**. |
| Station 4 did not respond. Check the station and retry. ✓ | No answer within 10 seconds. | Check the pill, then **Retry**. |
| Not connected to the broker. Check Settings and retry. ✓ | No network link. | Move into coverage, then **Retry**. |
| This bag is not waiting to be weighed. Register the collection first. | No collection for this bag. | Run Waste Collection first. |
| This bag has already been weighed. Start the next collection. | Already done. | Move on to the next bag. |
| This collection is in conflict and a manager must review it. | The station needs a manager to decide. | Set the bag aside and fetch a manager. |
| You do not have permission to capture waste. Ask a manager. | Your account cannot weigh. | Ask a manager. |
| The scale is not available at Station 4. Ask a manager. | The scale is broken or disconnected. | Report it. |
| Your session has ended. Sign in again. | The session expired. | Sign in, then weigh again. |
| Station 4 could not read that bag code. Scan it again. | The code was damaged. | Scan again. A damaged label needs replacing. |
| That weigh request clashed with an earlier one. Scan the bag again. | A duplicate request. | Scan again; report it if it repeats. |
| Station 4 refused a retained request. Scan the bag again. | A technical refusal. | Scan again; report it if it repeats. |
| Station 4 rejected the weigh request. Scan the bag again. | The request failed a check. | Scan again; report it with the bag code if it repeats. |
| This app is out of date for Station 4. Ask a manager. | The handheld needs an app update. | Ask a manager. |
| This scanner's identity did not match. Ask a manager. | A configuration fault. | Give the Device ID from Settings. |
| This scanner's clock is wrong. Ask a manager. | The date or time is wrong. | Ask a manager to set the clock. |
| This scanner's clock is ahead of Station 4. Ask a manager. | The clocks disagree. | Ask a manager to set the clock. |
| Station 4 refused the weigh. Scan the bag again. | Refused without a reason. | Scan again. |
| Station 4 refused the weigh: *code*. Ask a manager. | A code this app does not know. | Do not retry. Report the code and bag code. |

Table: Settings
| Message | What it means | What to do |
|---|---|---|
| Log in before refreshing the catalogue | The catalogue needs a signed-in operator. | Sign in, then refresh. |
| Catalogue refresh failed: *reason* | Station 4 did not send the catalogue. | Check the pill, then try again. |
| Enter the supervisor PIN. | The PIN box is empty. | Supervisor only. |
| Incorrect PIN. *N* attempts left before lockout. | Wrong PIN. | Supervisor only. |
| Too many attempts. Try again in *N*s. | Five wrong PINs lock the box for 30 seconds. | Wait for the countdown. |
| Saved, but could not connect to *host*:*port*. Check the broker settings. | The new settings were saved but do not connect. | Supervisor: check the values. |

# Settings {#settings}

The gear opens Settings from any screen. The top half is open to everyone and is what support will ask you to read out. The bottom half needs the supervisor PIN.

::: step Diagnostics: anyone
shot: 08_settings_diag.png
caption: Diagnostics, the catalogue, and the PIN gate below.
alt: Settings Diagnostics: MQTT broker Connected, Station 4 Online, version v1.5.0 (7), device ID, catalogue synced, Refresh catalogue, Catalogue updated — 6 waste types
- **MQTT broker**: the network link on its own (Connected, Reconnecting, Offline).
- **Station 4**: the station PC (Online, Offline, Unknown). Broker *Connected* with Station 4 *Offline* means the network is fine and the station is the problem.
- **Version**: `v1.5.0 (7)` for this release.
- **Device ID**: this handheld's identity, such as `scanner_59fcb3c4129d`. Read it out when you report a fault.
- **Catalogue**: when the category and type lists last came from Station 4. *built-in seed — never synced* means the handheld has only its starter list.
- **Refresh catalogue**: fetches the lists again. You must be signed in. It reports *Catalogue updated — N waste types*.
- **Signed in as** and **Log out** appear at the bottom when you are signed in.
:::

> [!SUPERVISOR] Configuration
> Type the supervisor PIN and tap **Unlock**. Five wrong attempts lock the PIN box for 30 seconds, and the count survives leaving Settings. Never tell an operator the PIN; operators should ask the supervisor.

::: step Unlocked: supervisors only
shot: 10_settings_unlocked.png
caption: Unlocked, showing the standard broker settings.
alt: Unlocked Settings showing Host mqtt.sysone.co.za, Port 443, WebSocket on, TLS on, Username admin, Password, and the Session section
- **Host** and **Port**: normally `mqtt.sysone.co.za` and `443`.
- **WebSocket** and **TLS**: both on for the standard setup.
- **Username** and **Password**: the broker account, not an operator account. Leave the password blank to keep the current one.
- **Auto sign-out after (minutes, 0 = never)**: 15 by default, up to 1440.

**Test & Apply** saves the settings and then tests them. *Connected — settings saved* means it worked, and the section locks itself again after two seconds. If it cannot connect it says *Saved, but could not connect to host:port. Check the broker settings.* The working connection is kept until you fix the values.
:::

# Finishing up {#finishing}

::: step Handing the handheld over
shot: 39_logout_dialog.png
caption: Log out asks first.
alt: Log out? dialog reading "You'll need to log in again to continue." with Cancel and a red Log out button
1. Check that no collection is still queued (no amber *awaiting delivery* banner on Waste Collection).
2. Tap your name in the header (or **Log out** in Settings).
3. Confirm **Log out**.

Always log out before you pass the handheld to someone else. Otherwise their bags are recorded under your name.
:::

::: step Closing the app
shot: 40_exit_dialog.png
caption: Back from Log In or the home screen.
alt: Close the app? dialog reading "You'll leave PPNAM Station 4 and return to the home screen." with Stay and Close
From the Log In or home screen, the **Back** key asks *Close the app?*. Tap **Close** to return to the Launcher, or **Stay**. On Log In, if the keyboard is open, the first Back only closes the keyboard.

At the end of the shift, log out and put the handheld on its charger.
:::

# Troubleshooting {#troubleshooting}

Table: Common problems
| Problem | Likely cause | What to do |
|---|---|---|
| The pill stays amber **Station 4 offline**. | The Station 4 PC is off, or its program is not running. | Keep registering collections; they queue. Tell your supervisor. Weighs wait until the station is back. |
| The pill stays **Reconnecting** or red **Offline**. | No Wi-Fi where you are, or wrong broker settings. | Move into coverage. If it stays, your supervisor checks Settings. |
| The category list shows only *Uncategorised*. | The handheld has never fetched the real catalogue. | Settings, **Refresh catalogue** while signed in. Do this once when a handheld is issued. |
| Step 3 keeps failing. | The operator look-up needs Station 4 online. | Wait for **Connected**, then scan the card again. |
| A weigh says *This bag is not waiting to be weighed*. | No collection, or it is still queued. | Check the Waste Collection banners. Register the bag or let the queue deliver, then weigh. |
| *… from a previous sign-in was not delivered* | Collections were queued when someone logged out. | Register those bags again. |
| No scan sounds. | Media volume is down. | Turn up the media volume. The vibration still works. |
| Scans do nothing. | The screen does not take scans (home, steps 4 and 5, review), or the app is not in front. | Go to the step that asks for a scan. |
| Signed out unexpectedly. | 15 minutes without a touch or scan, or Station 4 ended the session. | Sign in again. |

> [!NOTE] Reporting a fault
> Give your supervisor the **Device ID** and **Version** from Settings, the time, the bag code, and the exact message on screen.
