package com.mitas.ppnam.station4aa.data.rfid

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant

/** Ported from Station 2 AA's ScanEventBus/ScanEvent — see
 * `com.mitas.ppnam.station4aa.data.mqtt.MqttTopics`' class doc. */
sealed class ScanEvent {
    data class RfidTag(val tagId: String, val timestamp: Instant) : ScanEvent()
    data class Barcode(val value: String, val format: String, val timestamp: Instant) : ScanEvent()
}

class ScanEventBus {
    private val _events = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ScanEvent> = _events.asSharedFlow()

    fun emit(event: ScanEvent) {
        val delivered = _events.tryEmit(event)
        if (!delivered) {
            Log.w("ScanEventBus", "tryEmit dropped $event - buffer full or no active collectors")
        }
    }
}
