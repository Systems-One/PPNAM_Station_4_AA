package com.mitas.ppnam.station4aa.domain.feedback

import com.mitas.ppnam.station4aa.domain.capture.CaptureRefusals
import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import com.mitas.ppnam.station4aa.domain.usecase.WasteCaptureOutcome
import com.mitas.ppnam.station4aa.domain.usecase.WasteOperatorOutcome
import com.mitas.ppnam.station4aa.domain.usecase.WasteOperatorRefusals
import com.mitas.ppnam.station4aa.domain.wizard.ScanDispatchResult
import com.mitas.ppnam.station4aa.domain.wizard.ScanStageResult
import com.mitas.ppnam.station4aa.domain.wizard.WasteWizardController
import com.mitas.ppnam.station4aa.domain.wizard.WizardStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Suite rule S1 (UI_Design/sounds/README.md): a scan sounds once — success when it is accepted,
 * fail when it is refused, times out, or the app refuses it locally. The raw scanner read and
 * non-scan actions (category and type choices, review navigation, login, settings) make none.
 */
class ScanSoundsTest {

    private val process = WasteCategory(code = "CAT-01", name = "Process", sortOrder = 1)
    private val bubbleBreaks =
        WasteType(code = "WT-01", name = "Bubble breaks", categoryCode = "CAT-01", sortOrder = 1)

    // ---- wizard: bag and job scans (decided on the handheld) ----

    @Test
    fun `an accepted bag code sounds success`() {
        val wizard = WasteWizardController()
        assertEquals(ScanSound.SUCCESS, ScanSounds.forDispatch(wizard.handleScannedValue("BAG-01")))
        assertEquals(WizardStep.SCAN_JOB, wizard.step)
    }

    @Test
    fun `a refused bag code sounds fail`() {
        val wizard = WasteWizardController()
        assertEquals(ScanSound.FAIL, ScanSounds.forDispatch(wizard.handleScannedValue("   ")))
        assertEquals(WizardStep.SCAN_BAG, wizard.step)
    }

    @Test
    fun `an accepted job number sounds success and a placeholder one sounds fail`() {
        val wizard = WasteWizardController()
        wizard.handleScannedValue("BAG-01")
        assertEquals(ScanSound.FAIL, ScanSounds.forDispatch(wizard.handleScannedValue("N/A")))
        assertEquals(ScanSound.SUCCESS, ScanSounds.forDispatch(wizard.handleScannedValue("JOB-2026-0041")))
        assertEquals(WizardStep.SCAN_OPERATOR, wizard.step)
    }

    @Test
    fun `a badge read on the bag or job step is refused locally with a fail`() {
        val wizard = WasteWizardController()
        assertEquals(ScanSound.FAIL, ScanSounds.forStage(wizard.stageScannedBadge("E2801170000002")))
        wizard.handleScannedValue("BAG-01")
        assertEquals(ScanSound.FAIL, ScanSounds.forStage(wizard.stageScannedBadge("E2801170000002")))
    }

    @Test
    fun `a read held for Confirm is silent and sounds once when it is confirmed`() {
        val wizard = WasteWizardController()
        val staged = wizard.stageScannedValue("BAG-01")
        assertEquals(ScanStageResult.Staged("BAG-01"), staged)
        assertNull(ScanSounds.forStage(staged))
        // A repeat of the same read is just as silent, and still changes nothing.
        assertNull(ScanSounds.forStage(wizard.stageScannedValue("BAG-01")))
        assertEquals(WizardStep.SCAN_BAG, wizard.step)
        assertEquals(ScanSound.SUCCESS, ScanSounds.forDispatch(wizard.handleScannedValue("BAG-01")))
    }

    @Test
    fun `a typed bag or job entry sounds like the scan it stands in for`() {
        val wizard = WasteWizardController()
        assertEquals(ScanSound.SUCCESS, ScanSounds.forDispatch(ScanDispatchResult.Applied(wizard.submitBagCode("BAG-01"))))
        assertEquals(ScanSound.FAIL, ScanSounds.forDispatch(ScanDispatchResult.Applied(wizard.submitJobNumber(""))))
    }

    // ---- wizard: operator card (decided by Station 4) ----

    @Test
    fun `an operator card sent for lookup makes no sound until Station 4 answers`() {
        val wizard = WasteWizardController()
        wizard.handleScannedValue("BAG-01")
        wizard.handleScannedValue("JOB-1")
        val dispatch = wizard.handleScannedValue("0000427")
        assertEquals(ScanDispatchResult.LookupOperator("0000427"), dispatch)
        assertNull(ScanSounds.forDispatch(dispatch))
    }

    @Test
    fun `a locally invalid operator card sounds fail`() {
        val wizard = WasteWizardController()
        wizard.handleScannedValue("BAG-01")
        wizard.handleScannedValue("JOB-1")
        assertEquals(ScanSound.FAIL, ScanSounds.forDispatch(wizard.handleScannedValue("UNKNOWN")))
    }

