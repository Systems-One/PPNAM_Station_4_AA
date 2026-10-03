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

/**
 * Process-wide guard for the Enter key-up that follows an Enter-submit. A submit swaps the step or
 * disposes the field on key-down, so the matching key-up has no focused field to consume it and
 * Compose hands it to the first focusable control (first the operator chip, then "Cancel
 * transaction"), which "clicks" on key-up. A submit calls [arm]; `MainActivity.dispatchKeyEvent`
 * drops an Enter key-up for [WINDOW_MS] afterwards.
 */
object EnterKeyGuard {
    private const val WINDOW_MS = 600L
    @Volatile private var armedUntil = 0L

    fun arm() {
        armedUntil = android.os.SystemClock.uptimeMillis() + WINDOW_MS
    }

    fun shouldSwallow(event: android.view.KeyEvent): Boolean =
        event.action == android.view.KeyEvent.ACTION_UP &&
            (event.keyCode == android.view.KeyEvent.KEYCODE_ENTER || event.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) &&
            android.os.SystemClock.uptimeMillis() < armedUntil
}
