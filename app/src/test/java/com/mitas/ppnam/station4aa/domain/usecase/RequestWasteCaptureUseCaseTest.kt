package com.mitas.ppnam.station4aa.domain.usecase

import com.mitas.ppnam.station4aa.data.mqtt.FailureKind
import com.mitas.ppnam.station4aa.data.mqtt.MqttOutcome
import com.mitas.ppnam.station4aa.data.mqtt.MqttTopics
import com.mitas.ppnam.station4aa.data.mqtt.RequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCapturePayload
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCaptureResultMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DEVICE_ID = "scanner_a1b2c3d4e5f6"
private const val SESSION_ID = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83"
private const val BAG_CODE = "CUSTOMER-LABEL-88120"

private class FakeCaptureChannel(private val outcome: MqttOutcome<WasteCaptureResultMessage>) : RequestChannel {
    var lastRequestType: String? = null
    var lastPayload: Any? = null
    var lastDeviceId: String? = null
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
        lastDeviceId = deviceId
        lastOperatorSessionId = operatorSessionId
        return outcome as MqttOutcome<T>
    }
}

class RequestWasteCaptureUseCaseTest {

    private fun useCase(
        outcome: MqttOutcome<WasteCaptureResultMessage>,
    ): Pair<RequestWasteCaptureUseCase, FakeCaptureChannel> {
        val channel = FakeCaptureChannel(outcome)
        return RequestWasteCaptureUseCase(channel, DEVICE_ID) to channel
    }

    private fun weighed() = WasteCaptureResultMessage(
        inResponseToMessageId = "req-1",
        schemaVersion = "4.1",
        deviceId = DEVICE_ID,
        accepted = true,
        bagCode = BAG_CODE,
        collectionId = "131c2e86141a423c828aae693651d56c",
        weightKg = 8.5,
        capturedAtUtc = "2026-09-03T07:41:14.881204Z",
        capturedBy = "Collector One",
        nextAction = "start_next_collection",
    )

    @Test
    fun `an accepted weigh returns the weight and who it was attributed to`() = runTest {
        val (useCase, channel) = useCase(MqttOutcome.Accepted(weighed()))

        val result = useCase.request(SESSION_ID, BAG_CODE)

        assertEquals(
            WasteCaptureOutcome.Weighed(
                bagCode = BAG_CODE,
                weightKg = 8.5,
                capturedBy = "Collector One",
                capturedAtUtc = "2026-09-03T07:41:14.881204Z",
                collectionId = "131c2e86141a423c828aae693651d56c",
            ),
            result,
        )
        assertEquals(MqttTopics.WASTE_CAPTURE_REQUESTED, channel.lastRequestType)
        assertEquals(DEVICE_ID, channel.lastDeviceId)
        assertEquals(SESSION_ID, channel.lastOperatorSessionId)
    }

    @Test
    fun `the request carries the session and the trimmed bag code as its only workflow field`() = runTest {
        // §9.2: the standard 4.1 envelope "plus one workflow field, `bagCode`". The envelope is
        // RequestEnvelope's job, so what this use case must get right is the payload — and the
        // "exact trimmed scan" the contract matches ordinally at the station.
        val (useCase, channel) = useCase(MqttOutcome.Accepted(weighed()))

        useCase.request(SESSION_ID, "  $BAG_CODE  ")

        assertEquals(WasteCapturePayload(operatorSessionId = SESSION_ID, bagCode = BAG_CODE), channel.lastPayload)
    }

    @Test
    fun `a bag code the contract could never accept never reaches the broker`() = runTest {
        // §9.2: bagCode is 1-100 printable characters. Refusing locally saves a round trip and,
        // more importantly, avoids burning a *stored* `invalid_payload` refusal at the station —
        // one that would then replay forever for that messageId.
        listOf("", "   ", "x".repeat(101), "BAG" + 1.toChar() + "CODE").forEach { bad ->
            val (useCase, channel) = useCase(MqttOutcome.Accepted(weighed()))

            val result = useCase.request(SESSION_ID, bad)

            assertTrue("'$bad' should be refused locally", result is WasteCaptureOutcome.InvalidBagCode)
            assertFalse((result as WasteCaptureOutcome.InvalidBagCode).message.isBlank())
            assertEquals("'$bad' should not have been published", 0, channel.callCount)
        }
    }

