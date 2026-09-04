package com.mitas.ppnam.station4aa.data.mqtt

/**
 * Every topic this app touches, all inside the per-station namespace `PPNAM/station_4/...`.
 *
 * The normative shape is the fleet topic structure at
 * `C:\Dev\Clients\PPNAM\MQTT_TOPIC_STRUCTURE.md` §1 (the fleet authority for hierarchy, which
 * `C:\Dev\Clients\PPNAM\Andriod\MQTT_BASE_README.md` §1 mirrors for the Android apps):
 *
 * ```
 * PPNAM/station_4                              station presence   (retained online/offline + LWT)
 * PPNAM/station_4/{deviceId}                   scanner presence   (retained online/offline + LWT)
 * PPNAM/station_4/{deviceId}/req/{type}        scanner -> station request
 * PPNAM/station_4/{deviceId}/res/{type}        station -> scanner response
 * PPNAM/station_4/res/{type}                   station broadcast  (reserved; Station 4 never uses it)
 * ```
 *
 * Those five shapes are now the *whole* list. Contract 5.0.0 aligned Station 4 with the base
 * standard and made the waste collection an ordinary scanner request
 * ([wasteCollectionRequest]) answered on the existing [wasteCollectionResult], retiring the
 * station-scoped `PPNAM/station_4/waste/collection` topic that predated it. Nothing here is
 * configurable at either end — the base standard fixes the hierarchy, so contract §17.45 requires
 * that "Settings exposes no topic to configure". This app's Settings shows the collection topic
 * read-only under Diagnostics purely so support can reconcile it against the broker ACL.
 *
 * That retirement also freed the `waste` segment: `res` is now the only name reserved directly
 * under the station node (§3), so `waste` is an ordinary — if unlikely — derived device id again,
 * and [validateSegment] must not refuse it.
 *
 * [request]/[responseWildcard] carry the schema 4.1 request/response exchange: the operator SCRAM
 * login mirrored from Station 2 AA, the waste catalogue sync ([SyncWasteCatalogueUseCase]), and
 * since contract 5.1.0 the handheld-triggered weigh ([WASTE_CAPTURE_REQUESTED], §9.2). Station 4's
 * backend answers all three — see `MqttScramAuthenticationService.cs`, `MqttCatalogueProcessor.cs`
 * and its capture processor in the sibling WPF repo.
 *
 * [STATION_PRESENCE] and [devicePresence] are the presence convention: retained `online`/`offline`
 * (and the Last Will) on the base node itself, never a `/status` sub-topic.
 */
object MqttTopics {

    private const val STATION_BASE = "PPNAM/station_4"

    /**
     * The one literal segment Station 4's contract uses directly under its base node. Per the
     * fleet standard §1 and contract §3 it can never be a device id: it is the station-broadcast
     * tree, and a scanner that claimed it would have Station 4 answering into a subtree addressed
     * to every handheld at once.
     */
    private val RESERVED_SEGMENTS = setOf("res")

    /** Station 4's own base node — carries the station's retained presence payload (contract
     * v3.1.0). The scanner subscribes to it so "station offline" is distinguishable from
     * "broker disconnected". */
    const val STATION_PRESENCE = STATION_BASE

    /** `{type}` for the contract 5.1.0 §9.2 handheld weigh, published via [request]. */
    const val WASTE_CAPTURE_REQUESTED = "waste_capture_requested"

    /** `{type}` for the contract 5.0.0 collection event, published via [wasteCollectionRequest]. */
    const val WASTE_COLLECTION_REQUESTED = "waste_collection_requested"

    fun request(deviceId: String, requestType: String): String {
        validateSegment(deviceId, "deviceId")
        validateSegment(requestType, "requestType")
        return "$STATION_BASE/$deviceId/req/$requestType"
    }

    fun responseWildcard(deviceId: String): String {
        validateSegment(deviceId, "deviceId")
        return "$STATION_BASE/$deviceId/res/+"
    }

    /**
     * The collection event's topic (contract §3/§9). Since 5.0.0 this is an ordinary request on
     * the publishing handheld's own subtree, not a shared station topic, so [deviceId] MUST be the
     * `deviceId` carried in the payload: Station 4 refuses a mismatch **without publishing a
     * reply**, because the reply is addressed by the topic's id.
     */
    fun wasteCollectionRequest(deviceId: String): String = request(deviceId, WASTE_COLLECTION_REQUESTED)

    /** The `waste_collection_result` response topic (contract §3/§12), validated the same way
     * every other deviceId-derived topic in this file is — [deviceId] is derived on-device now
     * (base standard §2), but defence in depth still refuses to let any value smuggle an MQTT
     * wildcard or a reserved segment into a subscription. */
    fun wasteCollectionResult(deviceId: String): String {
        validateSegment(deviceId, "deviceId")
        return "$STATION_BASE/$deviceId/res/waste_collection_result"
    }

    /** The device's base node — carries its retained presence payload and Last Will. */
    fun devicePresence(deviceId: String): String {
        validateSegment(deviceId, "deviceId")
        return "$STATION_BASE/$deviceId"
    }

    private fun validateSegment(value: String, name: String) {
        require(value.isNotBlank()) { "$name must not be blank" }
        require(value.none { it == '/' || it == '+' || it == '#' }) {
            "$name must not contain '/', '+' or '#': was '$value'"
        }
        require(value !in RESERVED_SEGMENTS) {
            "$name must not be a reserved station segment ${RESERVED_SEGMENTS.sorted()}: was '$value'"
        }
    }
}
