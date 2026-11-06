package com.cleanai.debloat.ui

import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.cleanai.debloat.data.PdfEdit

/**
 * A file to open in the PDF, Word or Excel editor: one tapped in another app ("Open with Mobile Handler")
 * or a recent file chosen on Home. The editor takes it when it shows, or straight away if it is already open.
 * An editor with unsaved changes asks before anything replaces it.
 */
object DocLaunch {
    enum class Kind(val screen: String) { PDF("pdfreader"), WORD("word"), EXCEL("excel") }

    private class Pending(val kind: Kind, val uri: Uri)

    private var pending by mutableStateOf<Pending?>(null)

    /** The editor that has unsaved changes right now, if any. */
    var unsaved: Kind? = null
        private set

    /** Opens an editor's screen; set by the activity. */
    var showScreen: (String) -> Unit = {}

    /** Changes whenever a new file is waiting, so an open editor can pick it up. */
    val waiting: Uri? get() = pending?.uri
    private val waitingKind: Kind? get() = pending?.kind

    fun open(kind: Kind, uri: Uri) { pending = Pending(kind, uri) }

    /** The waiting file for [kind], handed over once. */
    fun take(kind: Kind): Uri? = pending?.takeIf { it.kind == kind }?.uri?.also { pending = null }

    /** True when the file can go straight to its editor; false when an editor with unsaved changes must ask first. */
    fun canSwitchTo(kind: Kind) = unsaved == null || unsaved == kind

    private const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private val SHEET_TYPES = setOf(XLSX, "text/csv", "text/comma-separated-values")

    /** Which editor handles a file, from its type or, failing that, its name; null when none does. */
    fun kindOf(mime: String?, name: String?): Kind? {
        val type = mime?.lowercase()
        val ext = name?.substringAfterLast('.', "")?.lowercase()
        return when {
            type == "application/pdf" || ext == "pdf" -> Kind.PDF
            type == DOCX || ext == "docx" -> Kind.WORD
            type in SHEET_TYPES || ext == "xlsx" || ext == "csv" -> Kind.EXCEL
            else -> null
        }
    }

    /**
     * Used by an open editor: opens files sent to it with [open], and, while it has unsaved changes,
     * asks before a file from another app replaces it or takes the user to another editor.
     */
    @Composable
    internal fun Receiver(kind: Kind, dirty: Boolean, currentName: String, open: (Uri) -> Unit) {
        val ctx = LocalContext.current
        var asking by remember { mutableStateOf(false) }
        DisposableEffect(dirty) {
            if (dirty) unsaved = kind else if (unsaved == kind) unsaved = null
            onDispose { if (unsaved == kind) unsaved = null }
        }
        LaunchedEffect(waiting) {
            val next = waitingKind ?: return@LaunchedEffect
            when {
                dirty -> asking = true
                next == kind -> take(kind)?.let(open)
            }
        }
        val next = pending
        if (asking && next != null) {
            OpenAnotherDialog(
                current = currentName,
                next = PdfEdit.displayName(ctx, next.uri),
                onOpen = {
                    asking = false
                    if (next.kind == kind) take(kind)?.let(open) else { unsaved = null; showScreen(next.kind.screen) }
                },
                onKeep = { asking = false; pending = null },
            )
        }
    }
}

/** Asked when a file arrives from another app while the open one has unsaved changes. */
@Composable
private fun OpenAnotherDialog(current: String, next: String, onOpen: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Open $next?") },
        text = { Text("Your changes to $current aren't saved yet and will be lost.") },
        confirmButton = { TextButton(onClick = onOpen) { Text("Open") } },
        dismissButton = { TextButton(onClick = onKeep) { Text("Keep editing") } },
    )
}
