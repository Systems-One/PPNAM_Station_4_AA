package com.ppnam.station4aa.domain.validation

/**
 * Pre-publish gating the contract puts on the handheld itself (not just on Station 4's
 * consumer-side quarantine rules), from
 * `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`:
 *
 * "The handheld MUST NOT publish if the machine-operator ID is missing, blank, cancelled, or
 * invalid under the handheld's configured identity rules. It MUST NOT use configured placeholder
 * or sentinel values; examples include `UNKNOWN` and `N/A`." / "Every required string MUST be
 * non-null and non-blank after trimming leading and trailing whitespace. Control characters are
 * prohibited."
 *
 * Returns `null` for a valid value, or an operator-facing message explaining why it was rejected.
 * This is a courtesy gate so a bad transaction never reaches the outbox at all — Station 4 still
 * re-validates and quarantines independently; this is not a substitute for that.
 */
object WasteCollectionValidator {

    // The contract gives these two as examples, not an exhaustive enumeration ("examples
    // include"). Kept short deliberately: a longer denylist invites false positives on a
    // legitimate ID that happens to collide with a word on the list.
    private val PLACEHOLDER_VALUES = setOf("UNKNOWN", "N/A", "NA", "NONE")

    private const val COLLECTED_BY_MAX_LENGTH = 200
    private const val MACHINE_OPERATOR_ID_MAX_LENGTH = 100
    private const val MACHINE_CODE_MAX_LENGTH = 100
    private const val BAG_CODE_MAX_LENGTH = 100

    /** `machineOperatorUserId`: entered/scanned fresh for every transaction, so placeholder
     * values are checked — a scanner default or a bored double-tap must not slip through. */
    fun validateMachineOperatorUserId(raw: String): String? =
        validateRequiredIdentity(raw, MACHINE_OPERATOR_ID_MAX_LENGTH, rejectPlaceholders = true)

    /** `collectedBy`: the existing wastage-operator value the handheld already holds. Same
     * blank/length/control-character rules; not placeholder-checked since it isn't freshly typed
     * per transaction the way the machine-operator ID is. */
    fun validateCollectedBy(raw: String): String? =
        validateRequiredIdentity(raw, COLLECTED_BY_MAX_LENGTH, rejectPlaceholders = false)

    /** `machineCode`: scanned fresh at the start of every transaction. Not placeholder-checked —
     * unlike `machineOperatorUserId` it isn't a freely typed identity field under the handheld's
     * identity rules, it's whatever a real machine's printed barcode contains. */
    fun validateMachineCode(raw: String): String? =
        validateRequiredIdentity(raw, MACHINE_CODE_MAX_LENGTH, rejectPlaceholders = false)

    /** `bagCode`: scanned fresh for every transaction. Not placeholder-checked — Station4's own
     * server-side `WastageBagCodePolicy.TryNormalize` only rejects blank/over-length/control-
     * character bag codes, never placeholder values, so client-side rejection here would only
     * create false positives against a legitimately configured code. */
    fun validateBagCode(raw: String): String? =
        validateRequiredIdentity(raw, BAG_CODE_MAX_LENGTH, rejectPlaceholders = false)

    private fun validateRequiredIdentity(
        raw: String,
        maxLength: Int,
        rejectPlaceholders: Boolean,
    ): String? {
        val trimmed = raw.trim()
        return when {
            trimmed.isBlank() -> "Required."
            trimmed.any { it.isISOControl() } -> "Must not contain control characters."
            trimmed.length > maxLength -> "Must be $maxLength characters or fewer."
            rejectPlaceholders && trimmed.uppercase() in PLACEHOLDER_VALUES ->
                "Enter a real ID — \"$trimmed\" looks like a placeholder value."
            else -> null
        }
    }
}
