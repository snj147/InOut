package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.FlowRecord
import com.personal.inout.data.MovementNature
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTransactionsSearchSheet(
    flowRecords: List<FlowRecord>,
    isPrivacyMode: Boolean,
    isProUser: Boolean,
    onDismiss: () -> Unit,
    onEditRecord: (FlowRecord) -> Unit,
    onExportCsv: () -> Unit,
    onExportPdfDossier: () -> Unit
) {
    val theme = LocalThemeColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    val categories = remember(flowRecords) {
        flowRecords.map { it.category.ifBlank { "General" } }.distinct()
    }

    val filteredRecords = remember(flowRecords, searchQuery, selectedCategoryFilter) {
        flowRecords.filter { f ->
            val amtStr = (f.amount ?: 0.0).toString()
            val matchQuery = searchQuery.isBlank() ||
                    f.note.contains(searchQuery, ignoreCase = true) ||
                    f.category.contains(searchQuery, ignoreCase = true) ||
                    amtStr.contains(searchQuery)
            val matchCat = selectedCategoryFilter == null || f.category.equals(selectedCategoryFilter, ignoreCase = true)
            matchQuery && matchCat
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                    text = "Ledger Audit Records",
                    color = theme.textBright,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onExportCsv) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = theme.accent)
                    }
                    IconButton(onClick = onExportPdfDossier) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = theme.accent)
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
                    unfocusedBorderColor = theme.surfaceAlt,
                    focusedTextColor = theme.textBright,
                    unfocusedTextColor = theme.textBright
                ),
                shape = RoundedCornerShape(10.dp)
            )

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
                            Text("No matching transaction records found.", color = theme.textMuted, fontSize = 13.sp)
                        }
                    }
                } else {
                    items(filteredRecords, key = { it.id }) { f ->
                        val isOut = f.nature in listOf(
                            MovementNature.OUTFLOW,
                            MovementNature.PEER_LEND,
                            MovementNature.PEER_REPAY,
                            MovementNature.CARD_PAYMENT
                        )
                        val amtVal = f.amount ?: 0.0

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surfaceAlt),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditRecord(f) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = f.note.ifBlank { f.category },
                                        color = theme.textBright,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(f.timestamp))
                                    Text(text = "${f.category} • $dateStr", color = theme.textMuted, fontSize = 10.5.sp)
                                }
                                Text(
                                    text = if (isPrivacyMode) "₹ •••" else "${if (isOut) "-" else "+"}₹${String.format("%,.0f", amtVal)}",
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
