package com.mitas.ppnam.station4aa.data.mqtt

/**
 * The result of one login-exchange request/response round trip. Trimmed from Station 2 AA's
 * MqttOutcome (which also carries `nextAction`/`fieldErrors`/`exceptionId` for its much larger
 * multi-workflow message catalog — none of that applies to a login-only exchange).
 *
 * `Accepted` means the server processed the request, not necessarily that it was favourable — not
 * meaningfully different here since login either succeeds or is `Rejected`, but kept for shape
 * parity with the mirrored contract.
 */
sealed interface MqttOutcome<out T> {
    data class Accepted<T>(val body: T) : MqttOutcome<T>
    data class Rejected<T>(val body: T?, val errorCode: String?, val reason: String?) : MqttOutcome<T>
    data class NoResponse(val kind: FailureKind) : MqttOutcome<Nothing>
}

enum class FailureKind {
    /** Published, but no matching response arrived within the timeout. */
    Timeout,
    /** Not connected to the broker, or the publish itself failed. */
    NotConnected,
    /** A response arrived but could not be parsed. */
    MalformedResponse,
}

internal fun FailureKind.describe(): String = when (this) {
    FailureKind.NotConnected -> "Not connected to the broker"
    FailureKind.Timeout -> "No response received"
    FailureKind.MalformedResponse -> "Received an unreadable response"
}
