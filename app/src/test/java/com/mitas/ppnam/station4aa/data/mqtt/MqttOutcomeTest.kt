package com.mitas.ppnam.station4aa.data.mqtt

import org.junit.Assert.assertEquals
import org.junit.Test

/** The fleet standard (audit §5 "Timeout seconds / wording"): 10 s, single attempt, Station 3's
 * wording, and the operator is told to retry. */
class MqttOutcomeTest {

    @Test
    fun `the default request timeout is ten seconds`() {
        assertEquals(10_000L, RequestChannel.DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun `a timeout reads as the station not responding`() {
        assertEquals("Station 4 did not respond. Check the station and retry.", FailureKind.Timeout.describe())
    }

    @Test
    fun `not connected tells the operator where to look`() {
        assertEquals("Not connected to the broker. Check Settings and retry.", FailureKind.NotConnected.describe())
    }

    @Test
    fun `an unreadable reply is still a retry`() {
        assertEquals("Station 4 sent an unreadable reply. Retry.", FailureKind.MalformedResponse.describe())
    }
}
