package com.mitas.ppnam.station4aa.data.mqtt

import com.google.gson.Gson
import com.mitas.ppnam.station4aa.data.local.WasteOutboxDao
import com.mitas.ppnam.station4aa.data.local.WasteOutboxEntity
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        jobNumber = "JOB-2026-0041",
        operatorId = "MO-00427",
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

/** In-memory fake of [WasteOutboxDao] for exercising [WasteCollectionResultChannel.handleIncoming]
 * without a real Room database. Only the operations [WasteCollectionResultChannel] actually calls
 * need real behavior; [pendingCount] is unused by that path. */
private class FakeWasteOutboxDao : WasteOutboxDao {
    val rows = mutableMapOf<String, WasteOutboxEntity>()

    override suspend fun insert(entity: WasteOutboxEntity) {
        if (!rows.containsKey(entity.messageId)) rows[entity.messageId] = entity
    }

    override suspend fun getPending(): List<WasteOutboxEntity> =
        rows.values.filter { it.status == WasteOutboxEntity.Status.PENDING }

    override fun pendingCount(): Flow<Int> = flowOf(0)

    override suspend fun findByMessageId(messageId: String): WasteOutboxEntity? = rows[messageId]

    override suspend fun recordAttempt(messageId: String, nowEpochMs: Long) {
        rows[messageId]?.let { rows[messageId] = it.copy(attemptCount = it.attemptCount + 1, lastAttemptEpochMs = nowEpochMs) }
    }

    override suspend fun markAccepted(messageId: String) {
        rows[messageId]?.let {
            if (it.status == WasteOutboxEntity.Status.PENDING) {
                rows[messageId] = it.copy(status = WasteOutboxEntity.Status.ACCEPTED)
            }
        }
    }

    override suspend fun markRejected(messageId: String, errorCode: String?, reason: String?, nextAction: String?) {
        rows[messageId]?.let {
            if (it.status == WasteOutboxEntity.Status.PENDING) {
                rows[messageId] = it.copy(
                    status = WasteOutboxEntity.Status.REJECTED,
                    errorCode = errorCode,
                    reason = reason,
                    nextAction = nextAction,
                )
            }
        }
    }
}

/** Exercises the actual correlation/DAO-write/emit path in [WasteCollectionResultChannel.handleIncoming]
 * — [WasteCollectionResultChannelTest] above only tests the pure [evaluateOutcome] decision logic,
 * which doesn't see the bugs that lived in [WasteCollectionResultChannel.handleIncoming] itself
 * (a null-parsed payload NPE-ing the process; a terminal row being flipped by a late/duplicate
 * result). */
@OptIn(ExperimentalCoroutinesApi::class)
class WasteCollectionResultChannelHandleIncomingTest {

    private val gson = Gson()

    private fun storedRow(
        messageId: String = "msg-1",
        deviceId: String = "HH-01",
        operatorSessionId: String = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
        collectionId: String = "COL-1",
        bagCode: String = "BAG-01",
        status: String = WasteOutboxEntity.Status.PENDING,
    ) = WasteOutboxEntity(
        messageId = messageId,
        deviceId = deviceId,
        operatorSessionId = operatorSessionId,
        collectionId = collectionId,
        bagCode = bagCode,
        jobNumber = "JOB-2026-0041",
        operatorId = "MO-00427",
        wasteTypeCode = "WT-01",
        collectedBy = "Collector One",
        collectedAtUtc = "2026-07-30T10:15:30.000Z",
        status = status,
        createdAtEpochMs = 0L,
        lastAttemptEpochMs = null,
        attemptCount = 1,
        errorCode = null,
        reason = null,
        nextAction = null,
    )

    private fun resultJson(
        inResponseToMessageId: String = "msg-1",
        deviceId: String = "HH-01",
        operatorSessionId: String = "4dfda8bb-e9bf-4e92-b8a9-acde673fbb83",
        collectionId: String = "COL-1",
        bagCode: String = "BAG-01",
        accepted: Boolean = true,
        errorCode: String? = null,
        reason: String? = null,
        nextAction: String = "start_next_collection",
    ): String = gson.toJson(
        WasteCollectionResultMessage(
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
        ),
    )

    @Test
    fun `a well-formed accepted result marks the stored PENDING row ACCEPTED and emits`() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeWasteOutboxDao()
        dao.rows["msg-1"] = storedRow()
        val channel = WasteCollectionResultChannel(dao, MqttConnectionManager(deviceId = "HH-01"))
        val emitted = mutableListOf<WasteCollectionResultMessage>()
        val job = launch { channel.results.toList(emitted) }

