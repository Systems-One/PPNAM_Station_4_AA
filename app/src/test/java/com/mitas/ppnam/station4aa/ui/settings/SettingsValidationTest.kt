package com.mitas.ppnam.station4aa.ui.settings

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit §5 "Settings action": Test & Apply keeps Station 4's inline-test behaviour but gains
 * Station 1's host/port validation; S4-07: the timeout is never a library string. */
class SettingsValidationTest {

    @Test
    fun `a complete draft has no errors`() {
        val errors = validateSettingsDraft(host = "10.0.2.2", portText = "9001", autoSignOutText = "15")
        assertFalse(errors.hasErrors)
        assertNull(errors.host)
        assertNull(errors.port)
        assertNull(errors.autoSignOut)
    }

    @Test
    fun `a blank host is required`() {
        assertEquals("Host required", validateSettingsDraft("   ", "9001", "15").host)
    }

    @Test
    fun `an emptied or out-of-range port is invalid rather than silently kept`() {
        assertEquals("Invalid port (1–65535)", validateSettingsDraft("h", "", "15").port)
        assertEquals("Invalid port (1–65535)", validateSettingsDraft("h", "70000", "15").port)
        assertEquals("Invalid port (1–65535)", validateSettingsDraft("h", "0", "15").port)
        assertNull(validateSettingsDraft("h", "443", "15").port)
    }

    @Test
    fun `auto sign-out minutes use Station 1's range`() {
        assertEquals("Enter 0–1440", validateSettingsDraft("h", "9001", "").autoSignOut)
        assertEquals("Enter 0–1440", validateSettingsDraft("h", "9001", "2000").autoSignOut)
        assertNull(validateSettingsDraft("h", "9001", "0").autoSignOut)
    }

    @Test
    fun `parsePort mirrors Station 1`() {
        assertEquals(9001, parsePort(" 9001 "))
        assertNull(parsePort("abc"))
        assertNull(parsePort("65536"))
    }

    @Test
    fun `a connect timeout is described without the library text`() = runTest {
        val failure = try {
            withTimeout(1) { delay(1_000) }
            null
        } catch (e: TimeoutCancellationException) {
            e
        }
        assertTrue(failure is TimeoutCancellationException)
        assertEquals("Could not reach the broker (15 s)", describeConnectFailure(failure))
    }

    @Test
    fun `any other connect failure is generic and actionable`() {
        assertEquals(
            "Could not connect to the broker. Check the host, port and credentials.",
            describeConnectFailure(IllegalStateException("Timed out waiting for 15000 ms")),
        )
        assertEquals(
            "Could not connect to the broker. Check the host, port and credentials.",
            describeConnectFailure(null),
        )
    }
}
