package com.personal.inout.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.inout.data.LedgerTransaction
import com.personal.inout.ui.ThemeColors
import com.personal.inout.ui.components.CompactInputField
import com.personal.inout.ui.components.CustomCalendarDialog
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EditRecurringRuleModal(
    rule: LedgerTransaction,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (desc: String, amount: Double, frequency: String, anchorDate: Long) -> Unit
) {
    var rawAmount by remember { mutableStateOf(String.format("%.0f", rule.amount)) }
    var description by remember { mutableStateOf(rule.description) }
    var frequency by remember { mutableStateOf(rule.recurringFrequency) }
    var selectedDateEpoch by remember { mutableStateOf(rule.timestamp) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormatted = remember(selectedDateEpoch) { 
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateEpoch)) 
    }

    if (showDatePicker) {
        CustomCalendarDialog(initialDateMillis = selectedDateEpoch, onDismiss = { showDatePicker = false }) { 
            selectedDateEpoch = it; showDatePicker = false 
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, 
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp), 
                colors = CardDefaults.cardColors(containerColor = theme.surface), 
                modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 420.dp).border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("EDIT RECURRING MANDATE", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    
                    CompactInputField(value = description, onValueChange = { description = it }, placeholder = "Mandate Description / Payee", modifier = Modifier.fillMaxWidth())

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactInputField(
                            value = rawAmount, 
                            onValueChange = { input -> if (input.all { c -> c.isDigit() || c == '.' }) rawAmount = input }, 
                            placeholder = "Amount ₹", 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), 
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).clickable { showDatePicker = true }.padding(horizontal = 10.dp), 
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(dateFormatted, color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Text("EXECUTION CADENCE", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly", "YEARLY" to "Yearly").forEach { (key, label) ->
                            val isSel = frequency == key
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.accent else theme.surfaceAlt).border(1.dp, if (isSel) theme.accent else theme.borderLight, RoundedCornerShape(6.dp)).clickable { frequency = key }.padding(vertical = 7.dp), 
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (isSel) theme.bg else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    val isValid = description.isNotBlank() && (rawAmount.toDoubleOrNull() ?: 0.0) > 0.0
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { val amt = rawAmount.toDoubleOrNull() ?: return@Button; onSave(description, amt, frequency, selectedDateEpoch) }, 
                            modifier = Modifier.weight(1.3f).height(44.dp), 
                            shape = RoundedCornerShape(8.dp), 
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight), 
                            colors = ButtonDefaults.buttonColors(containerColor = if (isValid) theme.accent else Color.Transparent, disabledContainerColor = Color.Transparent), 
                            enabled = isValid
                        ) {
                            Text("Save Mandate", color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onDismiss, 
                            modifier = Modifier.weight(1f).height(44.dp), 
                            shape = RoundedCornerShape(8.dp), 
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)
                        ) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
