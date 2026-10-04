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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.inout.data.LedgerTransaction
import com.personal.inout.ui.ThemeColors

@Composable
fun CenteredRecurringVoucherModal(
    rule: LedgerTransaction,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
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
                    Text(rule.description.ifBlank { rule.category }, color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Cadence: ${rule.recurringFrequency} • Scheduled: ₹${rule.amount.toInt()}", color = theme.textMuted, fontSize = 11.sp)

                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    TactileActionButton(
                        label = "✎ Edit Mandate Amount & Dates",
                        isPrimary = true,
                        theme = theme,
                        onClick = onEdit
                    )

                    TactileActionButton(
                        label = "✕ Terminate Mandate",
                        isPrimary = false,
                        isDanger = true,
                        theme = theme,
                        onClick = onDelete
                    )

                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Close", color = theme.textMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
