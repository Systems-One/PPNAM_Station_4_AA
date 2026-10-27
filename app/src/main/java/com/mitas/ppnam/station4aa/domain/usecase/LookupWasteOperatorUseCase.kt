package com.mitas.ppnam.station4aa.domain.usecase

import com.mitas.ppnam.station4aa.data.mqtt.MqttOutcome
import com.mitas.ppnam.station4aa.data.mqtt.MqttTopics
import com.mitas.ppnam.station4aa.data.mqtt.RequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.describe
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteOperatorPayload
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteOperatorResultMessage
import com.mitas.ppnam.station4aa.domain.validation.WasteCollectionValidator
import kotlinx.coroutines.CancellationException

/** What one production-card lookup came to. */
sealed interface WasteOperatorOutcome {
    /** Station 4 resolved [operatorId] (the card string as scanned) to [operatorName]. */
    data class Resolved(val operatorId: String, val operatorName: String) : WasteOperatorOutcome

    /** Station 4 answered, and said no. */
    data class Refused(val refusal: WasteOperatorRefusal) : WasteOperatorOutcome

    /** The bag or card could not be sent as-is; nothing was published. */
    data class Invalid(val message: String) : WasteOperatorOutcome

    /** Nothing usable came back — offline, timed out, unreadable, or an answer whose echoed
     * identity did not match what was asked. Always safe to scan the card again. */
    data class Failed(val message: String) : WasteOperatorOutcome
}

/** One §9.3 refusal, phrased for the collector. The station's free-text `reason` is never shown. */
data class WasteOperatorRefusal(
    val errorCode: String?,
    val message: String,
    val requiresLogin: Boolean,
)

/**
 * The refusal table of contract 5.4.0 §9.3. Shared with `CollectionRejections`, because Station 4
 * resolves the card again when it accepts the collection and can refuse it there with the same
 * production-card codes.
 */
object WasteOperatorRefusals {

    /** Codes Station 4 can only produce after reading the card directory — the ones whose
     * `bagCode`/`operatorId` echo must match before the refusal is believed. */
    val CARD_DIRECTORY_CODES = setOf(
        "production_operator_invalid",
        "production_operator_unknown",
        "production_operator_name_unavailable",
        "production_operator_lookup_unavailable",
    )

    fun describe(errorCode: String?): WasteOperatorRefusal = when (errorCode) {
        "operator_session_invalid" ->
            WasteOperatorRefusal(errorCode, "Your session has ended. Sign in again.", requiresLogin = true)
        "validation_failed" ->
            refusal(errorCode, "Station 4 could not read that card. Scan it again.")
        "bag_code_invalid" ->
            refusal(errorCode, "The bag code is not valid. Cancel and scan a valid bag.")
        "production_operator_invalid" ->
            refusal(errorCode, "That is not a valid operator card. Scan the card again.")
        "production_operator_unknown" ->
            refusal(errorCode, "That card is not registered to an active operator. Scan a valid card.")
        "production_operator_name_unavailable" ->
            refusal(errorCode, "That card has no operator name on record. Scan a different card or ask a manager.")
        "production_operator_lookup_unavailable" ->
            refusal(errorCode, "Station 4 cannot look up operators right now. Try again shortly.")
        null, "" -> refusal(errorCode, "Station 4 refused the operator card. Scan it again.")
        else -> refusal(errorCode, "Station 4 refused the operator card: $errorCode. Ask a manager.")
    }

    private fun refusal(errorCode: String?, message: String) =
        WasteOperatorRefusal(errorCode, message, requiresLogin = false)
}

/**
 * Resolves a scanned production-operator card to its owner's name — contract 5.4.0 §9.3's
 * `waste_operator_requested` / `waste_operator_result` pair.
 *
 * This is a directory lookup, not a login: the card owner is never signed in and the collector's
 * session is untouched, which is why it must never go through `login_requested`. Like the weigh it
 * has no outbox — the underlying [RequestChannel] mints a fresh messageId per call and only
 * completes the first reply correlated to it, so every rescan is a fresh lookup and a duplicate or
 * late reply to an earlier request is dropped.
 *
 * [lookup] is total: every path resolves to a [WasteOperatorOutcome] rather than throwing.
 */
