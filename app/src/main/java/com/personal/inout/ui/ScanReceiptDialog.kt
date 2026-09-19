package com.personal.inout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.ocr.ParsedReceipt

@Composable
fun ScanReceiptDialog(
    parsedData: ParsedReceipt,
    onDismiss: () -> Unit,
    onConfirm: (merchant: String, total: Double) -> Unit
) {
    val theme = LocalThemeColors.current

    val initialTotal = (parsedData.total ?: 0.0).let { if (it > 0.0) String.format("%.2f", it) else "" }
    var merchant by remember { mutableStateOf(parsedData.merchant) }
    var totalText by remember { mutableStateOf(initialTotal) }

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = {
            Text("Receipt Scanned", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactInputField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    placeholder = "Merchant / Note"
                )
                CompactInputField(
                    value = totalText,
                    onValueChange = { totalText = it },
                    placeholder = "Total Amount"
                )
                if (parsedData.lineItems.isNotEmpty()) {
                    Text(
                        "Extracted ${parsedData.lineItems.size} line preview(s)",
                        color = theme.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = totalText.toDoubleOrNull() ?: 0.0
                    onConfirm(merchant.ifBlank { "Receipt" }, amount)
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Fill Transaction", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textMuted)
            }
        }
    )
}
