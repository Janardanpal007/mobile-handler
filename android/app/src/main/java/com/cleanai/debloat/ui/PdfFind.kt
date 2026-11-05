package com.cleanai.debloat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.data.EditState
import com.cleanai.debloat.data.PdfSession
import kotlinx.coroutines.delay

/** One place the searched words appear: the page's position in the document (0-based) and the line they are on. */
data class FindHit(val page: Int, val line: String)

private const val MAX_HITS = 200
private const val TYPING_PAUSE_MS = 250L

/**
 * Lines that contain [query], ignoring case and extra spaces, in page order.
 * [pages] holds each page's lines of text, in the order the pages are shown.
 */
fun findInPages(pages: List<List<String>>, query: String, limit: Int = MAX_HITS): List<FindHit> {
    val q = query.trim().replace(Regex("\\s+"), " ")
    if (q.isEmpty()) return emptyList()
    val hits = mutableListOf<FindHit>()
    pages.forEachIndexed { page, lines ->
        lines.forEach { line ->
            if (hits.size < limit && line.replace(Regex("\\s+"), " ").contains(q, ignoreCase = true)) hits += FindHit(page, line.trim())
        }
    }
    return hits
}

/**
 * "Find text": type words, see every line that has them, tap one to go to its page.
 * Works on the pages as they are now (moved or turned pages included); scanned pages have no text to search.
 */
@Composable
internal fun FindTextDialog(state: EditState, session: PdfSession, onGo: (page: Int) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var pagesText by remember { mutableStateOf<List<List<String>>?>(null) }
    var hits by remember { mutableStateOf(emptyList<FindHit>()) }
    LaunchedEffect(state.pages) {
        pagesText = state.pages.map { p -> if (p.source < 0) emptyList() else session.lines(p.source).map { it.text } }
    }
    LaunchedEffect(query, pagesText) {
        val text = pagesText ?: return@LaunchedEffect
        delay(TYPING_PAUSE_MS)
        hits = findInPages(text, query)
    }
    val noText = pagesText?.all { it.isEmpty() } == true
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Find text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query, { query = it.take(100) }, singleLine = true, label = { Text("Words to find") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth(),
                )
                val status = when {
                    pagesText == null -> "Reading the pages…"
                    noText -> "This PDF has no text to search. It may be a scan or photos of pages."
                    query.isBlank() -> "Type a word or phrase."
                    hits.isEmpty() -> "Not found."
                    hits.size >= MAX_HITS -> "First $MAX_HITS matches"
                    else -> "${hits.size} match${if (hits.size == 1) "" else "es"} on ${hits.distinctBy { it.page }.size} page${if (hits.distinctBy { it.page }.size == 1) "" else "s"}"
                }
                Text(status, color = TextSecondary, fontSize = 13.sp)
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(hits) { hit ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button, onClickLabel = "Go to page ${hit.page + 1}") { onGo(hit.page) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text("Page ${hit.page + 1}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(hit.line, color = TextPrimary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}
