package com.mitas.ppnam.station4aa.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mitas.ppnam.station4aa.ui.components.AppScaffold
import com.mitas.ppnam.station4aa.ui.theme.AmberPrimary
import com.mitas.ppnam.station4aa.ui.theme.DangerRed
import com.mitas.ppnam.station4aa.ui.theme.GraphiteBackground
import com.mitas.ppnam.station4aa.ui.theme.GraphiteBorder
import com.mitas.ppnam.station4aa.ui.theme.GraphiteSurface
import com.mitas.ppnam.station4aa.ui.theme.TextMuted
import com.mitas.ppnam.station4aa.ui.theme.TextPrimary

/** Ported from Station 2 AA's LoginScreen — see
 * `com.mitas.ppnam.station4aa.data.mqtt.MqttTopics`' class doc. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onNavigateSettings: () -> Unit,
    onExitApp: () -> Unit = {},
    viewModel: LoginViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { destination ->
            if (destination == "home") onLoggedIn()
        }
    }

    // Login is the start destination, so Back here would otherwise drop straight to the Android
    // launcher without even dismissing the IME first — easy to hit by accident on a shared
    // handheld. Two-stage instead: with the keyboard up, Back just closes it; only from a settled
    // screen does it ask whether to leave the app.
    val imeVisible = WindowInsets.isImeVisible
    BackHandler {
        if (imeVisible) {
            keyboard?.hide()
            focusManager.clearFocus()
        } else {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Close the app?", color = TextPrimary) },
            text = { Text("You'll leave PPNAM Station 4 and return to the home screen.", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onExitApp()
                }) { Text("Close", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Stay") }
            },
            containerColor = GraphiteSurface
        )
    }

    AppScaffold(
        title = "Log In",
        status = connectionStatus,
        onSettings = onNavigateSettings
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
                border = BorderStroke(1.dp, GraphiteBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = uiState !is LoginUiState.LoggingIn,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            focusedLabelColor = AmberPrimary,
                            cursorColor = AmberPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = uiState !is LoginUiState.LoggingIn,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.submitCredentials(username, password) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            focusedLabelColor = AmberPrimary,
                            cursorColor = AmberPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState is LoginUiState.Error) {
                        Text(
                            text = (uiState as LoginUiState.Error).message,
                            color = DangerRed,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Button(
                        onClick = { viewModel.submitCredentials(username, password) },
                        enabled = uiState !is LoginUiState.LoggingIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (uiState is LoginUiState.LoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = GraphiteBackground,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Log In")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = GraphiteBorder)
                        Text(
                            "  or scan your badge  ",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                        HorizontalDivider(Modifier.weight(1f), color = GraphiteBorder)
                    }
                }
            }
        }
    }
}
