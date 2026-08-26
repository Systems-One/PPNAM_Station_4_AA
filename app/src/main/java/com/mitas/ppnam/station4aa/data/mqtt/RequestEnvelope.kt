package com.mitas.ppnam.station4aa.data.mqtt

import com.google.gson.Gson

/** Payload for envelope-only requests (e.g. `reader_logout_requested`). */
object EmptyPayload

/**
 * Builds a login-exchange request as one flat JSON object: the caller's message-specific payload,
 * with the transport-controlled envelope merged in and always winning on a name collision. Ported
 * from Station 2 AA's RequestEnvelope — see `MqttTopics`' class doc for why this exists here.
 */
object RequestEnvelope {

    fun build(
        gson: Gson,
        payload: Any,
        messageId: String,
        deviceId: String,
        operatorSessionId: String,
        timestampUtc: String,
    ): String {
        val obj = gson.toJsonTree(payload).asJsonObject
        obj.addProperty("messageId", messageId)
        obj.addProperty("schemaVersion", MqttSchema.VERSION)
        obj.addProperty("deviceId", deviceId)
        obj.addProperty("operatorSessionId", operatorSessionId)
        obj.addProperty("timestampUtc", timestampUtc)
        return gson.toJson(obj)
    }
}
