package com.mitas.ppnam.station4aa.domain.session

/** Shown on Login after an inactivity sign-out (Station 1's `signed_out_inactivity`). */
fun signedOutAfterMinutes(minutes: Int): String =
    "Signed out after $minutes ${if (minutes == 1) "minute" else "minutes"} of inactivity."

/** Shown on Login after the station refused the session (`operator_session_invalid`). */
const val SIGNED_OUT_SESSION_ENDED = "Your session has ended. Sign in again."
