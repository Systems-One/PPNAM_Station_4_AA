package com.mitas.ppnam.station4aa.domain.collection

import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review focus 3: the result channel replays its last result to every new collector, so a
 * stale `operator_session_invalid` from the previous session must not sign out the operator
 * who just logged in. */
class CollectionResultFilterTest {

    private fun result(session: String) = WasteCollectionResultMessage(
        inResponseToMessageId = "msg-1",
        deviceId = "HH-01",
        operatorSessionId = session,
        collectionId = "COL-1",
        bagCode = "BAG-01",
        accepted = false,
        errorCode = "operator_session_invalid",
        nextAction = "login",
    )

    @Test
    fun `a result for the current session is applied`() {
        assertTrue(isResultForSession(result("sess-new"), currentSessionId = "sess-new"))
    }

    @Test
    fun `a result for a previous session is ignored`() {
        assertFalse(isResultForSession(result("sess-old"), currentSessionId = "sess-new"))
    }

    @Test
    fun `no session at all ignores everything`() {
        assertFalse(isResultForSession(result("sess-old"), currentSessionId = ""))
    }
}
