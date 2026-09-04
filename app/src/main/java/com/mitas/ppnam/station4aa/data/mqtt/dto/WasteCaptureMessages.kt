package com.mitas.ppnam.station4aa.data.mqtt.dto

/**
 * Wire shapes for the `waste_capture_requested` / `waste_capture_result` exchange on
 * `PPNAM/station_4/{deviceId}/req|res/...` — contract 5.1.0 §9.2, the handheld-triggered weigh.
 * It rides the same schema 4.1 envelope every other request on that channel uses, so
 * `RequestEnvelope` supplies messageId/schemaVersion/deviceId/operatorSessionId/timestampUtc and
 * this payload adds the one workflow field.
 *
 * Gson serializes Kotlin property names verbatim, so these property names ARE the wire keys.
 * Every parameter keeps a default value — without that, Gson falls back to UnsafeAllocator and
 * silently deserializes every field to null regardless of declared non-null types.
 */
data class WasteCapturePayload(
    val operatorSessionId: String = "",
    val bagCode: String = "",
)

/**
 * §9.2's response. On refusal `collectionId`, `weightKg`, `capturedAtUtc` and `capturedBy` are
 * **omitted** — the contract is explicit that they are "never sent as null" — which is why they
 * are nullable here and why an `accepted: true` carrying no [weightKg] is treated as malformed
 * rather than as a zero-kilogram weigh.
 *
 * [weightKg] is a JSON number of at most three decimal places. It is deliberately [Double] and not
 * a formatted string: the contract fixes the serialization on the station side, and the operator
 * only ever sees it rendered.
 */
data class WasteCaptureResultMessage(
    val messageId: String = "",
    val inResponseToMessageId: String = "",
    val schemaVersion: String = "",
    val deviceId: String = "",
    val timestampUtc: String = "",
    val accepted: Boolean = false,
    val bagCode: String = "",
    val collectionId: String? = null,
    val weightKg: Double? = null,
    val capturedAtUtc: String? = null,
    val capturedBy: String? = null,
    val errorCode: String? = null,
    val reason: String? = null,
    val nextAction: String? = null,
)
