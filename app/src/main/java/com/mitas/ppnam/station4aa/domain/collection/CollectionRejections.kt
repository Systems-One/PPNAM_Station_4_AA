package com.mitas.ppnam.station4aa.domain.collection

/** One `waste_collection_result` rejection, phrased for the operator. */
data class CollectionRejection(val message: String, val requiresLogin: Boolean)

/** The operator-facing table for collection rejections — the collection-side twin of
 * `CaptureRefusals`. The station's free-text `reason` and `nextAction` are never shown. */
object CollectionRejections {
    fun describe(bagCode: String, errorCode: String?): CollectionRejection = when (errorCode) {
        "bag_code_in_use" -> CollectionRejection(
            "Bag $bagCode is already waiting to be weighed. Weigh it before registering it again.",
            requiresLogin = false,
        )
        "operator_session_invalid" -> CollectionRejection(
            "Your session ended before bag $bagCode was delivered. Sign in again and register it again.",
            requiresLogin = true,
        )
        "invalid_payload", "validation_failed", "required_field_missing" -> CollectionRejection(
            "Station 4 could not read the collection for bag $bagCode. Register it again.",
            requiresLogin = false,
        )
        null, "" -> CollectionRejection("Station 4 rejected bag $bagCode. Register it again.", requiresLogin = false)
        else -> CollectionRejection("Station 4 rejected bag $bagCode ($errorCode). Ask a manager.", requiresLogin = false)
    }
}
