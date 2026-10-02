package com.mitas.ppnam.station4aa.data.mqtt

import com.mitas.ppnam.station4aa.data.local.WasteOutboxDao
import com.mitas.ppnam.station4aa.data.local.WasteOutboxEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeOutboxDao : WasteOutboxDao {
    val rows = mutableMapOf<String, WasteOutboxEntity>()
    override suspend fun insert(entity: WasteOutboxEntity) { rows.putIfAbsent(entity.messageId, entity) }
    override suspend fun getPending(): List<WasteOutboxEntity> =
        rows.values.filter { it.status == WasteOutboxEntity.Status.PENDING }.sortedBy { it.createdAtEpochMs }
    override fun pendingCount(): Flow<Int> = flowOf(0)
    override suspend fun findByMessageId(messageId: String): WasteOutboxEntity? = rows[messageId]
    override suspend fun recordAttempt(messageId: String, nowEpochMs: Long) {
        rows[messageId]?.let { rows[messageId] = it.copy(attemptCount = it.attemptCount + 1, lastAttemptEpochMs = nowEpochMs) }
    }
    override suspend fun markAccepted(messageId: String) {
        rows[messageId]?.let { if (it.status == WasteOutboxEntity.Status.PENDING) rows[messageId] = it.copy(status = WasteOutboxEntity.Status.ACCEPTED) }
    }
    override suspend fun markRejected(messageId: String, errorCode: String?, reason: String?, nextAction: String?) {
        rows[messageId]?.let { if (it.status == WasteOutboxEntity.Status.PENDING) rows[messageId] = it.copy(status = WasteOutboxEntity.Status.REJECTED, errorCode = errorCode, reason = reason, nextAction = nextAction) }
    }
    override suspend fun restampSession(messageId: String, operatorSessionId: String) {
        rows[messageId]?.let { if (it.status == WasteOutboxEntity.Status.PENDING) rows[messageId] = it.copy(operatorSessionId = operatorSessionId) }
    }
}

/** Audit S4-04: a PENDING row queued under a session that no longer exists is re-stamped to the
 * current session before replay, so Station 4 can accept it; terminal rows are untouched. */
class WasteCollectionPublisherTest {

    private fun row(messageId: String, session: String, status: String = WasteOutboxEntity.Status.PENDING) = WasteOutboxEntity(
        messageId = messageId, deviceId = "HH-01", operatorSessionId = session, collectionId = "COL-$messageId",
        bagCode = "BAG-$messageId", jobNumber = "JOB-1", operatorId = "MO-1", wasteTypeCode = "WT-01",
        collectedBy = "Operator One", collectedAtUtc = "2026-10-01T10:00:00.000Z", status = status,
        createdAtEpochMs = 0L, lastAttemptEpochMs = null, attemptCount = 0, errorCode = null, reason = null, nextAction = null,
    )

    private fun publisher(dao: FakeOutboxDao): WasteCollectionPublisher {
        val manager = MqttConnectionManager(deviceId = "HH-01")
        return WasteCollectionPublisher(dao, manager, WasteCollectionResultChannel(dao, manager))
    }

    @Test
    fun `pending rows from an old session are re-stamped and attempted`() = runTest {
        val dao = FakeOutboxDao()
        dao.rows["m1"] = row("m1", "sess-old")
        dao.rows["m2"] = row("m2", "sess-new")

        publisher(dao).retryPending(currentSessionId = "sess-new")

        assertEquals("sess-new", dao.rows["m1"]!!.operatorSessionId)
        assertEquals("sess-new", dao.rows["m2"]!!.operatorSessionId)
        assertEquals(1, dao.rows["m1"]!!.attemptCount)
        assertEquals(1, dao.rows["m2"]!!.attemptCount)
    }

    @Test
    fun `terminal rows are never re-stamped or attempted`() = runTest {
        val dao = FakeOutboxDao()
        dao.rows["m1"] = row("m1", "sess-old", status = WasteOutboxEntity.Status.REJECTED)

        publisher(dao).retryPending(currentSessionId = "sess-new")

        assertEquals("sess-old", dao.rows["m1"]!!.operatorSessionId)
        assertEquals(0, dao.rows["m1"]!!.attemptCount)
    }

    @Test
    fun `without a session nothing is replayed`() = runTest {
        val dao = FakeOutboxDao()
        dao.rows["m1"] = row("m1", "sess-old")

        publisher(dao).retryPending(currentSessionId = "")

        assertEquals("sess-old", dao.rows["m1"]!!.operatorSessionId)
        assertEquals(0, dao.rows["m1"]!!.attemptCount)
    }
}
