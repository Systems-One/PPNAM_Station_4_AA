package com.mitas.ppnam.station4aa.domain.wizard

import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WasteWizardControllerTest {

    private val process = WasteCategory(code = "CAT-01", name = "Process", sortOrder = 1)
    private val quality = WasteCategory(code = "CAT-02", name = "Quality", sortOrder = 2)
    private val bubbleBreaks =
        WasteType(code = "WT-01", name = "Bubble breaks", categoryCode = "CAT-01", sortOrder = 1)
    private val ghostPrints =
        WasteType(code = "WT-14", name = "Ghost prints", categoryCode = "CAT-02", sortOrder = 14)

    /** Drives a controller to REVIEW with valid values. */
    private fun completedController(): WasteWizardController {
        val controller = WasteWizardController()
        controller.submitBagCode("BAG-01")
        controller.submitJobNumber("JOB-2026-0041")
        controller.submitOperatorId("MO-00427")
        controller.confirmCategory(process)
        controller.confirmWasteType(bubbleBreaks)
        return controller
    }

    @Test
    fun `the wizard starts by scanning the bag`() {
        assertEquals(WizardStep.SCAN_BAG, WasteWizardController().step)
    }

    @Test
    fun `a full pass walks the six steps in the documented order`() {
        val controller = WasteWizardController()
        assertNull(controller.submitBagCode("BAG-01"))
        assertEquals(WizardStep.SCAN_JOB, controller.step)
        assertNull(controller.submitJobNumber("JOB-2026-0041"))
        assertEquals(WizardStep.SCAN_OPERATOR, controller.step)
        assertNull(controller.submitOperatorId("MO-00427"))
        assertEquals(WizardStep.SELECT_CATEGORY, controller.step)
        controller.confirmCategory(process)
        assertEquals(WizardStep.SELECT_WASTE_TYPE, controller.step)
        controller.confirmWasteType(bubbleBreaks)
        assertEquals(WizardStep.REVIEW, controller.step)
    }

    @Test
    fun `the completed draft carries every captured field`() {
        val draft = completedController().draft
        assertEquals("BAG-01", draft.bagCode)
        assertEquals("JOB-2026-0041", draft.jobNumber)
        assertEquals("MO-00427", draft.operatorId)
        assertEquals(process, draft.category)
        assertEquals(bubbleBreaks, draft.wasteType)
    }

    @Test
    fun `an invalid value returns an error and does not advance`() {
        val controller = WasteWizardController()
        assertNotNull(controller.submitBagCode("  "))
        assertEquals(WizardStep.SCAN_BAG, controller.step)
        assertNull(controller.draft.bagCode)
    }

    @Test
    fun `a placeholder job number is refused`() {
        val controller = WasteWizardController()
        controller.submitBagCode("BAG-01")
        assertNotNull(controller.submitJobNumber("N/A"))
        assertEquals(WizardStep.SCAN_JOB, controller.step)
    }

    @Test
    fun `submitted values are trimmed`() {
        val controller = WasteWizardController()
        controller.submitBagCode("  BAG-01  ")
        assertEquals("BAG-01", controller.draft.bagCode)
    }

    @Test
    fun `scans are dispatched to whichever scan step is active`() {
        val controller = WasteWizardController()
        assertEquals(ScanDispatchResult.Applied(null), controller.handleScannedValue("BAG-01"))
        assertEquals(WizardStep.SCAN_JOB, controller.step)
        assertEquals(ScanDispatchResult.Applied(null), controller.handleScannedValue("JOB-1"))
        assertEquals(WizardStep.SCAN_OPERATOR, controller.step)
    }

    @Test
    fun `scans are ignored on the selection and review steps`() {
        val controller = WasteWizardController()
        controller.submitBagCode("BAG-01")
        controller.submitJobNumber("JOB-1")
        controller.submitOperatorId("MO-1")
        assertEquals(ScanDispatchResult.Ignored, controller.handleScannedValue("STRAY"))
        controller.confirmCategory(process)
        assertEquals(ScanDispatchResult.Ignored, controller.handleScannedValue("STRAY"))
        controller.confirmWasteType(bubbleBreaks)
        assertEquals(ScanDispatchResult.Ignored, controller.handleScannedValue("STRAY"))
        assertEquals(WizardStep.REVIEW, controller.step)
    }

    @Test
    fun `cancel resets to the first step and clears the draft`() {
        val controller = completedController()
        controller.cancel()
        assertEquals(WizardStep.SCAN_BAG, controller.step)
        assertEquals(WasteTransactionDraft(), controller.draft)
    }

    @Test
    fun `confirmCategory outside its own step throws`() {
        assertThrows(IllegalStateException::class.java) {
            WasteWizardController().confirmCategory(process)
        }
    }

    @Test
    fun `confirmWasteType outside its own step throws`() {
        assertThrows(IllegalStateException::class.java) {
            WasteWizardController().confirmWasteType(bubbleBreaks)
        }
    }

    @Test
    fun `editField is only legal from review`() {
        val controller = WasteWizardController()
        assertThrows(IllegalStateException::class.java) {
            controller.editField(WizardStep.SCAN_BAG)
        }
    }

    @Test
    fun `editField cannot target review itself`() {
        val controller = completedController()
        assertThrows(IllegalStateException::class.java) {
            controller.editField(WizardStep.REVIEW)
        }
    }

    @Test
    fun `editing the bag code returns straight to review with the new value`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_BAG)
        assertEquals(WizardStep.SCAN_BAG, controller.step)

        assertNull(controller.submitBagCode("BAG-02"))

        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals("BAG-02", controller.draft.bagCode)
        // Nothing else was disturbed.
        assertEquals("JOB-2026-0041", controller.draft.jobNumber)
        assertEquals(bubbleBreaks, controller.draft.wasteType)
    }

    @Test
    fun `editing the job number returns straight to review`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_JOB)
        controller.submitJobNumber("JOB-2026-0099")
        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals("JOB-2026-0099", controller.draft.jobNumber)
    }

    @Test
    fun `editing the operator id returns straight to review`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_OPERATOR)
        controller.submitOperatorId("MO-00999")
        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals("MO-00999", controller.draft.operatorId)
    }

    @Test
    fun `editing the waste type returns straight to review`() {
        val controller = completedController()
        controller.editField(WizardStep.SELECT_WASTE_TYPE)
        controller.confirmWasteType(
            WasteType(code = "WT-02", name = "Startup", categoryCode = "CAT-01", sortOrder = 2)
        )
        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals("WT-02", controller.draft.wasteType?.code)
    }

    @Test
    fun `a failed edit stays on the edited step rather than returning to review`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_BAG)
        assertNotNull(controller.submitBagCode("   "))
        assertEquals(WizardStep.SCAN_BAG, controller.step)
        assertEquals("BAG-01", controller.draft.bagCode)
    }

    @Test
    fun `re-confirming the same category from review returns straight to review`() {
        val controller = completedController()
        controller.editField(WizardStep.SELECT_CATEGORY)
        controller.confirmCategory(process)
        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals(bubbleBreaks, controller.draft.wasteType)
    }

    @Test
    fun `changing the category from review clears the type and forces reselection`() {
        val controller = completedController()
        controller.editField(WizardStep.SELECT_CATEGORY)

        controller.confirmCategory(quality)

        assertEquals(WizardStep.SELECT_WASTE_TYPE, controller.step)
        assertEquals(quality, controller.draft.category)
        assertNull(controller.draft.wasteType)
    }

    @Test
    fun `after a forced reselection the wizard lands back on review`() {
        val controller = completedController()
        controller.editField(WizardStep.SELECT_CATEGORY)
        controller.confirmCategory(quality)

        controller.confirmWasteType(ghostPrints)

        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals(ghostPrints, controller.draft.wasteType)
    }

    @Test
    fun `cancel clears a pending return-to-review`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_BAG)
        controller.cancel()

        // A fresh transaction must walk the whole flow, not jump to review after one scan.
        controller.submitBagCode("BAG-09")
        assertEquals(WizardStep.SCAN_JOB, controller.step)
    }
}
