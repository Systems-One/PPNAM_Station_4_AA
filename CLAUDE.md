# Repo Rules

## External directory: C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4

This is the sibling WPF/Core/CLI repo for PPNAM Station 4 (not this Android app). It is **read-only**
reference material — never edit or write to any file under it. It moved here from the old
`C:\Dev\PPNAM-Station-4` path; that path no longer exists. The normative MQTT wire contract this
Android app implements lives at
`C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`, currently
**v3.2.0**. The fleet-wide topic authority is `C:\Dev\Clients\PPNAM\MQTT_TOPIC_STRUCTURE.md`.

This app publishes **schema v4** (`schemaVersion: 4` as a JSON integer — see
`data/mqtt/dto/WasteCollectionMessage.kt`), matching the contract. Re-verify the contract's rollout
order before pointing a handheld at a live broker: the contract requires Station 4's migrations,
category allocation, result-topic ACLs and app upgrade to be complete first, and forbids leaving an
old scanner publishing an unversioned/v2/v3 payload after cutover.

## The collection topic is fixed here, deliberately against the contract

Contract v3.2.0 calls the collection topic "Settings-configured" (lines 11/408/882) and constrains
it to the `PPNAM/station_4/waste` subtree (line 152). This handheld **deviates on purpose**: it is
Station 4's scanner and nothing else, so the topic is the constant `MqttTopics.WASTE_COLLECTION`
(`PPNAM/station_4/waste/collection`), not a Settings field. It is shown read-only in
Settings → Diagnostics as "PUBLISHES TO" so support can reconcile it against the broker ACL.

This satisfies line 152 by construction and removes the only way a typo in Settings could point a
scanner outside its own namespace. The cost: if a deployment ever moves the topic, this app needs a
rebuild. Do not reintroduce the field without asking — its removal was an explicit user decision.
Every other topic (login `req`/`res`, catalogue `req`/`res`, station and device presence) has always
been derived in `data/mqtt/MqttTopics.kt` and was never configurable. The broker
(host/port/WebSocket/TLS/credentials) remains operator-editable, because the contract genuinely does
call it "deployment-configured".

## Operator login is mirrored from Station 2 AA — and Station 4's backend has now caught up

`Station4_Wastage_MQTT_Contract.md` originally defined no login/auth mechanism, so at the user's
explicit request the handheld's login (SCRAM-SHA-256 challenge/proof + RFID badge, request/response
on `PPNAM/station_4/{deviceId}/req|res/...`) was ported from `C:\Dev\PPNAM_Station_2_AA`'s real,
working login mechanism — see `data/mqtt/MqttTopics.kt`'s class doc.

That port is **no longer speculative**. The Station 4 repo now ships
`PPNAM.Station4.Core/Services/MqttScramAuthenticationService.cs` and `MqttAuthenticationProcessor.cs`,
which answer `scram_start_requested` / `scram_proof_requested` and reply on
`PPNAM/station_4/{deviceId}/res/scram_challenge`, `.../res/scram_proof_result` and
`.../res/operator_context` — the same topics this app uses. `MqttCatalogueProcessor.cs` is likewise
present for the waste catalogue. Before assuming any of it is wired up end to end, check the
processors against `MqttRequestChannel.kt` and the contract rather than trusting this paragraph.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
