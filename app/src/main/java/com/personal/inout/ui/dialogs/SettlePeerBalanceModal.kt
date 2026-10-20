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
import kotlin.math.abs

@Composable
fun SettlePeerBalanceModal(
    peerPocket: LedgerPocket,
    currentBalance: Long, // STRICTLY PAISE
    liquidPockets: List<LedgerPocket>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSettle: (isOutgoingFromBank: Boolean, amount: Double, bankId: Long) -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var selectedBankId by remember(liquidPockets) { mutableStateOf(liquidPockets.firstOrNull()?.id) }
    var showDropdown by remember { mutableStateOf(false) }

    val isDebtor = currentBalance >= 0L
    val displayBalDouble = abs(currentBalance) / 100.0

    // true = Money leaves bank, goes to peer (Lend / Repay)
    // false = Money leaves peer, enters bank (Collect / Borrow)
    var isOutgoingFromBank by remember { mutableStateOf(isDebtor) } 

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp).border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🤝 TRANSACT WITH PEER", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Peer: ${peerPocket.name}", color = theme.textMuted, fontSize = 11.5.sp)
                        val statText = if (currentBalance > 0L) "They Owe You ₹ ${String.format("%,.0f", displayBalDouble)}" else if (currentBalance < 0L) "You Owe Them ₹ ${String.format("%,.0f", displayBalDouble)}" else "Settled (₹0)"
                        val statColor = if (currentBalance > 0L) theme.mildGreen else if (currentBalance < 0L) theme.mildRed else theme.textBright
                        Text("Current Status: $statText", color = statColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    // DYNAMIC UX OPTIONS BASED ON DEBT STATE
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val activeBg = theme.accent.copy(alpha = 0.2f)
                        val inactiveBg = theme.surfaceAlt

                        if (isDebtor) {
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isOutgoingFromBank) activeBg else inactiveBg).border(1.dp, if (isOutgoingFromBank) theme.accent else theme.borderLight, RoundedCornerShape(6.dp)).clickable { isOutgoingFromBank = true }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text("Lend More", color = if (isOutgoingFromBank) theme.accent else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (!isOutgoingFromBank) activeBg else inactiveBg).border(1.dp, if (!isOutgoingFromBank) theme.accent else theme.borderLight, RoundedCornerShape(6.dp)).clickable { isOutgoingFromBank = false }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text("Collect Funds", color = if (!isOutgoingFromBank) theme.accent else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isOutgoingFromBank) activeBg else inactiveBg).border(1.dp, if (isOutgoingFromBank) theme.accent else theme.borderLight, RoundedCornerShape(6.dp)).clickable { isOutgoingFromBank = true }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text("Repay Debt", color = if (isOutgoingFromBank) theme.accent else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (!isOutgoingFromBank) activeBg else inactiveBg).border(1.dp, if (!isOutgoingFromBank) theme.accent else theme.borderLight, RoundedCornerShape(6.dp)).clickable { isOutgoingFromBank = false }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text("Borrow More", color = if (!isOutgoingFromBank) theme.accent else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    CompactInputField(value = amountInput, onValueChange = { input -> if (input.all { c -> c.isDigit() || c == '.' }) amountInput = input }, placeholder = "Settlement Amount ₹", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

                    val activeBank = liquidPockets.firstOrNull { it.id == selectedBankId }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).clickable { showDropdown = true }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Bank Account: ${activeBank?.name ?: "Select Bank"}", color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = theme.accent)
                        }
                        DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }, modifier = Modifier.background(theme.surface)) {
                            liquidPockets.forEach { pocket -> DropdownMenuItem(text = { Text(pocket.name, color = theme.textBright) }, onClick = { selectedBankId = pocket.id; showDropdown = false }) }
                        }
                    }

                    val isValid = (amountInput.toDoubleOrNull() ?: 0.0) > 0.0 && selectedBankId != null
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSettle(isOutgoingFromBank, amountInput.toDouble(), selectedBankId!!) }, modifier = Modifier.weight(1.3f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight), colors = ButtonDefaults.buttonColors(containerColor = if (isValid) theme.accent else Color.Transparent, disabledContainerColor = Color.Transparent), enabled = isValid) {
                            Text("Log Transaction", color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
