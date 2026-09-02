package com.mitas.ppnam.station4aa.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WasteCollectionValidatorTest {

    @Test
    fun `blank collected by is rejected`() {
        assertEquals("Required.", WasteCollectionValidator.validateCollectedBy(""))
    }

    @Test
    fun `collected by is not placeholder-checked`() {
        // Unlike machineOperatorUserId, collectedBy is an existing handheld value, not freshly
        // typed per transaction — see WasteCollectionValidator's class doc.
        assertNull(WasteCollectionValidator.validateCollectedBy("UNKNOWN"))
    }

    @Test
    fun `collected by over 200 characters is rejected`() {
        val tooLong = "A".repeat(201)
        assertNotNull(WasteCollectionValidator.validateCollectedBy(tooLong))
    }

    @Test
    fun `blank bag code is rejected`() {
        assertEquals("Required.", WasteCollectionValidator.validateBagCode(""))
        assertEquals("Required.", WasteCollectionValidator.validateBagCode("   "))
    }

    @Test
    fun `bag code is not placeholder-checked`() {
        // WastageBagCodePolicy.TryNormalize (Station4's server-side allow-list check) only
        // rejects blank, over-length, or control-character bag codes — never placeholder values.
        // A real configured bag code could plausibly look like a short denylist word.
        assertNull(WasteCollectionValidator.validateBagCode("UNKNOWN"))
        assertNull(WasteCollectionValidator.validateBagCode("n/a"))
    }

    @Test
    fun `control characters in bag code are rejected`() {
        val bell = 7.toChar()
        assertNotNull(WasteCollectionValidator.validateBagCode("BAG-001$bell"))
    }

    @Test
    fun `bag code over 100 characters is rejected`() {
        assertNotNull(WasteCollectionValidator.validateBagCode("A".repeat(101)))
    }

    @Test
    fun `valid bag code is accepted`() {
        assertNull(WasteCollectionValidator.validateBagCode("BAG-00931"))
    }

    @Test
    fun `a valid operator id is accepted`() {
        assertNull(WasteCollectionValidator.validateOperatorId("MO-00427"))
    }

    @Test
    fun `a blank operator id is rejected`() {
        assertEquals("Required.", WasteCollectionValidator.validateOperatorId("   "))
    }

    @Test
    fun `a placeholder operator id is rejected`() {
        assertNotNull(WasteCollectionValidator.validateOperatorId("N/A"))
        assertNotNull(WasteCollectionValidator.validateOperatorId("unknown"))
    }

    @Test
    fun `an over-length operator id is rejected`() {
        assertNotNull(WasteCollectionValidator.validateOperatorId("x".repeat(101)))
    }

    @Test
    fun `a valid job number is accepted`() {
        assertNull(WasteCollectionValidator.validateJobNumber("JOB-2026-0041"))
    }

    @Test
    fun `a blank job number is rejected`() {
        assertEquals("Required.", WasteCollectionValidator.validateJobNumber(""))
    }

    @Test
    fun `a placeholder job number is rejected because it can be hand-typed`() {
        assertNotNull(WasteCollectionValidator.validateJobNumber("N/A"))
        assertNotNull(WasteCollectionValidator.validateJobNumber("NONE"))
    }

    @Test
    fun `a job number containing control characters is rejected`() {
        val bell = 7.toChar()
        assertNotNull(WasteCollectionValidator.validateJobNumber("JOB$bell-1"))
    }

    @Test
    fun `an over-length job number is rejected`() {
        assertNotNull(WasteCollectionValidator.validateJobNumber("9".repeat(101)))
    }

    @Test
    fun `job number surrounding whitespace does not itself fail validation`() {
        assertNull(WasteCollectionValidator.validateJobNumber("  JOB-1  "))
    }
}
