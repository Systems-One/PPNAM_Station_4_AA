package com.mitas.ppnam.station4aa.data.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MqttTopicsTest {

    @Test
    fun `the collection event is an ordinary scanner request on this device's own request topic`() {
        // Contract 5.0.0's breaking transport change: the collection stopped being a station-scoped
        // topic of its own and became a request like every other. Case-sensitive exact match
        // against the transport table in §3.
        assertEquals(
            "PPNAM/station_4/scanner_5c64df8d86a8/req/waste_collection_requested",
            MqttTopics.wasteCollectionRequest("scanner_5c64df8d86a8")
        )
    }

    @Test
    fun `the collection request and its result mirror one device id`() {
        // §9/§12: the payload deviceId, the request topic's device segment and the result topic's
        // device segment are one identity. Station 4 refuses a mismatch *without a reply*, so a
        // divergence here would strand the handheld waiting for a result that can never arrive.
        val deviceId = "scanner_5c64df8d86a8"
        assertEquals("PPNAM/station_4/$deviceId/req/waste_collection_requested", MqttTopics.wasteCollectionRequest(deviceId))
        assertEquals("PPNAM/station_4/$deviceId/res/waste_collection_result", MqttTopics.wasteCollectionResult(deviceId))
    }

    @Test
    fun `the capture request and result sit on the same device's req and res segments`() {
        // Contract 5.1.0 §9.2.
        val deviceId = "scanner_5c64df8d86a8"
        assertEquals(
            "PPNAM/station_4/$deviceId/req/waste_capture_requested",
            MqttTopics.request(deviceId, MqttTopics.WASTE_CAPTURE_REQUESTED)
        )
    }

    @Test
    fun `request topic nests under the station namespace`() {
        assertEquals(
            "PPNAM/station_4/station4_handheld_1/req/scram_start_requested",
            MqttTopics.request("station4_handheld_1", "scram_start_requested")
        )
    }

    @Test
    fun `responseWildcard subscribes to the res segment only`() {
        assertEquals(
            "PPNAM/station_4/station4_handheld_1/res/+",
            MqttTopics.responseWildcard("station4_handheld_1")
        )
    }

    @Test
    fun `waste collection result topic nests under the station namespace`() {
        assertEquals(
            "PPNAM/station_4/station4_handheld_1/res/waste_collection_result",
            MqttTopics.wasteCollectionResult("station4_handheld_1")
        )
    }

    @Test
    fun `device presence lives on the device base node`() {
        assertEquals(
            "PPNAM/station_4/station4_handheld_1",
            MqttTopics.devicePresence("station4_handheld_1")
        )
    }

    @Test
    fun `blank deviceId is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            MqttTopics.request("", "scram_start_requested")
        }
    }

    @Test
    fun `deviceId containing a wildcard is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            MqttTopics.responseWildcard("device+1")
        }
    }

    @Test
    fun `devicePresence validates its device id`() {
        assertThrows(IllegalArgumentException::class.java) {
            MqttTopics.devicePresence("device#1")
        }
    }

    @Test
    fun `station presence lives on the station base node`() {
        // Fleet standard MQTT_TOPIC_STRUCTURE.md section 1/2: presence is the retained payload on
        // the base node itself, never a `/status` sub-topic.
        assertEquals("PPNAM/station_4", MqttTopics.STATION_PRESENCE)
    }

    @Test
    fun `res is rejected as a device id`() {
        // Base standard §1 and contract §3: `res` is the fleet-wide station-broadcast tree and the
        // one segment reserved directly under the station node, so it can never be a scanner id.
        assertThrows(IllegalArgumentException::class.java) { MqttTopics.responseWildcard("res") }
        assertThrows(IllegalArgumentException::class.java) { MqttTopics.devicePresence("res") }
        assertThrows(IllegalArgumentException::class.java) { MqttTopics.wasteCollectionRequest("res") }
    }

    @Test
    fun `waste is an ordinary device id again`() {
        // Contract 5.0.0 retired the `waste` reserved segment along with the station-scoped
        // collection topic it rooted: "`waste` is an ordinary (if unlikely) device id again" (§3).
        // Keeping it reserved would refuse a legitimately derived scanner id.
        assertEquals(
            "PPNAM/station_4/waste/req/waste_collection_requested",
            MqttTopics.wasteCollectionRequest("waste")
        )
        assertEquals("PPNAM/station_4/waste", MqttTopics.devicePresence("waste"))
    }
}
