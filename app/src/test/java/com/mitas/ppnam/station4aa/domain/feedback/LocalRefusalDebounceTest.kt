package com.mitas.ppnam.station4aa.domain.feedback

import com.mitas.ppnam.station4aa.domain.usecase.WasteCaptureOutcome
import com.mitas.ppnam.station4aa.domain.wizard.WasteWizardController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Suite rule S3 (UI_Design/sounds/README.md): one trigger pull fires several reads of the same
 * value. The same value refused locally again within 1.5 s is the same scan and plays nothing;
 * a different value is a new scan and plays fail once.
 */
class LocalRefusalDebounceTest {

    private var now = 10_000L
    private val debounce = LocalRefusalDebounce(nowMs = { now })

    @Test
    fun `the first local refusal plays fail`() {
        assertEquals(ScanSound.FAIL, debounce.refuse("505055ABCDEF"))
    }

    @Test
    fun `repeat reads of the same refused value within 1_5 s play nothing`() {
        assertEquals(ScanSound.FAIL, debounce.refuse("505055ABCDEF"))
        now += 100
        assertNull(debounce.refuse("505055ABCDEF"))
        now += 400
        assertNull(debounce.refuse(" 505055ABCDEF\n"))
    }

    @Test
    fun `a held trigger keeps repeating silently, each repeat extending the window`() {
        assertEquals(ScanSound.FAIL, debounce.refuse("505055ABCDEF"))
        repeat(10) {
            now += 1_000
            assertNull(debounce.refuse("505055ABCDEF"))
        }
    }

    @Test
    fun `the same value refused again after 1_5 s of quiet is a new scan and plays fail`() {
        assertEquals(ScanSound.FAIL, debounce.refuse("505055ABCDEF"))
        now += 1_500
        assertEquals(ScanSound.FAIL, debounce.refuse("505055ABCDEF"))
    }

    @Test
    fun `a different value is a new scan and plays fail once`() {
        assertEquals(ScanSound.FAIL, debounce.refuse("505055ABCDEF"))
        now += 100
        assertEquals(ScanSound.FAIL, debounce.refuse("E2801170000099"))
        now += 100
        assertNull(debounce.refuse("E2801170000099"))
        // Going back to the first value is a new scan again.
        assertEquals(ScanSound.FAIL, debounce.refuse("505055ABCDEF"))
    }

    // ---- Station 4: the wizard and Weigh refusals go through filter() ----

    @Test
    fun `a bag code refused by validation and read again within 1_5 s buzzes once`() {
        val wizard = WasteWizardController()
        val tooLong = "B".repeat(101)
        assertEquals(ScanSound.FAIL, debounce.filter(tooLong, ScanSounds.forDispatch(wizard.handleScannedValue(tooLong))))
        now += 200
        assertNull(debounce.filter(tooLong, ScanSounds.forDispatch(wizard.handleScannedValue(tooLong))))
    }

    @Test
    fun `a job number refused by validation and read again within 1_5 s buzzes once`() {
        val wizard = WasteWizardController()
        wizard.handleScannedValue("BAG-01")
        assertEquals(ScanSound.FAIL, debounce.filter("N/A", ScanSounds.forDispatch(wizard.handleScannedValue("N/A"))))
        now += 200
        assertNull(debounce.filter("N/A", ScanSounds.forDispatch(wizard.handleScannedValue("N/A"))))
    }

    @Test
    fun `a badge read on the bag step repeated by one trigger buzzes once`() {
        val wizard = WasteWizardController()
        val badge = "E2801170000002"
        assertEquals(ScanSound.FAIL, debounce.filter(badge, ScanSounds.forDispatch(wizard.handleScannedBadge(badge))))
        repeat(3) {
            now += 50
            assertNull(debounce.filter(badge, ScanSounds.forDispatch(wizard.handleScannedBadge(badge))))
        }
    }

    @Test
    fun `an invalid Weigh bag code tapped again within 1_5 s buzzes once`() {
        val invalid = WasteCaptureOutcome.InvalidBagCode("Scan a bag first.")
        assertEquals(ScanSound.FAIL, debounce.filter("", ScanSounds.forWeigh(invalid)))
        now += 300
        assertNull(debounce.filter("", ScanSounds.forWeigh(invalid)))
    }

    @Test
    fun `success passes through and never starts a debounce window`() {
        val wizard = WasteWizardController()
        assertEquals(ScanSound.SUCCESS, debounce.filter("BAG-01", ScanSounds.forDispatch(wizard.handleScannedValue("BAG-01"))))
        assertEquals(ScanSound.FAIL, debounce.filter("N/A", ScanSound.FAIL))
        assertNull(debounce.filter("x", null))
    }
}
