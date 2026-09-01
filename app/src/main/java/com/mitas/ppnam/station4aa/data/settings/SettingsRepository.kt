package com.mitas.ppnam.station4aa.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mitas.ppnam.station4aa.data.mqtt.MqttTopics
import com.mitas.ppnam.station4aa.data.security.SecureCredentialStore
import com.mitas.ppnam.station4aa.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_settings")

/**
 * Device configuration, mirroring Station 2's SettingsRepository.
 *
 * ### The broker password is not stored here
 *
 * Everything else lives in the DataStore preferences file, which is app-private but plaintext on
 * disk and readable on a rooted or debuggable device. The provisioned MQTT password is routed
 * through [SecureCredentialStore] instead and never written to [Keys].
 */
class SettingsRepository(
    private val context: Context,
    private val credentialStore: SecureCredentialStore,
) {
    // The retired editable "device_id" key is deliberately absent: the device id is now derived
    // on-device (base standard §2 — see data/identity/DeviceIdentity.kt). A leftover stored value
    // from an older install is simply never read again.
    private object Keys {
        val WASTE_COLLECTION_TOPIC = stringPreferencesKey("waste_collection_topic")
        val MQTT_HOST              = stringPreferencesKey("mqtt_host")
        val MQTT_PORT              = intPreferencesKey("mqtt_port")
        val MQTT_USE_WEBSOCKET     = booleanPreferencesKey("mqtt_use_websocket")
        val MQTT_USE_TLS           = booleanPreferencesKey("mqtt_use_tls")
        val MQTT_USERNAME          = stringPreferencesKey("mqtt_username")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            // Migrated on read: a stored value equal to the retired pre-3.1.0 default follows the
            // 2026-08-17 rename to PPNAM/station_4/...; a deliberately custom topic is untouched.
            wasteCollectionTopic = MqttTopics.migrateWasteCollectionTopic(
                prefs[Keys.WASTE_COLLECTION_TOPIC] ?: defaults.wasteCollectionTopic
            ),
            mqttHost             = prefs[Keys.MQTT_HOST]              ?: defaults.mqttHost,
            mqttPort             = prefs[Keys.MQTT_PORT]              ?: defaults.mqttPort,
            mqttUseWebSocket     = prefs[Keys.MQTT_USE_WEBSOCKET]     ?: defaults.mqttUseWebSocket,
            mqttUseTls           = prefs[Keys.MQTT_USE_TLS]           ?: defaults.mqttUseTls,
            // No `?: "admin"`. An unprovisioned handheld reports no credential rather than
            // silently presenting a shared one — see AppSettings.hasBrokerCredential.
            mqttUsername         = prefs[Keys.MQTT_USERNAME].orEmpty(),
            mqttPassword         = credentialStore.retrieve().orEmpty(),
        )
    }

    suspend fun current(): AppSettings = settingsFlow.first()

    suspend fun save(settings: AppSettings) {
        // The password goes to the Keystore first: if that fails we must not leave the app
        // believing it saved a credential it cannot retrieve.
        if (settings.mqttPassword.isNotBlank()) {
            credentialStore.store(settings.mqttPassword)
        }
        context.dataStore.edit { prefs ->
            prefs[Keys.WASTE_COLLECTION_TOPIC] = settings.wasteCollectionTopic
            prefs[Keys.MQTT_HOST]              = settings.mqttHost
            prefs[Keys.MQTT_PORT]              = settings.mqttPort
            prefs[Keys.MQTT_USE_WEBSOCKET]     = settings.mqttUseWebSocket
            prefs[Keys.MQTT_USE_TLS]           = settings.mqttUseTls
            prefs[Keys.MQTT_USERNAME]          = settings.mqttUsername
        }
    }

    /** Whether this handheld has been provisioned with its own broker credential. */
    suspend fun isProvisioned(): Boolean = current().hasBrokerCredential

    /** Wipes the broker credential. For decommissioning a handheld. */
    suspend fun clearCredential() {
        credentialStore.clear()
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.MQTT_USERNAME)
        }
    }
}
