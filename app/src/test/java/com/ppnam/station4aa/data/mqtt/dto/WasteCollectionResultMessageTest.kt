package com.ppnam.station4aa.data.mqtt.dto

import com.ppnam.station4aa.data.mqtt.WireJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WasteCollectionResultMessageTest {

    @Test
    fun `parses a successful result exactly as Station4 publishes it`() {
        val json = """
            {
              "schemaVersion": 3,
              "messageId": "fe3e4ee4d73c49d393c6cc1bb194c1e1",
              "inResponseToMessageId": "01K1F4Y2C8E7K1R6DT5MAB9P3Q",
              "deviceId": "HH-01",
              "operatorSessionId": "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
              "timestampUtc": "2026-07-30T10:15:30.125000Z",
              "collectionId": "01K1F4XZS92T3V7A6Q8C0N5JHE",
              "bagCode": "BAG-01",
              "accepted": true,
              "isDuplicate": false,
              "collectionStatus": "AwaitingWeight",
              "nextAction": "start_next_collection"
            }
        """.trimIndent()

        val result = WireJson.gson.fromJson(json, WasteCollectionResultMessage::class.java)

        assertEquals(3, result.schemaVersion)
        assertEquals("fe3e4ee4d73c49d393c6cc1bb194c1e1", result.messageId)
        assertEquals("01K1F4Y2C8E7K1R6DT5MAB9P3Q", result.inResponseToMessageId)
        assertEquals("HH-01", result.deviceId)
        assertEquals("4dfda8bb-e9bf-4e92-b8a9-acde673fbb83", result.operatorSessionId)
        assertEquals("01K1F4XZS92T3V7A6Q8C0N5JHE", result.collectionId)
        assertEquals("BAG-01", result.bagCode)
        assertTrue(result.accepted)
        assertEquals(false, result.isDuplicate)
        assertEquals("AwaitingWeight", result.collectionStatus)
        assertNull(result.errorCode)
        assertNull(result.reason)
        assertEquals("start_next_collection", result.nextAction)
    }

    @Test
    fun `parses a rejected result including errorCode and reason`() {
        val json = """
            {
              "schemaVersion": 3,
              "messageId": "d7db43bd39d84df2bb1f8d8fb2a0feeb",
              "inResponseToMessageId": "01K1F50N46H9VEK2D7SAB3M8QW",
              "deviceId": "HH-01",
              "operatorSessionId": "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
              "timestampUtc": "2026-07-30T10:16:04.830000Z",
              "collectionId": "01K1F50MZZR4P0B1X6G2K8HV9T",
              "bagCode": "BAG-01",
              "accepted": false,
              "isDuplicate": false,
              "collectionStatus": "Rejected",
              "errorCode": "bag_code_in_use",
              "reason": "Bag code 'BAG-01' is already awaiting weight for collection '01K1F4XZS92T3V7A6Q8C0N5JHE'.",
              "nextAction": "complete_existing_bag_weight"
            }
        """.trimIndent()

        val result = WireJson.gson.fromJson(json, WasteCollectionResultMessage::class.java)

        assertEquals(false, result.accepted)
        assertEquals("Rejected", result.collectionStatus)
        assertEquals("bag_code_in_use", result.errorCode)
        assertEquals(
            "Bag code 'BAG-01' is already awaiting weight for collection '01K1F4XZS92T3V7A6Q8C0N5JHE'.",
            result.reason,
        )
        assertEquals("complete_existing_bag_weight", result.nextAction)
    }
}
