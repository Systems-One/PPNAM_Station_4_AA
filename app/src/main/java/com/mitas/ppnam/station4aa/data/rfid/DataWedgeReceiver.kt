package com.mitas.ppnam.station4aa.data.rfid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Instant

/**
 * Ported from Station 2 AA's DataWedgeReceiver — see
 * `com.mitas.ppnam.station4aa.data.mqtt.MqttTopics`' class doc.
 *
 * Registered dynamically at runtime (see PpnamApplication.onCreate), not declared in the
 * manifest. Android 11+ package-visibility filtering silently drops fully-implicit broadcasts (no
 * target package/component set) sent to manifest-declared receivers from apps we're not "visible"
 * to — which is exactly how the Chainway scanner service and its keyboard-emulator companion app
 * deliver scans. Dynamic registration bypasses that resolution path.
 *
 * The device this app was tested on (a Zebra C72) can genuinely emit these broadcasts, but the
 * DataWedge/Chainway profile still needs to be configured on-device to target
 * `com.mitas.ppnam.station4aa.ACTION_SCAN` specifically — that's a provisioning step, not code.
 */
class DataWedgeReceiver(
    private val scanEventBus: ScanEventBus,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SCAN -> {
                val source = intent.getStringExtra(EXTRA_SOURCE) ?: ""
                val data = intent.getStringExtra(EXTRA_DATA)
                if (data == null) {
                    Log.w(TAG, "ACTION_SCAN received with no $EXTRA_DATA extra - ignoring")
                    return
                }
                val labelType = intent.getStringExtra(EXTRA_LABEL_TYPE) ?: ""
                val event = if (source.equals("RFID", ignoreCase = true) ||
                    labelType.startsWith("LABEL-TYPE-RFID", ignoreCase = true)
                ) {
                    ScanEvent.RfidTag(tagId = data, timestamp = Instant.now())
                } else {
                    ScanEvent.Barcode(value = data, format = labelType, timestamp = Instant.now())
                }
                scanEventBus.emit(event)
            }
            ACTION_CHAINWAY_BARCODE -> {
                val data = intent.getStringExtra(EXTRA_CHAINWAY_DATA)
                if (data == null) {
                    Log.w(TAG, "ACTION_CHAINWAY_BARCODE received with no '$EXTRA_CHAINWAY_DATA' extra - ignoring")
                    return
                }
                scanEventBus.emit(ScanEvent.Barcode(value = data, format = "", timestamp = Instant.now()))
            }
            ACTION_CHAINWAY_RFID -> {
                val data = intent.getStringExtra(EXTRA_CHAINWAY_DATA)
                if (data == null) {
                    Log.w(TAG, "ACTION_CHAINWAY_RFID received with no '$EXTRA_CHAINWAY_DATA' extra - ignoring")
                    return
                }
                scanEventBus.emit(ScanEvent.RfidTag(tagId = data, timestamp = Instant.now()))
            }
            else -> Log.w(TAG, "onReceive: unrecognized action ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "DataWedgeReceiver"
        const val ACTION_SCAN = "com.mitas.ppnam.station4aa.ACTION_SCAN"
        const val EXTRA_DATA = "com.symbol.datawedge.data_string"
        const val EXTRA_SOURCE = "com.symbol.datawedge.source"
        const val EXTRA_LABEL_TYPE = "com.symbol.datawedge.label_type"

        // Chainway RFID/barcode reader broadcasts.
        const val ACTION_CHAINWAY_BARCODE = "com.scanner.broadcast"
        const val ACTION_CHAINWAY_RFID = "com.rscja.scanner.action.scanner.RFID"
        const val EXTRA_CHAINWAY_DATA = "data"
    }
}
