package com.mitas.ppnam.station4aa.data.mqtt

/**
 * Wire topics for the two independent things this app talks to over MQTT, both nested under the
 * per-station namespace `PPNAM/station_4/...` (contract v3.1.0, 2026-08-17: the previously
 * un-namespaced `station4/...` and `PPNAM/{deviceId}/...` topics moved into the station tree;
 * payloads unchanged).
 *
 * [WASTE_COLLECTION] is from the normative contract at
 * `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`: one Settings-configured
 * publish-only topic, no application-level ACK — "the scanner-visible PUBACK... does not confirm
 * that Station 4 accepted the business event."
 *
 * [request]/[responseWildcard] are a *different*, deliberately mirrored contract: Station 2 AA's
 * operator-login request/response pattern (`PPNAM/station_2/{deviceId}/req|res/...`), ported on
 * request so Station 4's login works the same way Station 2's does — with `station_4` as the
 * namespace segment so Station 4 never answers Station 2 traffic on a shared broker. There is no
 * evidence Station 4's actual backend (`C:\Dev\PPNAM-Station-4`) implements a matching MQTT auth
 * service yet — its only login today is a local SQL Server check inside the WPF desktop app,
 * unreachable from this handheld — so this topic family is speculative/forward-looking the same
 * way schema v2 publishing was before Station 4 supported it. See `data/mqtt/MqttRequestChannel.kt`.
 *
 * [devicePresence] is the fleet-wide presence convention: retained `online`/`offline` (and the
 * Last Will) on the device's base node `PPNAM/station_4/{deviceId}` — no `/status` sub-topic.
 */
object MqttTopics {

    private const val STATION_BASE = "PPNAM/station_4"

    /** Default collection topic. Deployments may configure an exact override in Settings. */
    const val WASTE_COLLECTION = "$STATION_BASE/waste/collection"

    /** The pre-v3.1.0 default collection topic, before the per-station namespace move. */
    private const val LEGACY_WASTE_COLLECTION = "station4/waste/collection"

    /**
     * Maps a stored Settings value equal to the retired default onto the renamed default, so
     * existing installs follow the topic restructure without re-provisioning. A deliberately
     * custom topic is returned unchanged.
     */
    fun migrateWasteCollectionTopic(stored: String): String =
        if (stored == LEGACY_WASTE_COLLECTION) WASTE_COLLECTION else stored

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
     * wildcard segment into a subscription. */
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
    }
}
