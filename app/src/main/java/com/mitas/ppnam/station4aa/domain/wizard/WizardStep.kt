package com.mitas.ppnam.station4aa.domain.wizard

/** The five states of the scan-driven waste collection wizard — see
 * `docs/superpowers/specs/2026-08-05-scan-driven-waste-wizard-design.md`. */
enum class WizardStep { SCAN_MACHINE, SCAN_OPERATOR, SELECT_WASTE_TYPE, SCAN_BAG, REVIEW }
