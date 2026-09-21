package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockPaywallBottomSheet(
    currentProState: Boolean,
    onDismiss: () -> Unit,
    onSimulatePurchaseSuccess: () -> Unit,
    onSimulateRevokePro: () -> Unit
) {
    val theme = LocalThemeColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.textMuted.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with Pro Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(theme.accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = theme.accent, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("InOut Pro", color = theme.textBright, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(theme.accent)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("LIFETIME", color = theme.bg, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text("The High-Velocity Financial Operating System", color = theme.textMuted, fontSize = 11.sp)
                    }
                }

                // Price Tag Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.mildGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("₹21 only", color = theme.mildGreen, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }

            HorizontalDivider(color = theme.surfaceAlt, thickness = 0.8.dp)

            // Current Core Features Checklist
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                ProFeatureRow(
                    icon = Icons.Outlined.Bolt,
                    title = "Unlimited Shorthand Macros",
                    desc = "Bind frequent commands to custom 1-word aliases (e.g. alias chai = spent 20 chai)"
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Savings,
                    title = "Overdue Goal Allocation Guards",
                    desc = "Dynamic date-bound pacing with missed installment debt tracking"
                )
                ProFeatureRow(
                    icon = Icons.Outlined.DocumentScanner,
                    title = "On-Device Vision OCR & PDF Dossier",
                    desc = "Instant camera receipt parsing + complete branded balance sheets for tax filing"
                )
                ProFeatureRow(
                    icon = Icons.Outlined.SyncAlt,
                    title = "Triangular Peer Debt Matrix",
                    desc = "Execute zero-cash triangular settlements (settle friend A with friend B directly)"
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Shield,
                    title = "Temptation Delay Quarantine & Auditor",
                    desc = "Cool-off impulse capital tracking + automated subscription price creep alerts"
                )
            }

            // Google Play Simulation Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surfaceAlt)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Shop, contentDescription = null, tint = theme.textMuted, modifier = Modifier.size(16.dp))
                        Text("Google Play Billing Sandbox", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("MOCK ENGINE", color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Action Buttons
            if (!currentProState) {
                Button(
                    onClick = {
                        onSimulatePurchaseSuccess()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = theme.bg, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Simulate ₹21 Purchase (Unlock Pro)", color = theme.bg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        onSimulateRevokePro()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.mildRed)
                ) {
                    Text("Revoke Pro (Test Free Tier Experience)", color = theme.mildRed, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ProFeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    val theme = LocalThemeColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(theme.surfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(title, color = theme.textBright, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = theme.textMuted, fontSize = 10.5.sp)
        }
    }
}

// ---------------- DEVELOPER SANDBOX BAR (Preserved for compatibility) ----------------

@Composable
fun DevSandboxTogglePill(
    isProUnlocked: Boolean,
    onOpenPaywall: () -> Unit
) {
    val theme = LocalThemeColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.surfaceAlt)
            .clickable { onOpenPaywall() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isProUnlocked) theme.mildGreen else theme.accent)
                )
                Text(
                    text = if (isProUnlocked) "TEST MODE: Pro Active (Click to switch)" else "TEST MODE: Free Tier (Click to upgrade)",
                    color = theme.textBright,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text("MOCK SHEET →", color = theme.accent, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
