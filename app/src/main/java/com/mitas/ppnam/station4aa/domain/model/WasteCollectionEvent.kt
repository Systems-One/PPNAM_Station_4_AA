package com.mitas.ppnam.station4aa.domain.model

import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionMessage
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

/**
 * One immutable waste-collection creation event, matching schema v4 of
 * `C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`. v4 drops
 * `machineCode`/`machineName` — the machine is no longer scanned — and renames v3's
 * `machineOperatorUserId` to [operatorId], the production operator who ran the machine that
 * produced this waste. `bagCode` (the physical, reusable wastage-bag barcode) and `collectionId`
 * (this handheld's own globally-unique transaction ID) are two distinct required fields — they are
 * never the same value.
 *
 * The waste *category* is deliberately absent from the payload: it is local-only wizard state that
 * narrows the type list and appears on the review screen, and Station 4 derives the category from
 * the published `wasteTypeCode`.
 *
 * "Generate `messageId`, `collectionId`, and `collectedAtUtc` only for the completed transaction"
 * (contract, "Required handheld workflow" step 13) — see [create], the only place these three
 * fields are minted. Once created, an event's fields never change: a delivery retry MUST reuse
 * them unchanged, and this class has no setters.
 */
data class WasteCollectionEvent(
    val messageId: String,
    val deviceId: String,
    val operatorSessionId: String,
    val collectionId: String,
    val bagCode: String,
    val jobNumber: String,
    val operatorId: String,
    val wasteTypeCode: String,
    val collectedBy: String,
    val collectedAtUtc: String,
) {
    companion object {
        /** The only schema version this app publishes. */
        const val SCHEMA_VERSION = 4

        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        /**
         * Mints a new event for a just-completed handheld transaction. Caller-supplied fields
         * are trimmed but otherwise not re-validated here — see
         * [com.mitas.ppnam.station4aa.domain.validation.WasteCollectionValidator] for the contract's
         * required pre-publish checks, which the UI runs before this is ever called.
         *
         * `collectionId`'s "WC-{yyyyMMdd}-{random}" shape mirrors the contract's earlier example
         * but, absent a server-assigned or device-local sequence, is randomly generated rather
         * than counted — collision odds are negligible for one handheld's daily volume but not
         * zero. A future revision with a real sequence source should replace this.
         */
        fun create(
            bagCode: String,
            jobNumber: String,
            operatorId: String,
            wasteTypeCode: String,
            collectedBy: String,
            deviceId: String,
            operatorSessionId: String,
            now: Instant = Instant.now(),
        ): WasteCollectionEvent = WasteCollectionEvent(
            messageId = UUID.randomUUID().toString(),
            deviceId = deviceId.trim(),
            operatorSessionId = operatorSessionId.trim(),
            collectionId = generateCollectionId(now),
            bagCode = bagCode.trim(),
            jobNumber = jobNumber.trim(),
            operatorId = operatorId.trim(),
            wasteTypeCode = wasteTypeCode.trim(),
            collectedBy = collectedBy.trim(),
            collectedAtUtc = TIMESTAMP_FORMATTER.format(now),
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
        deviceId = deviceId,
        operatorSessionId = operatorSessionId,
        collectionId = collectionId,
        bagCode = bagCode,
        jobNumber = jobNumber,
        operatorId = operatorId,
        wasteTypeCode = wasteTypeCode,
        collectedBy = collectedBy,
        collectedAtUtc = collectedAtUtc,
    )
}
