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
import com.personal.inout.ui.CompactInputField
import com.personal.inout.ui.ThemeColors

@Composable
fun SweepGoalSavingsModal(
    goalPocket: LedgerPocket,
    currentBalance: Double,
    liquidPockets: List<LedgerPocket>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSweep: (isAdding: Boolean, amount: Double, bankId: Long) -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var selectedBankId by remember(liquidPockets) { mutableStateOf(liquidPockets.firstOrNull()?.id) }
    var isAdding by remember { mutableStateOf(true) }
    var showDropdown by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss, 
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() }, 
                    indication = null
                ) { onDismiss() }, 
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp), 
                colors = CardDefaults.cardColors(containerColor = theme.surface), 
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 400.dp)
                    .border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() }, 
                        indication = null
                    ) {}
            ) {
                Column(
                    modifier = Modifier.padding(18.dp), 
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🎯 SWEEP SAVINGS", 
                        color = theme.textBright, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 13.sp, 
                        letterSpacing = 1.sp
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Goal: ${goalPocket.name}", color = theme.textMuted, fontSize = 11.5.sp)
                        Text(
                            text = "Target: ₹ ${String.format("%,.0f", goalPocket.targetGoalAmount)} • Saved: ₹ ${String.format("%,.0f", currentBalance)}", 
                            color = theme.accent, 
                            fontSize = 11.5.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isAdding) theme.mildGreen.copy(alpha = 0.25f) else theme.surfaceAlt)
                                .border(1.dp, if (isAdding) theme.mildGreen else theme.borderLight, RoundedCornerShape(6.dp))
                                .clickable { isAdding = true }
                                .padding(vertical = 8.dp), 
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Add to Goal", 
                                color = if (isAdding) theme.mildGreen else theme.textBright, 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isAdding) theme.mildRed.copy(alpha = 0.25f) else theme.surfaceAlt)
                                .border(1.dp, if (!isAdding) theme.mildRed else theme.borderLight, RoundedCornerShape(6.dp))
                                .clickable { isAdding = false }
                                .padding(vertical = 8.dp), 
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Withdraw to Bank", 
                                color = if (!isAdding) theme.mildRed else theme.textBright, 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    CompactInputField(
                        value = amountInput, 
                        onValueChange = { input -> if (input.all { c -> c.isDigit() || c == '.' }) amountInput = input }, 
                        placeholder = "Sweep Amount ₹", 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), 
                        modifier = Modifier.fillMaxWidth()
                    )

                    val activeBank = liquidPockets.firstOrNull { it.id == selectedBankId }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                            .clickable { showDropdown = true }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(), 
                            horizontalArrangement = Arrangement.SpaceBetween, 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Source/Dest Bank: ${activeBank?.name ?: "Select Bank"}", 
                                color = theme.textBright, 
                                fontSize = 11.5.sp, 
                                fontWeight = FontWeight.Medium
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = theme.accent)
                        }
                        DropdownMenu(
                            expanded = showDropdown, 
                            onDismissRequest = { showDropdown = false }, 
                            modifier = Modifier.background(theme.surface)
                        ) {
                            liquidPockets.forEach { pocket -> 
                                DropdownMenuItem(
                                    text = { Text(pocket.name, color = theme.textBright) }, 
                                    onClick = { selectedBankId = pocket.id; showDropdown = false }
                                ) 
                            }
                        }
                    }

                    val isValid = (amountInput.toDoubleOrNull() ?: 0.0) > 0.0 && selectedBankId != null
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp), 
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSweep(isAdding, amountInput.toDouble(), selectedBankId!!) }, 
                            modifier = Modifier.weight(1.3f).height(44.dp), 
                            shape = RoundedCornerShape(8.dp), 
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight), 
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isValid) theme.accent else Color.Transparent, 
                                disabledContainerColor = Color.Transparent
                            ), 
                            enabled = isValid
                        ) {
                            Text(
                                text = "Sweep Funds", 
                                color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f), 
                                fontWeight = FontWeight.Bold, 
                                fontSize = 12.sp
                            )
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
