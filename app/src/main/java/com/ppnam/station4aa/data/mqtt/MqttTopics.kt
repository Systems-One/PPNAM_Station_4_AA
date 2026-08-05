package com.ppnam.station4aa.data.mqtt

/**
 * Wire topics for the two independent things this app talks to over MQTT.
 *
 * [WASTE_COLLECTION] is from the normative contract at
 * `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`: one fixed publish-only topic,
 * no subscriptions, no presence topic, no application-level ACK — "the scanner-visible PUBACK...
 * does not confirm that Station 4 accepted the business event."
 *
 * [request]/[responseWildcard] are a *different*, deliberately mirrored contract: Station 2 AA's
 * operator-login request/response pattern (`PPNAM/{deviceId}/req|res/...`), ported on request so
 * Station 4's login works the same way Station 2's does. There is no evidence Station 4's actual
 * backend (`C:\Dev\PPNAM-Station-4`) implements a matching MQTT auth service yet — its only login
 * today is a local SQL Server check inside the WPF desktop app, unreachable from this handheld —
 * so this topic family is speculative/forward-looking the same way schema v2 publishing was
 * before Station 4 supported it. See `data/mqtt/MqttRequestChannel.kt`.
 */
object MqttTopics {
    /** Exact, case-sensitive topic. QoS 1, retain false — see MqttConnectionManager.publish. */
    const val WASTE_COLLECTION = "station4/waste/collection"

    fun request(deviceId: String, requestType: String): String {
        validateSegment(deviceId, "deviceId")
        validateSegment(requestType, "requestType")
        return "PPNAM/$deviceId/req/$requestType"
    }

    fun responseWildcard(deviceId: String): String {
        validateSegment(deviceId, "deviceId")
        return "PPNAM/$deviceId/res/+"
    }

    /** The `waste_collection_result` response topic (contract §3/§12), validated the same way
     * every other deviceId-derived topic in this file is — [deviceId] is operator-editable Settings
     * input and must not be allowed to smuggle an MQTT wildcard segment into a subscription. */
    fun wasteCollectionResult(deviceId: String): String {
        validateSegment(deviceId, "deviceId")
        return "PPNAM/station4/$deviceId/res/waste_collection_result"
    }

    private fun validateSegment(value: String, name: String) {
        require(value.isNotBlank()) { "$name must not be blank" }
        require(value.none { it == '/' || it == '+' || it == '#' }) {
            "$name must not contain '/', '+' or '#': was '$value'"
        }
    }
}
