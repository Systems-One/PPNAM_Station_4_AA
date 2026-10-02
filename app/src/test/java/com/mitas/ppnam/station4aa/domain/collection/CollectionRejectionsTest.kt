package com.mitas.ppnam.station4aa.domain.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit S4-04 / group (f): the rejection line never shows the backend sentence or the
 * nextAction token ("Session is malformed, inactive, expired, or wrong-device. (login)"). */
class CollectionRejectionsTest {

    @Test
    fun `bag already in use`() {
        val r = CollectionRejections.describe("BAG-TO-2", "bag_code_in_use")
        assertEquals("Bag BAG-TO-2 is already waiting to be weighed. Weigh it before registering it again.", r.message)
        assertFalse(r.requiresLogin)
    }

    @Test
    fun `session invalid asks for a new login`() {
        val r = CollectionRejections.describe("BAG-TO-2", "operator_session_invalid")
        assertEquals("Your session ended before bag BAG-TO-2 was delivered. Sign in again and register it again.", r.message)
        assertTrue(r.requiresLogin)
    }

    @Test
    fun `payload problems say register again`() {
        for (code in listOf("invalid_payload", "validation_failed", "required_field_missing")) {
            assertEquals(
                "Station 4 could not read the collection for bag BAG-1. Register it again.",
                CollectionRejections.describe("BAG-1", code).message,
            )
        }
    }

    @Test
    fun `an unknown code names itself and points at a manager`() {
        assertEquals(
            "Station 4 rejected bag BAG-1 (future_code). Ask a manager.",
            CollectionRejections.describe("BAG-1", "future_code").message,
        )
        assertEquals(
            "Station 4 rejected bag BAG-1. Register it again.",
            CollectionRejections.describe("BAG-1", null).message,
        )
    }
}
