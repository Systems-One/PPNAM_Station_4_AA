package com.mitas.ppnam.station4aa.ui.weigh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mitas.ppnam.station4aa.ui.components.AppScaffold
import com.mitas.ppnam.station4aa.ui.theme.AmberPrimary
import com.mitas.ppnam.station4aa.ui.theme.GraphiteBorder
import com.mitas.ppnam.station4aa.ui.theme.GraphiteSurface
import com.mitas.ppnam.station4aa.ui.theme.SuccessGreen
import com.mitas.ppnam.station4aa.ui.theme.TextMuted
import com.mitas.ppnam.station4aa.ui.theme.TextPrimary
import com.mitas.ppnam.station4aa.ui.theme.WarningOrange
import java.util.Locale

/**
 * The handheld-triggered weigh of contract 5.1.0 §9.2. The operator puts a registered bag on
 * Station 4's scale, scans its code, and asks for the weight; Station 4 measures and answers.
 *
 * Nobody need be signed in at the station PC — this handheld's own session is the authority — so
 * the screen deliberately says so rather than leaving the operator wondering whether to go and
 * find someone at the PC first.
 */
@Composable
fun WeighBagScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    viewModel: WeighBagViewModel,
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val session by viewModel.session.collectAsState()
    val operatorName by viewModel.operatorName.collectAsState()
    val bagCode by viewModel.bagCode.collectAsState()
    val feedback by viewModel.feedback.collectAsState()
    val isWeighing by viewModel.isWeighing.collectAsState()

    AppScaffold(
        title = "Weigh bag",
        status = connectionStatus,
        onBack = onBack,
        onSettings = onSettings,
        operatorName = operatorName.takeIf { it.isNotBlank() },
        operatorRole = session?.role,
        loading = isWeighing,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Place the bag on the Station 4 scale, then scan its code.",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                "Nobody needs to be signed in at the station — the weight is recorded against you.",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )

            OutlinedTextField(
                value = bagCode,
                onValueChange = viewModel::onBagCodeChanged,
                label = { Text("Bag code") },
                singleLine = true,
                enabled = !isWeighing,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AmberPrimary,
                    focusedLabelColor = AmberPrimary,
                    cursorColor = AmberPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::onRequestWeight,
                enabled = bagCode.isNotBlank() && !isWeighing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isWeighing) "Weighing…" else "Request weight")
            }

            when (val current = feedback) {
                is WeighFeedback.Weighed -> WeighedCard(current)
                is WeighFeedback.Problem -> ProblemCard(current)
                null -> Unit
            }
        }
    }
}

@Composable
private fun WeighedCard(result: WeighFeedback.Weighed) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, GraphiteBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                // Three decimals is the contract's own precision for weightKg; trailing zeroes are
                // trimmed so a clean 8.5 kg does not read as the false precision of 8.500 kg.
                "${formatKilograms(result.weightKg)} kg",
                style = MaterialTheme.typography.headlineMedium,
                color = SuccessGreen,
            )
            Text(
                result.bagCode,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = TextPrimary,
            )
            result.capturedBy?.takeIf { it.isNotBlank() }?.let {
                Text("Captured by $it", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            }
            Text(
                "Scan the next bag.",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun ProblemCard(problem: WeighFeedback.Problem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, WarningOrange),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(problem.message, style = MaterialTheme.typography.bodyMedium, color = WarningOrange)
            if (problem.canRetrySameBag) {
                // The bag code is still in the field precisely so this is one tap away.
                Text(
                    "The bag code is still here — fix the problem and tap Request weight again.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
    }
}

/** Renders at the contract's three-decimal precision without inventing trailing zeroes. */
internal fun formatKilograms(weightKg: Double): String =
    String.format(Locale.US, "%.3f", weightKg).trimEnd('0').trimEnd('.')
