package com.mitas.ppnam.station4aa.domain.collection

import com.mitas.ppnam.station4aa.data.local.WasteOutboxEntity
import com.mitas.ppnam.station4aa.data.local.WasteOutboxEntity.Status
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The banner logic WasteGatheringViewModel exposes (it is just `collectionBannerFlow` over the
 * app-scoped tracker, the Room row and the session), driven without Android: a "recreated
 * ViewModel" is a second flow built over the same tracker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionBannerTest {

    private fun row(status: String, errorCode: String? = null, session: String = "sess-1") = WasteOutboxEntity(
        messageId = "m1", deviceId = "HH-01", operatorSessionId = session, collectionId = "WC-1",
        bagCode = "BAG-1", jobNumber = "JOB-1", operatorId = "MO-1", wasteTypeCode = "WT-01",
        collectedBy = "Operator One", collectedAtUtc = "2026-10-01T10:00:00.000Z", status = status,
        createdAtEpochMs = 0L, lastAttemptEpochMs = null, attemptCount = 0, errorCode = errorCode,
        reason = null, nextAction = null,
    )

    private class Rig(initial: WasteOutboxEntity?) {
        val tracker = CollectionBannerTracker()
        val rowFlow = MutableStateFlow(initial)
        val session = MutableStateFlow("sess-1")
        /** One "ViewModel's" banner. */
        fun banner() = collectionBannerFlow(tracker, { rowFlow }, session)
    }

    @Test
    fun `result arriving before the banner is shown still reads accepted, not Queued`() = runTest {
        // The correlated result already flipped the row before anything observed it.
        val rig = Rig(row(Status.ACCEPTED))
        rig.tracker.track("WC-1", "sess-1")
        assertEquals("Collection WC-1 accepted by Station 4.", rig.banner().first()?.message)
    }

    @Test
    fun `banner follows the row from Queued to a rejection with the stored reason`() = runTest {
        val rig = Rig(row(Status.PENDING))
        rig.tracker.track("WC-1", "sess-1")
        val banner = rig.banner()
        assertEquals("Queued WC-1 for delivery", banner.first()?.message)
        rig.rowFlow.value = row(Status.REJECTED, errorCode = "bag_code_in_use")
        val rejected = banner.first { it?.isError == true }
        assertEquals("Bag BAG-1 is already waiting to be weighed. Weigh it before registering it again.", rejected?.message)
    }

    @Test
    fun `dismiss survives ViewModel recreation`() = runTest {
        val rig = Rig(row(Status.REJECTED, errorCode = "bag_code_in_use"))
        rig.tracker.track("WC-1", "sess-1")
        assertEquals(true, rig.banner().first()?.isError)
        rig.tracker.dismiss()
        // A new ViewModel (screen left and re-entered) builds a fresh flow over the same tracker.
        assertNull(rig.banner().first())
        assertNull(rig.banner().first())
    }

    @Test
    fun `another operator does not see an accepted or queued banner but still sees a rejection`() = runTest {
        val rig = Rig(row(Status.ACCEPTED))
        rig.tracker.track("WC-1", "sess-1")
        rig.session.value = "sess-manager"
        assertNull(rig.banner().first())

        rig.rowFlow.value = row(Status.PENDING)
        assertNull(rig.banner().first())

        rig.rowFlow.value = row(Status.REJECTED, errorCode = "bag_code_in_use")
        assertEquals(true, rig.banner().first()?.isError)
    }

    @Test
    fun `a new submission after a dismiss shows again`() = runTest {
        val rig = Rig(row(Status.ACCEPTED))
        rig.tracker.track("WC-1", "sess-1")
        rig.tracker.dismiss()
        assertNull(rig.banner().first())
        rig.tracker.track("WC-1", "sess-1")
        assertEquals("Collection WC-1 accepted by Station 4.", rig.banner().first()?.message)
    }
}
