package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
                            .background(theme.accent.copy(alpha = 0.18f)),
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
                        Text("Institutional Sovereign Ledger & Reporting", color = theme.textMuted, fontSize = 11.sp)
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

            // Features aligned with the 37-rule specs
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                ProFeatureRow(
                    icon = Icons.Outlined.AccountBalance,
                    title = "Statutory Balance Sheet & ITR Schedules (Rule 28)",
                    desc = "Generates ICAI T-format Balance Sheets, Schedule AL, and Section 80C/80D tax deduction dossiers without watermarks."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.ReceiptLong,
                    title = "TallyPrime XML & Indian Multi-Column Journals (Rule 34)",
                    desc = "Direct XML voucher imports for TallyPrime and multi-column debit/credit journals for CA audits and spreadsheets."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.DocumentScanner,
                    title = "Document Intent Gate OCR (Rule 29)",
                    desc = "On-device ML classifier identifying whether a document is a retail receipt or a multi-line statutory statement."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Lock,
                    title = "Credit Card Phantom Lock (Rules 3 & 4)",
                    desc = "Automatic 1:1 phantom ring-fencing separating unbilled card liabilities from your spendable liquid runway."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Shield,
                    title = "Cryptographic Archive Engine (Rules 24 & 34.4)",
                    desc = "Hardware-backed AES-256-GCM encryption with SHA-256 integrity verification for local ledger backups."
                )
            }

            // Google Play Sandbox Indicator
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.borderLight, RoundedCornerShape(12.dp)),
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
                    Text("SANDBOX ENGINE", color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.mildRed.copy(alpha = 0.5f)),
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
                .background(theme.surfaceAlt)
                .border(1.dp, theme.borderLight, CircleShape),
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
            .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
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
