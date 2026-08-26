package com.mitas.ppnam.station4aa.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import com.mitas.ppnam.station4aa.data.session.OperatorSessionHolder
import com.mitas.ppnam.station4aa.navigation.NavRoutes

/**
 * Sends the operator back to Login whenever the session disappears. Ported from Station 2 AA's
 * SessionWatcher — see `com.mitas.ppnam.station4aa.data.mqtt.MqttTopics`' class doc. Adapted to take
 * [OperatorSessionHolder] directly (no Hilt, so no `hiltViewModel()`/wrapper ViewModel needed to
 * inject it).
 */
@Composable
fun SessionWatcher(
    navController: NavHostController,
    sessionHolder: OperatorSessionHolder,
) {
    val session by sessionHolder.session.collectAsState()

    LaunchedEffect(session) {
        if (session != null) return@LaunchedEffect
        val current = navController.currentDestination?.route ?: return@LaunchedEffect
        if (current == NavRoutes.LOGIN) return@LaunchedEffect
        navController.navigate(NavRoutes.LOGIN) {
            // Nothing behind us is usable without a session.
            popUpTo(0)
        }
    }
}
