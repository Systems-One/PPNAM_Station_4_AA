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
 * PPNAM/station_4/waste/collection             Station 4's station-scoped collection event topic
 * ```
 *
 * [WASTE_COLLECTION] is from the normative station contract at
 * `C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`: one
 * publish-only topic, no application-level PUBACK semantics — "the scanner-visible PUBACK... does
 * not confirm that Station 4 accepted the business event". It is the one station-scoped topic the
 * fleet standard allows beyond the shapes above, and it is allowed precisely because it starts
 * with the reserved segment `waste`.
 *
 * The contract v3.2.0 calls that topic "Settings-configured" (lines 11/408/882) and constrains it
 * to the `PPNAM/station_4/waste` subtree (line 152). This handheld deliberately deviates: it is
 * Station 4's scanner and nothing else, so the topic is this constant rather than a Settings
 * field an operator can mistype. That satisfies line 152 by construction, at the cost of needing
 * a rebuild if a deployment ever moves the topic — see CLAUDE.md.
 *
 * [request]/[responseWildcard] carry the operator-login request/response exchange mirrored from
 * Station 2 AA, on `station_4` topics so Station 4 never answers Station 2 traffic on a shared
 * broker. There is still no evidence Station 4's backend implements a matching MQTT auth service
 * — see `data/mqtt/MqttRequestChannel.kt`.
 *
 * [stationPresence] and [devicePresence] are the presence convention: retained `online`/`offline`
 * (and the Last Will) on the base node itself, never a `/status` sub-topic.
 */
object MqttTopics {

    private const val STATION_BASE = "PPNAM/station_4"

    /**
     * Literal segments Station 4's contract uses directly under its base node. Per the fleet
     * standard §1 these can never be a device id — otherwise a misderived id could shadow the
     * collection topic or the (Station 1 only) broadcast tree.
     */
    private val RESERVED_SEGMENTS = setOf("res", "waste")

    /** Station 4's own base node — carries the station's retained presence payload (contract
     * v3.1.0). The scanner subscribes to it so "station offline" is distinguishable from
     * "broker disconnected". */
    const val STATION_PRESENCE = STATION_BASE

    /** The collection topic. Fixed, not configurable — see this class' doc. */
    const val WASTE_COLLECTION = "$STATION_BASE/waste/collection"

    fun request(deviceId: String, requestType: String): String {
        validateSegment(deviceId, "deviceId")
        validateSegment(requestType, "requestType")
        return "$STATION_BASE/$deviceId/req/$requestType"
    }

    fun responseWildcard(deviceId: String): String {
        validateSegment(deviceId, "deviceId")
        return "$STATION_BASE/$deviceId/res/+"
    }

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

    /**
     * Rejects a Settings-configured publish topic that could never work on the wire, loudly and
     * at the point of configuration rather than as a buried publish failure later: MQTT forbids
     * `+`/`#` in a topic a client publishes to, and the fleet standard §1 requires implementations
     * to reject such values rather than let them silently reshape a topic. Empty segments and
     * leading/trailing separators go the same way. The topic is otherwise left alone — the station
     * contract calls it a deployment-configured *exact* topic, so this deliberately does not force
     * it back into `PPNAM/station_4/...`.
     */
    fun validatePublishTopic(topic: String, name: String = "topic") {
        require(topic.isNotBlank()) { "$name must not be blank" }
        require(topic.none { it == '+' || it == '#' }) {
            "$name must not contain the MQTT wildcards '+' or '#': was '$topic'"
        }
        require(topic.split('/').none { it.isEmpty() }) {
            "$name must not contain an empty topic segment: was '$topic'"
        }
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
