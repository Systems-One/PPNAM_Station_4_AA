package com.mitas.ppnam.station4aa.ui.waste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionManager
import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionState
import com.mitas.ppnam.station4aa.data.mqtt.WasteCollectionPublisher
import com.mitas.ppnam.station4aa.data.catalogue.WasteCatalogueRepository
import com.mitas.ppnam.station4aa.data.mqtt.dto.WasteCollectionResultMessage
import com.mitas.ppnam.station4aa.data.rfid.ScanEvent
import com.mitas.ppnam.station4aa.data.rfid.ScanEventBus
import com.mitas.ppnam.station4aa.data.session.OperatorSession
import com.mitas.ppnam.station4aa.data.session.OperatorSessionHolder
import com.mitas.ppnam.station4aa.data.settings.SettingsRepository
import com.mitas.ppnam.station4aa.domain.model.WasteCategory
import com.mitas.ppnam.station4aa.domain.model.WasteCollectionEvent
import com.mitas.ppnam.station4aa.domain.model.WasteType
import com.mitas.ppnam.station4aa.domain.usecase.AuthUseCase
import com.mitas.ppnam.station4aa.domain.validation.WasteCollectionValidator
import com.mitas.ppnam.station4aa.domain.wizard.ScanDispatchResult
import com.mitas.ppnam.station4aa.domain.wizard.WasteTransactionDraft
import com.mitas.ppnam.station4aa.domain.wizard.WasteWizardController
import com.mitas.ppnam.station4aa.domain.wizard.WizardStep
import com.mitas.ppnam.station4aa.ui.components.ConnectionStatus
import com.mitas.ppnam.station4aa.ui.components.connectionStatusFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the scan-driven waste collection wizard implementing
 * `C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md`'s
 * "Required handheld workflow", per
 * `docs/superpowers/specs/2026-09-02-phase-1-wastage-bag-flow-design.md`. All step transitions
 * (scan bag → scan job → scan operator → select category → select waste type → review) only mutate
 * local [wizardController] state; [onReviewConfirmed] is the wizard's one and only MQTT publish
 * point.
 */
