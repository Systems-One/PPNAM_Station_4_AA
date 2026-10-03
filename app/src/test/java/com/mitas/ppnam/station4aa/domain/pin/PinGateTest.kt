package com.mitas.ppnam.station4aa.domain.pin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit group (c): the lockout must survive leaving the screen, a blank submit must not count,
 * and the countdown must be derived from a persisted deadline. */
class PinGateTest {

    private var now = 1_000_000L
    private val store = InMemoryPinLockoutStore()
    private fun gate() = PinGate(correctPin = "079545", store = store, now = { now })

    @Test
    fun `the correct PIN unlocks and resets the counter`() {
        val gate = gate()
        gate.submit("000000")
        assertEquals(PinGateResult.Unlocked, gate.submit("079545"))
        assertEquals(0, store.failedAttempts)
    }

    @Test
    fun `a wrong PIN reports attempts left`() {
        assertEquals(PinGateResult.Wrong(attemptsLeft = 4), gate().submit("000000"))
        assertEquals(PinGateResult.Wrong(attemptsLeft = 3), gate().submit("000000"))
    }

    @Test
    fun `a blank PIN is not an attempt`() {
        val gate = gate()
        assertEquals(PinGateResult.Blank, gate.submit(""))
        assertEquals(PinGateResult.Blank, gate.submit("   "))
        assertEquals(0, store.failedAttempts)
        assertEquals(PinGateResult.Wrong(attemptsLeft = 4), gate.submit("1"))
    }

    @Test
    fun `five wrong attempts lock the gate for thirty seconds`() {
        val gate = gate()
        repeat(4) { gate.submit("000000") }
        assertEquals(PinGateResult.LockedOut(remainingMs = 30_000L), gate.submit("000000"))
        assertTrue(gate.isLockedOut)
        now += 29_000
        assertEquals(PinGateResult.LockedOut(remainingMs = 1_000L), gate.submit("079545"))
        now += 1_000
        assertFalse(gate.isLockedOut)
        assertEquals(PinGateResult.Unlocked, gate.submit("079545"))
    }

    @Test
    fun `the lockout survives a new gate instance because it lives in the store`() {
        repeat(5) { gate().submit("000000") }
        // A fresh ViewModel (new screen, or process restart with the same prefs) must still be locked.
        assertTrue(gate().isLockedOut)
        assertEquals(PinGateResult.LockedOut(remainingMs = 30_000L), gate().submit("079545"))
    }

    @Test
    fun `messages read the way the audit glossary says`() {
        assertEquals("Incorrect PIN. 1 attempt left before lockout.", wrongPinMessage(1))
        assertEquals("Incorrect PIN. 4 attempts left before lockout.", wrongPinMessage(4))
        assertEquals("Too many attempts. Try again in 30s.", lockoutMessage(30_000L))
        assertEquals("Too many attempts. Try again in 1s.", lockoutMessage(1L))
    }
}
