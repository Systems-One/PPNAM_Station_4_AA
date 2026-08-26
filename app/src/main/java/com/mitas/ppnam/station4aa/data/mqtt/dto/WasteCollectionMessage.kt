package com.mitas.ppnam.station4aa.data.mqtt.dto

/**
 * Wire shape for the schema v3 payload defined in
 * `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md` (§1 intro/identity table and the
 * actual Station4 validator — see that repo's `PPNAM.Station4.Core/Services/MqttMessageValidator.cs`,
 * which is authoritative over the contract doc's own stale §9 body left over from the v2→v3 bump).
 * Property names are the exact camelCase names the contract requires — Gson serializes Kotlin
 * property names verbatim, so these ARE the wire keys; there is no `@SerializedName` remapping.
 *
 * `schemaVersion` MUST be exactly `3` (a JSON integer, never the string `"3"`) — see
 * [com.mitas.ppnam.station4aa.domain.model.WasteCollectionEvent.SCHEMA_VERSION]. `bagCode` and
 * `collectionId` are two distinct required fields, not one merged value — see
 * `WasteCollectionEvent`'s class doc.
 */
data class WasteCollectionMessage(
    val schemaVersion: Int,
    val messageId: String,
    val deviceId: String,
    val operatorSessionId: String,
    val collectionId: String,
    val bagCode: String,
    val machineCode: String,
    val machineName: String,
    val machineOperatorUserId: String,
    val wasteTypeCode: String,
    val collectedBy: String,
    val collectedAtUtc: String,
)
