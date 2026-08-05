package com.ppnam.station4aa.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WasteCollectionEventTest {

    private val fixedInstant: Instant = Instant.parse("2026-07-30T10:15:30.000Z")

    private fun buildEvent(
        machineCode: String = "EXT-04",
        machineName: String = "Extruder 4",
        wasteTypeCode: String = "WT-01",
        collectedBy: String = "WO-00112",
        machineOperatorUserId: String = "MO-00427",
        bagCode: String = "BAG-00931",
        deviceId: String = "HH-01",
        operatorSessionId: String = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
        now: Instant = fixedInstant,
    ) = WasteCollectionEvent.create(
        machineCode = machineCode,
        machineName = machineName,
        wasteTypeCode = wasteTypeCode,
        collectedBy = collectedBy,
        machineOperatorUserId = machineOperatorUserId,
        bagCode = bagCode,
        deviceId = deviceId,
        operatorSessionId = operatorSessionId,
        now = now,
    )

    @Test
    fun `create trims fields and stamps schema version 3 on the wire message`() {
        val event = buildEvent(
            machineCode = "  EXT-04  ",
            machineName = " Extruder 4 ",
            wasteTypeCode = " WT-01 ",
            collectedBy = " WO-00112 ",
            machineOperatorUserId = " MO-00427 ",
            bagCode = " BAG-00931 ",
            deviceId = " HH-01 ",
            operatorSessionId = " 4dfda8bb-e9bf-4e92-b8a9-acde673fbb83 ",
        )

        assertEquals("EXT-04", event.machineCode)
        assertEquals("Extruder 4", event.machineName)
        assertEquals("WT-01", event.wasteTypeCode)
        assertEquals("WO-00112", event.collectedBy)
        assertEquals("MO-00427", event.machineOperatorUserId)
        assertEquals("BAG-00931", event.bagCode)
        assertEquals("HH-01", event.deviceId)
        assertEquals("4dfda8bb-e9bf-4e92-b8a9-acde673fbb83", event.operatorSessionId)
        assertEquals("2026-07-30T10:15:30.000Z", event.collectedAtUtc)
        assertEquals(3, event.toWireMessage().schemaVersion)
    }

    @Test
    fun `collectionId follows the contract's WC-yyyyMMdd- shape and is distinct from bagCode`() {
        val event = buildEvent()
        assertTrue(event.collectionId.matches(Regex("WC-20260730-\\d{6}")))
        assertTrue(event.collectionId != event.bagCode)
    }

    @Test
    fun `wire JSON uses the exact camelCase property names the contract requires`() {
        val event = buildEvent()
        val json = Gson().toJson(event.toWireMessage())

        listOf(
            "\"schemaVersion\":3",
            "\"messageId\"",
            "\"deviceId\":\"HH-01\"",
            "\"operatorSessionId\":\"4dfda8bb-e9bf-4e92-b8a9-acde673fbb83\"",
            "\"collectionId\"",
            "\"bagCode\":\"BAG-00931\"",
            "\"machineCode\":\"EXT-04\"",
            "\"machineName\":\"Extruder 4\"",
            "\"machineOperatorUserId\":\"MO-00427\"",
            "\"wasteTypeCode\":\"WT-01\"",
            "\"collectedBy\":\"WO-00112\"",
            "\"collectedAtUtc\":\"2026-07-30T10:15:30.000Z\"",
        ).forEach { expectedFragment ->
            assertTrue("Expected JSON to contain $expectedFragment but was $json", json.contains(expectedFragment))
        }
    }

    @Test
    fun `two events created back to back get different messageIds`() {
        val first = buildEvent()
        val second = buildEvent()
        assertTrue(first.messageId != second.messageId)
    }
}
