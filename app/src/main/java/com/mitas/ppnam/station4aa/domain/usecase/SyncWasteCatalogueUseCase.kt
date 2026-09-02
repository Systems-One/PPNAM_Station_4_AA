package com.mitas.ppnam.station4aa.domain.usecase

import com.mitas.ppnam.station4aa.data.catalogue.WasteCatalogueRepository
import com.mitas.ppnam.station4aa.data.mqtt.MqttOutcome
import com.mitas.ppnam.station4aa.data.mqtt.RequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.describe
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCatalogueRequestPayload
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCatalogueResponse
import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import kotlinx.coroutines.CancellationException
import java.time.Instant

sealed interface CatalogueSyncResult {
    data class Replaced(val categoryCount: Int, val typeCount: Int) : CatalogueSyncResult
    data class Failed(val reason: String) : CatalogueSyncResult
}

/**
 * Pulls the category/waste-type catalogue from Station 4 and replaces the local cache with it.
 *
 * Failure is never fatal and never blocks a collection: the cached (or seeded) catalogue stays in
 * use and the failure is recorded for Diagnostics. This is enforced, not just assumed — [sync] is
 * total. Every step (the MQTT request, and the Room write in [WasteCatalogueRepository.replaceWith])
 * is wrapped, so a thrown exception (e.g. `SQLiteException` on a low-storage handheld) is converted
 * to a [CatalogueSyncResult.Failed] rather than propagating out of this function. Even recording
 * that failure is best-effort: [WasteCatalogueRepository.recordSyncFailure] touches the same DAO
 * that may be the thing that just failed, so a second exception there is swallowed rather than
 * allowed to replace the [CatalogueSyncResult.Failed] this function must still return.
 *
 * The one rule worth stating plainly is that an `accepted: true` response carrying no categories or
 * no waste types is treated as a **failure**, not as an instruction to empty the catalogue —
 * otherwise one bad server-side query would leave every handheld in the plant unable to select a
 * waste type, with nothing on screen explaining why.
 */
class SyncWasteCatalogueUseCase(
    private val requestChannel: RequestChannel,
    private val repository: WasteCatalogueRepository,
    private val deviceId: String,
    private val clock: () -> Instant = Instant::now,
) {
    companion object {
        const val REQUEST_TYPE = "waste_catalogue_requested"
    }

    suspend fun sync(operatorSessionId: String): CatalogueSyncResult {
        return try {
            val outcome = requestChannel.request(
                deviceId = deviceId,
                requestType = REQUEST_TYPE,
                responseClass = WasteCatalogueResponse::class.java,
                payload = WasteCatalogueRequestPayload(operatorSessionId = operatorSessionId),
                operatorSessionId = operatorSessionId,
            )

            val body = when (outcome) {
                is MqttOutcome.Accepted -> outcome.body
                is MqttOutcome.Rejected ->
                    return fail(outcome.reason ?: outcome.errorCode ?: "Station 4 rejected the request")
                is MqttOutcome.NoResponse -> return fail(outcome.kind.describe())
            }

            if (body.categories.isEmpty() || body.wasteTypes.isEmpty()) {
                return fail("Station 4 returned an empty catalogue")
            }

            repository.replaceWith(
                categories = body.categories.map {
                    WasteCategory(code = it.code, name = it.name, sortOrder = it.sortOrder)
                },
                types = body.wasteTypes.map {
                    WasteType(
                        code = it.code,
                        name = it.name,
                        categoryCode = it.categoryCode,
                        sortOrder = it.sortOrder,
                    )
                },
                catalogueVersion = body.catalogueVersion,
                nowUtc = clock().toString(),
            )
            CatalogueSyncResult.Replaced(
                categoryCount = body.categories.size,
                typeCount = body.wasteTypes.size,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e.message ?: "${e::class.java.simpleName} during catalogue sync")
        }
    }

    /**
     * Records a sync failure and returns [CatalogueSyncResult.Failed] with [reason]. Recording is
     * best-effort: [WasteCatalogueRepository.recordSyncFailure] writes through the same DAO whose
     * failure may be why we are here, so if it throws too, that second exception is swallowed —
     * [reason] is still returned rather than letting the recording attempt crash the caller.
     */
    private suspend fun fail(reason: String): CatalogueSyncResult.Failed {
        try {
            repository.recordSyncFailure(clock().toString())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort only: losing the Diagnostics failure timestamp is acceptable,
            // crashing the caller over it is not.
        }
        return CatalogueSyncResult.Failed(reason)
    }
}
