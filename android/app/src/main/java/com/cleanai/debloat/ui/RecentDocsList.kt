package com.cleanai.debloat.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.data.RecentDocs

/** Recently opened or saved Word / Excel files on a start page; nothing is shown when there are none. */
@Composable
internal fun RecentDocsList(title: String, entries: List<RecentDocs.Entry>, onOpen: (RecentDocs.Entry) -> Unit) {
    if (entries.isEmpty()) return
    Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
    entries.forEach { e ->
        val ago = DateUtils.getRelativeTimeSpanString(e.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        ToolRow(e.name, ago) { onOpen(e) }
    }
}
