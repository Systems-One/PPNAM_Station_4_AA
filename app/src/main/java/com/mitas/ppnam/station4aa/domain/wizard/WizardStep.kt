package com.mitas.ppnam.station4aa.domain.wizard

/** The six states of the Phase 1 wastage-bag wizard, in the order the process document defines —
 * see `docs/superpowers/specs/2026-09-02-phase-1-wastage-bag-flow-design.md`. */
enum class WizardStep { SCAN_BAG, SCAN_JOB, SCAN_OPERATOR, SELECT_CATEGORY, SELECT_WASTE_TYPE, REVIEW }
