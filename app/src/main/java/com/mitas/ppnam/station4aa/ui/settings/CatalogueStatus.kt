package com.mitas.ppnam.station4aa.ui.settings

import com.mitas.ppnam.station4aa.domain.model.CatalogueMeta
import com.mitas.ppnam.station4aa.domain.model.CatalogueSource

/**
 * One honest line about where the cached catalogue came from, for Settings → Diagnostics. Pure, so
 * it is tested without Android.
 */
fun describeCatalogue(meta: CatalogueMeta?): String {
    if (meta == null) return "Catalogue: not loaded"
    val base = when (meta.source) {
        CatalogueSource.SEED -> "Catalogue: built-in seed — never synced"
        CatalogueSource.SYNCED ->
            "Catalogue: ${meta.catalogueVersion} — synced ${meta.syncedAtUtc}"
    }
    return meta.lastFailedAtUtc
        ?.let { "$base, last refresh failed $it" }
        ?: base
}
