package com.mitas.ppnam.station4aa.domain.model

/** Where the currently cached catalogue came from. */
enum class CatalogueSource { SEED, SYNCED }

/**
 * Provenance of the cached catalogue, surfaced in Settings → Diagnostics. Without this, a handheld
 * quietly running the built-in seed against a real station is indistinguishable from a correctly
 * synced one.
 *
 * [catalogueVersion] is opaque to this app: it is stored and displayed so a support call can
 * compare it against the station, and carries no ordering or comparison semantics here.
 */
data class CatalogueMeta(
    val catalogueVersion: String,
    val syncedAtUtc: String?,
    val source: CatalogueSource,
    val lastFailedAtUtc: String?,
)
