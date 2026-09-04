# Repo Rules

## External directory: C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4

This is the sibling WPF/Core/CLI repo for PPNAM Station 4 (not this Android app). It is **read-only**
reference material — never edit or write to any file under it. It moved here from the old
`C:\Dev\PPNAM-Station-4` path; that path no longer exists. The normative MQTT wire contract this
Android app implements lives at
`C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`, currently
**5.1.1**. Two documents sit above it: the fleet-wide topic authority
`C:\Dev\Clients\PPNAM\MQTT_TOPIC_STRUCTURE.md`, and the Android base standard
`C:\Dev\Clients\PPNAM\Andriod\MQTT_BASE_README.md`, which every `PPNAM_Station_{1..5}_AA` app
follows and which contract 5.0.0 aligned Station 4 with. On a topic-hierarchy conflict the fleet
document wins.

This app publishes **schema v4** (`schemaVersion: 4` as a JSON integer — see
`data/mqtt/dto/WasteCollectionMessage.kt`), matching the contract. Re-verify the contract's rollout
order before pointing a handheld at a live broker: the contract requires Station 4's migrations,
category allocation, result-topic ACLs and app upgrade to be complete first, and forbids leaving an
old scanner publishing an unversioned/v2/v3 payload after cutover.

## No topic is configurable, at either end

Contract 5.0.0 made the waste collection an ordinary scanner request on
`PPNAM/station_4/{deviceId}/req/waste_collection_requested`, answered on the existing
`res/waste_collection_result`, and **deleted Station 4's own "Collection topic" Settings field**.
The base standard fixes the hierarchy, so there is nothing left to configure: §17.45 now requires
that "Settings exposes no topic to configure", which this app already satisfied.

That release also **retired the `waste` reserved segment** — `res` is the only name reserved
directly under the station node — so `waste` is an ordinary derived device id again and
`MqttTopics.validateSegment` must not refuse it.

Every topic is derived in `data/mqtt/MqttTopics.kt` from the on-device `deviceId`. The collection
topic is shown read-only in Settings → Diagnostics as "PUBLISHES TO" so support can reconcile it
against the broker ACL. Do not reintroduce a topic field. The broker
(host/port/WebSocket/TLS/credentials) remains operator-editable, because the contract genuinely does
call it "deployment-configured".

The topic device segment and the payload `deviceId` **must match exactly** — Station 4 refuses a
mismatch *without publishing a reply*, because the reply is addressed by the topic's id, so a
divergence strands the handheld waiting for a result that can never arrive.
`WasteCollectionPublisher` derives the topic from the event it is publishing, which makes this true
by construction including for outbox replays.

## Handheld-triggered weight capture (contract 5.1.0 §9.2)

The handheld can ask Station 4 to weigh a bag on its scale — `waste_capture_requested` /
`waste_capture_result`, keyed only by `bagCode` — **with the station PC signed out**. Station 4 does
the measuring (it is the only participant wired to the scale) and attributes the capture to this
handheld's own session. See `ui/weigh/`, `domain/usecase/RequestWasteCaptureUseCase.kt` and the
refusal table in `domain/capture/CaptureRefusal.kt`.

Two rules that are easy to get wrong:

- This is **not** queued in a durable outbox, unlike a collection. A weigh only means anything
  while the bag is physically on the pan. `MqttRequestChannel` mints a fresh `messageId` per call,
  which is exactly what §9.2 demands after `bag_code_unknown` ("a handheld that retries MUST
  generate a new messageId").
- Only `scale_busy`, `scale_no_load` and `capture_not_confirmed` keep the scanned bag code for a
  retry. Those three are the refusals Station 4 deliberately does **not** store; every other code,
  `bag_code_unknown` included, is terminal for that scan.

The app cannot gate on the Capture Waste permission locally — the session carries no `allowedTabs`
— and is not meant to: the station checks the permission before the pan moves and answers
`capture_not_permitted`.

## Operator login is mirrored from Station 2 AA — and Station 4's backend has now caught up

`Station4_Wastage_MQTT_Contract.md` originally defined no login/auth mechanism, so at the user's
explicit request the handheld's login (SCRAM-SHA-256 challenge/proof + RFID badge, request/response
on `PPNAM/station_4/{deviceId}/req|res/...`) was ported from `C:\Dev\PPNAM_Station_2_AA`'s real,
working login mechanism — see `data/mqtt/MqttTopics.kt`'s class doc.

That port is **no longer speculative**, and the base standard has since made it the fleet norm: SCRAM
schema 4.1 is `MQTT_BASE_README.md` §5, shared by every station app. The Station 4 repo ships
`PPNAM.Station4.Core/Services/MqttScramAuthenticationService.cs` and `MqttAuthenticationProcessor.cs`,
which answer `scram_start_requested` / `scram_proof_requested` and reply on
`PPNAM/station_4/{deviceId}/res/scram_challenge`, `.../res/scram_proof_result` and
`.../res/operator_context` — the same topics this app uses. `MqttCatalogueProcessor.cs` is likewise
present for the waste catalogue, and a capture processor for §9.2. Before assuming any of it is
wired up end to end, check the processors against `MqttRequestChannel.kt` and the contract rather
than trusting this paragraph.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
