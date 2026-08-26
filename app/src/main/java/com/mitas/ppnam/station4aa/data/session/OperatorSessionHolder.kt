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
 * No password or session token is ever persisted to disk. */
class OperatorSessionHolder {
    private val _session = MutableStateFlow<OperatorSession?>(null)
    val session: StateFlow<OperatorSession?> = _session.asStateFlow()

    fun set(session: OperatorSession) {
        _session.value = session
    }

    fun clear() {
        _session.value = null
    }

    fun currentSessionIdOrEmpty(): String = _session.value?.operatorSessionId ?: ""
}
