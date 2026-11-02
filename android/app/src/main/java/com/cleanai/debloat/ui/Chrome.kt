package com.cleanai.debloat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.data.ShizukuShell

// Shared HaHo-style building blocks for every screen.

/** Round back button · centred title · optional action, like the HaHo kit's headers. */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    action: @Composable () -> Unit = { Spacer(Modifier.size(44.dp)) },
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        action()
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.bouncy(onClick = onClick)
            .size(44.dp)
            .clip(CircleShape)
            .background(CardHi)
            .border(1.dp, Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(20.dp))
    }
}

/** Small pastel pill, e.g. "● Shizuku connected". */
@Composable
fun StatusChip(text: String, dot: Color, background: Color = CardHi.copy(alpha = 0.14f), textColor: Color = Color.White) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(text, color = textColor, fontSize = 12.sp)
    }
}

fun shizukuLabel(state: ShizukuShell.State) = when (state) {
    ShizukuShell.State.READY -> "Shizuku connected"
    ShizukuShell.State.NO_PERMISSION -> "Waiting for Shizuku permission"
    ShizukuShell.State.NOT_RUNNING -> "Shizuku not connected"
}

fun shizukuDot(state: ShizukuShell.State) = if (state == ShizukuShell.State.READY) Ok else Amber
