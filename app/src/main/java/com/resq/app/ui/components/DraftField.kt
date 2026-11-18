package com.resq.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Text that is edited locally and persisted in the background.
 *
 * Feeding a text field straight from the persisted value round-trips every
 * keystroke through DataStore -> Flow -> UiState, which lags typing by a few
 * frames and makes the cursor jump and characters drop. Here the field owns
 * its own state; [adopt] loads the saved value in once when it arrives,
 * without ever overwriting what the user has already started typing.
 */
class DraftField internal constructor(
    private val state: MutableState<TextFieldValue>,
    private val adopted: MutableState<Boolean>,
) {
    var value: TextFieldValue
        get() = state.value
        set(new) {
            adopted.value = true
            state.value = new
        }

    internal fun adopt(persisted: String) {
        if (!adopted.value && persisted.isNotEmpty()) {
            state.value = TextFieldValue(persisted, TextRange(persisted.length))
            adopted.value = true
        }
    }
}

@Composable
fun rememberDraftField(persisted: String): DraftField {
    val state = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(persisted))
    }
    val adopted = rememberSaveable { mutableStateOf(persisted.isNotEmpty()) }
    val field = remember(state, adopted) { DraftField(state, adopted) }
    LaunchedEffect(persisted) { field.adopt(persisted) }
    return field
}
