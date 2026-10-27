package com.mitas.ppnam.station4aa.domain.usecase

import com.mitas.ppnam.station4aa.data.mqtt.FailureKind
import com.mitas.ppnam.station4aa.data.mqtt.MqttOutcome
import com.mitas.ppnam.station4aa.data.mqtt.MqttTopics
import com.mitas.ppnam.station4aa.data.mqtt.RequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteOperatorPayload
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteOperatorResultMessage
import com.mitas.ppnam.station4aa.domain.collection.CollectionRejections
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DEVICE_ID = "scanner_1"
private const val SESSION_ID = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83"
private const val BAG_CODE = "CUSTOMER-LABEL-9F2A"
private const val CARD = "0000427"

private class FakeOperatorChannel(private val outcome: MqttOutcome<WasteOperatorResultMessage>) : RequestChannel {
    var lastRequestType: String? = null
    var lastPayload: Any? = null
    var lastOperatorSessionId: String? = null
    var callCount = 0

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> request(
        deviceId: String,
        requestType: String,
        responseClass: Class<T>,
        payload: Any,
        operatorSessionId: String,
        timeoutMs: Long,
    ): MqttOutcome<T> {
        callCount++
        lastRequestType = requestType
        lastPayload = payload
        lastOperatorSessionId = operatorSessionId
        return outcome as MqttOutcome<T>
    }
}

class LookupWasteOperatorUseCaseTest {

    private fun useCase(outcome: MqttOutcome<WasteOperatorResultMessage>) =
        FakeOperatorChannel(outcome).let { LookupWasteOperatorUseCase(it, DEVICE_ID) to it }

    private fun resolved(
        deviceId: String = DEVICE_ID,
        operatorSessionId: String = SESSION_ID,
        bagCode: String = BAG_CODE,
        operatorId: String = CARD,
        operatorName: String? = "Production Operator One",
    ) = WasteOperatorResultMessage(
        inResponseToMessageId = "waste-operator-001",
        schemaVersion = "4.1",
        deviceId = deviceId,
        operatorSessionId = operatorSessionId,
        accepted = true,
        bagCode = bagCode,
        operatorId = operatorId,
        operatorName = operatorName,
        nextAction = "continue",
    )

    private fun refused(code: String, bagCode: String = BAG_CODE, operatorId: String = CARD) =
        WasteOperatorResultMessage(
            deviceId = DEVICE_ID,
            operatorSessionId = SESSION_ID,
            bagCode = bagCode,
            operatorId = operatorId,
            errorCode = code,
        ).let { MqttOutcome.Rejected(it, code, "reason") }

    @Test
    fun `an accepted lookup returns the card owner's name and keeps the card string`() = runTest {
        val (uc, channel) = useCase(MqttOutcome.Accepted(resolved()))

        val result = uc.lookup(SESSION_ID, " $BAG_CODE ", " $CARD ")

        assertEquals(WasteOperatorOutcome.Resolved(CARD, "Production Operator One"), result)
        assertEquals(MqttTopics.WASTE_OPERATOR_REQUESTED, channel.lastRequestType)
        assertEquals(WasteOperatorPayload(bagCode = BAG_CODE, operatorId = CARD), channel.lastPayload)
        assertEquals(SESSION_ID, channel.lastOperatorSessionId)
    }

    @Test
    fun `the card goes on the wire as a string with its leading zeroes`() = runTest {
        val (uc, channel) = useCase(MqttOutcome.Accepted(resolved()))
        uc.lookup(SESSION_ID, BAG_CODE, CARD)
        val json = com.mitas.ppnam.station4aa.data.mqtt.WireJson.gson.toJson(channel.lastPayload)
        assertTrue(json, json.contains("\"operatorId\":\"0000427\""))
    }

    @Test
    fun `an accepted answer whose echoes disagree is not believed`() = runTest {
        listOf(
            resolved(deviceId = "scanner_2"),
            resolved(operatorSessionId = "other-session"),
            resolved(bagCode = "OTHER-BAG"),
            resolved(operatorId = "427"),
        ).forEach { body ->
            val (uc, _) = useCase(MqttOutcome.Accepted(body))
            assertTrue(uc.lookup(SESSION_ID, BAG_CODE, CARD) is WasteOperatorOutcome.Failed)
        }
    }

