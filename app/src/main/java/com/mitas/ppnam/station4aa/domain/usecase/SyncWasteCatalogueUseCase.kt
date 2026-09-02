package com.mitas.ppnam.station4aa.domain.usecase

import com.mitas.ppnam.station4aa.data.catalogue.WasteCatalogueRepository
import com.mitas.ppnam.station4aa.data.mqtt.MqttOutcome
import com.mitas.ppnam.station4aa.data.mqtt.RequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.describe
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCatalogueRequestPayload
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCatalogueResponse
import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteType
import java.time.Instant

sealed interface CatalogueSyncResult {
    data class Replaced(val categoryCount: Int, val typeCount: Int) : CatalogueSyncResult
    data class Failed(val reason: String) : CatalogueSyncResult
}

/**
 * Pulls the category/waste-type catalogue from Station 4 and replaces the local cache with it.
 *
 * Failure is never fatal and never blocks a collection: the cached (or seeded) catalogue stays in
 * use and the failure is recorded for Diagnostics. The one rule worth stating plainly is that an
 * `accepted: true` response carrying no categories or no waste types is treated as a **failure**,
 * not as an instruction to empty the catalogue — otherwise one bad server-side query would leave
 * every handheld in the plant unable to select a waste type, with nothing on screen explaining why.
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
        return CatalogueSyncResult.Replaced(
            categoryCount = body.categories.size,
            typeCount = body.wasteTypes.size,
        )
    }

    private suspend fun fail(reason: String): CatalogueSyncResult.Failed {
        repository.recordSyncFailure(clock().toString())
        return CatalogueSyncResult.Failed(reason)
    }
}
