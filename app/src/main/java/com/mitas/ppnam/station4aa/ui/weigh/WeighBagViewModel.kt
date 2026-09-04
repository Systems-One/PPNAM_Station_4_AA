package com.mitas.ppnam.station4aa.ui.weigh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionManager
import com.mitas.ppnam.station4aa.data.rfid.ScanEvent
import com.mitas.ppnam.station4aa.data.rfid.ScanEventBus
import com.mitas.ppnam.station4aa.data.session.OperatorSession
import com.mitas.ppnam.station4aa.data.session.OperatorSessionHolder
import com.mitas.ppnam.station4aa.domain.usecase.RequestWasteCaptureUseCase
import com.mitas.ppnam.station4aa.domain.usecase.WasteCaptureOutcome
import com.mitas.ppnam.station4aa.ui.components.ConnectionStatus
import com.mitas.ppnam.station4aa.ui.components.connectionStatusFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the screen is currently showing below the Request Weight button. */
sealed interface WeighFeedback {
    /** Station 4 weighed the bag. */
    data class Weighed(
        val bagCode: String,
        val weightKg: Double,
        val capturedBy: String?,
    ) : WeighFeedback

    /** Anything that went wrong, already phrased for the operator. [canRetrySameBag] mirrors
     * `CaptureRefusal.keepBagCode` — true only when tapping again could actually succeed. */
    data class Problem(val message: String, val canRetrySameBag: Boolean) : WeighFeedback
}

/**
 * Drives the handheld-triggered weigh of contract 5.1.0 §9.2: the operator puts a registered bag
 * on Station 4's scale, scans its code here, and asks Station 4 to weigh it. Station 4 does the
 * measuring — it is the only participant wired to the scale — and attributes the capture to this
 * handheld's own signed-in operator, so the station PC can be signed out entirely.
 *
 * Deliberately separate from [com.mitas.ppnam.station4aa.ui.waste.WasteGatheringViewModel]: a
 * weigh is keyed only by `bagCode` and happens at a different time and place from the registration
 * that created the collection, so it must work for any pending bag — including one another
 * operator registered — and not only for the transaction this handheld just finished.
 */
class WeighBagViewModel(
    private val connectionManager: MqttConnectionManager,
    private val sessionHolder: OperatorSessionHolder,
    private val scanEventBus: ScanEventBus,
    private val requestCapture: RequestWasteCaptureUseCase,
) : ViewModel() {

    val connectionStatus: StateFlow<ConnectionStatus> = connectionStatusFlow(
        connectionManager.connectionState,
        connectionManager.stationOnline,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionStatus.Offline)

    val session: StateFlow<OperatorSession?> = sessionHolder.session

    val operatorName: StateFlow<String> = session
        .map { it?.operatorName?.ifBlank { it.operatorId } ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _bagCode = MutableStateFlow("")
    val bagCode: StateFlow<String> = _bagCode.asStateFlow()

    private val _feedback = MutableStateFlow<WeighFeedback?>(null)
    val feedback: StateFlow<WeighFeedback?> = _feedback.asStateFlow()

    /** True while a weigh is in flight. Guards against a double-tap asking Station 4 to weigh the
     * same pan twice — the same protection `onReviewConfirmed` gives the collection publish. */
    private val _isWeighing = MutableStateFlow(false)
    val isWeighing: StateFlow<Boolean> = _isWeighing.asStateFlow()

    init {
        viewModelScope.launch {
            scanEventBus.events.filterIsInstance<ScanEvent.Barcode>().collect { event ->
                // A scan always replaces whatever is in the field and clears the previous answer:
                // the operator has physically moved to a different bag.
                _bagCode.value = event.value.trim()
                _feedback.value = null
            }
        }
    }

    fun onBagCodeChanged(value: String) {
        _bagCode.value = value
        _feedback.value = null
    }

    fun onRequestWeight() {
        if (_isWeighing.value) return
        val operatorSessionId = sessionHolder.currentSessionIdOrEmpty()
        _isWeighing.value = true
        _feedback.value = null

        viewModelScope.launch {
            try {
                when (val outcome = requestCapture.request(operatorSessionId, _bagCode.value)) {
                    is WasteCaptureOutcome.Weighed -> {
                        _feedback.value = WeighFeedback.Weighed(
                            bagCode = outcome.bagCode,
                            weightKg = outcome.weightKg,
                            capturedBy = outcome.capturedBy,
                        )
                        // The bag is spent: its collection is captured and its code is eligible
                        // for a brand new collection. Holding it would invite a second weigh that
                        // could only ever come back `bag_already_captured`.
                        _bagCode.value = ""
                    }

                    is WasteCaptureOutcome.Refused -> {
                        val refusal = outcome.refusal
                        _feedback.value = WeighFeedback.Problem(refusal.message, refusal.keepBagCode)
                        if (!refusal.keepBagCode) _bagCode.value = ""
                        // `nextAction: login` — the session is gone, so drop it and let
                        // SessionWatcher take the operator back to the login screen. Doing this
                        // last means the message is already on screen as the navigation happens.
                        if (refusal.requiresLogin) sessionHolder.clear()
                    }

                    is WasteCaptureOutcome.InvalidBagCode ->
                        _feedback.value = WeighFeedback.Problem(outcome.message, canRetrySameBag = false)

                    is WasteCaptureOutcome.Failed ->
                        // Transport trouble, not a station verdict: nothing was weighed, so the
                        // scanned code stays put for another attempt.
                        _feedback.value = WeighFeedback.Problem(outcome.message, canRetrySameBag = true)
                }
            } finally {
                _isWeighing.value = false
            }
        }
    }
}
