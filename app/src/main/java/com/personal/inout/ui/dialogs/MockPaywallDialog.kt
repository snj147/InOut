package com.personal.inout.ui.dialogs

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
import com.personal.inout.ui.LocalThemeColors

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
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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
                        Text("Institutional Double-Entry Accounting", color = theme.textMuted, fontSize = 11.sp)
                    }
                }

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

            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                ProFeatureRow(
                    icon = Icons.Outlined.AccountBalance,
                    title = "Statutory Balance Sheet & Tax Reports",
                    desc = "Generate clean ICAI-compliant Balance Sheets, Profit & Loss statements, and Section 80C/80D deduction summaries with zero watermarks."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.ReceiptLong,
                    title = "TallyPrime XML & Accounting Exports",
                    desc = "Export native XML vouchers for direct import into TallyPrime, alongside multi-column journals for CAs and spreadsheets."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.DocumentScanner,
                    title = "Intelligent Receipt & Statement OCR",
                    desc = "On-device ML automatically reads retail receipts and complex financial statements with zero cloud data sharing."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Lock,
                    title = "Credit Card Phantom Lock",
                    desc = "Protects your real runway by automatically ring-fencing unbilled credit card liabilities from spendable bank cash."
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Shield,
                    title = "Encrypted Vault Cold Backups",
                    desc = "Hardware-backed AES-256 encrypted backups with cryptographic checksums to keep your financial ledger private and portable."
                )
            }

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
                        Icon(
                            imageVector = Icons.Default.Shop,
                            contentDescription = null,
                            tint = theme.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Google Play Billing Sandbox",
                            color = theme.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text("SANDBOX ENGINE", color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = theme.bg,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock Lifetime Pro (₹21)", color = theme.bg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
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
            Icon(imageVector = icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
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
