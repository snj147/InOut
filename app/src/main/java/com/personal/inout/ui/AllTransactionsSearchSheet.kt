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
import com.personal.inout.data.LedgerPocket
import com.personal.inout.data.LedgerTransaction
import com.personal.inout.data.MovementNature
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTransactionsSearchSheet(
    flowRecords: List<LedgerTransaction>,
    rawPockets: List<LedgerPocket>,
    isPrivacyMode: Boolean,
    isProUser: Boolean,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit,
    onEditRecord: (LedgerTransaction) -> Unit,
    onExportCsv: () -> Unit,
    onExportPdfDossier: () -> Unit
) {
    val theme = LocalThemeColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var filterTaxDeductibleOnly by remember { mutableStateOf(false) }

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ledger Audit Records (Rule 24)",
                    color = theme.textBright,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onExportCsv) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export Tally / CSV", tint = theme.accent)
                    }
                    IconButton(onClick = onExportPdfDossier) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export ICAI PDF", tint = theme.accent)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textMuted)
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search note, category, or amount", color = theme.textMuted, fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = theme.accent) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = theme.borderLight,
                    focusedTextColor = theme.textBright,
                    unfocusedTextColor = theme.textBright,
                    focusedContainerColor = theme.surfaceAlt,
                    unfocusedContainerColor = theme.surfaceAlt
                ),
                shape = RoundedCornerShape(10.dp)
            )

            // Category & Statutory Tax Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filterTaxDeductibleOnly,
                    onClick = { filterTaxDeductibleOnly = !filterTaxDeductibleOnly },
                    label = { Text("80C / Tax Deductible", fontSize = 11.sp) },
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
                    label = { Text("All Categories", fontSize = 11.sp) },
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
                        label = { Text(cat, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.accent,
                            selectedLabelColor = theme.bg,
                            containerColor = theme.surfaceAlt,
                            labelColor = theme.textMuted
                        )
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                if (filteredRecords.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No matching ledger transactions found.", color = theme.textMuted, fontSize = 13.sp)
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
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surfaceAlt),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, theme.borderLight, RoundedCornerShape(10.dp))
                                .clickable { onEditRecord(tx) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = tx.description.ifBlank { tx.category },
                                            color = theme.textBright,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (tx.isTaxDeductible) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(theme.accent.copy(alpha = 0.2f))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text("80C", color = theme.accent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(tx.timestamp))
                                    Text(text = "${tx.category} • $accountName • $dateStr", color = theme.textMuted, fontSize = 10.5.sp)
                                }
                                Text(
                                    text = if (isPrivacyMode) "₹ •••" else "${if (isOut) "-" else "+"}₹${String.format("%,.0f", tx.amount)}",
                                    color = if (isOut) theme.mildRed else theme.mildGreen,
                                    fontSize = 13.sp,
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
