package com.personal.inout.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
import com.personal.inout.util.MathEvaluator
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingCommandHud(
    activePockets: List<VaultPocket>,
    prefilledPocketId: Long? = null,
    prefilledNote: String = "",
    prefilledAmount: Double? = null,
    onDismiss: () -> Unit,
    onSubmit: (nature: MovementNature, sourceId: Long?, targetId: Long?, amount: Double, cat: String, note: String, date: Long, isRec: Boolean, freq: String) -> Unit
) {
    val theme = LocalThemeColors.current
    val scrollState = rememberScrollState()

    var primaryNature by remember { mutableStateOf(MovementNature.OUTFLOW) }
    var expression by remember { mutableStateOf(prefilledAmount?.let { String.format("%.2f", it) } ?: "") }
    var note by remember { mutableStateOf(prefilledNote) }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val liquidPockets = remember(activePockets) { activePockets.filter { it.pocketType == PocketType.LIQUID } }
    val creditPockets = remember(activePockets) { activePockets.filter { it.pocketType == PocketType.CREDIT_LINE } }
    val spendOptions = remember(liquidPockets, creditPockets) { liquidPockets + creditPockets }

    var selectedSpendPocket by remember(spendOptions, prefilledPocketId) {
        mutableStateOf(spendOptions.firstOrNull { it.id == prefilledPocketId } ?: spendOptions.firstOrNull())
    }

    var selectedTransferTarget by remember(liquidPockets) {
        mutableStateOf(liquidPockets.firstOrNull { it.id != selectedSpendPocket?.id } ?: liquidPockets.firstOrNull())
    }

    val categories = listOf("Food & Dining", "Groceries", "Transport", "Shopping", "Bills", "Health", "Leisure", "General")
    var selectedCategory by remember { mutableStateOf("Food & Dining") }

    var isRecurring by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var showMoreOptions by remember { mutableStateOf(false) }

    val computedAmount = remember(expression) { MathEvaluator.evaluate(expression) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.surfaceAlt)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        MovementNature.OUTFLOW to "Expense",
                        MovementNature.INFLOW to "Income",
                        MovementNature.TRANSFER to "Transfer"
                    ).forEach { (nat, label) ->
                        val isSel = primaryNature == nat
                        val activeColor = when (nat) {
                            MovementNature.OUTFLOW -> theme.mildRed
                            MovementNature.INFLOW -> theme.mildGreen
                            else -> theme.accent
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) activeColor else Color.Transparent)
                                .clickable { primaryNature = nat }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (isSel) theme.bg else theme.textMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Column {
                    CompactInputField(
                        value = expression,
                        onValueChange = { expression = it },
                        placeholder = "Amount (e.g. 150+40)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (computedAmount != null && expression.any { it in "+-*/" }) {
                        Text(
                            "= ₹ ${String.format("%.2f", computedAmount)}",
                            color = theme.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                        )
                    }
                }

                if (primaryNature == MovementNature.TRANSFER) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillDropdown(
                            label = "From: ${selectedSpendPocket?.name ?: "Select"}",
                            modifier = Modifier.weight(1f),
                            items = liquidPockets.map { it.name },
                            onSelect = { name -> selectedSpendPocket = liquidPockets.firstOrNull { it.name == name } },
                            theme = theme
                        )
                        PillDropdown(
                            label = "To: ${selectedTransferTarget?.name ?: "Select"}",
                            modifier = Modifier.weight(1f),
                            items = liquidPockets.filter { it.id != selectedSpendPocket?.id }.map { it.name },
                            onSelect = { name -> selectedTransferTarget = liquidPockets.firstOrNull { it.name == name } },
                            theme = theme
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val sourcePockets = if (primaryNature == MovementNature.OUTFLOW) spendOptions else liquidPockets
                        PillDropdown(
                            label = "Pocket: ${selectedSpendPocket?.name ?: "Select"}",
                            modifier = Modifier.weight(1f),
                            items = sourcePockets.map { it.name },
                            onSelect = { name -> selectedSpendPocket = sourcePockets.firstOrNull { it.name == name } },
                            theme = theme
                        )
                        PillDropdown(
                            label = selectedCategory,
                            modifier = Modifier.weight(1f),
                            items = categories,
                            onSelect = { selectedCategory = it },
                            theme = theme
                        )
                    }
                }

                CompactInputField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "Merchant / Note / Narration",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMoreOptions = !showMoreOptions }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
                    Text("Date: $dStr", color = theme.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (showMoreOptions) "Fewer Options ▲" else "More Options ▼",
                        color = theme.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                AnimatedVisibility(visible = showMoreOptions) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showDatePicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Change Date", color = theme.textBright, fontSize = 11.sp)
                        }

                        if (primaryNature != MovementNature.TRANSFER) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Auto Recurring?", color = theme.textBright, fontSize = 12.sp)
                                Switch(
                                    checked = isRecurring,
                                    onCheckedChange = { isRecurring = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = theme.bg,
                                        checkedTrackColor = theme.accent
                                    )
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val amt = computedAmount ?: 0.0
                        if (amt > 0.0) {
                            when (primaryNature) {
                                MovementNature.OUTFLOW -> {
                                    selectedSpendPocket?.let {
                                        onSubmit(MovementNature.OUTFLOW, it.id, null, amt, selectedCategory, note, selectedDateMillis, isRecurring, frequency)
                                    }
                                }
                                MovementNature.INFLOW -> {
                                    selectedSpendPocket?.let {
                                        onSubmit(MovementNature.INFLOW, null, it.id, amt, selectedCategory, note, selectedDateMillis, isRecurring, frequency)
                                    }
                                }
                                MovementNature.TRANSFER -> {
                                    if (selectedSpendPocket != null && selectedTransferTarget != null) {
                                        onSubmit(MovementNature.TRANSFER, selectedSpendPocket!!.id, selectedTransferTarget!!.id, amt, "Transfer", note, selectedDateMillis, false, "NONE")
                                    }
                                }
                                else -> Unit
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("Commit to Vault", color = theme.bg, fontWeight = FontWeight.Black, fontSize = 13.5.sp)
                }
            }
        }
    }

    if (showDatePicker) {
        HighContrastDatePicker(
            initialDateMillis = selectedDateMillis,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                selectedDateMillis = it
                showDatePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighContrastDatePicker(
    initialDateMillis: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val theme = LocalThemeColors.current
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { pickerState.selectedDateMillis?.let { onDateSelected(it) } }) {
                Text("Select", color = theme.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } },
        colors = DatePickerDefaults.colors(
            containerColor = theme.surface,
            titleContentColor = theme.textBright,
            headlineContentColor = theme.accent,
            weekdayContentColor = theme.textMuted,
            subheadContentColor = theme.textBright,
            yearContentColor = theme.textBright,
            currentYearContentColor = theme.accent,
            selectedYearContentColor = theme.bg,
            selectedYearContainerColor = theme.accent,
            dayContentColor = theme.textBright,
            selectedDayContentColor = theme.bg,
            selectedDayContainerColor = theme.accent,
            todayContentColor = theme.accent,
            todayDateBorderColor = theme.accent
        )
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun PillDropdown(
    label: String,
    modifier: Modifier,
    items: List<String>,
    onSelect: (String) -> Unit,
    theme: ThemeColors
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(theme.surfaceAlt)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(label, color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(theme.surface)
        ) {
            items.forEach { itm ->
                DropdownMenuItem(
                    text = { Text(itm, color = theme.textBright, fontSize = 12.sp) },
                    onClick = {
                        onSelect(itm)
                        expanded = false
                    }
                )
            }
        }
    }
}
