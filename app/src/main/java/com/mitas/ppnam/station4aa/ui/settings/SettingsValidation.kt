package com.mitas.ppnam.station4aa.ui.settings

import com.mitas.ppnam.station4aa.domain.model.AutoSignOut
import kotlinx.coroutines.TimeoutCancellationException

/** Per-field messages for the Connection card; null = valid. Pure, so it is unit-tested. */
data class SettingsFieldErrors(
    val host: String? = null,
    val port: String? = null,
    val autoSignOut: String? = null,
) {
    val hasErrors: Boolean get() = host != null || port != null || autoSignOut != null
}

/** Station 1's `BrokerSettings.parsePort`. */
fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..65535 }

fun validateSettingsDraft(host: String, portText: String, autoSignOutText: String): SettingsFieldErrors =
    SettingsFieldErrors(
        host = if (host.isBlank()) "Host required" else null,
        port = if (parsePort(portText) == null) "Invalid port (1–65535)" else null,
        autoSignOut = if (AutoSignOut.parseMinutes(autoSignOutText) == null) "Enter 0–1440" else null,
    )

/** Test & Apply failure line. The 15 s is MqttConnectionManager.CONNECT_TIMEOUT_MS; the previous
 * text was HiveMQ's "Timed out waiting for 15000 ms" (audit S4-07). */
fun describeConnectFailure(failure: Throwable?): String = when (failure) {
    is TimeoutCancellationException -> "Could not reach the broker (15 s)"
    else -> "Could not connect to the broker. Check the host, port and credentials."
}

/** Test & Apply failure line now that the draft is persisted before the connection test. */
fun savedButNotConnectedMessage(host: String, port: Int): String =
    "Saved, but could not connect to $host:$port. Check the broker settings."
