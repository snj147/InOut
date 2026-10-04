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
import com.personal.inout.data.PocketType
import com.personal.inout.ui.ThemeColors
import kotlin.math.abs

@Composable
fun CenteredAccountVoucherModal(
    pocket: LedgerPocket,
    balance: Double,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onQuickAction: () -> Unit,
    onViewHistory: () -> Unit,
    onEdit: () -> Unit,
    onArchiveDelete: () -> Unit
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pocket.name, color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            pocket.type.name.replace("_", " "),
                            color = theme.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val displayBal = if (pocket.type == PocketType.LIABILITY_LOAN || pocket.type == PocketType.CREDIT_CARD) abs(balance) else balance
                    Text(
                        text = "Current Book Balance: ₹ ${String.format("%,.2f", displayBal)}",
                        color = theme.textMuted,
                        fontSize = 11.sp
                    )

                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    val actionLabel = when (pocket.type) {
                        PocketType.CREDIT_CARD -> "💳 Card Payment"
                        PocketType.GOAL_POT -> "🎯 Sweep Savings (In / Out)"
                        PocketType.INVESTMENT, PocketType.FIXED_ASSET -> "📈 Revalue / Liquidate Holding"
                        PocketType.PEER_RECEIVABLE, PocketType.PEER_PAYABLE -> "🤝 Lend / Borrow or Transact"
                        PocketType.LIABILITY_LOAN -> "🏛 Log Monthly EMI Principal"
                        else -> "⚡ Quick Transact"
                    }

                    TactileActionButton(label = actionLabel, isPrimary = true, theme = theme, onClick = onQuickAction)
                    TactileActionButton(label = "🧾 View Transactions", isPrimary = false, theme = theme, onClick = onViewHistory)
                    TactileActionButton(label = "✎ Edit Name & Configuration", isPrimary = false, theme = theme, onClick = onEdit)
                    TactileActionButton(label = "✕ Deactivate / Archive Account", isPrimary = false, isDanger = true, theme = theme, onClick = onArchiveDelete)

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close", color = theme.textMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TactileActionButton(
    label: String,
    isPrimary: Boolean = false,
    isDanger: Boolean = false,
    theme: ThemeColors,
    onClick: () -> Unit
) {
    val bg = when {
        isPrimary -> theme.accent
        isDanger -> theme.mildRed.copy(alpha = 0.12f)
        else -> theme.surfaceAlt
    }
    val fg = when {
        isPrimary -> theme.bg
        isDanger -> theme.mildRed
        else -> theme.textBright
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, if (isDanger) theme.mildRed.copy(alpha = 0.3f) else theme.borderLight, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = "→", color = fg.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
