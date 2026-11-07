package com.cleanai.debloat.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import com.cleanai.debloat.data.FileNames

/**
 * Asks for a file name. [initial] is shown selected so typing replaces it; [ext] (".pdf", ".docx", …) is added for you.
 * [onDone] gets the cleaned name without its extension.
 */
@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    ext: String,
    confirm: String = "Save",
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = initial.removeSuffix(ext)
    var value by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    val cleaned = FileNames.clean(value.text, ext)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("File name") },
                suffix = { Text(ext) },
                supportingText = { if (cleaned == null) Text("Type a name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { cleaned?.let(onDone) }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { cleaned?.let(onDone) }, enabled = cleaned != null) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Why a name is being asked for, which sets the dialog's wording. */
internal enum class NameFor(val title: String, val confirm: String) {
    NEW_FILE("Name your file", "Save"),
    COPY("Save as a new file", "Save"),
    RENAME("Rename", "Rename"),
}

/** A pending name question: what it is for and the extension the file gets. */
internal class NameAsk(val why: NameFor, val ext: String, val initial: String)
