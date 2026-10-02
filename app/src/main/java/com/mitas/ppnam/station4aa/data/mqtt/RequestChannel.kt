package com.mitas.ppnam.station4aa.data.mqtt

/**
 * The request/response round trip [MqttRequestChannel] performs, as an interface so use cases can
 * be tested without a broker. Defaults live here; implementations must not repeat them.
 */
interface RequestChannel {
    suspend fun <T : Any> request(
        deviceId: String,
        requestType: String,
        responseClass: Class<T>,
        payload: Any,
        operatorSessionId: String = "",
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): MqttOutcome<T>

    companion object {
        /** Fleet standard: 10 s, one attempt (Stations 1–3 already use 10 s; 15 s was Station 4's
         * own number). Weigh/login/catalogue all inherit this. */
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
