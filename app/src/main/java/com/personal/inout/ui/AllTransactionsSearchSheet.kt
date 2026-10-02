package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.personal.inout.data.LedgerTransaction
import com.personal.inout.data.MovementNature
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AllTransactionsSearchSheet(
    flowRecords: List<LedgerTransaction>,
    rawPockets: List<LedgerPocket>,
    isPrivacyMode: Boolean,
    isProUser: Boolean,
    sheetState: SheetState? = null,
    onDismiss: () -> Unit,
    onEditRecord: (LedgerTransaction) -> Unit,
    onExportCsv: () -> Unit,
    onExportPdfDossier: () -> Unit
) {
    val theme = LocalThemeColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var filterTaxDeductibleOnly by remember { mutableStateOf(false) }

    var inspectingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }

    val categories = remember(flowRecords) {
        flowRecords.map { it.category.ifBlank { "General" } }.distinct()
    }

    val filteredRecords = remember(flowRecords, searchQuery, selectedCategoryFilter, filterTaxDeductibleOnly) {
        flowRecords.filter { tx ->
            val amtStr = tx.amount.toString()
            val matchQuery = searchQuery.isBlank() ||
                    tx.description.contains(searchQuery, ignoreCase = true) ||
                    tx.category.contains(searchQuery, ignoreCase = true) ||
                    amtStr.contains(searchQuery)
            val matchCat = selectedCategoryFilter == null || tx.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchTax = !filterTaxDeductibleOnly || tx.isTaxDeductible
            matchQuery && matchCat && matchTax
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.90f)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(16.dp))
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LEDGER MATRIX AUDIT",
                            color = theme.textBright,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onExportCsv) {
                                Icon(Icons.Default.FileDownload, contentDescription = "CSV", tint = theme.accent, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onExportPdfDossier) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = theme.accent, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search narration, category, amount", color = theme.textMuted, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.accent,
                            unfocusedBorderColor = theme.borderLight,
                            focusedTextColor = theme.textBright,
                            unfocusedTextColor = theme.textBright,
                            focusedContainerColor = theme.surfaceAlt,
                            unfocusedContainerColor = theme.surfaceAlt
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Dual-Rail live filters
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = filterTaxDeductibleOnly,
                            onClick = { filterTaxDeductibleOnly = !filterTaxDeductibleOnly },
                            label = { Text("80C Tagged", fontSize = 10.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.accent,
                                selectedLabelColor = theme.bg,
                                containerColor = theme.surfaceAlt,
                                labelColor = theme.textMuted
                            )
                        )

                        FilterChip(
                            selected = selectedCategoryFilter == null,
                            onClick = { selectedCategoryFilter = null },
                            label = { Text("All (${flowRecords.size})", fontSize = 10.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.accent,
                                selectedLabelColor = theme.bg,
                                containerColor = theme.surfaceAlt,
                                labelColor = theme.textMuted
                            )
                        )

                        categories.forEach { cat ->
                            val isSel = selectedCategoryFilter == cat
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedCategoryFilter = if (isSel) null else cat },
                                label = { Text(cat, fontSize = 10.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = theme.accent,
                                    selectedLabelColor = theme.bg,
                                    containerColor = theme.surfaceAlt,
                                    labelColor = theme.textMuted
                                )
                            )
                        }
                    }

                    // Continuous Ledger Stream
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (filteredRecords.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No matching ledger transactions found.", color = theme.textMuted, fontSize = 12.sp)
                                }
                            }
                        } else {
                            items(filteredRecords, key = { it.id }) { tx ->
                                val isOut = tx.movementNature in listOf(
                                    MovementNature.OPERATING_EXPENSE,
                                    MovementNature.TRANSFER,
                                    MovementNature.DEPRECIATION_WRITE,
                                    MovementNature.EMI_PRINCIPAL
                                )
                                val accountName = rawPockets.firstOrNull { it.id == tx.sourcePocketId }?.name ?: "Account"

                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = theme.surfaceAlt),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                                        .clickable { inspectingTransaction = tx }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = tx.description.ifBlank { tx.category },
                                                    color = theme.textBright,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (tx.isTaxDeductible) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(theme.accent.copy(alpha = 0.2f))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("80C", color = theme.accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(tx.timestamp))
                                            Text(text = "${tx.category} • $accountName • $dateStr", color = theme.textMuted, fontSize = 10.sp)
                                        }
                                        Text(
                                            text = if (isPrivacyMode) "₹ •••" else "${if (isOut) "-" else "+"}₹ ${String.format("%,.0f", tx.amount)}",
                                            color = if (isOut) theme.mildRed else theme.mildGreen,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Centered Audit Voucher Inspection Ticket
    inspectingTransaction?.let { tx ->
        CenteredAuditVoucherTicket(
            tx = tx,
            rawPockets = rawPockets,
            theme = theme,
            onDismiss = { inspectingTransaction = null },
            onEdit = {
                inspectingTransaction = null
                onEditRecord(tx)
            }
        )
    }
}

@Composable
private fun CenteredAuditVoucherTicket(
    tx: LedgerTransaction,
    rawPockets: List<LedgerPocket>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val srcPocket = rawPockets.firstOrNull { it.id == tx.sourcePocketId }?.name ?: "Source"
    val tgtPocket = tx.targetPocketId?.let { tgtId -> rawPockets.firstOrNull { it.id == tgtId }?.name }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 380.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable(enabled = false) {}
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
                        Text("AUDIT VOUCHER", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "#TX-${tx.id.toString().padStart(4, '0')}",
                            color = theme.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "₹ ${String.format("%,.2f", tx.amount)}",
                        color = theme.textBright,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )

                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VoucherKeyVal("Narration", tx.description.ifBlank { "None" }, theme)
                        VoucherKeyVal("Movement Nature", tx.movementNature.name, theme)
                        VoucherKeyVal("Category", tx.category, theme)
                        VoucherKeyVal("Debit Account", srcPocket, theme)
                        if (tgtPocket != null) {
                            VoucherKeyVal("Credit Account", tgtPocket, theme)
                        }
                        val dStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(tx.timestamp))
                        VoucherKeyVal("Booked Timestamp", dStr, theme)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Close", color = theme.textMuted)
                        }

                        Button(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                        ) {
                            Text("Edit Entry", color = theme.bg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoucherKeyVal(label: String, value: String, theme: ThemeColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = theme.textMuted, fontSize = 11.sp)
        Text(value, color = theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
