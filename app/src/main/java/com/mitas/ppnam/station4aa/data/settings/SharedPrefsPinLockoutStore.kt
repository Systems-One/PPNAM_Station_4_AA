package com.mitas.ppnam.station4aa.data.settings

import android.content.Context
import com.mitas.ppnam.station4aa.domain.pin.PinLockoutStore

/** Process- and screen-independent backing for the supervisor PIN gate. Plain prefs: the values
 * are a counter and a deadline, nothing secret. */
class SharedPrefsPinLockoutStore(context: Context) : PinLockoutStore {
    private val prefs = context.applicationContext.getSharedPreferences("pin_gate", Context.MODE_PRIVATE)

    override var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED, 0)
        set(value) = prefs.edit().putInt(KEY_FAILED, value).apply()

    override var lockedOutUntilMs: Long
        get() = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_LOCKED_UNTIL, value).apply()

    private companion object {
        const val KEY_FAILED = "failed_attempts"
        const val KEY_LOCKED_UNTIL = "locked_out_until_ms"
    }
}
