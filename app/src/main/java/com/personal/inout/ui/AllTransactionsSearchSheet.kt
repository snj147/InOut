package com.personal.inout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTransactionsSearchSheet(
    flowRecords: List<FlowRecord> = emptyList(),
    isPrivacyMode: Boolean = false,
    isProUser: Boolean = true,
    onDismiss: () -> Unit = {},
    onEditRecord: (FlowRecord) -> Unit = {},
    onExportCsv: () -> Unit = {},
    onExportPdfDossier: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filteredRecords = remember(flowRecords, searchQuery) {
        flowRecords.filter { record ->
            searchQuery.isBlank() ||
                    record.note.contains(searchQuery, ignoreCase = true) ||
                    record.category.contains(searchQuery, ignoreCase = true) ||
                    record.amount.toString().contains(searchQuery)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141211),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All Vault Records (${flowRecords.size})",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF888888))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by note, amount, category...", color = Color(0xFF666666), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFE59C5C)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1E1C1A),
                    unfocusedContainerColor = Color(0xFF1E1C1A),
                    focusedBorderColor = Color(0xFFE59C5C),
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    TransactionSearchItem(
                        record = record,
                        isPrivacyMode = isPrivacyMode,
                        onClick = { onEditRecord(record) }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionSearchItem(
    record: FlowRecord,
    isPrivacyMode: Boolean = false,
    onClick: () -> Unit
) {
    val dateStr = remember(record.timestamp) {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        sdf.format(Date(record.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C1A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(record.note.ifBlank { record.category }, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                Text(record.category, color = Color(0xFF888888), fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (isPrivacyMode) "₹ •••" else "₹${record.amount.toInt()}", color = Color(0xFFE59C5C), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(dateStr, color = Color(0xFF666666), fontSize = 10.5.sp)
            }
        }
    }
}
