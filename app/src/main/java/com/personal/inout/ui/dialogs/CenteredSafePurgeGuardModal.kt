package com.personal.inout.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.inout.data.LedgerPocket
import com.personal.inout.ui.ThemeColors
import kotlin.math.abs

@Composable
fun CenteredSafePurgeGuardModal(
    pocket: LedgerPocket,
    balance: Double,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val canDeactivate = balance == 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 380.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val titleText = if (pocket.name.startsWith("Recurring")) "TERMINATE MANDATE" 
                                    else if (pocket.name.startsWith("Transaction")) "DELETE ENTRY" 
                                    else "DEACTIVATE ACCOUNT"
                                    
                    Text(titleText, color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Are you sure you want to proceed with \"${pocket.name}\"?", color = theme.textMuted, fontSize = 12.sp)

                    if (!canDeactivate) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.mildRed.copy(alpha = 0.15f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Active balance is ₹${String.format("%,.0f", abs(balance))}. Settle balance to ₹0 first before deactivating.",
                                color = theme.mildRed,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Text("Action confirmed. Affected balances will recalculate automatically.", color = theme.mildGreen, fontSize = 11.5.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1.3f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed),
                            enabled = canDeactivate
                        ) {
                            Text(if (pocket.name.startsWith("Recurring")) "Terminate" else "Confirm & Delete", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)
                        ) {
                            Text("Cancel", color = theme.textMuted)
                        }
                    }
                }
            }
        }
    }
}
