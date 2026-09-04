package com.mitas.ppnam.station4aa.data.mqtt.dto

/**
 * Wire shape for the schema v4 payload defined in
 * `C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`.
 * Property names are the exact camelCase names the contract requires — Gson serializes Kotlin
 * property names verbatim, so these ARE the wire keys; there is no `@SerializedName` remapping.
 *
 * `schemaVersion` MUST be exactly `4` (a JSON integer, never the string `"4"`) — see
 * [com.mitas.ppnam.station4aa.domain.model.WasteCollectionEvent.SCHEMA_VERSION]. `bagCode` and
 * `collectionId` are two distinct required fields, not one merged value — see
 * `WasteCollectionEvent`'s class doc, which also explains why the waste category is deliberately
 * not on the wire.
 */
data class WasteCollectionMessage(
    val schemaVersion: Int,
    val messageId: String,
    val deviceId: String,
    val operatorSessionId: String,
    val collectionId: String,
    val bagCode: String,
    val jobNumber: String,
    val operatorId: String,
    val wasteTypeCode: String,
    val collectedBy: String,
    val collectedAtUtc: String,
)
