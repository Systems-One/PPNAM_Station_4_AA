package com.mitas.ppnam.station4aa.domain.model

/**
 * Device configuration.
 *
 * Broker host/port/websocket/TLS defaults match the deployment default stated in the normative
 * contract (`C:\Dev\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md` — "Broker |
 * Deployment-configured; the current Station 4 default is `ppnam-mqtt:1883`"), not Station 2's
 * broker — the two stations are separate deployments. All of it remains operator-editable in
 * Settings because the contract calls the broker "deployment-configured".
 *
 * The device id is deliberately NOT a setting any more: per the fleet MQTT base standard §2 it
 * is derived on-device (`scanner_` + hashed hardware id — see
 * [com.mitas.ppnam.station4aa.data.identity.DeviceIdentity]) and shown read-only in Settings →
 * Diagnostics for enrolment. The MQTT client identifier is likewise no longer configured here
 * (see MqttClientFactory — unique per connection, per base standard §2 rule 6).
 *
 * The collection topic is NOT a setting either: contract 5.0.0 made the collection an ordinary
 * request on this scanner's own subtree, so it is derived from the device id
 * (`MqttTopics.wasteCollectionRequest`) and shown read-only in Settings → Diagnostics. The
 * contract now requires exactly this — §17.45: "Settings exposes no topic to configure" — having
 * deleted its own Collection topic field in the same release.
 *
 * Broker credentials have no defaults deliberately: a default here is an APK constant shipped to
 * every device. [com.mitas.ppnam.station4aa.data.security.SecureCredentialStore] holds the password
 * encrypted under an Android Keystore key; this field carries it in memory only, between being
 * read out of that store and being handed to the MQTT client.
 */
data class AppSettings(
    val mqttHost: String = "ppnam-mqtt",
    val mqttPort: Int = 1883,
    val mqttUseWebSocket: Boolean = false,
    val mqttUseTls: Boolean = false,
    val mqttUsername: String = "",
    val mqttPassword: String = "",
) {
    /** True once this handheld has been provisioned with its own broker credential. */
    val hasBrokerCredential: Boolean
        get() = mqttUsername.isNotBlank() && mqttPassword.isNotBlank()
}
