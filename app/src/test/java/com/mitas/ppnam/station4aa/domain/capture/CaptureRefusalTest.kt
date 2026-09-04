package com.mitas.ppnam.station4aa.domain.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every refusal contract 5.1.0 §9.2 can raise, and the shared envelope refusals §15 says the same
 * validator can answer first. The table below IS the contract's two tables — a code Station 4 can
 * send that this app has no entry for would reach the operator as a bare machine token.
 */
class CaptureRefusalTest {

    /** §9.2's own table, in its published order. */
    private val workflowCodes = listOf(
        "retained_message_not_allowed" to "resend_not_retained",
        "message_id_reused" to "use_new_message_id",
        "invalid_payload" to "correct_and_resubmit",
        "operator_session_invalid" to "login",
        "bag_code_unknown" to "register_collection",
        "bag_already_captured" to "start_next_collection",
        "collection_sync_conflict" to "contact_manager",
        "capture_not_permitted" to "contact_manager",
        "scale_busy" to "retry",
        "scale_no_load" to "place_bag_and_retry",
        "scale_unavailable" to "contact_manager",
        "capture_not_confirmed" to "retry",
    )

    /** §9.2: "the shared envelope check that runs before them can also answer ... each with
     * nextAction: correct_and_resubmit". */
    private val envelopeCodes = listOf(
        "schema_version_unsupported",
        "device_id_mismatch",
        "validation_failed",
        "timestamp_invalid",
        "timestamp_in_future",
    )

    @Test
    fun `every contract code is recognised and explained in the operator's words`() {
        (workflowCodes.map { it.first } + envelopeCodes).forEach { code ->
            val refusal = CaptureRefusals.describe(code, nextAction = null)
            assertTrue("$code fell through to the unknown-code fallback", refusal.isRecognised)
            assertFalse("$code has no operator message", refusal.message.isBlank())
            assertFalse(
                "$code leaks the raw machine token to the operator: '${refusal.message}'",
                refusal.message.contains(code)
            )
        }
    }

    @Test
    fun `each code carries the exact nextAction the contract pairs it with`() {
        workflowCodes.forEach { (code, expected) ->
            assertEquals(code, expected, CaptureRefusals.describe(code, nextAction = null).nextAction)
        }
        envelopeCodes.forEach { code ->
            assertEquals(code, "correct_and_resubmit", CaptureRefusals.describe(code, nextAction = null).nextAction)
        }
    }

    @Test
    fun `only the transient scale conditions keep the scanned bag code for an immediate retry`() {
        // §9.2: scale_busy, scale_no_load and capture_not_confirmed "describe a transient scale
        // condition rather than a terminal outcome ... an operator who fixes the pan and re-sends
        // can still succeed". Everything else is terminal for this bag code, so holding onto it
        // would invite a retry that can never succeed.
        val keeps = (workflowCodes.map { it.first } + envelopeCodes)
            .filter { CaptureRefusals.describe(it, nextAction = null).keepBagCode }
            .toSet()
        assertEquals(setOf("scale_busy", "scale_no_load", "capture_not_confirmed"), keeps)
    }

    @Test
    fun `bag_code_unknown never invites a retry of the same scan`() {
        // §9.2: bag_code_unknown "is a terminal outcome and IS stored ... redelivering the same
        // messageId replays that same stored refusal forever, even after the collection is later
        // registered." The operator must go and register the collection, not tap again.
        val refusal = CaptureRefusals.describe("bag_code_unknown", nextAction = null)
        assertFalse(refusal.keepBagCode)
        assertEquals("register_collection", refusal.nextAction)
    }

    @Test
    fun `an expired session is the one refusal that ends the operator's session`() {
        assertTrue(CaptureRefusals.describe("operator_session_invalid", nextAction = null).requiresLogin)
        (workflowCodes.map { it.first } + envelopeCodes)
            .filter { it != "operator_session_invalid" }
            .forEach { assertFalse(it, CaptureRefusals.describe(it, nextAction = null).requiresLogin) }
    }

    @Test
    fun `an unrecognised code still reaches the operator as something actionable`() {
        // Station 4 may add a code before this app ships again. Showing nothing, or a blank
        // banner, is worse than naming it — this is the one place the raw token is allowed.
        val refusal = CaptureRefusals.describe("some_future_code", nextAction = "contact_manager")
        assertFalse(refusal.isRecognised)
        assertTrue(refusal.message.contains("some_future_code"))
        assertEquals("contact_manager", refusal.nextAction)
        assertFalse(refusal.keepBagCode)
    }

    @Test
    fun `a refusal with no code at all is still explained`() {
        val refusal = CaptureRefusals.describe(null, nextAction = null)
        assertFalse(refusal.isRecognised)
        assertFalse(refusal.message.isBlank())
        assertFalse(refusal.keepBagCode)
    }

    @Test
    fun `a recognised code keeps its contract nextAction even when the station sends another`() {
        // Defence in depth against a station-side regression: the pairing is fixed by §9.2, and
        // the app's own behaviour (keepBagCode, requiresLogin) is derived from the code, so a
        // stray nextAction must not be able to steer the handheld somewhere the code doesn't.
        val refusal = CaptureRefusals.describe("scale_no_load", nextAction = "login")
        assertEquals("place_bag_and_retry", refusal.nextAction)
        assertTrue(refusal.keepBagCode)
        assertFalse(refusal.requiresLogin)
    }
}
