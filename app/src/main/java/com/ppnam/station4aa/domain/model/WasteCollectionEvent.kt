package com.ppnam.station4aa.domain.model

import com.ppnam.station4aa.data.mqtt.dto.WasteCollectionMessage
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

/**
 * One immutable waste-collection creation event, matching schema v3 of
 * `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md` — confirmed against the real
 * Station4 validator (`PPNAM.Station4.Core/Services/MqttMessageValidator.cs`) since the contract
 * doc's own §9 body still shows stale v2 text (`schemaVersion` integer `2`, no `bagCode`) left over
 * from the 2026-08-05 version bump. `bagCode` (the physical, reusable wastage-bag barcode) and
 * `collectionId` (this handheld's own globally-unique transaction ID) are two distinct required
 * fields — they are never the same value.
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
    val machineCode: String,
    val machineName: String,
    val machineOperatorUserId: String,
    val wasteTypeCode: String,
    val collectedBy: String,
    val collectedAtUtc: String,
) {
    companion object {
        /** The only schema version this app publishes. */
        const val SCHEMA_VERSION = 3

        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        /**
         * Mints a new event for a just-completed handheld transaction. Caller-supplied fields
         * are trimmed but otherwise not re-validated here — see
         * [com.ppnam.station4aa.domain.validation.WasteCollectionValidator] for the contract's
         * required pre-publish checks, which the UI runs before this is ever called.
         *
         * `collectionId`'s "WC-{yyyyMMdd}-{random}" shape mirrors the contract's earlier example
         * but, absent a server-assigned or device-local sequence, is randomly generated rather
         * than counted — collision odds are negligible for one handheld's daily volume but not
         * zero. A future revision with a real sequence source should replace this.
         */
        fun create(
            machineCode: String,
            machineName: String,
            wasteTypeCode: String,
            collectedBy: String,
            machineOperatorUserId: String,
            bagCode: String,
            deviceId: String,
            operatorSessionId: String,
            now: Instant = Instant.now(),
        ): WasteCollectionEvent = WasteCollectionEvent(
            messageId = UUID.randomUUID().toString(),
            deviceId = deviceId.trim(),
            operatorSessionId = operatorSessionId.trim(),
            collectionId = generateCollectionId(now),
            bagCode = bagCode.trim(),
            machineCode = machineCode.trim(),
            machineName = machineName.trim(),
            machineOperatorUserId = machineOperatorUserId.trim(),
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
        machineCode = machineCode,
        machineName = machineName,
        machineOperatorUserId = machineOperatorUserId,
        wasteTypeCode = wasteTypeCode,
        collectedBy = collectedBy,
        collectedAtUtc = collectedAtUtc,
    )
}
