package com.mitas.ppnam.station4aa.ui.waste

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mitas.ppnam.station4aa.domain.wizard.WasteTransactionDraft
import com.mitas.ppnam.station4aa.domain.wizard.WizardStep
import com.mitas.ppnam.station4aa.ui.components.AppScaffold
import com.mitas.ppnam.station4aa.ui.components.DiscardDraftDialog
import com.mitas.ppnam.station4aa.ui.theme.AmberPrimary
import com.mitas.ppnam.station4aa.ui.theme.DangerRed
import com.mitas.ppnam.station4aa.ui.theme.GraphiteSurface
import com.mitas.ppnam.station4aa.ui.theme.TextMuted
import com.mitas.ppnam.station4aa.ui.theme.TextPrimary
import com.mitas.ppnam.station4aa.ui.theme.WarningOrange

@Composable
fun WasteGatheringScreen(
    onBack: () -> Unit,
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

    val hasDraft = draft != WasteTransactionDraft()
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var leaveAfterDiscard by rememberSaveable { mutableStateOf(false) }
    val requestLeave: () -> Unit = {
        if (hasDraft) {
            leaveAfterDiscard = true
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    // Mid-wizard Back with values captured asks first; the review AlertDialog has its own Back
    // handling (it is a separate window), so this only runs on steps 1-5.
    BackHandler(enabled = hasDraft && step != WizardStep.REVIEW) { requestLeave() }

    if (showDiscardDialog) {
        DiscardDraftDialog(
            onKeep = {
                showDiscardDialog = false
                leaveAfterDiscard = false
            },
            onDiscard = {
                showDiscardDialog = false
                viewModel.onCancelTransaction()
                if (leaveAfterDiscard) {
                    leaveAfterDiscard = false
                    onBack()
                }
            },
        )
    }

    if (step == WizardStep.REVIEW) {
        AlertDialog(
            onDismissRequest = { viewModel.onReviewDismissed() },
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
                        Text(stepError!!, style = MaterialTheme.typography.labelSmall, color = DangerRed)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onReviewConfirmed() }, enabled = !isSubmitting) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onReviewDismissed() }) { Text("Back") }
            },
            containerColor = GraphiteSurface
        )
    }

    AppScaffold(
        title = "Waste Collection",
        status = connectionStatus,
        onBack = requestLeave,
        onSettings = onSettings,
        operatorName = session?.operatorName?.ifBlank { session?.operatorId },
        operatorRole = session?.role,
        onLogout = viewModel::logout,
    ) { padding ->
        // `padding` already carries the IME inset (AppScaffold uses WindowInsets.safeDrawing), so
        // the content area shrinks above the keyboard; what was missing was a scroll container —
        // without one, Submit was half-clipped and Cancel transaction unreachable (audit S4-01).
        // Deliberately no extra imePadding(): it would double the IME inset.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (pendingCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "$pendingCount collection${if (pendingCount == 1) "" else "s"} queued, awaiting delivery",
                        style = MaterialTheme.typography.labelMedium,
                        color = WarningOrange,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.retryNow() }) {
                        Text("Retry now")
                    }
                }
            }
            lastQueuedMessage?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (lastMessageIsError) DangerRed else TextMuted,
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
                    hint = "Scan the barcode, or enter it manually below.",
                    errorMessage = stepError,
                    onSubmit = viewModel::onBagCodeSubmitted,
                )
                WizardStep.SCAN_JOB -> ScanStep(
                    label = "Scan or enter the job number",
                    hint = "Scan the barcode, or enter it manually below.",
                    errorMessage = stepError,
                    onSubmit = viewModel::onJobNumberSubmitted,
                )
                WizardStep.SCAN_OPERATOR -> ScanStep(
                    label = "Scan or enter the operator ID",
                    hint = "Scan the operator's barcode or badge, or enter the ID manually below.",
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

            TextButton(onClick = { if (hasDraft) showDiscardDialog = true }) {
                Text("Cancel transaction", color = DangerRed)
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
    hint: String,
    errorMessage: String?,
    onSubmit: (String) -> Unit,
) {
    var manualValue by rememberSaveable(label) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val submit: () -> Unit = {
        if (manualValue.isNotBlank()) {
            // Clearing focus first stops a hardware Enter from landing on the toolbar icons
            // (audit S4-15) and closes the keyboard so the next step is fully visible.
            focusManager.clearFocus()
            onSubmit(manualValue)
            manualValue = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(hint, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        // Above the field, not below it: text under the field lands exactly under the keyboard
        // (audit group (a) sub-cause 4).
        if (errorMessage != null) {
            Text(errorMessage, style = MaterialTheme.typography.labelMedium, color = DangerRed)
        }
        OutlinedTextField(
            value = manualValue,
            onValueChange = { manualValue = it },
            label = { Text("Manual entry") },
            singleLine = true,
            isError = errorMessage != null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmberPrimary,
                focusedLabelColor = AmberPrimary,
                cursorColor = AmberPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = submit,
            enabled = manualValue.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
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
            modifier = Modifier.fillMaxWidth().height(56.dp),
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

    // Outlined like every other field in the app (audit S4-16); no Card wrapper.
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmberPrimary,
                focusedLabelColor = AmberPrimary,
            ),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Five 48 dp rows exactly: the menu never ends in a clipped sliver of a sixth item
            // (audit S4-16); longer lists scroll inside the menu.
            modifier = Modifier.heightIn(max = 240.dp),
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