        channel.handleIncoming(resultJson(accepted = true))

        assertEquals(WasteOutboxEntity.Status.ACCEPTED, dao.findByMessageId("msg-1")?.status)
        assertEquals(1, emitted.size)
        job.cancel()
    }

    @Test
    fun `a well-formed rejected result marks the stored PENDING row REJECTED with error details and emits`() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeWasteOutboxDao()
        dao.rows["msg-1"] = storedRow()
        val channel = WasteCollectionResultChannel(dao, MqttConnectionManager(deviceId = "HH-01"))
        val emitted = mutableListOf<WasteCollectionResultMessage>()
        val job = launch { channel.results.toList(emitted) }

        channel.handleIncoming(
            resultJson(
                accepted = false,
                errorCode = "bag_code_in_use",
                reason = "Bag code 'BAG-01' is already awaiting weight for collection 'COL-1'.",
                nextAction = "complete_existing_bag_weight",
            ),
        )

        val stored = dao.findByMessageId("msg-1")
        assertEquals(WasteOutboxEntity.Status.REJECTED, stored?.status)
        assertEquals("bag_code_in_use", stored?.errorCode)
        assertEquals("Bag code 'BAG-01' is already awaiting weight for collection 'COL-1'.", stored?.reason)
        assertEquals("complete_existing_bag_weight", stored?.nextAction)
        assertEquals(1, emitted.size)
        job.cancel()
    }

    @Test
    fun `an unknown inResponseToMessageId is dropped without crashing and leaves the dao unchanged`() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeWasteOutboxDao()
        dao.rows["msg-1"] = storedRow()
        val channel = WasteCollectionResultChannel(dao, MqttConnectionManager(deviceId = "HH-01"))
        val emitted = mutableListOf<WasteCollectionResultMessage>()
        val job = launch { channel.results.toList(emitted) }

        channel.handleIncoming(resultJson(inResponseToMessageId = "msg-does-not-exist"))

        assertEquals(WasteOutboxEntity.Status.PENDING, dao.findByMessageId("msg-1")?.status)
        assertTrue(emitted.isEmpty())
        job.cancel()
    }

    @Test
    fun `an identity-mismatched result leaves the stored row PENDING and unchanged, no emit`() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeWasteOutboxDao()
        dao.rows["msg-1"] = storedRow(bagCode = "BAG-01")
        val channel = WasteCollectionResultChannel(dao, MqttConnectionManager(deviceId = "HH-01"))
        val emitted = mutableListOf<WasteCollectionResultMessage>()
        val job = launch { channel.results.toList(emitted) }

        channel.handleIncoming(resultJson(bagCode = "BAG-02"))

        val stored = dao.findByMessageId("msg-1")
        assertEquals(WasteOutboxEntity.Status.PENDING, stored?.status)
        assertNull(stored?.errorCode)
        assertNull(stored?.reason)
        assertNull(stored?.nextAction)
        assertTrue(emitted.isEmpty())
        job.cancel()
    }

    @Test
    fun `an empty malformed JSON payload is dropped without crashing`() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeWasteOutboxDao()
        dao.rows["msg-1"] = storedRow()
        val channel = WasteCollectionResultChannel(dao, MqttConnectionManager(deviceId = "HH-01"))
        val emitted = mutableListOf<WasteCollectionResultMessage>()
        val job = launch { channel.results.toList(emitted) }

        channel.handleIncoming("")

        assertEquals(WasteOutboxEntity.Status.PENDING, dao.findByMessageId("msg-1")?.status)
        assertTrue(emitted.isEmpty())
        job.cancel()
    }

    @Test
    fun `a result for an already-ACCEPTED row is a no-op, status stays ACCEPTED, no emit`() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeWasteOutboxDao()
        dao.rows["msg-1"] = storedRow(status = WasteOutboxEntity.Status.ACCEPTED)
        val channel = WasteCollectionResultChannel(dao, MqttConnectionManager(deviceId = "HH-01"))
        val emitted = mutableListOf<WasteCollectionResultMessage>()
        val job = launch { channel.results.toList(emitted) }

        channel.handleIncoming(resultJson(accepted = false, errorCode = "some_error", reason = "late/replayed"))

        assertEquals(WasteOutboxEntity.Status.ACCEPTED, dao.findByMessageId("msg-1")?.status)
        assertTrue(emitted.isEmpty())
        job.cancel()
    }
}
