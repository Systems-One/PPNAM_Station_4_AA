package com.mitas.ppnam.station4aa.data.mqtt.dto

/**
 * Wire shapes for the `waste_operator_requested` / `waste_operator_result` exchange on
 * `PPNAM/station_4/{deviceId}/req|res/...` — contract 5.4.0 §9.3, the production-operator name
 * lookup. It rides the same schema 4.1 envelope as the weigh, so `RequestEnvelope` supplies
 * messageId/schemaVersion/deviceId/operatorSessionId/timestampUtc and this payload adds the two
 * workflow fields.
 *
 * [operatorId] is the trimmed card scan (badge tag/EPC or printed numeric `card_code`) as a JSON
 * string — leading zeroes preserved — never a person's name and never a login operator UUID.
 *
 * Gson serializes Kotlin property names verbatim, so these property names ARE the wire keys.
 * Every parameter keeps a default value — without that, Gson falls back to UnsafeAllocator and
 * silently deserializes every field to null regardless of declared non-null types.
 */
data class WasteOperatorPayload(
    val bagCode: String = "",
    val operatorId: String = "",
)

/**
 * §9.3's response. Early envelope/session/input refusals may echo an empty [bagCode] and
 * [operatorId]; the channel has already correlated the reply by `inResponseToMessageId`, so those
 * are handled on their error code alone.
 */
data class WasteOperatorResultMessage(
    val messageId: String = "",
    val inResponseToMessageId: String = "",
    val schemaVersion: String = "",
    val deviceId: String = "",
    val operatorSessionId: String? = null,
    val timestampUtc: String = "",
    val accepted: Boolean = false,
    val bagCode: String = "",
    val operatorId: String = "",
    val operatorName: String? = null,
    val errorCode: String? = null,
    val reason: String? = null,
    val nextAction: String? = null,
)
