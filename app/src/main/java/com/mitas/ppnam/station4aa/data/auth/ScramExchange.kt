package com.mitas.ppnam.station4aa.data.auth

import com.mitas.ppnam.station4aa.data.mqtt.MqttOutcome
import com.mitas.ppnam.station4aa.data.mqtt.MqttRequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.describe
import com.mitas.ppnam.station4aa.data.mqtt.dto.ScramChallengeResponse
import com.mitas.ppnam.station4aa.data.mqtt.dto.ScramProofPayload
import com.mitas.ppnam.station4aa.data.mqtt.dto.ScramProofResponse
import com.mitas.ppnam.station4aa.data.mqtt.dto.ScramStartPayload

/**
 * Runs one SCRAM-SHA-256 challenge/proof round trip, adapted from Station 2 AA's ScramExchange —
 * see `MqttTopics`' class doc. The password is never stored, never logged, and never leaves this
 * function's frame; the server signature is always verified before the caller is handed anything.
 */
class ScramExchange(
    private val requestChannel: MqttRequestChannel,
) {
    suspend fun authenticate(
        deviceId: String,
        username: String,
        password: String,
        purpose: String,
    ): Result<ScramProofResponse> {
        val clientNonce = ScramCrypto.generateClientNonce()

        val startOutcome = requestChannel.request(
            deviceId = deviceId,
            requestType = "scram_start_requested",
            responseClass = ScramChallengeResponse::class.java,
            payload = ScramStartPayload(username = username, clientNonce = clientNonce, purpose = purpose),
        )

        val challenge = when (startOutcome) {
            is MqttOutcome.Accepted -> startOutcome.body
            is MqttOutcome.Rejected -> return Result.failure(Exception(startOutcome.reason ?: "Authentication failed"))
            is MqttOutcome.NoResponse -> return Result.failure(Exception(startOutcome.kind.describe()))
        }

        if (challenge.challengeId.isBlank() || challenge.serverFirstMessage.isBlank()) {
            return Result.failure(Exception("The server sent an incomplete authentication challenge"))
        }
        if (challenge.iterations <= 0) {
            return Result.failure(Exception("The server sent an invalid authentication challenge"))
        }

        // RFC 5802: the combined nonce must extend the one we sent. Anything else means this
        // challenge is not an answer to our start — refuse rather than proving against it.
        val serverNonce = ScramCrypto.parseServerNonce(challenge.serverFirstMessage, clientNonce)
            ?: challenge.serverNonce.takeIf { it.startsWith(clientNonce) && it.length > clientNonce.length }
            ?: return Result.failure(Exception("The authentication challenge did not match this device's request"))

        val clientFinalWithoutProof = ScramCrypto.clientFinalWithoutProof(serverNonce)
        val proof = try {
            ScramCrypto.computeProof(
                password = password,
                saltBase64 = challenge.salt,
                iterations = challenge.iterations,
                authMessage = ScramCrypto.authMessage(
                    clientFirstBare = ScramCrypto.clientFirstBare(username, clientNonce),
                    serverFirstMessage = challenge.serverFirstMessage,
                    clientFinalWithoutProof = clientFinalWithoutProof,
                ),
            )
        } catch (e: Exception) {
            // A malformed salt lands here. Deliberately not echoing the exception text, which
            // would put challenge material into a user-facing string.
            return Result.failure(Exception("The server sent an unusable authentication challenge"))
        }

        val proofOutcome = requestChannel.request(
            deviceId = deviceId,
            requestType = "scram_proof_requested",
            responseClass = ScramProofResponse::class.java,
            payload = ScramProofPayload(
                challengeId = challenge.challengeId,
                clientFinalWithoutProof = clientFinalWithoutProof,
                clientProof = proof.clientProofBase64,
                purpose = purpose,
            ),
        )

        val result = when (proofOutcome) {
            is MqttOutcome.Accepted -> proofOutcome.body
            is MqttOutcome.Rejected -> return Result.failure(Exception(proofOutcome.reason ?: "Authentication failed"))
            is MqttOutcome.NoResponse -> return Result.failure(Exception(proofOutcome.kind.describe()))
        }

        // Mutual authentication. Without this check, an attacker who can answer on the response
        // topic could hand us a session we never actually proved for — precisely what SCRAM's
        // server signature exists to prevent.
        if (!ScramCrypto.verifyServerSignature(proof.expectedServerSignatureBase64, result.serverSignature)) {
            return Result.failure(Exception("Server authentication verification failed — this response is not trusted"))
        }

        return Result.success(result)
    }
}
