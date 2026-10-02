package com.mitas.ppnam.station4aa.data.session

import com.mitas.ppnam.station4aa.domain.model.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * The wastage operator's login session, ported from Station 2 AA's OperatorSessionHolder — see
 * `com.mitas.ppnam.station4aa.data.mqtt.MqttTopics`' class doc. [operatorName]/[operatorId] is what
 * populates the waste-collection contract's `collectedBy` field once logged in (see
 * `WasteGatheringViewModel`) — this replaces the free-text field / DataStore-backed
 * `CollectedByStore` that stood in for a real session before this existed.
 */
data class OperatorSession(
    val operatorSessionId: String,
    val operatorId: String,
    val operatorName: String,
    /** Display and audit only — nothing here gates on role. */
    val role: String,
    val sessionState: SessionState = SessionState.Active,
    val sessionExpiresAtUtc: Instant? = null,
)

/** In-memory only, like Station 2's — rebuilt from a fresh login/badge scan on process restart.
 * No password or session token is ever persisted to disk. [signedOutReason] is the one-shot
 * sentence Login shows when a session was dropped for the operator (audit static-05). */
class OperatorSessionHolder {
    private val _session = MutableStateFlow<OperatorSession?>(null)
    val session: StateFlow<OperatorSession?> = _session.asStateFlow()

    private val _signedOutReason = MutableStateFlow<String?>(null)
    val signedOutReason: StateFlow<String?> = _signedOutReason.asStateFlow()

    fun set(session: OperatorSession) {
        _signedOutReason.value = null
        _session.value = session
    }

    /** [reason] is null for a deliberate Log out (nothing to explain). */
    fun clear(reason: String? = null) {
        _signedOutReason.value = reason
        _session.value = null
    }

    /** Login reads the reason once and clears it so it does not reappear on the next visit. */
    fun consumeSignedOutReason(): String? {
        val reason = _signedOutReason.value
        _signedOutReason.value = null
        return reason
    }

    fun currentSessionIdOrEmpty(): String = _session.value?.operatorSessionId ?: ""
}
