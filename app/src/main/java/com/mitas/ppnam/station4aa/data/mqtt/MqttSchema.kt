package com.mitas.ppnam.station4aa.data.mqtt

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * Envelope schema for the mirrored login request/response exchange only — see `MqttTopics`'
 * class doc. Unrelated to `WasteCollectionEvent.SCHEMA_VERSION` (the real, contract-defined
 * `schemaVersion: 2` on the waste-collection publish topic); this "4.1" is Station 2's own
 * contract version, carried over because the login mechanism is being replicated as-is.
 */
object MqttSchema {
    const val VERSION = "4.1"

    /** RFC 3339 UTC with exactly six fractional digits and a literal `Z`, matching Station 2's
     * contract format for this envelope. `Instant.toString()` does not satisfy this — it varies
     * fractional-digit count and drops it entirely on a whole second. */
    private val RFC3339_MICROS: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
        .appendLiteral('Z')
        .toFormatter()
        .withZone(ZoneOffset.UTC)

    fun formatTimestamp(instant: Instant): String = RFC3339_MICROS.format(instant)
}
