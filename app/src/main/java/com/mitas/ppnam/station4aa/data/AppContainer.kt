package com.mitas.ppnam.station4aa.data

import android.content.Context
import com.mitas.ppnam.station4aa.data.auth.ScramExchange
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
        settingsRepository = settingsRepository,
    )

    // Login exchange, mirrored from Station 2 AA — see MqttTopics' class doc for why this talks
    // to a backend Station 4 doesn't demonstrably implement yet.
    val operatorSessionHolder = OperatorSessionHolder()
    private val requestChannel = MqttRequestChannel(connectionManager)
    private val scramExchange = ScramExchange(requestChannel)
    val authUseCase = AuthUseCase(requestChannel, operatorSessionHolder, scramExchange, deviceId)

    val scanEventBus = ScanEventBus()
    val dataWedgeReceiver = DataWedgeReceiver(scanEventBus)
}
