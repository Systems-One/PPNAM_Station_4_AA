package com.mitas.ppnam.station4aa.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Swallows the key-up of Enter / Numpad Enter on a field whose Enter key-down submits. Without it
 * the submit moves focus (or navigates) on key-down and the matching key-up then lands on
 * whichever control took focus, "clicking" it (audit S4-R01: the operator chip opened "Log out?").
 * The key-down is left alone so the IME action still fires.
 */
fun Modifier.consumeEnterKeyUp(): Modifier = onPreviewKeyEvent { event ->
    event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.NumPadEnter)
}
