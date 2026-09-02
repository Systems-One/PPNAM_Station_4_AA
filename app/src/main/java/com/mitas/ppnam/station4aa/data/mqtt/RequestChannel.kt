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
        timeoutMs: Long = 15_000L,
    ): MqttOutcome<T>
}
