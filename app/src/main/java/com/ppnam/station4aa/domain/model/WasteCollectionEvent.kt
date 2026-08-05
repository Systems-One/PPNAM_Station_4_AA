package com.ppnam.station4aa.domain.model

import com.ppnam.station4aa.data.mqtt.dto.WasteCollectionMessage
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

/**
 * One immutable waste-collection creation event, matching schema v2 of
 * `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md` §"Schema v2 payload".
 *
 * "Generate `messageId`, `collectionId`, and `collectedAtUtc` only for the completed transaction"
 * (contract, "Required handheld workflow" step 8) — see [create], the only place these three
 * fields are minted. Once created, an event's fields never change: a delivery retry MUST reuse
 * them unchanged (step 10 / "QoS 1, retries, and idempotency"), and this class has no setters.
 */
data class WasteCollectionEvent(
    val messageId: String,
    val collectionId: String,
    val machineCode: String,
    val machineName: String,
    val wasteTypeCode: String,
    val collectedBy: String,
    val machineOperatorUserId: String,
    val collectedAtUtc: String,
    val bagCode: String = "",
) {
    companion object {
        /** The only schema version this app publishes. */
        const val SCHEMA_VERSION = 2

        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        /**
         * Mints a new event for a just-completed handheld transaction. Caller-supplied fields
         * are trimmed but otherwise not re-validated here — see
         * [com.ppnam.station4aa.domain.validation.WasteCollectionValidator] for the contract's
         * required pre-publish checks, which the UI runs before this is ever called.
         *
         * `collectionId`'s "WC-{yyyyMMdd}-{random}" shape mirrors the contract's example
         * (`WC-20260730-000184`) but, absent a server-assigned or device-local sequence, is
         * randomly generated rather than counted — collision odds are negligible for one
         * handheld's daily volume but not zero. A future revision with a real sequence source
         * should replace this.
         */
        fun create(
            machineCode: String,
            machineName: String,
            wasteTypeCode: String,
            collectedBy: String,
            machineOperatorUserId: String,
            bagCode: String = "",
            now: Instant = Instant.now(),
        ): WasteCollectionEvent = WasteCollectionEvent(
            messageId = UUID.randomUUID().toString(),
            collectionId = generateCollectionId(now),
            machineCode = machineCode.trim(),
            machineName = machineName.trim(),
            wasteTypeCode = wasteTypeCode.trim(),
            collectedBy = collectedBy.trim(),
            machineOperatorUserId = machineOperatorUserId.trim(),
            collectedAtUtc = TIMESTAMP_FORMATTER.format(now),
            bagCode = bagCode.trim(),
        )

        private fun generateCollectionId(now: Instant): String {
            val datePart = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(now)
            val suffix = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
            return "WC-$datePart-$suffix"
        }
    }

    fun toWireMessage(): WasteCollectionMessage = WasteCollectionMessage(
        schemaVersion = SCHEMA_VERSION,
        messageId = messageId,
        collectionId = collectionId,
        machineCode = machineCode,
        machineName = machineName,
        wasteTypeCode = wasteTypeCode,
        collectedBy = collectedBy,
        machineOperatorUserId = machineOperatorUserId,
        collectedAtUtc = collectedAtUtc,
        bagCode = bagCode,
    )
}
