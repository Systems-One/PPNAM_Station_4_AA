package com.mitas.ppnam.station4aa.data.mqtt.dto

/**
 * Login-exchange messages, ported from Station 2 AA's AuthMessages.kt — see `MqttTopics`' class
 * doc for why this mirrors a contract Station 4's own backend doesn't yet implement. Station 2's
 * `ManagerAction`/manager-authorization variant of this exchange is intentionally not ported:
 * Station 4 has no privileged in-workflow actions that need it.
 *
 * Message-specific fields only; the transport injects the envelope (see RequestEnvelope).
 */

/** Purposes for a SCRAM exchange (`purpose` on both start and proof). Only LOGIN is used here —
 * MANAGER_ACTION is kept for shape-parity with the mirrored contract, not because Station 4 has a
 * manager-action flow. */
object ScramPurpose {
    const val LOGIN = "login"
    const val MANAGER_ACTION = "manager_action"
}

/** `scram_start_requested`. */
data class ScramStartPayload(
    val username: String,
    val clientNonce: String,
    val purpose: String,
    val actionTarget: String = "",
    val managerAction: String = "",
)

/** `scram_challenge`. [serverNonce] is the *combined* nonce and must begin with the client nonce
 * sent; [serverFirstMessage] is reproduced verbatim into the AuthMessage — any difference in
 * spelling breaks the proof. */
data class ScramChallengeResponse(
    val challengeId: String = "",
    val serverNonce: String = "",
    val salt: String = "",
    val iterations: Int = 0,
    val serverFirstMessage: String = "",
    val expiresAtUtc: String? = null,
)

/** `scram_proof_requested`. */
data class ScramProofPayload(
    val challengeId: String,
    val clientFinalWithoutProof: String,
    val clientProof: String,
    val purpose: String,
    val actionTarget: String = "",
    val managerAction: String = "",
)

/** Response to `scram_proof_requested`. [serverSignature] must be validated (see ScramCrypto)
 * before the session it carries is trusted. */
data class ScramProofResponse(
    val serverSignature: String = "",
    val operatorSessionId: String = "",
    val operatorId: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val role: String? = null,
    val allowedActions: List<String> = emptyList(),
    val allowedTabs: List<String> = emptyList(),
    val sessionState: String? = null,
    val sessionExpiresAtUtc: String? = null,
)

/** Badge login carries no secret. */
data class BadgeLoginPayload(
    val badgeTag: String,
)

/** Response to `login_requested` (badge) and `reader_logout_requested`. Reads
 * `operatorSessionId`, which is normally an envelope field rather than a body field — login is
 * where the session is issued, so this is the one deliberate overlap; Gson parses envelope and
 * body from the same flat JSON object regardless. */
data class OperatorContextResponse(
    val operatorSessionId: String = "",
    val operatorId: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val role: String? = null,
    val allowedActions: List<String> = emptyList(),
    val allowedTabs: List<String> = emptyList(),
    val sessionState: String? = null,
    val sessionExpiresAtUtc: String? = null,
)

/** One row of the pre-login operator directory (contract 5.3.0; Station 1 3.2.0 §4.5). Exactly
 * these two properties — the list confers nothing and must not hint at permissions. Also the
 * on-device cache's row shape (see `OperatorDirectoryCodec`). */
data class OperatorEntryDto(
    val username: String = "",
    val displayName: String = "",
)

/** Response to `operator_list_requested` on `res/operator_list`. The envelope's `accepted` decides
 * Accepted/Rejected as usual; `nextAction` is always `"login"` and is not parsed. An empty
 * [operators] with `accepted: true` is valid ("no password operators provisioned"). */
data class OperatorListResponse(
    val operators: List<OperatorEntryDto> = emptyList(),
)
