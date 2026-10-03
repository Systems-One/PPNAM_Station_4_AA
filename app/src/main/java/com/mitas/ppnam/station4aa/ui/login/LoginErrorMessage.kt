package com.mitas.ppnam.station4aa.ui.login

import com.mitas.ppnam.station4aa.data.mqtt.describe
import com.mitas.ppnam.station4aa.domain.usecase.LoginRejectedException
import com.mitas.ppnam.station4aa.domain.usecase.LoginTransportException

/** Station authentication codes that mean the username/password pair did not verify (the same
 * set Station 1 maps to "Incorrect username or password"). Any other rejection is a refusal the
 * operator cannot fix by retyping. */
private val CREDENTIAL_ERROR_CODES = setOf(
    "scram_proof_invalid", "scram_client_final_invalid", "authentication_failed",
)

/** One place that turns an auth failure into the line above the login fields. Pure. */
fun loginErrorMessage(failure: Throwable): String = when (failure) {
    is LoginRejectedException ->
        when {
            failure.errorCode.orEmpty().startsWith("badge") -> "Badge not recognised. Ask a manager."
            failure.errorCode in CREDENTIAL_ERROR_CODES -> "Incorrect username or password"
            else -> "Login was refused by the station (${failure.errorCode?.ifBlank { null } ?: "no code"}). Ask a supervisor."
        }
    is LoginTransportException -> failure.kind.describe()
    else -> "Login failed. Try again."
}
