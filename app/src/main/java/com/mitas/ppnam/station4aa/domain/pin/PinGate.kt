package com.mitas.ppnam.station4aa.domain.pin

/** Where the attempt counter and lockout deadline live. Persisted in production (SharedPreferences)
 * so leaving Settings, or restarting the process, cannot reset the counter (audit group (c)). */
interface PinLockoutStore {
    var failedAttempts: Int
    /** Wall-clock epoch ms; 0 = not locked out. */
    var lockedOutUntilMs: Long
}

class InMemoryPinLockoutStore : PinLockoutStore {
    override var failedAttempts: Int = 0
    override var lockedOutUntilMs: Long = 0L
}

sealed interface PinGateResult {
    object Blank : PinGateResult
    object Unlocked : PinGateResult
    data class Wrong(val attemptsLeft: Int) : PinGateResult
    data class LockedOut(val remainingMs: Long) : PinGateResult
}

/** Pure supervisor-PIN gate: same five-attempt / 30 s policy every PPNAM app uses. */
class PinGate(
    private val correctPin: String,
    private val store: PinLockoutStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L
    }

    fun remainingLockoutMs(): Long = (store.lockedOutUntilMs - now()).coerceAtLeast(0L)

    val isLockedOut: Boolean get() = remainingLockoutMs() > 0L

    fun submit(pin: String): PinGateResult {
        val remaining = remainingLockoutMs()
        if (remaining > 0L) return PinGateResult.LockedOut(remaining)
        if (pin.isBlank()) return PinGateResult.Blank
        if (pin == correctPin) {
            store.failedAttempts = 0
            store.lockedOutUntilMs = 0L
            return PinGateResult.Unlocked
        }
        val failed = store.failedAttempts + 1
        if (failed >= MAX_ATTEMPTS) {
            store.failedAttempts = 0
            store.lockedOutUntilMs = now() + LOCKOUT_MS
            return PinGateResult.LockedOut(LOCKOUT_MS)
        }
        store.failedAttempts = failed
        return PinGateResult.Wrong(MAX_ATTEMPTS - failed)
    }
}

fun wrongPinMessage(attemptsLeft: Int): String =
    "Incorrect PIN. $attemptsLeft ${if (attemptsLeft == 1) "attempt" else "attempts"} left before lockout."

fun lockoutMessage(remainingMs: Long): String =
    "Too many attempts. Try again in ${(remainingMs + 999) / 1_000}s."
