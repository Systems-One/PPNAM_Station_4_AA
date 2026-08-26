package com.mitas.ppnam.station4aa.data.mqtt.dto

/**
 * The envelope every login-exchange response carries, parsed from the same flat JSON object as
 * the message-specific body. Trimmed from Station 2 AA's ResponseEnvelope to the fields the login
 * flow actually uses — Station 2's version also carries mixing/ingredient-workflow fields
 * (`fieldErrors`, `nextAction`, `exceptionId`, ...) that have no Station 4 equivalent.
 *
 * Every constructor parameter must keep a default value: Kotlin only emits the no-arg constructor
 * Gson needs when every parameter has one, and dropping a default makes Gson fall back to
 * `UnsafeAllocator`, silently deserializing every field to null regardless of its declared
 * non-null type — with no compile error.
 */
data class ResponseEnvelope(
    val messageId: String = "",
    val inResponseToMessageId: String = "",
    val schemaVersion: String = "",
    val deviceId: String = "",
    val operatorSessionId: String? = null,
    val timestampUtc: String = "",
    val accepted: Boolean = false,
    /** Rollout-era mirror of [errorMessage]; fallback only, not the source of truth. */
    val reason: String? = null,
    /** Canonical human-readable failure text. Prefer this over [reason]. */
    val errorMessage: String? = null,
    /** Stable lowercase symbolic code. */
    val errorCode: String? = null,
) {
    /** The failure text to show a human, preferring [errorMessage] with [reason] as fallback.
     * Blank is treated as absent. */
    val displayMessage: String?
        get() = errorMessage?.takeIf { it.isNotBlank() } ?: reason?.takeIf { it.isNotBlank() }
}
