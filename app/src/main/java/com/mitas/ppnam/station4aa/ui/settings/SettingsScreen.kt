package com.mitas.ppnam.station4aa.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitas.ppnam.station4aa.BuildConfig
import com.mitas.ppnam.station4aa.data.mqtt.MqttConnectionState
import com.mitas.ppnam.station4aa.data.mqtt.MqttTopics
import com.mitas.ppnam.station4aa.ui.components.AppScaffold
import com.mitas.ppnam.station4aa.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val session by viewModel.session.collectAsState()
    val pinState = viewModel.pinState.value
    val pinInput = viewModel.pinInput.value
    val pinError = viewModel.pinError.value
    val pinErrorMessage = viewModel.pinErrorMessage.value
    val pinLockoutMessage = viewModel.pinLockoutMessage.value
    val applyState = viewModel.applyState.value
    val catalogueRefreshState = viewModel.catalogueRefreshState.value
    val draft = viewModel.draftSettings.value
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out?", color = TextPrimary) },
            text = { Text("You'll need to log in again to continue.", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                }) { Text("Log out", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            },
            containerColor = GraphiteSurface
        )
    }

    AppScaffold(
        title = "Settings",
        status = connectionStatus,
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionLabel("Diagnostics")

            Card(
                colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
                border = BorderStroke(1.dp, GraphiteBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // The contract gives the handheld no presence topic and no application-level
                    // ACK to consume — it's a pure publisher — so broker transport state is the
                    // whole picture here, not one line among several.
                    val (brokerColor, brokerLabel) = when (connectionState) {
                        MqttConnectionState.CONNECTED    -> SuccessGreen to "Connected"
                        MqttConnectionState.RECONNECTING -> AmberPrimary to "Reconnecting"
                        MqttConnectionState.DISCONNECTED -> DangerRed to "Disconnected"
                    }
                    DiagnosticRow("MQTT BROKER", brokerColor, brokerLabel)

                    HorizontalDivider(color = GraphiteBorder, modifier = Modifier.padding(vertical = 10.dp))

                    // Read-only by design (base standard §2): the device id is derived on-device
                    // and immutable — this row exists so it can be read off for enrolment.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "DEVICE ID",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                            color = TextMuted
                        )
                        Text(
                            viewModel.deviceId,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            color = TextPrimary
                        )
                    }

                    HorizontalDivider(color = GraphiteBorder, modifier = Modifier.padding(vertical = 10.dp))

                    // Read-only by design: the collection topic is fixed for Station 4, not
                    // configured. This row exists so support can read off what the handheld
                    // actually publishes to when reconciling against the broker ACL.
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "PUBLISHES TO",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                            color = TextMuted
                        )
                        Text(
                            MqttTopics.WASTE_COLLECTION,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = TextPrimary
                        )
                    }

                    HorizontalDivider(color = GraphiteBorder, modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "VERSION",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                            color = TextMuted
                        )
                        Text(
                            "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            color = TextPrimary
                        )
                    }

                    HorizontalDivider(color = GraphiteBorder, modifier = Modifier.padding(vertical = 10.dp))

                    val catalogueStatus by viewModel.catalogueStatus.collectAsState()
                    Text(
                        catalogueStatus,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                    TextButton(onClick = { viewModel.refreshCatalogue() }) {
                        Text("Refresh catalogue", color = AmberPrimary)
                    }

                    // Kept separate from the Configuration card's applyState block below: that one
                    // reports the broker Test & Apply outcome, this one reports the catalogue
                    // refresh — two independent operations that must not clobber each other's
                    // result, and the feedback belongs where the action was taken.
                    when (val state = catalogueRefreshState) {
                        ApplyState.Testing -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = AmberPrimary,
                                    strokeWidth = 2.dp
                                )
                                Text("Refreshing catalogue…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                            }
                        }
                        is ApplyState.Success -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = SuccessGreen)
                            }
                        }
                        is ApplyState.Failure -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Error, null, tint = DangerRed, modifier = Modifier.size(18.dp))
                                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = DangerRed)
                            }
                        }
                        ApplyState.Idle -> {}
                    }
                }
            }

            HorizontalDivider(color = GraphiteBorder)

            SectionLabel("Configuration")

            when (pinState) {
                PinState.Locked -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
                        border = BorderStroke(1.dp, GraphiteBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Enter supervisor PIN to edit settings",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = pinInput,
                                    onValueChange = viewModel::onPinChange,
                                    label = { Text("PIN") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.NumberPassword,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { viewModel.submitPin() }),
                                    isError = pinError,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AmberPrimary,
                                        focusedLabelColor = AmberPrimary,
                                        cursorColor = AmberPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = viewModel::submitPin,
                                    modifier = Modifier.height(56.dp)
                                ) { Text("Unlock") }
                            }
                            // Was a red border and nothing else — the operator had no idea
                            // whether the PIN was wrong or the field had simply mis-registered.
                            pinErrorMessage?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = DangerRed)
                            }
                            pinLockoutMessage?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = DangerRed)
                            }
                        }
                    }
                }

                PinState.Unlocked -> {
                    // Neither the Device ID nor the collection topic is a field here any more:
                    // the id is derived on-device (base standard §2) and the topic is fixed for
                    // Station 4. Both are read-only rows in the Diagnostics card above. What is
                    // left is genuinely deployment-configured — the broker.
                    ConfigSection(title = "Connection") {
                        SettingsTextField(
                            value = draft.mqttHost,
                            label = "Host",
                            onValueChange = { viewModel.updateDraft(draft.copy(mqttHost = it)) }
                        )
                        SettingsTextField(
                            value = draft.mqttPort.toString(),
                            label = "Port",
                            keyboardType = KeyboardType.Number,
                            onValueChange = {
                                viewModel.updateDraft(draft.copy(mqttPort = it.toIntOrNull() ?: draft.mqttPort))
                            }
                        )
                        SettingsToggleRow(
                            label = "WebSocket",
                            checked = draft.mqttUseWebSocket,
                            onCheckedChange = { viewModel.updateDraft(draft.copy(mqttUseWebSocket = it)) }
                        )
                        SettingsToggleRow(
                            label = "TLS",
                            checked = draft.mqttUseTls,
                            onCheckedChange = { viewModel.updateDraft(draft.copy(mqttUseTls = it)) }
                        )
                        SettingsTextField(
                            value = draft.mqttUsername,
                            label = "Username",
                            onValueChange = { viewModel.updateDraft(draft.copy(mqttUsername = it)) }
                        )
                        SettingsTextField(
                            value = draft.mqttPassword,
                            label = "Password",
                            keyboardType = KeyboardType.Password,
                            visualTransformation = PasswordVisualTransformation(),
                            onValueChange = { viewModel.updateDraft(draft.copy(mqttPassword = it)) }
                        )
                    }

                    when (val state = applyState) {
                        ApplyState.Testing -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = AmberPrimary,
                                    strokeWidth = 2.dp
                                )
                                Text("Testing connection…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                            }
                        }
                        is ApplyState.Success -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = SuccessGreen)
                            }
                        }
                        is ApplyState.Failure -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Error, null, tint = DangerRed, modifier = Modifier.size(18.dp))
                                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = DangerRed)
                            }
                        }
                        ApplyState.Idle -> {}
                    }

                    Button(
                        onClick = viewModel::testAndApply,
                        enabled = applyState !is ApplyState.Testing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text("Test & Apply")
                    }
                }
            }

            // Settings is reachable from the Login screen too (broker config has to be editable
            // before anyone can log in), so this whole section is conditional on a session
            // existing.
            session?.let { operator ->
                HorizontalDivider(color = GraphiteBorder)
                SectionLabel("Session")
                Card(
                    colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
                    border = BorderStroke(1.dp, GraphiteBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "SIGNED IN AS",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                                color = TextMuted
                            )
                            Text(
                                if (operator.role.isNotBlank()) "${operator.operatorName} · ${operator.role}"
                                else operator.operatorName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                        }
                        OutlinedButton(
                            onClick = { showLogoutDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                            border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) { Text("Log Out") }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** One labelled line of the Diagnostics card, with its own dot-and-text status badge. */
@Composable
private fun DiagnosticRow(label: String, dotColor: Color, statusLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = TextMuted
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(dotColor.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(6.dp)) {
                    drawCircle(dotColor, center = Offset(size.width / 2, size.height / 2))
                }
                Spacer(Modifier.width(5.dp))
                Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = dotColor)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
        color = TextMuted
    )
}

@Composable
private fun ConfigSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
        border = BorderStroke(1.dp, GraphiteBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                color = AmberPrimary
            )
            content()
        }
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AmberPrimary,
            focusedLabelColor = AmberPrimary,
            cursorColor = AmberPrimary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AmberPrimary,
                checkedTrackColor = AmberPrimary.copy(alpha = 0.4f)
            )
        )
    }
}
