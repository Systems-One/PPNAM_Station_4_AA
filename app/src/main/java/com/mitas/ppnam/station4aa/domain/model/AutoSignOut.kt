package com.mitas.ppnam.station4aa.domain.model

/** Inactivity auto sign-out rules, ported from Station 1's AutoLogout: whole minutes, 0 = never,
 * max one day. */
object AutoSignOut {
    const val DEFAULT_MINUTES = 15
    const val MAX_MINUTES = 1440

    fun parseMinutes(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in 0..MAX_MINUTES }

    fun timeoutMs(minutes: Int): Long = if (minutes <= 0) 0L else minutes * 60_000L
}
