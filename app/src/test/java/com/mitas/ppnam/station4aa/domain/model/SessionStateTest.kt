package com.mitas.ppnam.station4aa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStateTest {

    @Test
    fun `fromWire parses each known value`() {
        assertEquals(SessionState.Active, SessionState.fromWire("Active"))
        assertEquals(SessionState.Suspended, SessionState.fromWire("Suspended"))
        assertEquals(SessionState.Closed, SessionState.fromWire("Closed"))
    }

    @Test
    fun `fromWire degrades unknown or absent values to Active rather than locking the operator out`() {
        assertEquals(SessionState.Active, SessionState.fromWire(null))
        assertEquals(SessionState.Active, SessionState.fromWire(""))
        assertEquals(SessionState.Active, SessionState.fromWire("some_future_state"))
    }
}
