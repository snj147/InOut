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
    // Direct full pull-up without stopping halfway
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(theme.accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = theme.accent, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("InOut Pro", color = theme.textBright, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(theme.accent)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text("LIFETIME", color = theme.bg, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text("Sovereign, High-Velocity Financial Architecture", color = theme.textMuted, fontSize = 11.sp)
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

            // High-Value Pro Feature Showcase
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                ProFeatureRow(
                    icon = Icons.Outlined.DocumentScanner,
                    title = "Spatial Geometric Receipt Vision",
                    desc = "On-device Y-axis clustering auto-extracts merchant and totals instantly with zero cloud leakage."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Bolt,
                    title = "Zero-Command Predictive Terminal",
                    desc = "Natural language execution for transfers, multi-account splits, and recurring debts with real-time autocompletion."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Lock,
                    title = "Credit Card Phantom Lock",
                    desc = "Real-time liability ringfencing that stops unbilled credit dues from masking your actual liquid runway."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.CallSplit,
                    title = "Autonomous Multi-Account Liquidity Routing",
                    desc = "Cross-account auto-split engine prevents accidental overdrafts by seamlessly cascading charges across backup accounts."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Shield,
                    title = "Military-Grade AES-256 Cold Backups",
                    desc = "Export and restore encrypted .vault ledger archives directly on-device with zero cloud or third-party dependence."
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
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(theme.surfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = theme.textBright, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = theme.textMuted, fontSize = 10.5.sp, lineHeight = 14.sp)
        }
    }
}

// ---------------- DEVELOPER SANDBOX BAR ----------------

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
