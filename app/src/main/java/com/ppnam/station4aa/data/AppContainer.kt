package com.ppnam.station4aa.data

import android.content.Context
import com.ppnam.station4aa.data.auth.ScramExchange
import com.ppnam.station4aa.data.local.WasteOutboxDatabase
import com.ppnam.station4aa.data.mqtt.MqttConnectionManager
import com.ppnam.station4aa.data.mqtt.MqttRequestChannel
import com.ppnam.station4aa.data.mqtt.WasteCollectionPublisher
import com.ppnam.station4aa.data.mqtt.WasteCollectionResultChannel
import com.ppnam.station4aa.data.rfid.DataWedgeReceiver
import com.ppnam.station4aa.data.rfid.ScanEventBus
import com.ppnam.station4aa.data.security.SecureCredentialStore
import com.ppnam.station4aa.data.session.OperatorSessionHolder
import com.ppnam.station4aa.data.settings.SettingsRepository
import com.ppnam.station4aa.domain.usecase.AuthUseCase

/**
 * Station 4 has no Hilt (see the "minimal architecture" scope decision), so this is the one place
 * that wires up the shared, process-lifetime instances screens' ViewModels are constructed with —
 * a single MqttConnectionManager, SettingsRepository, WasteCollectionPublisher, and login stack
 * so every screen observes the same connection state, outbox, and operator session instead of
 * each opening its own.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val secureCredentialStore = SecureCredentialStore(appContext)
    val settingsRepository = SettingsRepository(appContext, secureCredentialStore)
    val connectionManager = MqttConnectionManager()

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
    val authUseCase = AuthUseCase(requestChannel, operatorSessionHolder, scramExchange, settingsRepository)

    val scanEventBus = ScanEventBus()
    val dataWedgeReceiver = DataWedgeReceiver(scanEventBus)
}
