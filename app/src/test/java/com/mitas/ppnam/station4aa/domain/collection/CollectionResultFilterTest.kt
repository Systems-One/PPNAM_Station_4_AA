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

    @Test
    fun `a result arriving after sign-out and sign-in is shown but does not match the new session`() {
        // Shown: the tracker has no notion of session. Not session-affecting: isResultForSession
        // is false, so a stale operator_session_invalid cannot sign out the new operator.
        val stale = result("sess-old")
        assertTrue(ShownResultTracker().shouldShow(stale))
        assertFalse(isResultForSession(stale, currentSessionId = "sess-new"))
    }

    @Test
    fun `a duplicate replay of a shown result is shown once`() {
        val tracker = ShownResultTracker()
        assertTrue(tracker.shouldShow(result("sess-old")))
        assertFalse(tracker.shouldShow(result("sess-old")))
        assertFalse(tracker.shouldShow(result("sess-new")))
    }

    @Test
    fun `a different collection is shown`() {
        val tracker = ShownResultTracker()
        assertTrue(tracker.shouldShow(result("s")))
        assertTrue(tracker.shouldShow(result("s").copy(collectionId = "COL-2")))
    }
}
