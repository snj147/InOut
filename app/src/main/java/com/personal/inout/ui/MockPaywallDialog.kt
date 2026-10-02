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
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.textMuted.copy(alpha = 0.35f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
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
                            .background(theme.accentDim),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("InOut Pro", color = theme.textBright, fontSize = 16.5.sp, fontWeight = FontWeight.Black)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(theme.accent)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text("LIFETIME", color = theme.bg, fontSize = 8.5.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Text("Sovereign Ledger & Statutory Reporting", color = theme.textMuted, fontSize = 10.5.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.mildGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("₹21 only", color = theme.mildGreen, fontWeight = FontWeight.Black, fontSize = 12.5.sp)
                }
            }

            HorizontalDivider(color = theme.borderLight, thickness = 0.6.dp)

            // Live Feature Showcase matching 37-rule specs
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                ProFeatureRow(
                    icon = Icons.Outlined.AccountBalance,
                    title = "Statutory Balance Sheet & ITR Schedules (Rule 28)",
                    desc = "Generates ICAI T-format Balance Sheets, Schedule AL, and 80C/80D tax deduction dossiers without watermarks."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.ReceiptLong,
                    title = "TallyPrime XML & Indian Multi-Column CSV (Rule 34)",
                    desc = "Direct voucher XML import for TallyPrime and multi-column debit/credit journals for CA audits and Excel."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.DocumentScanner,
                    title = "Document Intent Gate OCR (Rule 29)",
                    desc = "On-device document classifier distinguishing retail receipts from multi-line balance sheet schedules."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Lock,
                    title = "Credit Card Phantom Lock (Rules 3 & 4)",
                    desc = "Automatic 1:1 phantom ring-fencing separating unbilled credit card liabilities from spendable cash."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Shield,
                    title = "Cryptographic Archive Engine (Rules 24 & 34.4)",
                    desc = "Hardware-backed AES-256-GCM encryption with SHA-256 integrity verification for local ledger backups."
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.borderLight, RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surfaceAlt)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shop,
                            contentDescription = null,
                            tint = theme.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Google Play Billing Sandbox",
                            color = theme.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text("SANDBOX MODE", color = theme.accent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!currentProState) {
                Button(
                    onClick = {
                        onSimulatePurchaseSuccess()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = theme.bg,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock Lifetime Pro (₹21)", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        onSimulateRevokePro()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.mildRed.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.mildRed)
                ) {
                    Text("Revoke Pro (Test Free Sandbox Experience)", color = theme.mildRed, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ProFeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    val theme = LocalThemeColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(theme.surfaceAlt)
                .border(1.dp, theme.borderLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = theme.textMuted, fontSize = 10.sp, lineHeight = 13.5.sp)
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
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
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isProUnlocked) theme.mildGreen else theme.accent)
                )
                Text(
                    text = if (isProUnlocked) "SANDBOX: Pro Active (Tap to toggle)" else "SANDBOX: Free Tier (Tap to test Pro)",
                    color = theme.textBright,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text("MANAGE →", color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
