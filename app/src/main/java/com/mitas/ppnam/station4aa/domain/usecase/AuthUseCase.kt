package com.mitas.ppnam.station4aa.domain.usecase

import com.mitas.ppnam.station4aa.data.auth.ScramExchange
import com.mitas.ppnam.station4aa.data.mqtt.EmptyPayload
import com.mitas.ppnam.station4aa.data.mqtt.MqttOutcome
import com.mitas.ppnam.station4aa.data.mqtt.MqttRequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.describe
import com.mitas.ppnam.station4aa.data.mqtt.dto.BadgeLoginPayload
import com.mitas.ppnam.station4aa.data.mqtt.dto.OperatorContextResponse
import com.mitas.ppnam.station4aa.data.mqtt.dto.ScramPurpose
import com.mitas.ppnam.station4aa.data.session.OperatorSession
import com.mitas.ppnam.station4aa.data.session.OperatorSessionHolder
import com.mitas.ppnam.station4aa.domain.model.SessionState
import java.time.Instant

/** Ported from Station 2 AA's LoginMethod/AuthUseCase — see
 * `com.mitas.ppnam.station4aa.data.mqtt.MqttTopics`' class doc. */
sealed class LoginMethod {
    data class Credentials(val username: String, val password: String) : LoginMethod()
    data class Badge(val badgeTag: String) : LoginMethod()
}

class AuthUseCase(
    private val requestChannel: MqttRequestChannel,
    private val sessionHolder: OperatorSessionHolder,
    private val scramExchange: ScramExchange,
    /** The derived, immutable scanner identity (base standard §2) — every auth envelope and its
     * req/res topics carry this, never a Settings value. */
    private val deviceId: String,
) {
    /** A credentials login is a SCRAM-SHA-256 exchange, not a password on the wire. Badge login
     * carries no secret and keeps the original single `login_requested` shape. */
    suspend fun login(method: LoginMethod): Result<OperatorSession> {
        return when (method) {
            is LoginMethod.Credentials -> loginWithScram(deviceId, method)
            is LoginMethod.Badge -> loginWithBadge(deviceId, method)
        }
    }

    private suspend fun loginWithScram(deviceId: String, method: LoginMethod.Credentials): Result<OperatorSession> {
        val proof = scramExchange.authenticate(
            deviceId = deviceId,
            username = method.username,
            password = method.password,
            purpose = ScramPurpose.LOGIN,
        ).getOrElse { return Result.failure(it) }

        return buildSession(
            operatorSessionId = proof.operatorSessionId,
            operatorId = proof.operatorId,
            displayName = proof.displayName,
            role = proof.role,
            sessionState = proof.sessionState,
            sessionExpiresAtUtc = proof.sessionExpiresAtUtc,
        )
    }

    private suspend fun loginWithBadge(deviceId: String, method: LoginMethod.Badge): Result<OperatorSession> {
        val outcome = requestChannel.request(
            deviceId = deviceId,
            requestType = "login_requested",
            responseClass = OperatorContextResponse::class.java,
            payload = BadgeLoginPayload(method.badgeTag),
        )

        return when (outcome) {
            is MqttOutcome.Accepted -> outcome.body.let { response ->
                buildSession(
                    operatorSessionId = response.operatorSessionId,
                    operatorId = response.operatorId,
                    displayName = response.displayName,
                    role = response.role,
                    sessionState = response.sessionState,
                    sessionExpiresAtUtc = response.sessionExpiresAtUtc,
                )
            }
            is MqttOutcome.Rejected -> Result.failure(Exception(outcome.reason ?: "Login failed"))
            is MqttOutcome.NoResponse -> Result.failure(Exception(outcome.kind.describe()))
        }
    }

    private fun buildSession(
        operatorSessionId: String,
        operatorId: String?,
        displayName: String?,
        role: String?,
        sessionState: String?,
        sessionExpiresAtUtc: String?,
    ): Result<OperatorSession> {
        val state = SessionState.fromWire(sessionState)
        return when {
            operatorSessionId.isBlank() ->
                Result.failure(Exception("Login was accepted but no session was issued"))
            state == SessionState.Closed ->
                Result.failure(Exception("The session was closed immediately"))
            else -> {
                val session = OperatorSession(
                    operatorSessionId = operatorSessionId,
                    operatorId = operatorId.orEmpty(),
                    operatorName = displayName.orEmpty(),
                    role = role.orEmpty(),
                    sessionState = state,
                    // A bad timestamp must not fail an otherwise valid login — expiry is
                    // display-only here.
                    sessionExpiresAtUtc = sessionExpiresAtUtc?.let {
                        try { Instant.parse(it) } catch (e: Exception) { null }
                    },
                )
                sessionHolder.set(session)
                Result.success(session)
            }
        }
    }

    /** Closes this handheld's session. The local session is cleared regardless of the network
     * outcome — stranding an operator logged-in because of a network blip would be worse than a
     * server-side session that expires on its own. */
    suspend fun logout() {
        requestChannel.request(
            deviceId = deviceId,
            requestType = "reader_logout_requested",
            responseClass = OperatorContextResponse::class.java,
            payload = EmptyPayload,
            operatorSessionId = sessionHolder.currentSessionIdOrEmpty(),
        )
        sessionHolder.clear()
    }
}
