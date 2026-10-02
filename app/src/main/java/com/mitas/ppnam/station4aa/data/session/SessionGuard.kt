package com.mitas.ppnam.station4aa.data.session

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mitas.ppnam.station4aa.data.rfid.ScanEventBus
import com.mitas.ppnam.station4aa.data.settings.SettingsRepository
import com.mitas.ppnam.station4aa.domain.model.AutoSignOut
import com.mitas.ppnam.station4aa.domain.session.InactivityMonitor
import com.mitas.ppnam.station4aa.domain.session.signedOutAfterMinutes
import com.mitas.ppnam.station4aa.domain.usecase.AuthUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Process-wide inactivity sign-out, the Compose-app shape of Station 1's SessionGuard: the timer
 * starts with a session and the configured minutes, restarts when Settings changes the minutes,
 * stops when the session ends, is touched by every user interaction (MainActivity.onUserInteraction)
 * and every scan, and signs out with a reason Login shows. 0 minutes = never.
 */
class SessionGuard(
    private val sessionHolder: OperatorSessionHolder,
    settingsRepository: SettingsRepository,
    private val authUseCase: AuthUseCase,
    scanEventBus: ScanEventBus,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var currentMinutes = AutoSignOut.DEFAULT_MINUTES

    private val monitor = InactivityMonitor(
        now = { SystemClock.elapsedRealtime() },
        schedule = { delayMs, r -> handler.postDelayed(r, delayMs) },
        cancel = { r -> handler.removeCallbacks(r) },
        onExpired = { expire() },
    )

    init {
        scope.launch {
            combine(
                sessionHolder.session,
                settingsRepository.settingsFlow.map { it.autoSignOutMinutes }.distinctUntilChanged(),
            ) { session, minutes -> session to minutes }
                .collect { (session, minutes) ->
                    currentMinutes = minutes
                    if (session == null) monitor.stop() else monitor.start(AutoSignOut.timeoutMs(minutes))
                }
        }
        scope.launch { scanEventBus.events.collect { monitor.touch() } }
    }

    /** Any operator interaction. Safe from any thread. */
    fun touch() {
        handler.post { monitor.touch() }
    }

    /** Catches a deadline that passed while the app was in the background. */
    fun checkNow() {
        handler.post { monitor.checkNow() }
    }

    private fun expire() {
        if (sessionHolder.session.value == null) return
        val reason = signedOutAfterMinutes(currentMinutes)
        scope.launch { authUseCase.logout(reason) }
    }
}
