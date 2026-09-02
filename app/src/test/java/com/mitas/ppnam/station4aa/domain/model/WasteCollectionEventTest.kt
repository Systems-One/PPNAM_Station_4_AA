package com.mitas.ppnam.station4aa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WasteCollectionEventTest {

    private val fixedInstant: Instant = Instant.parse("2026-07-30T10:15:30.000Z")

    private fun buildEvent(
        bagCode: String = "BAG-01",
        jobNumber: String = "JOB-2026-0041",
        operatorId: String = "MO-00427",
        wasteTypeCode: String = "WT-01",
        collectedBy: String = "Collector One",
        deviceId: String = "scanner_a1b2c3d4e5f6",
        operatorSessionId: String = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
        now: Instant = fixedInstant,
    ) = WasteCollectionEvent.create(
        bagCode = bagCode,
        jobNumber = jobNumber,
        operatorId = operatorId,
        wasteTypeCode = wasteTypeCode,
        collectedBy = collectedBy,
        deviceId = deviceId,
        operatorSessionId = operatorSessionId,
        now = now,
    )

    @Test
    fun `the published schema version is 4`() {
        assertEquals(4, WasteCollectionEvent.SCHEMA_VERSION)
        assertEquals(4, buildEvent().toWireMessage().schemaVersion)
    }

    @Test
    fun `the wire message carries the v4 field set`() {
        val message = buildEvent().toWireMessage()
        assertEquals("BAG-01", message.bagCode)
        assertEquals("JOB-2026-0041", message.jobNumber)
        assertEquals("MO-00427", message.operatorId)
        assertEquals("WT-01", message.wasteTypeCode)
    }

    @Test
    fun `fields are trimmed when the event is minted`() {
        val event = buildEvent(jobNumber = "  JOB-7  ", operatorId = "  MO-7  ")
        assertEquals("JOB-7", event.jobNumber)
        assertEquals("MO-7", event.operatorId)
    }

    @Test
    fun `the generated identity fields are minted fresh per event, never shared`() {
        // A retry must republish the exact bytes originally queued, so these three are minted once
        // in create() and never regenerated — two separate transactions must not collide.
        val first = buildEvent()
        val second = buildEvent()
        assertNotEquals(first.messageId, second.messageId)
        assertNotEquals(first.collectionId, second.collectionId)
    }

    @Test
    fun `converting to the wire message does not re-mint anything`() {
        val event = buildEvent()
        val first = event.toWireMessage()
        val second = event.toWireMessage()
        assertEquals(first.messageId, second.messageId)
        assertEquals(first.collectionId, second.collectionId)
        assertEquals(first.collectedAtUtc, second.collectedAtUtc)
    }

    @Test
    fun `collectionId follows the contract's WC-yyyyMMdd- shape and is distinct from bagCode`() {
        val event = buildEvent()
        assertTrue(event.collectionId.matches(Regex("WC-20260730-\\d{6}")))
        assertTrue(event.collectionId != event.bagCode)
    }

    @Test
    fun `two events created back to back get different messageIds`() {
        val first = buildEvent()
        val second = buildEvent()
        assertTrue(first.messageId != second.messageId)
    }
}
