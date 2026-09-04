package com.mitas.ppnam.station4aa.domain.model

/**
 * Device configuration.
 *
 * The broker defaults are the deployment's dev broker — `wss://mqtt.sysone.co.za:443/`, WebSocket
 * over TLS with a verified certificate and **no base path**. The contract
 * (`C:\Dev\Clients\PPNAM\Windows\PPNAM-Station-4\DOCS\Station4_Wastage_MQTT_Contract.md` §3) names
 * `ppnam-mqtt:1883` as the nominal default but records this as the actual dev broker, and notes
 * that a broker published through an HTTP front door accepts only the WebSocket transport. All of
 * it remains operator-editable in Settings because the contract calls the broker
 * "deployment-configured".
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
 * The broker credentials now carry the deployment's shared `admin` default so a freshly imaged
 * handheld connects without being configured by hand. Be aware of what that trades away: a default
 * credential is an APK constant shipped to every device, readable by anyone who unpacks the APK,
 * so it is only as safe as the broker ACL behind it. Give the handhelds their own scoped broker
 * account before this leaves a dev deployment.
 * [com.mitas.ppnam.station4aa.data.security.SecureCredentialStore] still holds the password
 * encrypted under an Android Keystore key; this field carries it in memory only, between being
 * read out of that store and being handed to the MQTT client.
 */
data class AppSettings(
    val mqttHost: String = "mqtt.sysone.co.za",
    val mqttPort: Int = 443,
    val mqttUseWebSocket: Boolean = true,
    val mqttUseTls: Boolean = true,
    val mqttUsername: String = "admin",
    val mqttPassword: String = "admin",
) {
    /** True once this handheld has been provisioned with its own broker credential. */
    val hasBrokerCredential: Boolean
        get() = mqttUsername.isNotBlank() && mqttPassword.isNotBlank()
}