class WasteGatheringViewModel(
    private val settingsRepository: SettingsRepository,
    private val connectionManager: MqttConnectionManager,
    private val publisher: WasteCollectionPublisher,
    private val sessionHolder: OperatorSessionHolder,
    private val authUseCase: AuthUseCase,
    private val scanEventBus: ScanEventBus,
    private val catalogueRepository: WasteCatalogueRepository,
    /** The derived, immutable scanner identity (base standard §2) — stamped into every published
     * waste-collection event, never read from Settings. */
    private val deviceId: String,
) : ViewModel() {

    private val wizardController = WasteWizardController()

    val connectionStatus: StateFlow<ConnectionStatus> = connectionStatusFlow(
        connectionManager.connectionState,
        connectionManager.stationOnline,
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

    /** Categories offered on SELECT_CATEGORY, straight from the cached catalogue. */
    val categories: StateFlow<List<WasteCategory>> = catalogueRepository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Types offered on SELECT_WASTE_TYPE — only those in the chosen category. Empty until a
     * category is chosen, which is exactly when the step is unreachable. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val typesForSelectedCategory: StateFlow<List<WasteType>> = _draft
        .flatMapLatest { current ->
            current.category?.let { catalogueRepository.typesFor(it.code) } ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set by a failed scan or manual-entry attempt on the active step, or by a failed
     * [WasteCollectionValidator.validateCollectedBy] check on REVIEW's Confirm; cleared on every
     * new attempt, successful advance, or cancel. */
    private val _stepError = MutableStateFlow<String?>(null)
    val stepError: StateFlow<String?> = _stepError.asStateFlow()

    private val _lastQueuedMessage = MutableStateFlow<String?>(null)
    val lastQueuedMessage: StateFlow<String?> = _lastQueuedMessage.asStateFlow()

    /** Whether [lastQueuedMessage] is currently showing a rejection (vs. the routine "Queued ..."
     * confirmation) — lets the UI style the two differently so a rejection doesn't blend in. */
    private val _lastMessageIsError = MutableStateFlow(false)
    val lastMessageIsError: StateFlow<Boolean> = _lastMessageIsError.asStateFlow()

    /** True from the moment [onReviewConfirmed] commits to a publish until that publish's
     * coroutine finishes (success or failure). Guards against a double-tap on the REVIEW dialog's
     * Confirm button minting two events for one physical bag while `publisher.submit` — a full
     * QoS-1 round trip with no timeout — is still in flight; see the wizard design doc's "exactly
     * one MQTT publish per completed transaction" constraint. */
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

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
        viewModelScope.launch {
            publisher.results.collect { result ->
                if (!result.accepted) {
                    _lastQueuedMessage.value = "Bag ${result.bagCode} was rejected: " +
                        (result.reason ?: result.errorCode ?: "unknown reason") +
                        " (${result.nextAction})"
                    _lastMessageIsError.value = true
                }
                // An accepted result needs no new operator-visible message — "Queued ..." already
                // shown at publish time already told them the transaction is in motion, and the
                // wizard has already moved on to the next one.
            }
        }
    }

    fun onBagCodeSubmitted(raw: String) {
        syncFromController(wizardController.submitBagCode(raw))
    }

    fun onJobNumberSubmitted(raw: String) {
        syncFromController(wizardController.submitJobNumber(raw))
    }

    fun onOperatorIdSubmitted(raw: String) {
        syncFromController(wizardController.submitOperatorId(raw))
    }

    fun onCategoryConfirmed(category: WasteCategory) {
        wizardController.confirmCategory(category)
        syncFromController(null)
    }

    fun onWasteTypeConfirmed(type: WasteType) {
        wizardController.confirmWasteType(type)
        syncFromController(null)
    }

    /** Review-screen correction: jump to one capture step and come back once it is satisfied. */
    fun onEditField(target: WizardStep) {
        wizardController.editField(target)
        syncFromController(null)
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
     *
     * Re-entrancy guard: [_isSubmitting] covers both a second tap arriving before the launched
     * coroutine is even scheduled (the early `return` below) and one arriving while the publish
     * round-trip is still in flight (the button is disabled off [isSubmitting] in
     * `WasteGatheringScreen`). Without it, a stalled-but-connected broker link leaves the dialog
     * open and unchanged for the full duration of `publisher.submit`'s no-timeout QoS-1 round
     * trip, and every tap in that window would mint and publish a fresh event for the same bag.
     */
    fun onReviewConfirmed() {
        if (_isSubmitting.value) return

        val current = wizardController.draft
        val bagCode = requireNotNull(current.bagCode) { "REVIEW reached without bagCode" }
        val jobNumber = requireNotNull(current.jobNumber) { "REVIEW reached without jobNumber" }
        val operatorId = requireNotNull(current.operatorId) { "REVIEW reached without operatorId" }
        val wasteType = requireNotNull(current.wasteType) { "REVIEW reached without wasteType" }
        val operatorSessionId = requireNotNull(session.value?.operatorSessionId) {
            "REVIEW reached without an active operator session"
        }

        // Station4 rejects a blank collectedBy with required_field_missing, but PUBACK only
        // confirms broker receipt, not that acceptance — so this must be caught here, before
        // publish, the same way the old single-page form gated Submit on it.
        val collectedByValue = collectedBy.value
        val collectedByError = WasteCollectionValidator.validateCollectedBy(collectedByValue)
        if (collectedByError != null) {
            _stepError.value = collectedByError
            return
        }

        _isSubmitting.value = true
        // requireNotNull/validation guards above fail fast, synchronously, before a coroutine is
        // even launched. `publisher.submit` is `suspend`, so the publish itself lives inside the
        // launch — it cannot run on the synchronous path above.
        viewModelScope.launch {
            try {
                val event = WasteCollectionEvent.create(
                    bagCode = bagCode,
                    jobNumber = jobNumber,
                    operatorId = operatorId,
                    wasteTypeCode = wasteType.code,
                    collectedBy = collectedByValue,
                    deviceId = deviceId,
                    operatorSessionId = operatorSessionId,
                )
                publisher.submit(event)
                wizardController.cancel()
                syncFromController(null)
                // Acceptance criterion 20: a PUBACK (or even just a durable local write) is never
                // presented as Station 4 business acceptance — "Queued", not "Submitted"/"Accepted".
                _lastQueuedMessage.value = "Queued ${event.collectionId} for delivery"
                _lastMessageIsError.value = false
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    private fun syncFromController(error: String?) {
        _step.value = wizardController.step
        _draft.value = wizardController.draft
        _stepError.value = error
    }

    fun dismissLastQueuedMessage() {
        _lastQueuedMessage.value = null
        _lastMessageIsError.value = false
    }

    /** SessionWatcher (mounted at the nav-graph root) handles the actual navigation back to
     * Login once [sessionHolder]'s session goes null — this just triggers that. */
    fun logout() {
        viewModelScope.launch { authUseCase.logout() }
    }
}
