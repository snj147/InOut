package com.personal.inout.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
fun RevalueAssetPortfolioModal(
    assetPocket: LedgerPocket,
    currentBookValue: Double,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onUpdateValuation: (newValuation: Double, note: String) -> Unit
) {
    var valuationInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp).border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("📈 REVALUE ASSET PORTFOLIO", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Asset: ${assetPocket.name}", color = theme.textMuted, fontSize = 11.5.sp)
                        val balColor = if (currentBookValue == 0.0) theme.textBright else theme.accent
                        Text("Current Book Value: ₹ ${String.format("%,.0f", currentBookValue)}", color = balColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    CompactInputField(value = valuationInput, onValueChange = { input -> if (input.all { c -> c.isDigit() || c == '.' }) valuationInput = input }, placeholder = "New Market Valuation ₹", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    CompactInputField(value = note, onValueChange = { note = it }, placeholder = "Audit Note (e.g. Q3 Rally)", modifier = Modifier.fillMaxWidth())

                    val isValid = (valuationInput.toDoubleOrNull() ?: 0.0) > 0.0
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onUpdateValuation(valuationInput.toDouble(), note) }, modifier = Modifier.weight(1.3f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight), colors = ButtonDefaults.buttonColors(containerColor = if (isValid) theme.accent else Color.Transparent, disabledContainerColor = Color.Transparent), enabled = isValid) {
                            Text("Update Valuation", color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
