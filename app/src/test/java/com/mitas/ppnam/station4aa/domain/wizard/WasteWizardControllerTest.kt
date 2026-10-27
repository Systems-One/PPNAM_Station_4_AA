package com.mitas.ppnam.station4aa.domain.wizard

import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
        controller.resolveOperator("0000427", "Production Operator One")
        controller.confirmCategory(process)
        controller.confirmWasteType(bubbleBreaks)
        return controller
    }

    /** What the ViewModel does on the operator step: validate the card, then apply Station 4's name. */
    private fun WasteWizardController.resolveOperator(card: String, name: String) {
        assertEquals(ScanDispatchResult.LookupOperator(card), beginOperatorLookup(card))
        assertTrue(applyResolvedOperator(card, name))
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
        controller.resolveOperator("0000427", "Production Operator One")
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
        assertEquals("0000427", draft.operatorId)
        assertEquals("Production Operator One", draft.operatorName)
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
        controller.resolveOperator("MO-1", "Operator")
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
    fun `re-entering the same bag code from review returns straight to review`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_BAG)
        assertEquals(WizardStep.SCAN_BAG, controller.step)

        assertNull(controller.submitBagCode("BAG-01"))

        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals("Production Operator One", controller.draft.operatorName)
    }

    @Test
    fun `a different bag from review clears the operator and asks for the card again`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_BAG)

        assertNull(controller.submitBagCode("BAG-02"))

        assertEquals(WizardStep.SCAN_OPERATOR, controller.step)
        assertEquals("BAG-02", controller.draft.bagCode)
        assertNull(controller.draft.operatorId)
        assertNull(controller.draft.operatorName)
        // Nothing else was disturbed, and the re-resolved card lands back on review.
        assertEquals("JOB-2026-0041", controller.draft.jobNumber)
        assertEquals(bubbleBreaks, controller.draft.wasteType)
        controller.resolveOperator("0000427", "Production Operator One")
        assertEquals(WizardStep.REVIEW, controller.step)
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
    fun `editing the operator returns straight to review`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_OPERATOR)
        controller.resolveOperator("0000999", "Production Operator Two")
        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals("0000999", controller.draft.operatorId)
        assertEquals("Production Operator Two", controller.draft.operatorName)
    }

    @Test
    fun `a new card scan drops the previous name until Station 4 resolves the new one`() {
        val controller = completedController()
        controller.editField(WizardStep.SCAN_OPERATOR)
        controller.beginOperatorLookup("0000999")
        assertNull(controller.draft.operatorId)
        assertNull(controller.draft.operatorName)
        assertEquals(WizardStep.SCAN_OPERATOR, controller.step)
    }

    @Test
    fun `the operator step validates the card and keeps leading zeroes`() {
        val controller = WasteWizardController()
        controller.submitBagCode("BAG-01")
        controller.submitJobNumber("JOB-1")
        assertEquals(ScanDispatchResult.LookupOperator("0000427"), controller.handleScannedValue("  0000427 "))
        assertTrue(controller.beginOperatorLookup("N/A") is ScanDispatchResult.Applied)
        assertEquals(WizardStep.SCAN_OPERATOR, controller.step)
    }

    @Test
    fun `a late resolution after leaving the operator step changes nothing`() {
        val controller = WasteWizardController()
        controller.submitBagCode("BAG-01")
        controller.submitJobNumber("JOB-1")
        controller.beginOperatorLookup("0000427")
        controller.cancel()
        assertFalse(controller.applyResolvedOperator("0000427", "Production Operator One"))
        assertEquals(WasteTransactionDraft(), controller.draft)
    }

    @Test
    fun `invalidating the operator from review sends the wizard back to the card step`() {
        val controller = completedController()
        controller.invalidateOperator()
        assertEquals(WizardStep.SCAN_OPERATOR, controller.step)
        assertNull(controller.draft.operatorName)
        controller.resolveOperator("0000427", "Production Operator One")
        assertEquals(WizardStep.REVIEW, controller.step)
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
    @Test
    fun `dismissing the review returns to the waste-type step with the draft intact`() {
        val controller = completedController()
        controller.dismissReview()
        assertEquals(WizardStep.SELECT_WASTE_TYPE, controller.step)
        assertEquals("BAG-01", controller.draft.bagCode)
        assertEquals(bubbleBreaks, controller.draft.wasteType)
        // Re-confirming the type goes straight back to review.
        controller.confirmWasteType(bubbleBreaks)
        assertEquals(WizardStep.REVIEW, controller.step)
    }

    @Test
    fun `dismissReview is only legal from review`() {
        assertThrows(IllegalStateException::class.java) { WasteWizardController().dismissReview() }
    }

    @Test
    fun `hasDraft is false until something is captured and false again after cancel`() {
        val controller = WasteWizardController()
        assertFalse(controller.hasDraft)
        controller.submitBagCode("BAG-01")
        assertTrue(controller.hasDraft)
        controller.cancel()
        assertFalse(controller.hasDraft)
    }
    @Test
    fun `a badge scan on the operator step asks Station 4 for the badge owner`() {
        val controller = WasteWizardController()
        controller.submitBagCode("BAG-01")
        controller.submitJobNumber("JOB-1")
        assertEquals(
            ScanDispatchResult.LookupOperator("BADGE000000000000000001"),
            controller.handleScannedBadge("BADGE000000000000000001"),
        )
        // Nothing is stored until the name comes back.
        assertNull(controller.draft.operatorId)
        assertEquals(WizardStep.SCAN_OPERATOR, controller.step)
    }

    @Test
    fun `a badge scan on the bag or job step is refused with a hint and changes nothing`() {
        val controller = WasteWizardController()
        assertEquals(ScanDispatchResult.Applied("Scan a barcode, not a badge."), controller.handleScannedBadge("BADGE000000000000000001"))
        assertEquals(WizardStep.SCAN_BAG, controller.step)
        assertNull(controller.draft.bagCode)
        controller.submitBagCode("BAG-01")
        assertEquals(ScanDispatchResult.Applied("Scan a barcode, not a badge."), controller.handleScannedBadge("BADGE000000000000000001"))
        assertEquals(WizardStep.SCAN_JOB, controller.step)
        assertNull(controller.draft.jobNumber)
    }

    @Test
    fun `a badge scan is ignored on selection and review steps`() {
        val controller = completedController()
        assertEquals(ScanDispatchResult.Ignored, controller.handleScannedBadge("BADGE000000000000000001"))
        assertEquals(WizardStep.REVIEW, controller.step)
    }
}
