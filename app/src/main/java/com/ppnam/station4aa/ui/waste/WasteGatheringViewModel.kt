package com.ppnam.station4aa.ui.waste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ppnam.station4aa.data.mqtt.MqttConnectionManager
import com.ppnam.station4aa.data.mqtt.MqttConnectionState
import com.ppnam.station4aa.data.mqtt.WasteCollectionPublisher
import com.ppnam.station4aa.data.rfid.ScanEvent
import com.ppnam.station4aa.data.rfid.ScanEventBus
import com.ppnam.station4aa.data.session.OperatorSession
import com.ppnam.station4aa.data.session.OperatorSessionHolder
import com.ppnam.station4aa.data.settings.SettingsRepository
import com.ppnam.station4aa.domain.model.WasteCollectionEvent
import com.ppnam.station4aa.domain.model.WasteTypeCatalog
import com.ppnam.station4aa.domain.usecase.AuthUseCase
import com.ppnam.station4aa.domain.wizard.ScanDispatchResult
import com.ppnam.station4aa.domain.wizard.WasteTransactionDraft
import com.ppnam.station4aa.domain.wizard.WasteWizardController
import com.ppnam.station4aa.domain.wizard.WizardStep
import com.ppnam.station4aa.ui.components.ConnectionStatus
import com.ppnam.station4aa.ui.components.connectionStatusFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the scan-driven waste collection wizard implementing
 * `C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`'s "Required handheld workflow",
 * per `docs/superpowers/specs/2026-08-05-scan-driven-waste-wizard-design.md`. All step transitions
 * (scan machine → scan operator → select+confirm waste type → scan bag) only mutate local
 * [wizardController] state; [onReviewConfirmed] is the wizard's one and only MQTT publish point.
 */
class WasteGatheringViewModel(
    private val settingsRepository: SettingsRepository,
    private val connectionManager: MqttConnectionManager,
    private val publisher: WasteCollectionPublisher,
    private val sessionHolder: OperatorSessionHolder,
    private val authUseCase: AuthUseCase,
    private val scanEventBus: ScanEventBus,
) : ViewModel() {

    private val wizardController = WasteWizardController()

    val connectionStatus: StateFlow<ConnectionStatus> = connectionStatusFlow(
        connectionManager.connectionState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionStatus.Offline)

    /** Durably queued events awaiting PUBACK — surfaced so the operator can see unsynced work
     * exists, per the contract's reconciliation-visibility requirement. */
    val pendingCount: StateFlow<Int> = publisher.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val session: StateFlow<OperatorSession?> = sessionHolder.session

    /** The logged-in operator's own identity — "collectedBy" per the contract. SessionWatcher
     * guarantees this screen is never reached without a session. */
    val collectedBy: StateFlow<String> = session
        .map { it?.operatorName?.ifBlank { it.operatorId } ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _step = MutableStateFlow(wizardController.step)
    val step: StateFlow<WizardStep> = _step.asStateFlow()

    private val _draft = MutableStateFlow(wizardController.draft)
    val draft: StateFlow<WasteTransactionDraft> = _draft.asStateFlow()

    /** Set by a failed scan or manual-entry attempt on the active step; cleared on every new
     * attempt, successful advance, or cancel. */
    private val _stepError = MutableStateFlow<String?>(null)
    val stepError: StateFlow<String?> = _stepError.asStateFlow()

    private val _lastQueuedMessage = MutableStateFlow<String?>(null)
    val lastQueuedMessage: StateFlow<String?> = _lastQueuedMessage.asStateFlow()

    init {
        viewModelScope.launch { connectionManager.connect(settingsRepository.current()) }
        // Flush anything durably queued while offline as soon as the broker link comes back —
        // the contract requires retrying with the exact original payload, which retryPending()
        // does by re-reading the immutable rows rather than re-deriving anything.
        viewModelScope.launch {
            connectionManager.connectionState
                .filter { it == MqttConnectionState.CONNECTED }
                .collect { publisher.retryPending() }
        }
        viewModelScope.launch {
            scanEventBus.events.filterIsInstance<ScanEvent.Barcode>().collect { event ->
                when (val result = wizardController.handleScannedValue(event.value)) {
                    is ScanDispatchResult.Applied -> syncFromController(result.error)
                    ScanDispatchResult.Ignored -> Unit
                }
            }
        }
    }

    fun onMachineCodeSubmitted(raw: String) {
        syncFromController(wizardController.submitMachineCode(raw))
    }

    fun onOperatorIdSubmitted(raw: String) {
        syncFromController(wizardController.submitOperatorId(raw))
    }

    fun onWasteTypeConfirmed(type: WasteTypeCatalog) {
        wizardController.confirmWasteType(type)
        syncFromController(null)
    }

    fun onBagCodeSubmitted(raw: String) {
        syncFromController(wizardController.submitBagCode(raw))
    }

    /** Available on every step, including the review dialog. Always a full reset — there is no
     * partial-edit recovery path. */
    fun onCancelTransaction() {
        wizardController.cancel()
        syncFromController(null)
    }

    /**
     * The review dialog's Confirm action — the wizard's one and only publish point. Builds the
     * complete event from the finished draft plus the session's [collectedBy], durably queues it
     * exactly like the previous single-page form did (see [WasteCollectionPublisher.submit]),
     * then resets the wizard for the next transaction.
     */
    fun onReviewConfirmed() {
        val current = wizardController.draft
        val machineCode = requireNotNull(current.machineCode) { "REVIEW reached without machineCode" }
        val machineOperatorUserId = requireNotNull(current.machineOperatorUserId) {
            "REVIEW reached without machineOperatorUserId"
        }
        val wasteType = requireNotNull(current.wasteType) { "REVIEW reached without wasteType" }
        val bagCode = requireNotNull(current.bagCode) { "REVIEW reached without bagCode" }
        val operatorSessionId = requireNotNull(session.value?.operatorSessionId) {
            "REVIEW reached without an active operator session"
        }

        // requireNotNull guards above fail fast, synchronously, before a coroutine is even
        // launched. `settingsRepository.current()` is `suspend`, so event construction itself
        // moves inside the launch — it cannot run on the synchronous path above.
        viewModelScope.launch {
            val event = WasteCollectionEvent.create(
                machineCode = machineCode,
                machineName = machineCode,
                wasteTypeCode = wasteType.code,
                collectedBy = collectedBy.value,
                machineOperatorUserId = machineOperatorUserId,
                bagCode = bagCode,
                deviceId = settingsRepository.current().deviceId,
                operatorSessionId = operatorSessionId,
            )
            publisher.submit(event)
            wizardController.cancel()
            syncFromController(null)
            // Acceptance criterion 20: a PUBACK (or even just a durable local write) is never
            // presented as Station 4 business acceptance — "Queued", not "Submitted"/"Accepted".
            _lastQueuedMessage.value = "Queued ${event.collectionId} for delivery"
        }
    }

    private fun syncFromController(error: String?) {
        _step.value = wizardController.step
        _draft.value = wizardController.draft
        _stepError.value = error
    }

    fun dismissLastQueuedMessage() {
        _lastQueuedMessage.value = null
    }

    /** SessionWatcher (mounted at the nav-graph root) handles the actual navigation back to
     * Login once [sessionHolder]'s session goes null — this just triggers that. */
    fun logout() {
        viewModelScope.launch { authUseCase.logout() }
    }
}
