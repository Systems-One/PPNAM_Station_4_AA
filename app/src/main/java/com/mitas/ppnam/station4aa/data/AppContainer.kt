package com.mitas.ppnam.station4aa.data

import android.content.Context
import android.util.Log
import com.mitas.ppnam.station4aa.data.auth.ScramExchange
import com.mitas.ppnam.station4aa.data.catalogue.WasteCatalogueRepository
import com.mitas.ppnam.station4aa.data.identity.DeviceIdentity
import com.mitas.ppnam.station4aa.data.local.WasteOutboxDatabase
import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionManager
import com.mitas.ppnam.station4aa.data.mqtt.MqttRequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.WasteCollectionPublisher
import com.mitas.ppnam.station4aa.data.mqtt.WasteCollectionResultChannel
import com.mitas.ppnam.station4aa.data.rfid.DataWedgeReceiver
import com.mitas.ppnam.station4aa.data.rfid.ScanEventBus
import com.mitas.ppnam.station4aa.data.security.SecureCredentialStore
import com.mitas.ppnam.station4aa.data.session.OperatorSessionHolder
import com.mitas.ppnam.station4aa.data.settings.SettingsRepository
import com.mitas.ppnam.station4aa.domain.usecase.AuthUseCase
import com.mitas.ppnam.station4aa.domain.usecase.SyncWasteCatalogueUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "AppContainer"

/**
 * Station 4 has no Hilt (see the "minimal architecture" scope decision), so this is the one place
 * that wires up the shared, process-lifetime instances screens' ViewModels are constructed with —
 * a single MqttConnectionManager, SettingsRepository, WasteCollectionPublisher, and login stack
 * so every screen observes the same connection state, outbox, and operator session instead of
 * each opening its own.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** The derived, immutable scanner identity (base standard §2) — derived once here (then
     * cached/persisted by [DeviceIdentity]) and handed to everything that stamps a deviceId into
     * an MQTT topic, envelope, or payload. Never a Settings value. */
    val deviceId: String = DeviceIdentity.deviceId(appContext)

    private val secureCredentialStore = SecureCredentialStore(appContext)
    val settingsRepository = SettingsRepository(appContext, secureCredentialStore)
    val connectionManager = MqttConnectionManager(deviceId)

    private val outboxDatabase = WasteOutboxDatabase.create(appContext)
    private val wasteCollectionResultChannel = WasteCollectionResultChannel(
        outboxDao = outboxDatabase.wasteOutboxDao(),
        connectionManager = connectionManager,
    )
    val wasteCollectionPublisher = WasteCollectionPublisher(
        outboxDao = outboxDatabase.wasteOutboxDao(),
        connectionManager = connectionManager,
        resultChannel = wasteCollectionResultChannel,
    )
    val wasteCatalogueRepository = WasteCatalogueRepository(outboxDatabase.wasteCatalogueDao())

    // Login exchange, mirrored from Station 2 AA — see MqttTopics' class doc for why this talks
    // to a backend Station 4 doesn't demonstrably implement yet.
    val operatorSessionHolder = OperatorSessionHolder()
    private val requestChannel = MqttRequestChannel(connectionManager)
    private val scramExchange = ScramExchange(requestChannel)
    val authUseCase = AuthUseCase(requestChannel, operatorSessionHolder, scramExchange, deviceId)
    val syncWasteCatalogueUseCase = SyncWasteCatalogueUseCase(
        requestChannel = requestChannel,
        repository = wasteCatalogueRepository,
        deviceId = deviceId,
    )

    val scanEventBus = ScanEventBus()
    val dataWedgeReceiver = DataWedgeReceiver(scanEventBus)

    // Seeding touches disk, so it cannot run on the constructor's thread. Fire-and-forget: a
    // handheld whose seed has not landed yet shows an empty selection step with its own explicit
    // message, which is honest, rather than blocking startup on a database write.
    //
    // SupervisorJob only stops a failing child from cancelling its siblings; it does not stop that
    // child's own exception reaching Dispatchers.IO's default handler, which for an unhandled
    // exception is a process crash. AppContainer is built from PpnamApplication.onCreate(), so an
    // unguarded seed failure (full disk, corrupt DB, a future migration fault) would be a hard crash
    // at app launch. seedCatalogueSafely() below guards this the same way
    // SyncWasteCatalogueUseCase.sync() guards a sync failure, so the comment above is actually true.
    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        containerScope.launch { seedCatalogueSafely(wasteCatalogueRepository) }
    }
}

/**
 * Runs [WasteCatalogueRepository.seedIfEmpty] and converts any failure into a log line instead of
 * letting it propagate. Mirrors [com.mitas.ppnam.station4aa.domain.usecase.SyncWasteCatalogueUseCase.sync]'s
 * rule: [CancellationException] is rethrown (structured concurrency must still be able to cancel
 * this), everything else is swallowed. A failed seed simply leaves the catalogue empty, which the
 * wizard's own `CatalogueStep` already renders as an explicit "Refresh the catalogue in Settings"
 * message rather than a silently inert dropdown.
 */
internal suspend fun seedCatalogueSafely(repository: WasteCatalogueRepository) {
    try {
        repository.seedIfEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Failed to seed the waste catalogue; the wizard will show its empty-catalogue state until a sync succeeds", e)
    }
}
