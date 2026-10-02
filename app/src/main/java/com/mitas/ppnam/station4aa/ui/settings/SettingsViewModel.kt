package com.mitas.ppnam.station4aa.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionManager
import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionState
import com.mitas.ppnam.station4aa.data.session.OperatorSession
import com.mitas.ppnam.station4aa.data.session.OperatorSessionHolder
import com.mitas.ppnam.station4aa.data.catalogue.WasteCatalogueRepository
import com.mitas.ppnam.station4aa.data.settings.SettingsRepository
import com.mitas.ppnam.station4aa.domain.model.AppSettings
import com.mitas.ppnam.station4aa.domain.model.AutoSignOut
import com.mitas.ppnam.station4aa.domain.pin.PinGate
import com.mitas.ppnam.station4aa.domain.pin.PinGateResult
import com.mitas.ppnam.station4aa.domain.pin.PinLockoutStore
import com.mitas.ppnam.station4aa.domain.pin.lockoutMessage
import com.mitas.ppnam.station4aa.domain.pin.wrongPinMessage
import com.mitas.ppnam.station4aa.domain.usecase.AuthUseCase
import com.mitas.ppnam.station4aa.domain.usecase.CatalogueSyncResult
import com.mitas.ppnam.station4aa.domain.usecase.SyncWasteCatalogueUseCase
import com.mitas.ppnam.station4aa.ui.components.ConnectionStatus
import com.mitas.ppnam.station4aa.ui.components.connectionStatusStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PinState {
    object Locked : PinState
    object Unlocked : PinState
}

sealed interface ApplyState {
    object Idle : ApplyState
    object Testing : ApplyState
    data class Success(val message: String) : ApplyState
    data class Failure(val message: String) : ApplyState
}

