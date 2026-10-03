package com.mitas.ppnam.station4aa.domain.session

import org.junit.Assert.assertEquals
import org.junit.Test

/** Audit group (f)/(i): the sign-out reason is a sentence with a correct plural, never
 * "Signed out after 1 minutes". */
class SessionMessagesTest {
    @Test
    fun `plural minutes`() {
        assertEquals("Signed out after 15 minutes of inactivity.", signedOutAfterMinutes(15))
    }

    @Test
    fun `singular minute`() {
        assertEquals("Signed out after 1 minute of inactivity.", signedOutAfterMinutes(1))
    }
}
