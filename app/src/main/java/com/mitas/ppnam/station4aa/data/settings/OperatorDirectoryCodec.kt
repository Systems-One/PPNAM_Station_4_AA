package com.mitas.ppnam.station4aa.data.settings

import com.mitas.ppnam.station4aa.data.mqtt.WireJson
import com.mitas.ppnam.station4aa.data.mqtt.dto.OperatorEntryDto
import com.mitas.ppnam.station4aa.domain.model.OperatorEntry

/**
 * JSON array codec for the on-device operator directory cache — the same `[ {"username",
 * "displayName"} ]` shape the wire uses, so the cache file reads like the response it came from
 * (Station 1's `OperatorListCodec`). Pure: no Android, no prefs, so it is unit-tested directly.
 *
 * [decode] is tolerant by design. The cache is a convenience, never a source of truth, so a
 * corrupt or hand-edited file yields an empty dropdown rather than a crash on the login screen.
 */
object OperatorDirectoryCodec {

    fun encode(entries: List<OperatorEntry>): String =
        WireJson.gson.toJson(entries.map { OperatorEntryDto(username = it.username, displayName = it.displayName) })

    fun decode(text: String?): List<OperatorEntry> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val dtos = WireJson.gson.fromJson(text, Array<OperatorEntryDto>::class.java) ?: return emptyList()
            OperatorEntry.normalize(dtos.map { it?.toEntry() })
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/** Raw projection; blank-username filtering and display-name fallback happen in
 * [OperatorEntry.normalize], not here. */
internal fun OperatorEntryDto.toEntry(): OperatorEntry = OperatorEntry(username = username, displayName = displayName)
