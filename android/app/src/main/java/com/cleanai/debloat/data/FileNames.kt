package com.cleanai.debloat.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Turning what the user types into a safe file name, and renaming files other apps hold. */
object FileNames {
    private const val MAX_LENGTH = 80
    private val NOT_ALLOWED = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    /**
     * A clean name without its extension: characters files can't hold become spaces, spaces are tidied,
     * and a typed [ext] (".pdf", ".docx", …) is dropped. Null when nothing usable is left.
     */
    fun clean(typed: String, ext: String): String? {
        var s = typed.trim()
        if (s.endsWith(ext, ignoreCase = true)) s = s.dropLast(ext.length)
        s = s.replace(NOT_ALLOWED, " ").replace(Regex("\\s+"), " ").trim().trim('.').trim()
        return s.take(MAX_LENGTH).trim().ifEmpty { null }
    }

    /**
     * Renames a file picked through Android's file browser. Returns its new link, or null when that
     * folder or app doesn't allow renaming (many cloud and download folders don't).
     */
    fun renameDocument(ctx: Context, uri: Uri, newName: String): Uri? = runCatching {
        if (!DocumentsContract.isDocumentUri(ctx, uri)) return null
        DocumentsContract.renameDocument(ctx.contentResolver, uri, newName)
    }.getOrNull()
}