    @Test
    fun `an accepted answer without a name never falls back to the card code`() = runTest {
        listOf(null, "", "   ").forEach { name ->
            val (uc, _) = useCase(MqttOutcome.Accepted(resolved(operatorName = name)))
            assertTrue(uc.lookup(SESSION_ID, BAG_CODE, CARD) is WasteOperatorOutcome.Failed)
        }
    }

    @Test
    fun `card-directory refusals are reported when their echoes match`() = runTest {
        WasteOperatorRefusals.CARD_DIRECTORY_CODES.forEach { code ->
            val (uc, _) = useCase(refused(code))
            val result = uc.lookup(SESSION_ID, BAG_CODE, CARD)
            assertTrue(code, result is WasteOperatorOutcome.Refused)
            assertEquals(code, (result as WasteOperatorOutcome.Refused).refusal.errorCode)
        }
    }

    @Test
    fun `a card-directory refusal for a different card is discarded`() = runTest {
        val (uc, _) = useCase(refused("production_operator_unknown", operatorId = "9999"))
        assertTrue(uc.lookup(SESSION_ID, BAG_CODE, CARD) is WasteOperatorOutcome.Failed)
    }

    @Test
    fun `an early session refusal with empty echoes asks the collector to sign in again`() = runTest {
        val (uc, _) = useCase(refused("operator_session_invalid", bagCode = "", operatorId = ""))
        val result = uc.lookup(SESSION_ID, BAG_CODE, CARD) as WasteOperatorOutcome.Refused
        assertTrue(result.refusal.requiresLogin)
    }

    @Test
    fun `only a session refusal signs the collector out`() {
        listOf(
            "validation_failed", "bag_code_invalid", "production_operator_invalid",
            "production_operator_unknown", "production_operator_name_unavailable",
            "production_operator_lookup_unavailable", "something_new", null,
        ).forEach { assertFalse(it.toString(), WasteOperatorRefusals.describe(it).requiresLogin) }
    }

    @Test
    fun `an invalid card never reaches the broker`() = runTest {
        val (uc, channel) = useCase(MqttOutcome.Accepted(resolved()))
        assertTrue(uc.lookup(SESSION_ID, BAG_CODE, "  ") is WasteOperatorOutcome.Invalid)
        assertTrue(uc.lookup(SESSION_ID, BAG_CODE, "A".repeat(101)) is WasteOperatorOutcome.Invalid)
        assertTrue(uc.lookup(SESSION_ID, BAG_CODE, "04\u0007") is WasteOperatorOutcome.Invalid)
        assertTrue(uc.lookup(SESSION_ID, "", CARD) is WasteOperatorOutcome.Invalid)
        assertEquals(0, channel.callCount)
    }

    @Test
    fun `no response is a retryable failure`() = runTest {
        val (uc, _) = useCase(MqttOutcome.NoResponse(FailureKind.Timeout))
        assertTrue(uc.lookup(SESSION_ID, BAG_CODE, CARD) is WasteOperatorOutcome.Failed)
    }

    @Test
    fun `a thrown channel resolves to a failure rather than crashing`() = runTest {
        val throwing = object : RequestChannel {
            override suspend fun <T : Any> request(
                deviceId: String, requestType: String, responseClass: Class<T>,
                payload: Any, operatorSessionId: String, timeoutMs: Long,
            ): MqttOutcome<T> = error("boom")
        }
        val result = LookupWasteOperatorUseCase(throwing, DEVICE_ID).lookup(SESSION_ID, BAG_CODE, CARD)
        assertTrue(result is WasteOperatorOutcome.Failed)
    }

    @Test
    fun `the collection result names production-card refusals instead of a raw code`() {
        val message = CollectionRejections.describe(BAG_CODE, "production_operator_unknown").message
        assertFalse(message, message.contains("production_operator_unknown"))
    }
}
