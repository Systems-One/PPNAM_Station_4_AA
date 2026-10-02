package com.mitas.ppnam.station4aa

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mitas.ppnam.station4aa.navigation.AppNavGraph
import com.mitas.ppnam.station4aa.ui.theme.PPNAMStation4AATheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as PpnamApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            PPNAMStation4AATheme {
                AppNavGraph()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        container.sessionGuard.checkNow()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.sessionGuard.touch()
    }
}
