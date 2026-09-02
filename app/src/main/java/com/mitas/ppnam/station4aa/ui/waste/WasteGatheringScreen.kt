package com.mitas.ppnam.station4aa.ui.waste

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mitas.ppnam.station4aa.domain.wizard.WizardStep
import com.mitas.ppnam.station4aa.ui.components.AppScaffold
import com.mitas.ppnam.station4aa.ui.theme.AmberPrimary
import com.mitas.ppnam.station4aa.ui.theme.GraphiteBorder
import com.mitas.ppnam.station4aa.ui.theme.GraphiteSurface
import com.mitas.ppnam.station4aa.ui.theme.TextMuted
import com.mitas.ppnam.station4aa.ui.theme.TextPrimary
import com.mitas.ppnam.station4aa.ui.theme.WarningOrange

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
    val lastMessageIsError by viewModel.lastMessageIsError.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val wasteTypes by viewModel.typesForSelectedCategory.collectAsState()

    if (step == WizardStep.REVIEW) {
        AlertDialog(
            onDismissRequest = { viewModel.onCancelTransaction() },
            title = { Text("Confirm waste collection", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConfirmRow("Bag code", draft.bagCode.orEmpty()) {
                        viewModel.onEditField(WizardStep.SCAN_BAG)
                    }
                    ConfirmRow("Job number", draft.jobNumber.orEmpty()) {
                        viewModel.onEditField(WizardStep.SCAN_JOB)
                    }
                    ConfirmRow("Operator ID", draft.operatorId.orEmpty()) {
                        viewModel.onEditField(WizardStep.SCAN_OPERATOR)
                    }
                    ConfirmRow("Waste category", draft.category?.name.orEmpty()) {
                        viewModel.onEditField(WizardStep.SELECT_CATEGORY)
                    }
                    ConfirmRow("Waste type", draft.wasteType?.name.orEmpty()) {
                        viewModel.onEditField(WizardStep.SELECT_WASTE_TYPE)
                    }
                    ConfirmRow("Wastage operator", collectedBy)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (lastMessageIsError) WarningOrange else TextMuted,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.dismissLastQueuedMessage() }) {
                        Text("Dismiss")
                    }
                }
            }

            StepIndicator(step)

            when (step) {
                WizardStep.SCAN_BAG -> ScanStep(
                    label = "Scan bag code",
                    errorMessage = stepError,
                    onSubmit = viewModel::onBagCodeSubmitted,
                )
                WizardStep.SCAN_JOB -> ScanStep(
                    label = "Scan or enter the job number",
                    errorMessage = stepError,
                    onSubmit = viewModel::onJobNumberSubmitted,
                )
                WizardStep.SCAN_OPERATOR -> ScanStep(
                    label = "Scan or enter the operator ID",
                    errorMessage = stepError,
                    onSubmit = viewModel::onOperatorIdSubmitted,
                )
                WizardStep.SELECT_CATEGORY -> CatalogueStep(
                    title = "Select waste category",
                    emptyMessage = "No waste categories available. Refresh the catalogue in Settings.",
                    label = "Waste Category",
                    options = categories,
                    current = draft.category,
                    display = { it.name },
                    onConfirm = viewModel::onCategoryConfirmed,
                )
                WizardStep.SELECT_WASTE_TYPE -> CatalogueStep(
                    title = "Select waste type",
                    emptyMessage = "No waste types in this category. Refresh the catalogue in Settings.",
                    label = "Waste Type",
                    options = wasteTypes,
                    current = draft.wasteType,
                    display = { "${it.code} — ${it.name}" },
                    onConfirm = viewModel::onWasteTypeConfirmed,
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
    WizardStep.SCAN_BAG to 1,
    WizardStep.SCAN_JOB to 2,
    WizardStep.SCAN_OPERATOR to 3,
    WizardStep.SELECT_CATEGORY to 4,
    WizardStep.SELECT_WASTE_TYPE to 5,
    WizardStep.REVIEW to 5,
)

@Composable
private fun StepIndicator(step: WizardStep) {
    val label = when (step) {
        WizardStep.SCAN_BAG -> "Scan bag code"
        WizardStep.SCAN_JOB -> "Scan or enter the job number"
        WizardStep.SCAN_OPERATOR -> "Scan or enter the operator ID"
        WizardStep.SELECT_CATEGORY -> "Select waste category"
        WizardStep.SELECT_WASTE_TYPE -> "Select waste type"
        WizardStep.REVIEW -> "Review and confirm"
    }
    Text(
        "Step ${WIZARD_STEP_ORDINALS.getValue(step)} of 5 — $label",
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

/**
 * One selection step driven by the cached catalogue. Renders an explicit empty state rather than
 * an empty dropdown: a handheld whose catalogue failed to sync must say so, not present a control
 * that silently does nothing.
 *
 * [current] is what the draft already holds for this step — null on the normal forward pass,
 * non-null when the operator arrived from the review screen's Edit. Seeding `selectedValue` from
 * it is what makes a no-op edit actually a no-op: opening on `options[0]` instead would mean an
 * operator who taps Edit, decides nothing was wrong and presses Confirm silently submits the
 * first option. On the category step the controller would read that as a genuine category change,
 * discard the chosen waste type and force a reselection — the same silent contradiction the
 * category-invalidation rule exists to prevent, just arriving from the other direction.
 *
 * The selection is held as the chosen *value* (`selectedValue`, type `T?`), not an index into
 * [options], and [remember] is keyed only on [current] — not on [options]. A catalogue refresh
 * that arrives mid-step (e.g. a reconnect-triggered sync) replaces [options] with a new list
 * without re-keying this state, so the operator's own in-progress choice survives the refresh:
 * `selected` below is re-derived every recomposition by looking the held value up in the current
 * [options], and only falls back to `options[0]` when the refresh actually dropped that item from
 * the catalogue. Keying on an index instead (the previous shape) silently re-pointed at whatever
 * now occupied position 0 on every such refresh — the mislabelling this shape exists to avoid.
 */
@Composable
private fun <T> CatalogueStep(
    title: String,
    emptyMessage: String,
    label: String,
    options: List<T>,
    current: T?,
    display: (T) -> String,
    onConfirm: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        if (options.isEmpty()) {
            Text(emptyMessage, style = MaterialTheme.typography.labelMedium, color = WarningOrange)
            return@Column
        }
        var selectedValue by remember(current) { mutableStateOf(current) }
        // Falls back to the first option both when nothing has been chosen yet (forward pass) and
        // when a mid-step catalogue refresh removed the held value from the list.
        val selected = options.firstOrNull { it == selectedValue } ?: options[0]
        DropdownSelector(
            label = label,
            options = options,
            selected = selected,
            display = display,
            onSelected = { selectedValue = it },
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
private fun ConfirmRow(label: String, value: String, onEdit: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        }
        if (onEdit != null) {
            TextButton(onClick = onEdit) { Text("Edit", color = AmberPrimary) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownSelector(
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