    @Test
    fun `a refusal is translated through the contract's table`() = runTest {
        val refused = WasteCaptureResultMessage(
            deviceId = DEVICE_ID,
            accepted = false,
            bagCode = BAG_CODE,
            errorCode = "scale_no_load",
            reason = "The pan is empty.",
            nextAction = "place_bag_and_retry",
        )
        val (useCase, _) = useCase(MqttOutcome.Rejected(refused, "scale_no_load", "The pan is empty."))

        val result = useCase.request(SESSION_ID, BAG_CODE)

        val refusal = (result as WasteCaptureOutcome.Refused).refusal
        assertEquals("scale_no_load", refusal.errorCode)
        assertEquals("place_bag_and_retry", refusal.nextAction)
        assertTrue(refusal.keepBagCode)
    }

    @Test
    fun `a refusal with no parsed body still yields the envelope's error code`() = runTest {
        // MqttRequestChannel hands back Rejected(body = null) when a response parses as an
        // envelope but not as this message. The operator must still learn why.
        val (useCase, _) = useCase(MqttOutcome.Rejected(null, "capture_not_permitted", null))

        val result = useCase.request(SESSION_ID, BAG_CODE)

        val refusal = (result as WasteCaptureOutcome.Refused).refusal
        assertEquals("capture_not_permitted", refusal.errorCode)
        assertTrue(refusal.isRecognised)
    }

    @Test
    fun `an expired session is reported as a refusal that requires signing in again`() = runTest {
        val (useCase, _) = useCase(MqttOutcome.Rejected(null, "operator_session_invalid", null))

        val result = useCase.request(SESSION_ID, BAG_CODE)

        assertTrue((result as WasteCaptureOutcome.Refused).refusal.requiresLogin)
    }

    @Test
    fun `no response is a failure the operator can retry, not a refusal`() = runTest {
        listOf(FailureKind.Timeout, FailureKind.NotConnected, FailureKind.MalformedResponse).forEach { kind ->
            val (useCase, _) = useCase(MqttOutcome.NoResponse(kind))

            val result = useCase.request(SESSION_ID, BAG_CODE)

            assertTrue("$kind", result is WasteCaptureOutcome.Failed)
            assertFalse("$kind", (result as WasteCaptureOutcome.Failed).message.isBlank())
        }
    }

    @Test
    fun `an accepted response for a different bag is never shown as this bag's weight`() = runTest {
        // The channel correlates by inResponseToMessageId, but §9.2 also echoes bagCode. Showing a
        // weight belonging to another bag would be worse than showing nothing — the operator would
        // read it back against the wrong collection.
        val (useCase, _) = useCase(MqttOutcome.Accepted(weighed().copy(bagCode = "SOME-OTHER-BAG")))

        val result = useCase.request(SESSION_ID, BAG_CODE)

        assertTrue(result is WasteCaptureOutcome.Failed)
    }

    @Test
    fun `an accepted response from a different device is not trusted`() = runTest {
        val (useCase, _) = useCase(MqttOutcome.Accepted(weighed().copy(deviceId = "scanner_deadbeef0000")))

        val result = useCase.request(SESSION_ID, BAG_CODE)

        assertTrue(result is WasteCaptureOutcome.Failed)
    }

    @Test
    fun `an accepted response with no weight is malformed, not a zero-kilogram bag`() = runTest {
        // §9.2: on success weightKg is present, and the omitted-on-refusal fields are "never sent
        // as null". A missing number here means something is wrong, and rendering it as 0.0 kg
        // would silently understate the waste recorded against this collection.
        val (useCase, _) = useCase(MqttOutcome.Accepted(weighed().copy(weightKg = null)))

        val result = useCase.request(SESSION_ID, BAG_CODE)

        assertTrue(result is WasteCaptureOutcome.Failed)
    }

    @Test
    fun `an accepted weigh with no capturedBy still reports the weight`() = runTest {
        // capturedBy is required on success, but the weight is the value the process depends on:
        // a present weight with a missing name is worth showing, unlike the reverse.
        val (useCase, _) = useCase(MqttOutcome.Accepted(weighed().copy(capturedBy = null)))

        val result = useCase.request(SESSION_ID, BAG_CODE)

        assertEquals(8.5, (result as WasteCaptureOutcome.Weighed).weightKg, 0.0001)
        assertNull(result.capturedBy)
    }

    @Test
    fun `a thrown channel never escapes the use case`() = runTest {
        // Same totality rule as SyncWasteCatalogueUseCase: a failed weigh is a message on screen,
        // never a crash on a handheld in someone's hand.
        val throwing = object : RequestChannel {
            override suspend fun <T : Any> request(
                deviceId: String,
                requestType: String,
                responseClass: Class<T>,
                payload: Any,
                operatorSessionId: String,
                timeoutMs: Long,
            ): MqttOutcome<T> = throw IllegalStateException("broker exploded")
        }

        val result = RequestWasteCaptureUseCase(throwing, DEVICE_ID).request(SESSION_ID, BAG_CODE)

        assertTrue(result is WasteCaptureOutcome.Failed)
    }
}