    @Test
    fun `operator resolved sounds success`() {
        assertEquals(
            ScanSound.SUCCESS,
            ScanSounds.forOperatorLookup(WasteOperatorOutcome.Resolved("0000427", "Production Operator One")),
        )
    }

    @Test
    fun `operator refused, invalid or not answered sounds fail`() {
        assertEquals(
            ScanSound.FAIL,
            ScanSounds.forOperatorLookup(WasteOperatorOutcome.Refused(WasteOperatorRefusals.describe("production_operator_unknown"))),
        )
        assertEquals(
            ScanSound.FAIL,
            ScanSounds.forOperatorLookup(WasteOperatorOutcome.Refused(WasteOperatorRefusals.describe("operator_session_invalid"))),
        )
        assertEquals(ScanSound.FAIL, ScanSounds.forOperatorLookup(WasteOperatorOutcome.Invalid("bad card")))
        assertEquals(ScanSound.FAIL, ScanSounds.forOperatorLookup(WasteOperatorOutcome.Failed("Station 4 did not answer")))
    }

    // ---- wizard: steps that do not take scans, and non-scan actions ----

    @Test
    fun `a stray scan on a choice or review step is dropped silently`() {
        val wizard = WasteWizardController()
        wizard.handleScannedValue("BAG-01")
        wizard.handleScannedValue("JOB-1")
        wizard.beginOperatorLookup("0000427")
        wizard.applyResolvedOperator("0000427", "Production Operator One")
        assertEquals(WizardStep.SELECT_CATEGORY, wizard.step)
        assertNull(ScanSounds.forDispatch(wizard.handleScannedValue("BAG-02")))
        assertNull(ScanSounds.forStage(wizard.stageScannedValue("BAG-02")))
        assertNull(ScanSounds.forStage(wizard.stageScannedBadge("E2801170000002")))
        wizard.confirmCategory(process)
        wizard.confirmWasteType(bubbleBreaks)
        assertEquals(WizardStep.REVIEW, wizard.step)
        assertNull(ScanSounds.forDispatch(wizard.handleScannedValue("BAG-02")))
    }

    // ---- collection submit: Station 4's verdict ----

    @Test
    fun `an accepted collection sounds success once`() {
        val sounds = CollectionVerdictSounds()
        sounds.submitted("C-1")
        assertEquals(ScanSound.SUCCESS, sounds.onResult("C-1", accepted = true))
        assertNull(sounds.onResult("C-1", accepted = true))
    }

    @Test
    fun `a rejected collection sounds fail once`() {
        val sounds = CollectionVerdictSounds()
        sounds.submitted("C-1")
        assertEquals(ScanSound.FAIL, sounds.onResult("C-1", accepted = false))
        assertNull(sounds.onResult("C-1", accepted = false))
    }

    @Test
    fun `a collection queued while offline makes no sound until its verdict`() {
        val sounds = CollectionVerdictSounds()
        sounds.submitted("C-1")
        // Queued: no result yet, so nothing to sound. The verdict sounds when it arrives.
        assertEquals(ScanSound.SUCCESS, sounds.onResult("C-1", accepted = true))
    }

    @Test
    fun `a replayed or older result that was not submitted here makes no sound`() {
        val sounds = CollectionVerdictSounds()
        assertNull(sounds.onResult("C-0", accepted = true))
        sounds.submitted("C-1")
        assertNull(sounds.onResult("C-0", accepted = false))
    }

    // ---- Weigh ----

    @Test
    fun `a recorded weight sounds success`() {
        assertEquals(
            ScanSound.SUCCESS,
            ScanSounds.forWeigh(WasteCaptureOutcome.Weighed("BAG-01", 12.4, "Op", "2026-11-06T10:00:00Z", "C-1")),
        )
    }

    @Test
    fun `a refused weigh sounds fail`() {
        assertEquals(ScanSound.FAIL, ScanSounds.forWeigh(WasteCaptureOutcome.Refused(CaptureRefusals.describe("bag_code_unknown", null))))
        assertEquals(ScanSound.FAIL, ScanSounds.forWeigh(WasteCaptureOutcome.Refused(CaptureRefusals.describe("scale_busy", null))))
        assertEquals(ScanSound.FAIL, ScanSounds.forWeigh(WasteCaptureOutcome.Refused(CaptureRefusals.describe("something_new", null))))
    }

    @Test
    fun `a weigh refused locally or not answered sounds fail`() {
        assertEquals(ScanSound.FAIL, ScanSounds.forWeigh(WasteCaptureOutcome.InvalidBagCode("Scan a bag first.")))
        assertEquals(ScanSound.FAIL, ScanSounds.forWeigh(WasteCaptureOutcome.Failed("Station 4 did not answer")))
    }
}
