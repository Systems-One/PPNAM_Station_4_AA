package com.mitas.ppnam.station4aa.domain.model

/**
 * Stand-in for the active `wasteTypeCode` values the contract requires resolving against Station
 * 4's own waste-type list ("It MUST resolve to an active Station 4 waste type when consumed" —
 * `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`, field definitions).
 *
 * No synced waste-type directory is available to this app yet, so this is a small hardcoded
 * placeholder list — `WT-01` matches the contract's own worked example so at least that one code
 * is known-real; the rest are invented pending a real catalogue source. Station 4 quarantines any
 * `wasteTypeCode` it doesn't recognize as active (`waste_type_inactive_or_unknown`), so publishing
 * against this placeholder list against a real deployment will need it reconciled with Station 4's
 * actual waste types first.
 */
enum class WasteTypeCatalog(val code: String, val display: String) {
    GENERAL("WT-01", "General"),
    RECYCLABLE("WT-02", "Recyclable"),
    ORGANIC("WT-03", "Organic"),
    HAZARDOUS("WT-04", "Hazardous"),
}
