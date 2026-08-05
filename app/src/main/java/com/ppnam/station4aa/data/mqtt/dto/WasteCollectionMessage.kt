package com.ppnam.station4aa.data.mqtt.dto

/**
 * Wire shape for the schema v2 payload defined in
 * `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`. Property names are the exact
 * camelCase names the contract requires — Gson serializes Kotlin property names verbatim, so
 * these ARE the wire keys; there is no `@SerializedName` remapping.
 *
 * `schemaVersion` MUST be exactly `2` (a JSON integer, never the string `"2"`) — see
 * [com.ppnam.station4aa.domain.model.WasteCollectionEvent.SCHEMA_VERSION].
 */
data class WasteCollectionMessage(
    val schemaVersion: Int,
    val messageId: String,
    val collectionId: String,
    val machineCode: String,
    val machineName: String,
    val wasteTypeCode: String,
    val collectedBy: String,
    val machineOperatorUserId: String,
    val collectedAtUtc: String,
    val bagCode: String,
)
