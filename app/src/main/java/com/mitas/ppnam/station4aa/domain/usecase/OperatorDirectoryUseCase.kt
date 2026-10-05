package com.mitas.ppnam.station4aa.domain.usecase

import android.util.Log
import com.mitas.ppnam.station4aa.data.mqtt.EmptyPayload
import com.mitas.ppnam.station4aa.data.mqtt.MqttOutcome
import com.mitas.ppnam.station4aa.data.mqtt.MqttTopics
import com.mitas.ppnam.station4aa.data.mqtt.RequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.describe
import com.mitas.ppnam.station4aa.data.mqtt.dto.OperatorListResponse
import com.mitas.ppnam.station4aa.domain.login.OperatorDirectoryStore
import com.mitas.ppnam.station4aa.domain.model.OperatorEntry
import kotlinx.coroutines.CancellationException

private const val TAG = "OperatorDirectory"

/**
 * The login screen's operator directory (contract 5.3.0 "Operator directory (pre-login)", fleet
 * parity with Station 1 3.2.0 §4.5): asks Station 4 for the list of operators who can complete
 * a password login and keeps the last accepted list on the device so the dropdown is populated
 * before, or without, an answer.
 *
 * Display-only, and never allowed to break login: [refresh] is total. A rejection, a timeout, an
 * unparseable reply or a thrown exception all return `null` and leave the cache exactly as it was;
 * the operator still types a username that is not listed and signs in as before. The cache is only
 * ever written from an accepted response — an accepted *empty* list is valid ("no password
 * operators provisioned") and does replace it.
 *
 * Pre-login, so the request carries no session (`operatorSessionId` stays `""`) and the payload is
 * the bare schema 4.1 envelope ([EmptyPayload]).
 */
class OperatorDirectoryUseCase(
    private val requestChannel: RequestChannel,
    private val store: OperatorDirectoryStore,
    private val deviceId: String,
) {
    fun cached(): List<OperatorEntry> = store.load()

    /** The fresh, normalised list on success (now also cached); `null` on any failure, with the
     * cache untouched. */
    suspend fun refresh(): List<OperatorEntry>? {
        return try {
            val outcome = requestChannel.request(
                deviceId = deviceId,
                requestType = MqttTopics.OPERATOR_LIST_REQUESTED,
                responseClass = OperatorListResponse::class.java,
                payload = EmptyPayload,
            )
            when (outcome) {
                is MqttOutcome.Accepted -> {
                    val entries = OperatorEntry.normalize(
                        outcome.body.operators.map { OperatorEntry(username = it.username, displayName = it.displayName) },
                    )
                    store.save(entries)
                    entries
                }
                is MqttOutcome.Rejected -> {
                    Log.w(TAG, "Operator list rejected: ${outcome.errorCode ?: "?"} ${outcome.reason ?: ""}; keeping cached list")
                    null
                }
                is MqttOutcome.NoResponse -> {
                    Log.w(TAG, "Operator list unavailable: ${outcome.kind.describe()}; keeping cached list")
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Operator list refresh failed; keeping cached list", e)
            null
        }
    }
}
