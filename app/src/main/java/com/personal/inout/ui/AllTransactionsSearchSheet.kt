package com.inout.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.inout.vault.data.FlowRecord
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTransactionsSearchSheet(
    records: List<FlowRecord>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onUpdateRecord: (FlowRecord) -> Unit,
    onDeleteRecord: (FlowRecord) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var editingRecord by remember { mutableStateOf<FlowRecord?>(null) }

    val filteredRecords = remember(records, searchQuery, selectedCategoryFilter) {
        records.filter { record ->
            val matchesCategory = if (selectedCategoryFilter == "All") true else record.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    record.note.contains(searchQuery, ignoreCase = true) ||
                    record.category.contains(searchQuery, ignoreCase = true) ||
                    record.amount.toString().contains(searchQuery)
            matchesCategory && matchesQuery
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
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All Vault Records (${records.size})",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF888888))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by note, amount, category...", color = Color(0xFF666666)) },
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

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    TransactionSearchItem(record = record, onClick = { editingRecord = record })
                }
            }
        }
    }

    editingRecord?.let { record ->
        EditTransactionDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onUpdate = { updated ->
                onUpdateRecord(updated)
                editingRecord = null
            },
            onDelete = { toDelete ->
                onDeleteRecord(toDelete)
                editingRecord = null
            }
        )
    }
}

@Composable
fun TransactionSearchItem(record: FlowRecord, onClick: () -> Unit) {
    val dateStr = remember(record.timestamp) {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        sdf.format(Date(record.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C1A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(record.note.ifBlank { record.category }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(record.category, color = Color(0xFF888888), fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${record.amount}", color = Color(0xFFE59C5C), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(dateStr, color = Color(0xFF666666), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun EditTransactionDialog(
    record: FlowRecord,
    onDismiss: () -> Unit,
    onUpdate: (FlowRecord) -> Unit,
    onDelete: (FlowRecord) -> Unit
) {
    var note by remember { mutableStateOf(record.note) }
    var selectedCategory by remember { mutableStateOf(record.category) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val categories = remember {
        listOf(
            "Food & Dining", "Groceries", "Transport",
            "Shopping", "Bills", "Health",
            "Leisure", "Salary", "General", "Peer Transfer"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1C1A),
        shape = RoundedCornerShape(20.dp),
        title = { Text("Edit Entry", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Amount: ₹${record.amount}", color = Color(0xFF9E9E9E), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF2B2826),
                        unfocusedContainerColor = Color(0xFF2B2826),
                        focusedBorderColor = Color(0xFFE59C5C),
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Category", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                // Fixed 3-column uniform grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = cat.equals(selectedCategory, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .background(
                                    color = if (isSelected) Color(0xFFE59C5C) else Color(0xFF2B2826),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedCategory = cat },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.sp,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF1C1917) else Color(0xFFCCCCCC)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpdate(record.copy(note = note, category = selectedCategory)) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE59C5C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Update", color = Color(0xFF1C1917), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { showDeleteConfirmation = true }) {
                    Text("Delete", color = Color(0xFFE57373))
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF9E9E9E))
                }
            }
        }
    )

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            containerColor = Color(0xFF262320),
            title = { Text("Confirm Deletion", color = Color.White) },
            text = {
                Text(
                    "Are you sure you want to delete this record? This will adjust your ledger balances accordingly.",
                    color = Color(0xFFCCCCCC)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete(record)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel", color = Color(0xFF9E9E9E))
                }
            }
        )
    }
}
