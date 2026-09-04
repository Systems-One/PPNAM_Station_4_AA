package com.mitas.ppnam.station4aa.data.mqtt.dto

import com.google.gson.Gson
import com.mitas.ppnam.station4aa.data.mqtt.RequestEnvelope
import com.mitas.ppnam.station4aa.data.mqtt.WireJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reads the exact request and response bodies printed in contract 5.1.0 §9.2. These property names
 * ARE the wire keys, so a rename that compiles cleanly would silently stop Station 4 understanding
 * the handheld — this is what catches it.
 */
class WasteCaptureMessagesTest {

    private val gson = WireJson.gson

    @Test
    fun `the request is the 4_1 envelope plus bagCode, exactly as the contract prints it`() {
        val json = RequestEnvelope.build(
            gson = Gson(),
            payload = WasteCapturePayload(
                operatorSessionId = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
                bagCode = "CUSTOMER-LABEL-88120",
            ),
            messageId = "01K1F4Y2C8E7K1R6DT5MAB9P3Q",
            deviceId = "scanner_1",
            operatorSessionId = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
            timestampUtc = "2026-09-03T07:41:12.482913Z",
        )

        val parsed = Gson().fromJson(json, Map::class.java)
        assertEquals(
            setOf("messageId", "schemaVersion", "deviceId", "timestampUtc", "operatorSessionId", "bagCode"),
            parsed.keys,
        )
        assertEquals("4.1", parsed["schemaVersion"])
        assertEquals("CUSTOMER-LABEL-88120", parsed["bagCode"])
        assertEquals("scanner_1", parsed["deviceId"])
        assertEquals("01K1F4Y2C8E7K1R6DT5MAB9P3Q", parsed["messageId"])
    }

    @Test
    fun `the accepted response from the contract parses whole`() {
        val raw = """
            {
              "messageId": "response-01K1F4Y2C8E7K1R6DT5MAB9P3Q",
              "inResponseToMessageId": "01K1F4Y2C8E7K1R6DT5MAB9P3Q",
              "schemaVersion": "4.1",
              "deviceId": "scanner_1",
              "timestampUtc": "2026-09-03T07:41:14.902611Z",
              "accepted": true,
              "bagCode": "CUSTOMER-LABEL-88120",
              "collectionId": "131c2e86141a423c828aae693651d56c",
              "weightKg": 8.5,
              "capturedAtUtc": "2026-09-03T07:41:14.881204Z",
              "capturedBy": "Collector One",
              "nextAction": "start_next_collection"
            }
        """.trimIndent()

        val message = gson.fromJson(raw, WasteCaptureResultMessage::class.java)

        assertTrue(message.accepted)
        assertEquals("01K1F4Y2C8E7K1R6DT5MAB9P3Q", message.inResponseToMessageId)
        assertEquals("scanner_1", message.deviceId)
        assertEquals("CUSTOMER-LABEL-88120", message.bagCode)
        assertEquals("131c2e86141a423c828aae693651d56c", message.collectionId)
        assertEquals(8.5, message.weightKg!!, 0.0001)
        assertEquals("Collector One", message.capturedBy)
        assertEquals("start_next_collection", message.nextAction)
        assertNull(message.errorCode)
    }

    @Test
    fun `a refusal omitting the success fields leaves them null rather than defaulted`() {
        // §9.2: on refusal collectionId, weightKg, capturedAtUtc and capturedBy are "omitted —
        // never sent as null". Either way they must arrive as null, so the use case can tell a
        // real weight from an absent one instead of reporting a 0.0 kg bag.
        val raw = """
            {
              "messageId": "response-x",
              "inResponseToMessageId": "x",
              "schemaVersion": "4.1",
              "deviceId": "scanner_1",
              "timestampUtc": "2026-09-03T07:41:14.902611Z",
              "accepted": false,
              "bagCode": "CUSTOMER-LABEL-88120",
              "errorCode": "scale_no_load",
              "reason": "The pan is empty.",
              "nextAction": "place_bag_and_retry"
            }
        """.trimIndent()

        val message = gson.fromJson(raw, WasteCaptureResultMessage::class.java)

        assertEquals(false, message.accepted)
        assertEquals("scale_no_load", message.errorCode)
        assertEquals("place_bag_and_retry", message.nextAction)
        assertNull(message.weightKg)
        assertNull(message.collectionId)
        assertNull(message.capturedAtUtc)
        assertNull(message.capturedBy)
    }

    @Test
    fun `an explicit JSON null is pruned rather than binding over a non-null field`() {
        // WireJson's null-pruning contract, exercised on this message: a station that sends
        // "bagCode": null must not produce a null String in a non-null Kotlin field.
        val raw = """{"accepted":false,"bagCode":null,"errorCode":null,"weightKg":null}"""

        val message = gson.fromJson(raw, WasteCaptureResultMessage::class.java)

        assertEquals("", message.bagCode)
        assertNull(message.errorCode)
        assertNull(message.weightKg)
    }

    @Test
    fun `a weight with three decimals survives the wire`() {
        // §9.2: "weightKg is a JSON number with at most three decimal places".
        val message = gson.fromJson(
            """{"accepted":true,"bagCode":"B","weightKg":12.345}""",
            WasteCaptureResultMessage::class.java,
        )

        assertEquals(12.345, message.weightKg!!, 0.0000001)
    }
}
