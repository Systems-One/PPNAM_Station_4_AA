package com.mitas.ppnam.station4aa.domain.usecase

import com.mitas.ppnam.station4aa.data.mqtt.MqttOutcome
import com.mitas.ppnam.station4aa.data.mqtt.MqttTopics
import com.mitas.ppnam.station4aa.data.mqtt.RequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.describe
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCapturePayload
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCaptureResultMessage
import com.mitas.ppnam.station4aa.domain.capture.CaptureRefusal
import com.mitas.ppnam.station4aa.domain.capture.CaptureRefusals
import com.mitas.ppnam.station4aa.domain.validation.WasteCollectionValidator
import kotlinx.coroutines.CancellationException

/** What one handheld weigh attempt came to. */
sealed interface WasteCaptureOutcome {
    /** Station 4 weighed the bag and saved it against the collection. */
    data class Weighed(
        val bagCode: String,
        val weightKg: Double,
        val capturedBy: String?,
        val capturedAtUtc: String?,
        val collectionId: String?,
    ) : WasteCaptureOutcome

    /** Station 4 answered, and said no. [CaptureRefusal] carries what the operator should do. */
    data class Refused(val refusal: CaptureRefusal) : WasteCaptureOutcome

    /** The bag code could not be sent as-is; nothing was published. */
    data class InvalidBagCode(val message: String) : WasteCaptureOutcome

    /** Nothing usable came back — offline, timed out, unreadable, or an answer whose echoed
     * identity did not match what was asked. Always safe to try again. */
    data class Failed(val message: String) : WasteCaptureOutcome
}

/**
 * Asks Station 4 to weigh the bag already on its scale — contract 5.1.0 §9.2's
 * `waste_capture_requested` / `waste_capture_result` pair.
 *
 * Station 4 performs the measurement, because it is the only participant wired to the scale; this
 * handheld only asks. The handheld's own authenticated session supplies the operator the capture
 * is attributed to, so the weigh succeeds with the station PC signed out entirely.
 *
 * Unlike a collection ([com.mitas.ppnam.station4aa.data.mqtt.WasteCollectionPublisher]) this is
 * **not** queued in a durable outbox. A weigh is interactive and only meaningful while the bag is
 * physically on the pan, so replaying one hours later would attribute a weight to whatever the
 * scale happened to be holding. That also makes the messageId rules fall out correctly: the
 * underlying [RequestChannel] mints a fresh messageId per call, so every retry is a new logical
 * operation — which is exactly what §9.2 demands after `bag_code_unknown` ("a handheld that
 * retries MUST generate a new messageId"), and is harmless for the three transient scale
 * conditions Station 4 deliberately does not store.
 *
 * [request] is total, in the same way [SyncWasteCatalogueUseCase.sync] is: every path, including a
 * thrown channel, resolves to a [WasteCaptureOutcome] rather than propagating. A failed weigh is a
 * message on screen, never a crash on a handheld in someone's hand.
 */
class RequestWasteCaptureUseCase(
    private val requestChannel: RequestChannel,
    private val deviceId: String,
) {

    suspend fun request(operatorSessionId: String, rawBagCode: String): WasteCaptureOutcome {
        // Refuse locally what the contract could never accept. This is not just a saved round
        // trip: `invalid_payload` is a *stored* terminal refusal at the station, so sending a
        // knowingly bad code would burn that messageId permanently.
        WasteCollectionValidator.validateBagCode(rawBagCode)?.let {
            return WasteCaptureOutcome.InvalidBagCode(it)
        }
        val bagCode = rawBagCode.trim()

        return try {
            val outcome = requestChannel.request(
                deviceId = deviceId,
                requestType = MqttTopics.WASTE_CAPTURE_REQUESTED,
                responseClass = WasteCaptureResultMessage::class.java,
                payload = WasteCapturePayload(operatorSessionId = operatorSessionId, bagCode = bagCode),
                operatorSessionId = operatorSessionId,
            )

            when (outcome) {
                is MqttOutcome.Accepted -> accept(outcome.body, bagCode)
                is MqttOutcome.Rejected ->
                    // Prefer the envelope's code: it is the one MqttRequestChannel always has,
                    // even when the body failed to parse into this message type.
                    WasteCaptureOutcome.Refused(
                        CaptureRefusals.describe(
                            errorCode = outcome.errorCode ?: outcome.body?.errorCode,
                            nextAction = outcome.body?.nextAction,
                        )
                    )
                is MqttOutcome.NoResponse -> WasteCaptureOutcome.Failed(outcome.kind.describe())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WasteCaptureOutcome.Failed("Could not ask Station 4 for a weight.")
        }
    }

    /**
     * Verifies the echoed identity before believing a weight. The channel already correlated this
     * response by `inResponseToMessageId`, but §9.2 echoes `deviceId` and `bagCode` too, and a
     * weight shown against the wrong bag is worse than no weight at all — the operator would read
     * it back into the wrong collection.
     */
    private fun accept(body: WasteCaptureResultMessage, bagCode: String): WasteCaptureOutcome {
        if (body.deviceId != deviceId || body.bagCode != bagCode) {
            return WasteCaptureOutcome.Failed("Station 4's answer did not match the bag you scanned.")
        }
        // On success weightKg is always present — the contract omits the success fields on refusal
        // rather than nulling them, so a missing number here is a malformed answer. Rendering it
        // as 0.0 kg would silently understate the waste recorded against this collection.
        val weightKg = body.weightKg
            ?: return WasteCaptureOutcome.Failed("Station 4 accepted the weigh but sent no weight.")

        return WasteCaptureOutcome.Weighed(
            bagCode = bagCode,
            weightKg = weightKg,
            capturedBy = body.capturedBy,
            capturedAtUtc = body.capturedAtUtc,
            collectionId = body.collectionId,
        )
    }
}
