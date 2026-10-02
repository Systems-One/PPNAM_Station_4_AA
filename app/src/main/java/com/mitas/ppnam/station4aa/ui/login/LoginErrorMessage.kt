package com.mitas.ppnam.station4aa.ui.login

import com.mitas.ppnam.station4aa.data.mqtt.describe
import com.mitas.ppnam.station4aa.domain.usecase.LoginRejectedException
import com.mitas.ppnam.station4aa.domain.usecase.LoginTransportException

/** One place that turns an auth failure into the line above the login fields. Pure. */
fun loginErrorMessage(failure: Throwable): String = when (failure) {
    is LoginRejectedException ->
        if (failure.errorCode.orEmpty().startsWith("badge")) "Badge not recognised. Ask a manager."
        else "Incorrect username or password"
    is LoginTransportException -> failure.kind.describe()
    else -> "Login failed. Try again."
}
