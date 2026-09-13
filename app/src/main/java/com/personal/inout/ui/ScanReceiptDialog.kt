package com.personal.inout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.personal.inout.ocr.ParsedReceipt

data class EditableSplitItem(
    val description: String,
    val amountText: String,
    val category: String,
    val assignedTo: String = "Me"
)

@Composable
fun ScanReceiptDialog(
    parsedData: ParsedReceipt,
    onDismiss: () -> Unit,
    onConfirm: (total: Double, merchant: String, splits: List<EditableSplitItem>, tip: Double) -> Unit
) {
    var merchantName by remember { mutableStateOf(parsedData.merchant) }
    var totalText by remember { mutableStateOf(if (parsedData.total > 0) parsedData.total.toString() else "") }
    val splitItems = remember {
        mutableStateListOf<EditableSplitItem>().apply {
            if (parsedData.lineItems.isNotEmpty()) {
                addAll(
                    parsedData.lineItems.map { lineItem ->
                        EditableSplitItem(
                            description = lineItem.description,
                            amountText = lineItem.amount.toString(),
                            category = "Scanned Item",
                            assignedTo = "Me"
                        )
                    }
                )
            } else {
                add(EditableSplitItem(description = "Scanned Receipt", amountText = totalText, category = "General", assignedTo = "Me"))
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Receipt Breakdown & Split",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = merchantName,
                    onValueChange = { merchantName = it },
                    label = { Text("Store / Merchant Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it },
                    label = { Text("Receipt Total (₹)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Line Items (${splitItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = {
                        splitItems.add(EditableSplitItem(description = "", amountText = "0.0", category = "Split", assignedTo = "Me"))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Item")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(splitItems) { index, item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = item.description,
                                    onValueChange = { newDesc ->
                                        splitItems[index] = item.copy(description = newDesc)
                                    },
                                    label = { Text("Item") },
                                    modifier = Modifier.weight(1.5f)
                                )
                                OutlinedTextField(
                                    value = item.amountText,
                                    onValueChange = { newAmt ->
                                        splitItems[index] = item.copy(amountText = newAmt)
                                    },
                                    label = { Text("₹") },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { splitItems.removeAt(index) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val computedTotal = totalText.toDoubleOrNull()
                            ?: splitItems.sumOf { it.amountText.toDoubleOrNull() ?: 0.0 }
                        onConfirm(computedTotal, merchantName, splitItems, 0.0)
                    }) {
                        Text("Record & Split")
                    }
                }
            }
        }
    }
}
