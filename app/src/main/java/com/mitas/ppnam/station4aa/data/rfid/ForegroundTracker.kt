package com.mitas.ppnam.station4aa.data.rfid

/** Set by MainActivity.onResume/onPause. The scan receiver drops broadcasts while false: the
 * audit saw Station 4 log in from the background on a badge meant for Station 5 (S4-18). */
class ForegroundTracker {
    @Volatile var isResumed: Boolean = false
}
