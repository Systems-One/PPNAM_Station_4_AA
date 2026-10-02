package com.mitas.ppnam.station4aa.ui.login

import com.mitas.ppnam.station4aa.data.mqtt.FailureKind
import com.mitas.ppnam.station4aa.domain.usecase.LoginRejectedException
import com.mitas.ppnam.station4aa.domain.usecase.LoginTransportException
import org.junit.Assert.assertEquals
import org.junit.Test

/** Audit S4-07 / group (f): the operator never sees backend or library text on the login line. */
class LoginErrorMessageTest {

    @Test
    fun `a rejected password never echoes the backend reason`() {
        assertEquals(
            "Incorrect username or password",
            loginErrorMessage(LoginRejectedException("authentication_failed", "SCRAM proof rejected.")),
        )
    }

    @Test
    fun `a rejected badge is named as a badge problem`() {
        assertEquals(
            "Badge not recognised. Ask a manager.",
            loginErrorMessage(LoginRejectedException("badge_unknown", "No such badge")),
        )
    }

    @Test
    fun `a timeout uses the fleet timeout wording`() {
        assertEquals(
            "Station 4 did not respond. Check the station and retry.",
            loginErrorMessage(LoginTransportException(FailureKind.Timeout)),
        )
    }

    @Test
    fun `anything else is a generic retry, not an exception message`() {
        assertEquals("Login failed. Try again.", loginErrorMessage(IllegalStateException("java.lang.Whatever: boom")))
    }
}
