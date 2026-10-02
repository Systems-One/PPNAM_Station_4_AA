package com.mitas.ppnam.station4aa.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.mitas.ppnam.station4aa.ui.theme.DangerRed
import com.mitas.ppnam.station4aa.ui.theme.GraphiteSurface
import com.mitas.ppnam.station4aa.ui.theme.TextMuted
import com.mitas.ppnam.station4aa.ui.theme.TextPrimary

/** The fleet's "Close the app?" [Stay | Close] confirmation (Station 2's wording), used on both
 * Login and Home so Back behaves the same on every root screen (audit static-04). M3 default
 * rounded shape; neutral dismiss, red destructive confirm. */
@Composable
fun ExitAppDialog(onStay: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text("Close the app?", color = TextPrimary) },
        text = { Text("You'll leave PPNAM Station 4 and return to the home screen.", color = TextMuted) },
        confirmButton = { TextButton(onClick = onClose) { Text("Close", color = DangerRed) } },
        dismissButton = { TextButton(onClick = onStay) { Text("Stay") } },
        containerColor = GraphiteSurface,
    )
}

/** Asked before any path that would throw away a partly captured collection (audit S4-09). */
@Composable
fun DiscardDraftDialog(onKeep: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Discard this collection?", color = TextPrimary) },
        text = { Text("The values captured so far will be lost.", color = TextMuted) },
        confirmButton = { TextButton(onClick = onDiscard) { Text("Discard", color = DangerRed) } },
        dismissButton = { TextButton(onClick = onKeep) { Text("Keep editing") } },
        containerColor = GraphiteSurface,
    )
}
