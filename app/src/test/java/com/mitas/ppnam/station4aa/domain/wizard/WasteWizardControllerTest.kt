package com.mitas.ppnam.station4aa.domain.wizard

import com.mitas.ppnam.station4aa.domain.model.WasteTypeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WasteWizardControllerTest {

    @Test
    fun `starts on SCAN_MACHINE with an empty draft`() {
        val controller = WasteWizardController()
        assertEquals(WizardStep.SCAN_MACHINE, controller.step)
        assertEquals(WasteTransactionDraft(), controller.draft)
    }

    @Test
    fun `submitMachineCode with a valid code advances to SCAN_OPERATOR and trims the value`() {
        val controller = WasteWizardController()
        val error = controller.submitMachineCode("  EXT-04  ")
        assertNull(error)
        assertEquals(WizardStep.SCAN_OPERATOR, controller.step)
        assertEquals("EXT-04", controller.draft.machineCode)
    }

    @Test
    fun `submitMachineCode with a blank code returns an error and does not advance`() {
        val controller = WasteWizardController()
        val error = controller.submitMachineCode("   ")
        assertEquals("Required.", error)
        assertEquals(WizardStep.SCAN_MACHINE, controller.step)
        assertNull(controller.draft.machineCode)
    }

    @Test
    fun `full happy path walk populates every draft field and reaches REVIEW`() {
        val controller = WasteWizardController()

        assertNull(controller.submitMachineCode("EXT-04"))
        assertNull(controller.submitOperatorId("MO-00427"))
        controller.confirmWasteType(WasteTypeCatalog.RECYCLABLE)
        assertNull(controller.submitBagCode("BAG-00931"))

        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals(
            WasteTransactionDraft(
                machineCode = "EXT-04",
                machineOperatorUserId = "MO-00427",
                wasteType = WasteTypeCatalog.RECYCLABLE,
                bagCode = "BAG-00931",
            ),
            controller.draft,
        )
    }

    @Test
    fun `handleScannedValue routes to the field the active step expects`() {
        val controller = WasteWizardController()

        val machineResult = controller.handleScannedValue("EXT-04")
        assertEquals(ScanDispatchResult.Applied(null), machineResult)
        assertEquals(WizardStep.SCAN_OPERATOR, controller.step)

        val operatorResult = controller.handleScannedValue("MO-00427")
        assertEquals(ScanDispatchResult.Applied(null), operatorResult)
        assertEquals(WizardStep.SELECT_WASTE_TYPE, controller.step)
    }

    @Test
    fun `handleScannedValue is ignored during SELECT_WASTE_TYPE and does not mutate the draft`() {
        val controller = WasteWizardController()
        controller.submitMachineCode("EXT-04")
        controller.submitOperatorId("MO-00427")

        val before = controller.draft
        val result = controller.handleScannedValue("stray-scan")

        assertEquals(ScanDispatchResult.Ignored, result)
        assertEquals(WizardStep.SELECT_WASTE_TYPE, controller.step)
        assertEquals(before, controller.draft)
    }

    @Test
    fun `handleScannedValue is ignored during REVIEW`() {
        val controller = WasteWizardController()
        controller.submitMachineCode("EXT-04")
        controller.submitOperatorId("MO-00427")
        controller.confirmWasteType(WasteTypeCatalog.GENERAL)
        controller.submitBagCode("BAG-001")

        val before = controller.draft
        val result = controller.handleScannedValue("stray-scan")

        assertEquals(ScanDispatchResult.Ignored, result)
        assertEquals(WizardStep.REVIEW, controller.step)
        assertEquals(before, controller.draft)
    }

    @Test
    fun `an invalid scanned value is applied as an error and does not advance`() {
        val controller = WasteWizardController()
        val result = controller.handleScannedValue("   ")
        assertTrue(result is ScanDispatchResult.Applied)
        assertEquals("Required.", (result as ScanDispatchResult.Applied).error)
        assertEquals(WizardStep.SCAN_MACHINE, controller.step)
    }

    @Test
    fun `cancel resets to SCAN_MACHINE with an empty draft from any step`() {
        val controller = WasteWizardController()
        controller.submitMachineCode("EXT-04")
        controller.submitOperatorId("MO-00427")
        controller.confirmWasteType(WasteTypeCatalog.GENERAL)

        controller.cancel()

        assertEquals(WizardStep.SCAN_MACHINE, controller.step)
        assertEquals(WasteTransactionDraft(), controller.draft)
    }

    @Test
    fun `cancel from REVIEW also fully resets`() {
        val controller = WasteWizardController()
        controller.submitMachineCode("EXT-04")
        controller.submitOperatorId("MO-00427")
        controller.confirmWasteType(WasteTypeCatalog.GENERAL)
        controller.submitBagCode("BAG-001")
        assertEquals(WizardStep.REVIEW, controller.step)

        controller.cancel()

        assertEquals(WizardStep.SCAN_MACHINE, controller.step)
        assertEquals(WasteTransactionDraft(), controller.draft)
    }

    @Test(expected = IllegalStateException::class)
    fun `confirmWasteType outside SELECT_WASTE_TYPE throws`() {
        WasteWizardController().confirmWasteType(WasteTypeCatalog.GENERAL)
    }
}
