package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onExportCsv: () -> Unit,
    onExportPdfDossier: () -> Unit
) {
    val theme = LocalThemeColors.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredRecords = remember(flowRecords, searchQuery) {
        if (searchQuery.isBlank()) flowRecords
        else {
            flowRecords.filter {
                it.note.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true) ||
                it.nature.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.textMuted.copy(alpha = 0.4f)) },
        modifier = Modifier.fillMaxHeight(0.92f) // Opens at full 92% immediately
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "All Vault Flows (${filteredRecords.size})",
                    color = theme.textBright,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onExportCsv) {
                        Icon(Icons.Default.FileDownload, contentDescription = "CSV Export", tint = theme.accent)
                    }
                    if (isProUser) {
                        IconButton(onClick = onExportPdfDossier) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF Dossier", tint = theme.accent)
                        }
                    }
                }
            }

            CompactInputField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search note, merchant, category...",
                modifier = Modifier.fillMaxWidth()
            )

            if (filteredRecords.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No matching records found", color = theme.textMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(filteredRecords, key = { it.id }) { flow ->
                        val isOut = flow.nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.CARD_PAYMENT)
                        val flowColor = if (isOut) theme.mildRed else theme.mildGreen
                        val dStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(flow.timestamp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.surfaceAlt)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isOut) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = flowColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        flow.note.ifBlank { flow.category },
                                        color = theme.textBright,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp
                                    )
                                    val flowLabel = when (flow.nature) {
                                        MovementNature.OUTFLOW -> "Spent"
                                        MovementNature.INFLOW -> "Received"
                                        MovementNature.CARD_PAYMENT -> "Card Bill Paid"
                                        MovementNature.TRANSFER -> "Transferred"
                                        else -> flow.nature.name
                                    }
                                    Text("$flowLabel • $dStr", color = theme.textMuted, fontSize = 10.5.sp)
                                }
                            }

                            val amtStr = if (isPrivacyMode) "₹ •••" else "${if (isOut) "-" else "+"}₹ ${String.format("%,.0f", flow.amount)}"
                            Text(amtStr, color = flowColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
