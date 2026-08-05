package com.ppnam.station4aa.data.mqtt

import com.ppnam.station4aa.data.local.WasteOutboxEntity
import com.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WasteCollectionResultChannelTest {

    private fun storedRow(
        messageId: String = "msg-1",
        deviceId: String = "HH-01",
        operatorSessionId: String = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
        collectionId: String = "COL-1",
        bagCode: String = "BAG-01",
    ) = WasteOutboxEntity(
        messageId = messageId,
        deviceId = deviceId,
        operatorSessionId = operatorSessionId,
        collectionId = collectionId,
        bagCode = bagCode,
        machineCode = "EXT-04",
        machineName = "EXT-04",
        machineOperatorUserId = "MO-00427",
        wasteTypeCode = "WT-01",
        collectedBy = "Collector One",
        collectedAtUtc = "2026-07-30T10:15:30.000Z",
        status = WasteOutboxEntity.Status.PENDING,
        createdAtEpochMs = 0L,
        lastAttemptEpochMs = null,
        attemptCount = 1,
        errorCode = null,
        reason = null,
        nextAction = null,
    )

    private fun result(
        inResponseToMessageId: String = "msg-1",
        deviceId: String = "HH-01",
        operatorSessionId: String = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
        collectionId: String = "COL-1",
        bagCode: String = "BAG-01",
        accepted: Boolean = true,
        errorCode: String? = null,
        reason: String? = null,
        nextAction: String = "start_next_collection",
    ) = WasteCollectionResultMessage(
        schemaVersion = 3,
        messageId = "server-response-1",
        inResponseToMessageId = inResponseToMessageId,
        deviceId = deviceId,
        operatorSessionId = operatorSessionId,
        timestampUtc = "2026-07-30T10:15:30.125000Z",
        collectionId = collectionId,
        bagCode = bagCode,
        accepted = accepted,
        isDuplicate = false,
        collectionStatus = if (accepted) "AwaitingWeight" else "Rejected",
        errorCode = errorCode,
        reason = reason,
        nextAction = nextAction,
    )

    @Test
    fun `an accepted result with matching identity resolves to Accepted`() {
        val outcome = evaluateOutcome(result(accepted = true), storedRow())
        assertEquals(ResultOutcome.Accepted, outcome)
    }

    @Test
    fun `a rejected result with matching identity resolves to Rejected carrying the error details`() {
        val outcome = evaluateOutcome(
            result(
                accepted = false,
                errorCode = "bag_code_in_use",
                reason = "Bag code 'BAG-01' is already awaiting weight for collection 'COL-1'.",
                nextAction = "complete_existing_bag_weight",
            ),
            storedRow(),
        )
        assertTrue(outcome is ResultOutcome.Rejected)
        val rejected = outcome as ResultOutcome.Rejected
        assertEquals("bag_code_in_use", rejected.errorCode)
        assertEquals("Bag code 'BAG-01' is already awaiting weight for collection 'COL-1'.", rejected.reason)
        assertEquals("complete_existing_bag_weight", rejected.nextAction)
    }

    @Test
    fun `a result with a mismatched bagCode is treated as an identity mismatch, not applied`() {
        val outcome = evaluateOutcome(result(bagCode = "BAG-02"), storedRow(bagCode = "BAG-01"))
        assertEquals(ResultOutcome.IdentityMismatch, outcome)
    }

    @Test
    fun `a result with a mismatched collectionId is treated as an identity mismatch`() {
        val outcome = evaluateOutcome(result(collectionId = "COL-OTHER"), storedRow(collectionId = "COL-1"))
        assertEquals(ResultOutcome.IdentityMismatch, outcome)
    }

    @Test
    fun `a result with a mismatched deviceId or operatorSessionId is treated as an identity mismatch`() {
        assertEquals(
            ResultOutcome.IdentityMismatch,
            evaluateOutcome(result(deviceId = "HH-99"), storedRow(deviceId = "HH-01")),
        )
        assertEquals(
            ResultOutcome.IdentityMismatch,
            evaluateOutcome(result(operatorSessionId = "different-session"), storedRow()),
        )
    }
}