/** Mirrors Station 2's SettingsViewModel, including the session/logout section now that Station 4
 * has a real login flow (see `com.mitas.ppnam.station4aa.data.mqtt.MqttTopics`' class doc). */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val connectionManager: MqttConnectionManager,
    private val sessionHolder: OperatorSessionHolder,
    private val authUseCase: AuthUseCase,
    private val catalogueRepository: WasteCatalogueRepository,
    private val syncCatalogue: SyncWasteCatalogueUseCase,
    private val pinLockoutStore: PinLockoutStore,
    /** The derived, immutable scanner identity (base standard §2) — surfaced read-only in the
     * Diagnostics card so it can be read off the device for enrolment. Not editable: it is not
     * part of [AppSettings] or the draft at all. */
    val deviceId: String,
) : ViewModel() {

    /** Settings is reachable from the Login screen too (broker config has to be editable before
     * anyone can log in), so any logout affordance built on this must be conditional on it being
     * non-null. */
    val session: StateFlow<OperatorSession?> = sessionHolder.session

    fun logout() {
        viewModelScope.launch { authUseCase.logout() }
    }

    private val pinGate = PinGate(correctPin = "079545", store = pinLockoutStore)

    /** True while the persisted lockout deadline is in the future; the field and Unlock are
     * disabled and [pinLockoutMessage] counts down once a second. */
    var pinLockedOut = mutableStateOf(false)
        private set
    private var lockoutTicker: Job? = null

    var pinInput = mutableStateOf("")
        private set
    var pinState = mutableStateOf<PinState>(PinState.Locked)
        private set
    var pinError = mutableStateOf(false)
        private set

    /**
     * Why the last PIN attempt failed, or null. [pinError] alone drove nothing but the field's
     * red border, so a wrong PIN gave the operator no explanation at all. Deliberately says
     * nothing about the correct PIN's length or shape.
     */
    var pinErrorMessage = mutableStateOf<String?>(null)
        private set
    var pinLockoutMessage = mutableStateOf<String?>(null)
        private set
    var applyState = mutableStateOf<ApplyState>(ApplyState.Idle)
        private set

    /** Result of the Diagnostics card's own "Refresh catalogue" action, kept separate from
     * [applyState] (the broker Test & Apply flow further down): the two are independent
     * operations, and sharing one [ApplyState] meant whichever finished last silently clobbered
     * the other's outcome. */
    var catalogueRefreshState = mutableStateOf<ApplyState>(ApplyState.Idle)
        private set
    /** Host / WebSocket / TLS / username live here; password is blank = "keep the stored one". */
    var draftSettings = mutableStateOf(AppSettings())
        private set
    /** Port and minutes are kept as typed text so the operator can clear the field; they are
     * parsed and validated only on Test & Apply (audit S2-03 pattern, S4-14). */
    var portText = mutableStateOf("")
        private set
    var autoSignOutText = mutableStateOf("")
        private set
    var fieldErrors = mutableStateOf(SettingsFieldErrors())
        private set
    private var storedPassword = ""

    val connectionState: StateFlow<MqttConnectionState> = connectionManager.connectionState
    val stationOnline: StateFlow<Boolean?> = connectionManager.stationOnline

    val connectionStatus: StateFlow<ConnectionStatus> = connectionManager.connectionStatusStateFlow(viewModelScope)

    val catalogueStatus: StateFlow<String> = catalogueRepository.meta
        .map { describeCatalogue(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Catalogue: not loaded")

    /** Manual "Refresh catalogue". Requires an active session, because the request carries the
     * operatorSessionId Station 4 authorizes against. */
    fun refreshCatalogue() {
        val activeSession = session.value
        if (activeSession == null) {
            catalogueRefreshState.value = ApplyState.Failure("Log in before refreshing the catalogue")
            return
        }
        viewModelScope.launch {
            catalogueRefreshState.value = ApplyState.Testing
            catalogueRefreshState.value = when (val result = syncCatalogue.sync(activeSession.operatorSessionId)) {
                is CatalogueSyncResult.Replaced ->
                    ApplyState.Success("Catalogue updated — ${result.typeCount} waste types")
                is CatalogueSyncResult.Failed ->
                    ApplyState.Failure("Catalogue refresh failed: ${result.reason}")
            }
        }
    }

    init {
        if (pinGate.isLockedOut) startLockoutTicker()
        viewModelScope.launch {
            val current = settingsRepository.current()
            storedPassword = current.mqttPassword
            draftSettings.value = current.copy(mqttPassword = "")
            portText.value = current.mqttPort.toString()
            autoSignOutText.value = current.autoSignOutMinutes.toString()
        }
    }

    fun onPinChange(value: String) {
        applyState.value = ApplyState.Idle
        if (value.length <= 6) {
            pinInput.value = value
            pinError.value = false
            pinErrorMessage.value = null
        }
    }

    fun submitPin() {
        when (val result = pinGate.submit(pinInput.value)) {
            PinGateResult.Blank -> {
                pinError.value = true
                pinErrorMessage.value = "Enter the supervisor PIN."
            }
            PinGateResult.Unlocked -> {
                pinInput.value = ""
                pinError.value = false
                pinErrorMessage.value = null
                pinLockoutMessage.value = null
                pinState.value = PinState.Unlocked
            }
            is PinGateResult.Wrong -> {
                pinInput.value = ""
                pinError.value = true
                pinErrorMessage.value = wrongPinMessage(result.attemptsLeft)
                pinLockoutMessage.value = null
            }
            is PinGateResult.LockedOut -> {
                pinInput.value = ""
                pinError.value = true
                pinErrorMessage.value = null
                startLockoutTicker()
            }
        }
    }

    /** Re-derives the countdown from the persisted deadline every second until it passes, so the
     * message is never a static "30s" and a lockout started on a previous visit still shows. */
    private fun startLockoutTicker() {
        lockoutTicker?.cancel()
        lockoutTicker = viewModelScope.launch {
            while (true) {
                val remaining = pinGate.remainingLockoutMs()
                if (remaining <= 0L) {
                    pinLockedOut.value = false
                    pinLockoutMessage.value = null
                    pinError.value = false
                    break
                }
                pinLockedOut.value = true
                pinLockoutMessage.value = lockoutMessage(remaining)
                delay(1_000)
            }
        }
    }

    fun updateDraft(settings: AppSettings) {
        draftSettings.value = settings
        fieldErrors.value = fieldErrors.value.copy(host = null)
    }

    fun updatePortText(value: String) { portText.value = value; fieldErrors.value = fieldErrors.value.copy(port = null) }
    fun updateAutoSignOutText(value: String) { autoSignOutText.value = value; fieldErrors.value = fieldErrors.value.copy(autoSignOut = null) }

    fun testAndApply() {
        val errors = validateSettingsDraft(draftSettings.value.mqttHost, portText.value, autoSignOutText.value)
        fieldErrors.value = errors
        if (errors.hasErrors) {
            applyState.value = ApplyState.Failure("Fix the highlighted fields.")
            return
        }
        val effective = draftSettings.value.copy(
            mqttHost = draftSettings.value.mqttHost.trim(),
            mqttPort = parsePort(portText.value)!!,
            mqttUsername = draftSettings.value.mqttUsername.trim(),
            // Blank keeps the already-provisioned password (Station 1's rule, audit static-20).
            mqttPassword = draftSettings.value.mqttPassword.ifBlank { storedPassword },
            autoSignOutMinutes = AutoSignOut.parseMinutes(autoSignOutText.value)!!,
        )
        applyState.value = ApplyState.Testing
        viewModelScope.launch {
            val result = connectionManager.reconnectWith(effective)
            if (result.isSuccess) {
                settingsRepository.save(effective)
                storedPassword = effective.mqttPassword
                draftSettings.value = effective.copy(mqttPassword = "")
                // The Success row is rendered outside the PIN card (see SettingsScreen) so it
                // stays visible after the re-lock below — the audit found the old one vanished
                // with the card (S4-17).
                applyState.value = ApplyState.Success("Connected — settings saved")
                delay(2_000)
                pinState.value = PinState.Locked
                pinInput.value = ""
            } else {
                applyState.value = ApplyState.Failure(describeConnectFailure(result.exceptionOrNull()))
            }
        }
    }
}
