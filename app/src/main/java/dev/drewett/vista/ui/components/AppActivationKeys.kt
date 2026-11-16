package dev.drewett.vista.ui.components

import android.view.KeyEvent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Handles a remote select press once, including repeat events and focus transfer to its menu. */
internal fun Modifier.appActivationKeys(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier = composed {
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Job?>(null) }
    var down by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { pending?.cancel() } }
    this.onFocusChanged { if (!it.hasFocus) { pending?.cancel(); down = false } }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode !in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                false
            } else when (key.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (!down && key.repeatCount == 0) {
                        val openingKeyCode = key.keyCode
                        val openingDownTime = key.downTime
                        down = true
                        held = false
                        pending = scope.launch {
                            delay(timeout)
                            if (down && longClick != null) {
                                held = true
                                down = false // The matching release belongs to the new menu window.
                                OpeningSelectHandoff.press = OpeningSelectPress(openingKeyCode, openingDownTime)
                                longClick?.invoke()
                            }
                        }
                    }
                    true
                }
                KeyEvent.ACTION_UP -> {
                    pending?.cancel()
                    val activate = down && !held && !key.isCanceled
                    down = false
                    if (activate) click()
                    true
                }
                else -> true
            }
        }
}
