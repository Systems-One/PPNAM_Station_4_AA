package com.mitas.ppnam.station4aa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WasteOutboxDao {

    // IGNORE, not REPLACE: messageId is the primary key and the event is immutable once created
    // (contract: reuse the same messageId/payload on every retry). A second insert of the same
    // messageId is exactly that retry path re-queuing, not a new event to replace the old row.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: WasteOutboxEntity)

    @Query("SELECT * FROM waste_outbox WHERE status = 'PENDING' ORDER BY createdAtEpochMs ASC")
    suspend fun getPending(): List<WasteOutboxEntity>

    @Query("SELECT COUNT(*) FROM waste_outbox WHERE status = 'PENDING'")
    fun pendingCount(): Flow<Int>

    @Query("SELECT * FROM waste_outbox WHERE messageId = :messageId LIMIT 1")
    suspend fun findByMessageId(messageId: String): WasteOutboxEntity?

    @Query(
        "UPDATE waste_outbox SET attemptCount = attemptCount + 1, lastAttemptEpochMs = :nowEpochMs " +
            "WHERE messageId = :messageId"
    )
    suspend fun recordAttempt(messageId: String, nowEpochMs: Long)

    // "AND status = 'PENDING'" guards the terminal-state invariant race-free in SQL: ACCEPTED/
    // REJECTED are terminal (see WasteOutboxEntity.Status) and must never be overwritten by a
    // late/duplicate/replayed result, even under concurrent handlers.
    @Query("UPDATE waste_outbox SET status = 'ACCEPTED' WHERE messageId = :messageId AND status = 'PENDING'")
    suspend fun markAccepted(messageId: String)

    @Query(
        "UPDATE waste_outbox SET status = 'REJECTED', errorCode = :errorCode, reason = :reason, " +
            "nextAction = :nextAction WHERE messageId = :messageId AND status = 'PENDING'"
    )
    suspend fun markRejected(messageId: String, errorCode: String?, reason: String?, nextAction: String?)
}
