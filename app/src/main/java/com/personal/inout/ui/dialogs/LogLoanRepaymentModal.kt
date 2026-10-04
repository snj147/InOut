package com.personal.inout.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.personal.inout.data.LedgerPocket
import com.personal.inout.ui.ThemeColors
import com.personal.inout.ui.components.CompactInputField

@Composable
fun LogLoanRepaymentModal(
    loanPocket: LedgerPocket,
    outstandingBalance: Double,
    liquidPockets: List<LedgerPocket>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onRecordRepayment: (amount: Double, sourceBankId: Long) -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var selectedBankId by remember(liquidPockets) { mutableStateOf(liquidPockets.firstOrNull()?.id) }
    var showDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp).border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🏛 LOG LOAN REPAYMENT", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Account: ${loanPocket.name}", color = theme.textMuted, fontSize = 11.5.sp)
                        val balColor = if (outstandingBalance == 0.0) theme.textBright else theme.mildRed
                        Text("Outstanding Balance: ₹ ${String.format("%,.0f", outstandingBalance)}", color = balColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    CompactInputField(value = amountInput, onValueChange = { input -> if (input.all { c -> c.isDigit() || c == '.' }) amountInput = input }, placeholder = "Repayment Amount ₹", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

                    val activeBank = liquidPockets.firstOrNull { it.id == selectedBankId }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).clickable { showDropdown = true }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Paid From: ${activeBank?.name ?: "Select Bank"}", color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = theme.accent)
                        }
                        DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }, modifier = Modifier.background(theme.surface)) {
                            liquidPockets.forEach { pocket -> DropdownMenuItem(text = { Text(pocket.name, color = theme.textBright) }, onClick = { selectedBankId = pocket.id; showDropdown = false }) }
                        }
                    }

                    val isValid = (amountInput.toDoubleOrNull() ?: 0.0) > 0.0 && selectedBankId != null
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRecordRepayment(amountInput.toDouble(), selectedBankId!!) }, modifier = Modifier.weight(1.3f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight), colors = ButtonDefaults.buttonColors(containerColor = if (isValid) theme.accent else Color.Transparent, disabledContainerColor = Color.Transparent), enabled = isValid) {
                            Text("Record Repayment", color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
