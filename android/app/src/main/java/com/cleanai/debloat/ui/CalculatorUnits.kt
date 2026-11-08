package com.cleanai.debloat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanai.debloat.data.Units

/** Length, weight, temperature and more: type a value, pick its unit, and see it in every other unit at once. */
@Composable
internal fun ColumnScope.UnitsPad() {
    var categoryIndex by rememberSaveable { mutableStateOf(0) }
    var fromIndex by rememberSaveable { mutableStateOf(2) }
    var value by rememberSaveable { mutableStateOf("1") }
    val category = Units.categories[categoryIndex]
    val from = category.units[fromIndex.coerceIn(0, category.units.lastIndex)]
    Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryChips(categoryIndex) { i ->
            categoryIndex = i
            // Start on a common unit: metre, kilogram, Celsius…
            fromIndex = Units.categories[i].units.indexOfFirst { it.toBase(1.0) == 1.0 }.coerceAtLeast(0)
        }
        Field("Value", value, allowMinus = category.allowsNegative) { value = it }
        UnitPicker(category, from) { fromIndex = it }
        val v = value.toDoubleOrNull() ?: return@Column Text("Type a number to convert.", color = KeyFn, fontSize = 14.sp)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(horizontal = 18.dp, vertical = 10.dp)) {
            category.units.filter { it !== from }.forEach { to ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(to.name, color = KeyFn, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("${Units.format(Units.convert(v, from, to))} ${to.symbol}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Units.categories.forEachIndexed { i, c ->
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (i == selected) KeyOp else Panel).clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text(c.name, color = Color.White, fontSize = 13.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun UnitPicker(category: Units.Category, from: Units.Measure, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("From", color = KeyFn, fontSize = 13.sp)
                Text("${from.name} (${from.symbol})", color = Color.White, fontSize = 20.sp)
            }
            Text("▾", color = KeyOp, fontSize = 20.sp)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            category.units.forEachIndexed { i, u ->
                DropdownMenuItem(text = { Text("${u.name} (${u.symbol})") }, onClick = { open = false; onPick(i) })
            }
        }
    }
}
