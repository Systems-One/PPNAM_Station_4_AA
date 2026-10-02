package com.mitas.ppnam.station4aa.ui.settings

import com.mitas.ppnam.station4aa.domain.model.CatalogueMeta
import com.mitas.ppnam.station4aa.domain.model.CatalogueSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// Locale.ENGLISH, not UK: newer CLDR data abbreviates September as "Sept" for en-GB, which
// would differ between the JVM and the device. The pattern already fixes 24-hour time.
private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

/** "2026-10-01T16:12:37.942434Z" → "1 Oct 2026, 18:12" in [zone]; unparseable input is returned
 * untouched so a surprising station value is still visible to support. */
internal fun formatTimestampForDisplay(isoUtc: String, zone: ZoneId): String = try {
    DISPLAY_FORMAT.withZone(zone).format(Instant.parse(isoUtc))
} catch (e: DateTimeParseException) {
    isoUtc
}

/**
 * One honest line about where the cached catalogue came from, for Settings → Diagnostics. Pure, so
 * it is tested without Android; [zone] defaults to the device zone and is injected by tests.
 */
fun describeCatalogue(meta: CatalogueMeta?, zone: ZoneId = ZoneId.systemDefault()): String {
    if (meta == null) return "Catalogue: not loaded"
    val base = when (meta.source) {
        CatalogueSource.SEED -> "Catalogue: built-in seed — never synced"
        CatalogueSource.SYNCED ->
            "Catalogue: ${meta.catalogueVersion} — synced ${meta.syncedAtUtc?.let { formatTimestampForDisplay(it, zone) }}"
    }
    return meta.lastFailedAtUtc
        ?.let { "$base, last refresh failed ${formatTimestampForDisplay(it, zone)}" }
        ?: base
}
