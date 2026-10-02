package com.mitas.ppnam.station4aa.domain.usecase

import com.mitas.ppnam.station4aa.data.mqtt.FailureKind
import com.mitas.ppnam.station4aa.data.mqtt.describe

/** The station answered and said no. [reason] is backend text — kept for logs, never shown. */
class LoginRejectedException(val errorCode: String?, val reason: String?) :
    Exception(reason ?: errorCode ?: "Login rejected")

/** Nothing usable came back from the station. */
class LoginTransportException(val kind: FailureKind) : Exception(kind.describe())
