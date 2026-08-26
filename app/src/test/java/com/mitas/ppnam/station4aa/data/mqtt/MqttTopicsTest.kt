package com.mitas.ppnam.station4aa.data.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MqttTopicsTest {

    @Test
    fun `waste collection topic matches the contract exactly`() {
        // Case-sensitive exact match per Station4_Wastage_MQTT_Contract.md v3.1.0's transport
        // table: the default collection topic moved into the per-station tree on 2026-08-17.
        assertEquals("PPNAM/station_4/waste/collection", MqttTopics.WASTE_COLLECTION)
    }

    @Test
    fun `legacy configured collection topic migrates to the renamed default`() {
        assertEquals(
            "PPNAM/station_4/waste/collection",
            MqttTopics.migrateWasteCollectionTopic("station4/waste/collection")
        )
    }

    @Test
    fun `a deliberately custom collection topic is left alone by migration`() {
        assertEquals(
            "plant7/custom/waste",
            MqttTopics.migrateWasteCollectionTopic("plant7/custom/waste")
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
}