class LookupWasteOperatorUseCase(
    private val requestChannel: RequestChannel,
    private val deviceId: String,
) {

    suspend fun lookup(operatorSessionId: String, rawBagCode: String, rawCard: String): WasteOperatorOutcome {
        WasteCollectionValidator.validateBagCode(rawBagCode)?.let {
            return WasteOperatorOutcome.Invalid("Bag code: $it")
        }
        WasteCollectionValidator.validateOperatorId(rawCard)?.let {
            return WasteOperatorOutcome.Invalid(it)
        }
        val bagCode = rawBagCode.trim()
        val card = rawCard.trim()

        return try {
            val outcome = requestChannel.request(
                deviceId = deviceId,
                requestType = MqttTopics.WASTE_OPERATOR_REQUESTED,
                responseClass = WasteOperatorResultMessage::class.java,
                payload = WasteOperatorPayload(bagCode = bagCode, operatorId = card),
                operatorSessionId = operatorSessionId,
            )
            when (outcome) {
                is MqttOutcome.Accepted -> accept(outcome.body, operatorSessionId, bagCode, card)
                is MqttOutcome.Rejected -> refuse(outcome, operatorSessionId, bagCode, card)
                is MqttOutcome.NoResponse -> WasteOperatorOutcome.Failed(outcome.kind.describe())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WasteOperatorOutcome.Failed("Could not ask Station 4 for the operator's name.")
        }
    }

    /** A name is only believed when every echo matches what was asked — a name shown against the
     * wrong bag or card would be read back into the wrong collection. The scanned card code is
     * never used as a fallback name. */
    private fun accept(
        body: WasteOperatorResultMessage,
        operatorSessionId: String,
        bagCode: String,
        card: String,
    ): WasteOperatorOutcome {
        if (body.deviceId != deviceId ||
            body.operatorSessionId != operatorSessionId ||
            body.bagCode != bagCode ||
            body.operatorId != card
        ) {
            return WasteOperatorOutcome.Failed(MISMATCH)
        }
        val name = body.operatorName?.trim()
        if (name.isNullOrEmpty()) {
            return WasteOperatorOutcome.Failed("Station 4 accepted the card but sent no name.")
        }
        return WasteOperatorOutcome.Resolved(operatorId = card, operatorName = name)
    }

    /** Early envelope/session refusals may carry empty echoes, so only a non-blank echo that
     * disagrees discredits them; a card-directory refusal must echo the bag and card exactly. */
    private fun refuse(
        outcome: MqttOutcome.Rejected<WasteOperatorResultMessage>,
        operatorSessionId: String,
        bagCode: String,
        card: String,
    ): WasteOperatorOutcome {
        val body = outcome.body
        val errorCode = outcome.errorCode ?: body?.errorCode
        if (body != null) {
            if (body.deviceId.isNotBlank() && body.deviceId != deviceId) return WasteOperatorOutcome.Failed(MISMATCH)
            if (!body.operatorSessionId.isNullOrBlank() && body.operatorSessionId != operatorSessionId) {
                return WasteOperatorOutcome.Failed(MISMATCH)
            }
        }
        if (errorCode != null && errorCode in WasteOperatorRefusals.CARD_DIRECTORY_CODES &&
            (body == null || body.bagCode != bagCode || body.operatorId != card)
        ) {
            return WasteOperatorOutcome.Failed(MISMATCH)
        }
        return WasteOperatorOutcome.Refused(WasteOperatorRefusals.describe(errorCode))
    }

    private companion object {
        const val MISMATCH = "Station 4's answer did not match the card you scanned. Scan it again."
    }
}
