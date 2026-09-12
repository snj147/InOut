package com.personal.inout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.inout.ocr.ParsedReceipt

data class SplitItem(var category: String, var amountText: String)

@Composable
fun ScanReceiptDialog(
    parsedData: ParsedReceipt,
    onDismiss: () -> Unit,
    onConfirm: (total: Double, note: String, splits: List<SplitItem>, keepPhoto: Boolean) -> Unit
) {
    var totalText by remember { mutableStateOf(parsedData.totalAmount?.toString() ?: "") }
    var merchantText by remember { mutableStateOf(parsedData.merchantName ?: "") }
    var isSplitMode by remember { mutableStateOf(false) }
    var keepPhoto by remember { mutableStateOf(false) }

    // Multi-split lines
    val splits = remember {
        mutableStateListOf(
            SplitItem("Groceries", parsedData.totalAmount?.toString() ?: "")
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Processed Receipt") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = merchantText,
                        onValueChange = { merchantText = it },
                        label = { Text("Merchant / Store") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = totalText,
                        onValueChange = { totalText = it },
                        label = { Text("Total Bill (₹)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = keepPhoto, onCheckedChange = { keepPhoto = it })
                        Spacer(Modifier.width(4.dp))
                        Text("Archive compressed receipt image", style = MaterialTheme.typography.bodySmall)
                    }
                }

                item {
                    Divider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Split across categories?", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isSplitMode, onCheckedChange = { isSplitMode = it })
                    }
                }

                if (isSplitMode) {
                    itemsIndexed(splits) { index, split ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = split.category,
                                onValueChange = { splits[index] = split.copy(category = it) },
                                label = { Text("Category") },
                                modifier = Modifier.weight(1.2f)
                            )
                            OutlinedTextField(
                                value = split.amountText,
                                onValueChange = { splits[index] = split.copy(amountText = it) },
                                label = { Text("₹") },
                                modifier = Modifier.weight(0.8f)
                            )
                            if (splits.size > 1) {
                                IconButton(onClick = { splits.removeAt(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                                }
                            }
                        }
                    }

                    item {
                        TextButton(
                            onClick = { splits.add(SplitItem("General", "")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add Split Category")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTotal = totalText.toDoubleOrNull() ?: 0.0
                    onConfirm(finalTotal, merchantText, splits, keepPhoto)
                }
            ) {
                Text("Post to Ledger")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Discard") }
        }
    )
}
