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

    @Test
    fun `station presence lives on the station base node`() {
        // Fleet standard MQTT_TOPIC_STRUCTURE.md section 1/2: presence is the retained payload on
        // the base node itself, never a `/status` sub-topic.
        assertEquals("PPNAM/station_4", MqttTopics.STATION_PRESENCE)
    }

    @Test
    fun `a reserved station segment is rejected as a device id`() {
        // Fleet standard section 1: `res` and `waste` are literal segments Station 4's contract
        // uses directly under its base node, so neither can ever be a scanner id.
        assertThrows(IllegalArgumentException::class.java) { MqttTopics.devicePresence("waste") }
        assertThrows(IllegalArgumentException::class.java) { MqttTopics.responseWildcard("res") }
    }

    @Test
    fun `a configured collection topic containing a wildcard is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            MqttTopics.validatePublishTopic("PPNAM/station_4/+/collection")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MqttTopics.validatePublishTopic("PPNAM/station_4/waste/#")
        }
    }

    @Test
    fun `a configured collection topic with a blank or empty segment is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            MqttTopics.validatePublishTopic("   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MqttTopics.validatePublishTopic("PPNAM//waste/collection")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MqttTopics.validatePublishTopic("/PPNAM/station_4/waste/collection")
        }
    }

    @Test
    fun `the default and a deliberately custom collection topic both validate`() {
        MqttTopics.validatePublishTopic(MqttTopics.WASTE_COLLECTION)
        MqttTopics.validatePublishTopic("plant7/custom/waste")
    }
}
