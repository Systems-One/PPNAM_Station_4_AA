package com.mitas.ppnam.station4aa

import android.app.Application
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.mitas.ppnam.station4aa.data.AppContainer
import com.mitas.ppnam.station4aa.data.rfid.DataWedgeReceiver

class PpnamApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Dynamic registration, not a manifest entry — see DataWedgeReceiver's class doc for why.
        val filter = IntentFilter().apply {
            addAction(DataWedgeReceiver.ACTION_SCAN)
            addAction(DataWedgeReceiver.ACTION_CHAINWAY_BARCODE)
            addAction(DataWedgeReceiver.ACTION_CHAINWAY_RFID)
        }
        ContextCompat.registerReceiver(this, container.dataWedgeReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }
}
