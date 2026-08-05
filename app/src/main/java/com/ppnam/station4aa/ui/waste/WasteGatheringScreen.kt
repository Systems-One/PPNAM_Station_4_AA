package com.ppnam.station4aa.ui.waste

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ppnam.station4aa.domain.model.WasteTypeCatalog
import com.ppnam.station4aa.domain.wizard.WizardStep
import com.ppnam.station4aa.ui.components.AppScaffold
import com.ppnam.station4aa.ui.theme.AmberPrimary
import com.ppnam.station4aa.ui.theme.GraphiteBorder
import com.ppnam.station4aa.ui.theme.GraphiteSurface
import com.ppnam.station4aa.ui.theme.TextMuted
import com.ppnam.station4aa.ui.theme.TextPrimary
import com.ppnam.station4aa.ui.theme.WarningOrange

@Composable
fun WasteGatheringScreen(
    onSettings: () -> Unit,
    viewModel: WasteGatheringViewModel,
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val session by viewModel.session.collectAsState()
    val collectedBy by viewModel.collectedBy.collectAsState()
    val step by viewModel.step.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val stepError by viewModel.stepError.collectAsState()
    val lastQueuedMessage by viewModel.lastQueuedMessage.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()

    if (step == WizardStep.REVIEW) {
        AlertDialog(
            onDismissRequest = { viewModel.onCancelTransaction() },
            title = { Text("Confirm waste collection", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConfirmRow("Machine", draft.machineCode.orEmpty())
                    ConfirmRow("Waste type", draft.wasteType?.display.orEmpty())
                    ConfirmRow("Wastage operator", collectedBy)
                    ConfirmRow("Machine operator ID", draft.machineOperatorUserId.orEmpty())
                    ConfirmRow("Bag code", draft.bagCode.orEmpty())
                    if (stepError != null) {
                        Text(stepError!!, style = MaterialTheme.typography.labelSmall, color = WarningOrange)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onReviewConfirmed() }, enabled = !isSubmitting) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onCancelTransaction() }) { Text("Cancel") }
            },
            containerColor = GraphiteSurface
        )
    }

    AppScaffold(
        title = "Waste Gathering",
        status = connectionStatus,
        onSettings = onSettings,
        operatorName = session?.operatorName?.ifBlank { session?.operatorId },
        operatorRole = session?.role,
        onLogout = viewModel::logout,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (pendingCount > 0) {
                Text(
                    "$pendingCount collection${if (pendingCount == 1) "" else "s"} queued, awaiting delivery",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarningOrange,
                )
            }
            lastQueuedMessage?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            }

            StepIndicator(step)

            when (step) {
                WizardStep.SCAN_MACHINE -> ScanStep(
                    label = "Scan machine code",
                    errorMessage = stepError,
                    onSubmit = viewModel::onMachineCodeSubmitted,
                )
                WizardStep.SCAN_OPERATOR -> ScanStep(
                    label = "Scan machine operator code",
                    errorMessage = stepError,
                    onSubmit = viewModel::onOperatorIdSubmitted,
                )
                WizardStep.SELECT_WASTE_TYPE -> WasteTypeStep(
                    onConfirm = viewModel::onWasteTypeConfirmed,
                )
                WizardStep.SCAN_BAG -> ScanStep(
                    label = "Scan bag code",
                    errorMessage = stepError,
                    onSubmit = viewModel::onBagCodeSubmitted,
                )
                WizardStep.REVIEW -> Unit // rendered as the AlertDialog above
            }

            TextButton(onClick = { viewModel.onCancelTransaction() }) {
                Text("Cancel transaction", color = WarningOrange)
            }
        }
    }
}

private val WIZARD_STEP_ORDINALS = mapOf(
    WizardStep.SCAN_MACHINE to 1,
    WizardStep.SCAN_OPERATOR to 2,
    WizardStep.SELECT_WASTE_TYPE to 3,
    WizardStep.SCAN_BAG to 4,
    WizardStep.REVIEW to 4,
)

@Composable
private fun StepIndicator(step: WizardStep) {
    val label = when (step) {
        WizardStep.SCAN_MACHINE -> "Scan machine code"
        WizardStep.SCAN_OPERATOR -> "Scan machine operator code"
        WizardStep.SELECT_WASTE_TYPE -> "Select waste type"
        WizardStep.SCAN_BAG -> "Scan bag code"
        WizardStep.REVIEW -> "Review and confirm"
    }
    Text(
        "Step ${WIZARD_STEP_ORDINALS.getValue(step)} of 4 — $label",
        style = MaterialTheme.typography.labelLarge,
        color = AmberPrimary,
    )
}

@Composable
private fun ScanStep(
    label: String,
    errorMessage: String?,
    onSubmit: (String) -> Unit,
) {
    var manualValue by remember(label) { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(
            "Scan the barcode, or enter it manually below.",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
        OutlinedTextField(
            value = manualValue,
            onValueChange = { manualValue = it },
            label = { Text("Manual entry") },
            singleLine = true,
            isError = errorMessage != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmberPrimary,
                focusedLabelColor = AmberPrimary,
                cursorColor = AmberPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (errorMessage != null) {
            Text(errorMessage, style = MaterialTheme.typography.labelSmall, color = WarningOrange)
        }
        Button(
            onClick = {
                onSubmit(manualValue)
                manualValue = ""
            },
            enabled = manualValue.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Submit")
        }
    }
}

@Composable
private fun WasteTypeStep(onConfirm: (WasteTypeCatalog) -> Unit) {
    var selected by remember { mutableStateOf(WasteTypeCatalog.GENERAL) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Select waste type", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        EnumDropdownSelector(
            label = "Waste Type",
            options = WasteTypeCatalog.entries,
            selected = selected,
            display = { it.display },
            onSelected = { selected = it },
        )
        Button(
            onClick = { onConfirm(selected) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Confirm")
        }
    }
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdownSelector(
    label: String,
    options: List<T>,
    selected: T,
    display: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
        border = BorderStroke(1.dp, GraphiteBorder),
    ) {
        Box(Modifier.padding(12.dp)) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                TextField(
                    value = display(selected),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(label, color = TextMuted) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(display(option)) },
                            onClick = {
                                onSelected(option)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}
