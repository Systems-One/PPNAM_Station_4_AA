package com.mitas.ppnam.station4aa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Station 1's AutoLogout rules, ported: whole minutes, 0 = never, max one day. */
class AutoSignOutTest {

    @Test
    fun `parses whole minutes in range`() {
        assertEquals(0, AutoSignOut.parseMinutes("0"))
        assertEquals(15, AutoSignOut.parseMinutes(" 15 "))
        assertEquals(1440, AutoSignOut.parseMinutes("1440"))
    }

    @Test
    fun `rejects blanks, negatives, decimals and more than a day`() {
        assertNull(AutoSignOut.parseMinutes(""))
        assertNull(AutoSignOut.parseMinutes("-1"))
        assertNull(AutoSignOut.parseMinutes("1.5"))
        assertNull(AutoSignOut.parseMinutes("1441"))
    }

    @Test
    fun `zero means never`() {
        assertEquals(0L, AutoSignOut.timeoutMs(0))
        assertEquals(900_000L, AutoSignOut.timeoutMs(15))
    }
}
