package com.mitas.ppnam.station4aa.domain.model

/**
 * Login-session state, ported from Station 2 AA's SessionState — see
 * `com.mitas.ppnam.station4aa.data.mqtt.MqttTopics`' class doc.
 */
enum class SessionState {
    /** Device is online and the session is in use. */
    Active,

    /** The device went offline. The session is preserved, not destroyed — any valid request
     * resumes it. */
    Suspended,

    /** Terminal: logged out, replaced by a newer login, or expired. */
    Closed;

    companion object {
        /** Degrades an unknown or absent value to [Active] rather than locking an operator out of
         * a working session over an unrecognised string. */
        fun fromWire(raw: String?): SessionState =
            entries.firstOrNull { it.name == raw } ?: Active
    }
}
