package com.cleanai.debloat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.agent.AgentViewModel

/** Name, age and place: asked on first launch, editable later from Settings. Stored only on the phone. */
@Composable
fun ProfileScreen(vm: AgentViewModel, firstTime: Boolean, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(vm.userName) }
    var age by rememberSaveable { mutableStateOf(vm.userAge) }
    var place by rememberSaveable { mutableStateOf(vm.userPlace) }
    val ok = name.isNotBlank()

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding()) {
        if (!firstTime) ScreenTopBar("Your profile", onDone)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (firstTime) {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Charcoal)
                        .padding(22.dp),
                ) {
                    AppMark(Modifier.size(48.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("Welcome to\nMobile Handler", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Tell me a little about you, so I can greet you and help better.", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                }
            }
            Field("Your name", name, { name = it }, KeyboardType.Text, "e.g. Janardan")
            Field("Age (optional)", age, { a -> age = a.filter { it.isDigit() }.take(3) }, KeyboardType.Number, "e.g. 28")
            Field("City or place (optional)", place, { place = it }, KeyboardType.Text, "e.g. Delhi — used for weather")
            StatusChip("🔒 Saved only on this phone — never shared", Ok, background = Mint.copy(alpha = 0.5f), textColor = TextPrimary)
        }
        Box(
            Modifier.bouncy(enabled = ok) { vm.saveProfile(name, age, place); onDone() }
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(if (ok) Accent else Accent.copy(alpha = 0.35f))
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (firstTime) "Let's start" else "Save", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, type: KeyboardType, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label, fontSize = 16.sp) },
        placeholder = { Text(hint, color = TextSecondary) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, color = TextPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = type, capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TextPrimary, focusedLabelColor = TextPrimary, cursorColor = Accent,
            unfocusedContainerColor = CardHi, focusedContainerColor = CardHi,
        ),
    )
}
