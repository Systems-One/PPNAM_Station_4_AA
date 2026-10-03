package com.mitas.ppnam.station4aa.domain.model

/**
 * One row of the login screen's operator dropdown (contract 5.3.0 "Operator directory", fleet
 * parity with Station 1's 3.2.0 §4.5). Display-only: picking a row only pre-fills the username
 * that SCRAM then authenticates. Carries exactly the two properties the wire allows — no role, no
 * permissions, no badge or verifier material.
 */
data class OperatorEntry(val username: String, val displayName: String) {
    /** Dropdown row text: username first so what the operator reads matches what gets
     * authenticated; the display name is the human hint. */
    val label: String get() = "$username — $displayName"

    companion object {
        /**
         * The one normalisation every source of entries (wire response, on-device cache) goes
         * through: trims both fields, drops entries whose username is blank (nothing to log in
         * with), falls a blank display name back to the username, and sorts by display name
         * case-insensitively as the Station 1 reference does.
         */
        fun normalize(raw: List<OperatorEntry?>): List<OperatorEntry> = raw
            .mapNotNull { entry ->
                val username = entry?.username?.trim().orEmpty()
                if (username.isEmpty()) return@mapNotNull null
                val displayName = entry?.displayName?.trim().orEmpty().ifEmpty { username }
                OperatorEntry(username = username, displayName = displayName)
            }
            .sortedBy { it.displayName.lowercase() }
    }
}
