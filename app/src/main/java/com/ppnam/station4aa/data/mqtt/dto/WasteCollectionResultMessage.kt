package com.ppnam.station4aa.data.mqtt.dto

/**
 * Wire shape of the direct application-level response to a schema v3 collection publish, per
 * `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md` §12 — confirmed against the real
 * `PPNAM.Station4.Core/Models/Station4Models.cs`'s `WastageCollectionResultMessage`. Published by
 * Station 4 on `PPNAM/station4/{deviceId}/res/waste_collection_result` (QoS 1, retain false) for
 * every structurally valid collection request, after the accept/quarantine outcome is durable.
 *
 * This is a distinct DTO from [ResponseEnvelope] — [schemaVersion] here is the collection schema's
 * JSON integer (`3`), not the authentication envelope's version string (`"4.1"`), and this carries
 * fields ([collectionId], [bagCode], [isDuplicate], [collectionStatus], [nextAction]) that have no
 * `ResponseEnvelope` equivalent.
 *
 * Every constructor parameter keeps a default so Gson never falls back to `UnsafeAllocator` — see
 * [ResponseEnvelope]'s class doc for why that matters.
 */
data class WasteCollectionResultMessage(
    val schemaVersion: Int = 0,
    val messageId: String = "",
    val inResponseToMessageId: String = "",
    val deviceId: String = "",
    val operatorSessionId: String = "",
    val timestampUtc: String = "",
    val collectionId: String = "",
    val bagCode: String = "",
    val accepted: Boolean = false,
    val isDuplicate: Boolean = false,
    val collectionStatus: String = "",
    /** Omitted by Station 4 on success. */
    val errorCode: String? = null,
    /** Omitted by Station 4 on success. */
    val reason: String? = null,
    val nextAction: String = "",
)
