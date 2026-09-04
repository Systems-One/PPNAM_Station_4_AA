package com.mitas.ppnam.station4aa.data.mqtt.dto

/**
 * Wire shapes for the `waste_catalogue_requested` / `waste_catalogue` exchange on
 * `PPNAM/station_4/{deviceId}/req|res/...`, using the same schema 4.1 auth envelope every other
 * request on that channel uses (RequestEnvelope adds messageId/schemaVersion/deviceId/timestampUtc).
 *
 * Gson serializes Kotlin property names verbatim, so these property names ARE the wire keys.
 * Every parameter keeps a default value — without that, Gson falls back to UnsafeAllocator and
 * silently deserializes every field to null regardless of declared non-null types.
 */
data class WasteCatalogueRequestPayload(
    val operatorSessionId: String = "",
)

data class WasteCategoryDto(
    val code: String = "",
    val name: String = "",
    val sortOrder: Int = 0,
)

data class WasteTypeDto(
    val code: String = "",
    val name: String = "",
    val categoryCode: String = "",
    val sortOrder: Int = 0,
)

/**
 * Station 4 sends active types only; this app renders exactly what it receives and never filters.
 * [catalogueVersion] is opaque here — stored and displayed for support, never compared or ordered.
 */
data class WasteCatalogueResponse(
    val messageId: String = "",
    val inResponseToMessageId: String = "",
    val schemaVersion: String = "",
    val deviceId: String = "",
    val timestampUtc: String = "",
    val accepted: Boolean = false,
    val catalogueVersion: String = "",
    val categories: List<WasteCategoryDto> = emptyList(),
    val wasteTypes: List<WasteTypeDto> = emptyList(),
    val errorCode: String? = null,
    val reason: String? = null,
)
