package com.personal.inout.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.inout.data.LedgerPocket
import com.personal.inout.data.PocketType
import com.personal.inout.data.StagedStatementLineItem
import com.personal.inout.ocr.DocumentIntent
import com.personal.inout.ocr.ReceiptScanner
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateAccountDialog(
    initialType: PocketType = PocketType.LIQUID,
    allLiquidPockets: List<LedgerPocket> = emptyList(),
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        type: PocketType,
        limit: Double,
        dueDay: Int,
        targetAmt: Double,
        targetDateEpoch: Long,
        initialValuation: Double,
        interestRate: Double
    ) -> Unit,
    onSavePeerWithFunding: (
        contactName: String,
        isLend: Boolean,
        amount: Double,
        fundingPocketId: Long?
    ) -> Unit = { _, _, _, _ -> },
    onBatchCommit: (List<StagedStatementLineItem>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(initialType) }
    var limit by remember { mutableStateOf("") }
    var statementCycleDay by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }
    var targetAmt by remember { mutableStateOf("") }
    var initialValuation by remember { mutableStateOf("") }
    var interestRate by remember { mutableStateOf("") }
    var targetDateEpoch by remember { mutableStateOf(System.currentTimeMillis() + (30L * 24 * 3600 * 1000L)) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Peer Specific State: Lend vs Borrow toggle & linked funding account
    var peerIsLendMode by remember { mutableStateOf(true) }
    var peerFundingPocketId by remember(allLiquidPockets) {
        mutableStateOf(allLiquidPockets.firstOrNull()?.id)
    }
    var showFundingDropdown by remember { mutableStateOf(false) }

    var isProcessingDocument by remember { mutableStateOf(false) }
    var stagedBatchItems by remember { mutableStateOf<List<StagedStatementLineItem>?>(null) }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessingDocument = true
                val parsed = ReceiptScanner.processDocumentUri(context, uri)
                isProcessingDocument = false
                if (parsed.documentIntent == DocumentIntent.FINANCIAL_BALANCE_SHEET_STATEMENT && parsed.stagedLineItems.isNotEmpty()) {
                    stagedBatchItems = parsed.stagedLineItems
                } else if (parsed.total != null && parsed.total > 0.0) {
                    name = parsed.merchant
                    initialValuation = String.format("%.0f", parsed.total)
                }
            }
        }
    }

    val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(targetDateEpoch))

    if (showDatePicker) {
        CustomCalendarDialog(
            initialDateMillis = targetDateEpoch,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                targetDateEpoch = it
                showDatePicker = false
            }
        )
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
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 420.dp)
                    .border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable(enabled = false) {}
            ) {
                if (stagedBatchItems != null) {
                    BatchBalanceSheetStagingView(
                        stagedItems = stagedBatchItems!!,
                        theme = theme,
                        onDismissStaging = { stagedBatchItems = null },
                        onConfirmBatch = { selectedList ->
                            onBatchCommit(selectedList)
                            onDismiss()
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NEW ACCOUNT SETUP",
                                color = theme.textBright,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )

                            OutlinedButton(
                                onClick = { documentPickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.accent),
                                enabled = !isProcessingDocument
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = theme.accent, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isProcessingDocument) "Scanning..." else "Import PDF / Sheet",
                                    color = theme.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Symmetrical Classification Grid (Balanced 4 on Row 1, 3 on Row 2)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    Triple(PocketType.LIQUID, "Bank", Color(0xFF48BB78)),
                                    Triple(PocketType.PREPAID_WALLET, "Wallet", Color(0xFF38A169)),
                                    Triple(PocketType.CREDIT_CARD, "Card", Color(0xFFF56565)),
                                    Triple(PocketType.GOAL_POT, "Goal", Color(0xFFECC94B))
                                ).forEach { (t, lbl, c) ->
                                    val isSel = type == t
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSel) c.copy(alpha = 0.25f) else theme.surfaceAlt)
                                            .border(1.dp, if (isSel) c else theme.borderLight, RoundedCornerShape(6.dp))
                                            .clickable { type = t }
                                            .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(lbl, color = if (isSel) c else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    Triple(PocketType.INVESTMENT, "Invest", Color(0xFFED8936)),
                                    Triple(PocketType.PEER_RECEIVABLE, "Peer", Color(0xFF4FD1C5)),
                                    Triple(PocketType.LIABILITY_LOAN, "Loan", Color(0xFFE53E3E))
                                ).forEach { (t, lbl, c) ->
                                    val isSel = type == t
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSel) c.copy(alpha = 0.25f) else theme.surfaceAlt)
                                            .border(1.dp, if (isSel) c else theme.borderLight, RoundedCornerShape(6.dp))
                                            .clickable { type = t }
                                            .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(lbl, color = if (isSel) c else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Account Name Field (Slick 44dp height)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = {
                                Text(
                                    if (type == PocketType.PEER_RECEIVABLE || type == PocketType.PEER_PAYABLE) "Contact Name (e.g. Rahul Sharma)"
                                    else "Account Name (e.g. HDFC Salary, SBI Home Loan)",
                                    fontSize = 12.sp,
                                    color = theme.textMuted
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = theme.surfaceAlt,
                                unfocusedContainerColor = theme.surfaceAlt,
                                focusedBorderColor = theme.accent,
                                unfocusedBorderColor = theme.borderLight,
                                focusedTextColor = theme.textBright,
                                unfocusedTextColor = theme.textBright
                            )
                        )

                        when (type) {
                            PocketType.LIQUID, PocketType.PREPAID_WALLET -> {
                                OutlinedTextField(
                                    value = initialValuation,
                                    onValueChange = { input ->
                                        if (input.all { c -> c.isDigit() || c == '.' }) initialValuation = input
                                    },
                                    placeholder = { Text("Opening Balance ₹ (Optional)", fontSize = 12.sp, color = theme.textMuted) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = theme.surfaceAlt,
                                        unfocusedContainerColor = theme.surfaceAlt,
                                        focusedBorderColor = theme.accent,
                                        unfocusedBorderColor = theme.borderLight,
                                        focusedTextColor = theme.textBright,
                                        unfocusedTextColor = theme.textBright
                                    )
                                )
                            }

                            PocketType.CREDIT_CARD -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = limit,
                                        onValueChange = { input ->
                                            if (input.all { c -> c.isDigit() || c == '.' }) limit = input
                                        },
                                        placeholder = { Text("Credit Limit ₹", fontSize = 12.sp, color = theme.textMuted) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = theme.surfaceAlt,
                                            unfocusedContainerColor = theme.surfaceAlt,
                                            focusedBorderColor = theme.accent,
                                            unfocusedBorderColor = theme.borderLight,
                                            focusedTextColor = theme.textBright,
                                            unfocusedTextColor = theme.textBright
                                        )
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = statementCycleDay,
                                            onValueChange = { input ->
                                                if (input.all { c -> c.isDigit() } && (input.toIntOrNull() ?: 0) <= 31) statementCycleDay = input
                                            },
                                            placeholder = { Text("Bill Date (1-31)", fontSize = 12.sp, color = theme.textMuted) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = theme.surfaceAlt,
                                                unfocusedContainerColor = theme.surfaceAlt,
                                                focusedBorderColor = theme.accent,
                                                unfocusedBorderColor = theme.borderLight,
                                                focusedTextColor = theme.textBright,
                                                unfocusedTextColor = theme.textBright
                                            )
                                        )
                                        OutlinedTextField(
                                            value = dueDay,
                                            onValueChange = { input ->
                                                if (input.all { c -> c.isDigit() } && (input.toIntOrNull() ?: 0) <= 31) dueDay = input
                                            },
                                            placeholder = { Text("Due Date (1-31)", fontSize = 12.sp, color = theme.textMuted) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = theme.surfaceAlt,
                                                unfocusedContainerColor = theme.surfaceAlt,
                                                focusedBorderColor = theme.accent,
                                                unfocusedBorderColor = theme.borderLight,
                                                focusedTextColor = theme.textBright,
                                                unfocusedTextColor = theme.textBright
                                            )
                                        )
                                    }
                                }
                            }

                            PocketType.GOAL_POT -> {
                                OutlinedTextField(
                                    value = targetAmt,
                                    onValueChange = { input ->
                                        if (input.all { c -> c.isDigit() || c == '.' }) targetAmt = input
                                    },
                                    placeholder = { Text("Target Goal Amount ₹", fontSize = 12.sp, color = theme.textMuted) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = theme.surfaceAlt,
                                        unfocusedContainerColor = theme.surfaceAlt,
                                        focusedBorderColor = theme.accent,
                                        unfocusedBorderColor = theme.borderLight,
                                        focusedTextColor = theme.textBright,
                                        unfocusedTextColor = theme.textBright
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.surfaceAlt)
                                        .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                                        .clickable { showDatePicker = true }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "Target Date: $dateFormatted", color = theme.textBright, fontSize = 12.sp)
                                        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            PocketType.INVESTMENT, PocketType.FIXED_ASSET -> {
                                OutlinedTextField(
                                    value = initialValuation,
                                    onValueChange = { input ->
                                        if (input.all { c -> c.isDigit() || c == '.' }) initialValuation = input
                                    },
                                    placeholder = { Text("Current Valuation ₹", fontSize = 12.sp, color = theme.textMuted) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = theme.surfaceAlt,
                                        unfocusedContainerColor = theme.surfaceAlt,
                                        focusedBorderColor = theme.accent,
                                        unfocusedBorderColor = theme.borderLight,
                                        focusedTextColor = theme.textBright,
                                        unfocusedTextColor = theme.textBright
                                    )
                                )
                            }

                            // Dynamic Peer Lending / Borrowing Workflow
                            PocketType.PEER_RECEIVABLE, PocketType.PEER_PAYABLE -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (peerIsLendMode) theme.mildGreen.copy(alpha = 0.25f) else theme.surfaceAlt)
                                            .border(1.dp, if (peerIsLendMode) theme.mildGreen else theme.borderLight, RoundedCornerShape(6.dp))
                                            .clickable { peerIsLendMode = true }
                                            .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "I Lent (They Owe)",
                                                color = if (peerIsLendMode) theme.mildGreen else theme.textBright,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (!peerIsLendMode) theme.mildRed.copy(alpha = 0.25f) else theme.surfaceAlt)
                                            .border(1.dp, if (!peerIsLendMode) theme.mildRed else theme.borderLight, RoundedCornerShape(6.dp))
                                            .clickable { peerIsLendMode = false }
                                            .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "I Borrowed (I Owe)",
                                                color = if (!peerIsLendMode) theme.mildRed else theme.textBright,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = initialValuation,
                                        onValueChange = { input ->
                                            if (input.all { c -> c.isDigit() || c == '.' }) initialValuation = input
                                        },
                                        placeholder = { Text("Amount ₹", fontSize = 12.sp, color = theme.textMuted) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = theme.surfaceAlt,
                                            unfocusedContainerColor = theme.surfaceAlt,
                                            focusedBorderColor = theme.accent,
                                            unfocusedBorderColor = theme.borderLight,
                                            focusedTextColor = theme.textBright,
                                            unfocusedTextColor = theme.textBright
                                        )
                                    )

                                    val activeFundingPocket = allLiquidPockets.firstOrNull { it.id == peerFundingPocketId }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.surfaceAlt)
                                            .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                                            .clickable { showFundingDropdown = true }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (peerIsLendMode) "Debited From: ${activeFundingPocket?.name ?: "No Bank Account"}"
                                                else "Credited To: ${activeFundingPocket?.name ?: "No Bank Account"}",
                                                color = theme.textBright,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = theme.accent)
                                        }

                                        DropdownMenu(
                                            expanded = showFundingDropdown,
                                            onDismissRequest = { showFundingDropdown = false },
                                            modifier = Modifier.background(theme.surface)
                                        ) {
                                            allLiquidPockets.forEach { pocket ->
                                                DropdownMenuItem(
                                                    text = { Text(pocket.name, color = theme.textBright) },
                                                    onClick = {
                                                        peerFundingPocketId = pocket.id
                                                        showFundingDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Strict Symmetrical 2x2 Numeric Grid for Loans
                            PocketType.LIABILITY_LOAN -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = initialValuation,
                                            onValueChange = { input ->
                                                if (input.all { c -> c.isDigit() || c == '.' }) initialValuation = input
                                            },
                                            placeholder = { Text("Principal Debt ₹", fontSize = 12.sp, color = theme.textMuted) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = theme.surfaceAlt,
                                                unfocusedContainerColor = theme.surfaceAlt,
                                                focusedBorderColor = theme.accent,
                                                unfocusedBorderColor = theme.borderLight,
                                                focusedTextColor = theme.textBright,
                                                unfocusedTextColor = theme.textBright
                                            )
                                        )
                                        OutlinedTextField(
                                            value = targetAmt,
                                            onValueChange = { input ->
                                                if (input.all { c -> c.isDigit() || c == '.' }) targetAmt = input
                                            },
                                            placeholder = { Text("Monthly EMI ₹", fontSize = 12.sp, color = theme.textMuted) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = theme.surfaceAlt,
                                                unfocusedContainerColor = theme.surfaceAlt,
                                                focusedBorderColor = theme.accent,
                                                unfocusedBorderColor = theme.borderLight,
                                                focusedTextColor = theme.textBright,
                                                unfocusedTextColor = theme.textBright
                                            )
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = dueDay,
                                            onValueChange = { input ->
                                                if (input.all { c -> c.isDigit() } && (input.toIntOrNull() ?: 0) <= 31) dueDay = input
                                            },
                                            placeholder = { Text("Due Day (1-31)", fontSize = 12.sp, color = theme.textMuted) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = theme.surfaceAlt,
                                                unfocusedContainerColor = theme.surfaceAlt,
                                                focusedBorderColor = theme.accent,
                                                unfocusedBorderColor = theme.borderLight,
                                                focusedTextColor = theme.textBright,
                                                unfocusedTextColor = theme.textBright
                                            )
                                        )
                                        OutlinedTextField(
                                            value = interestRate,
                                            onValueChange = { input ->
                                                if (input.all { c -> c.isDigit() || c == '.' }) interestRate = input
                                            },
                                            placeholder = { Text("Interest Rate %", fontSize = 12.sp, color = theme.textMuted) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = theme.surfaceAlt,
                                                unfocusedContainerColor = theme.surfaceAlt,
                                                focusedBorderColor = theme.accent,
                                                unfocusedBorderColor = theme.borderLight,
                                                focusedTextColor = theme.textBright,
                                                unfocusedTextColor = theme.textBright
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Validation Check for Form Submission
                        val isFormValid = when (type) {
                            PocketType.LIABILITY_LOAN -> {
                                name.isNotBlank() &&
                                        (initialValuation.toDoubleOrNull() ?: 0.0) > 0.0 &&
                                        (targetAmt.toDoubleOrNull() ?: 0.0) > 0.0 &&
                                        (dueDay.toIntOrNull() ?: 0) in 1..31
                            }
                            PocketType.PEER_RECEIVABLE, PocketType.PEER_PAYABLE -> {
                                name.isNotBlank() && (initialValuation.toDoubleOrNull() ?: 0.0) > 0.0
                            }
                            PocketType.CREDIT_CARD -> {
                                name.isNotBlank() && (limit.toDoubleOrNull() ?: 0.0) > 0.0
                            }
                            else -> name.isNotBlank()
                        }

                        // Action Controls: Indian Layout Standard (Action on Left, Cancel on Right)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Left Button: Confirm / Save
                            Button(
                                onClick = {
                                    if (type == PocketType.PEER_RECEIVABLE || type == PocketType.PEER_PAYABLE) {
                                        val amt = initialValuation.toDoubleOrNull() ?: 0.0
                                        onSavePeerWithFunding(name, peerIsLendMode, amt, peerFundingPocketId)
                                        onDismiss()
                                    } else {
                                        onSave(
                                            name,
                                            type,
                                            limit.toDoubleOrNull() ?: 0.0,
                                            dueDay.toIntOrNull() ?: 0,
                                            targetAmt.toDoubleOrNull() ?: 0.0,
                                            targetDateEpoch,
                                            initialValuation.toDoubleOrNull() ?: 0.0,
                                            interestRate.toDoubleOrNull() ?: 0.0
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isFormValid) theme.accent else theme.borderLight
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFormValid) theme.accent else theme.surfaceAlt.copy(alpha = 0.5f),
                                    disabledContainerColor = theme.surfaceAlt.copy(alpha = 0.5f)
                                ),
                                enabled = isFormValid
                            ) {
                                Text(
                                    "Save Account",
                                    color = if (isFormValid) theme.bg else theme.textMuted.copy(alpha = 0.38f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            // Right Button: Cancel
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textMuted)
                            ) {
                                Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchBalanceSheetStagingView(
    stagedItems: List<StagedStatementLineItem>,
    theme: ThemeColors,
    onDismissStaging: () -> Unit,
    onConfirmBatch: (List<StagedStatementLineItem>) -> Unit
) {
    var itemsList by remember { mutableStateOf(stagedItems) }

    Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("STAGED BALANCE SHEET", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("${itemsList.count { it.isSelectedForCommit }} Selected", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Text("Review extracted assets and liabilities before importing to vault:", color = theme.textMuted, fontSize = 11.sp)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(itemsList) { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (item.isSelectedForCommit) theme.surfaceAlt else theme.surface)
                        .border(1.dp, if (item.isSelectedForCommit) theme.accent.copy(alpha = 0.5f) else theme.borderLight, RoundedCornerShape(8.dp))
                        .clickable {
                            val updated = itemsList.toMutableList()
                            updated[index] = item.copy(isSelectedForCommit = !item.isSelectedForCommit)
                            itemsList = updated
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.rawExtractedName, color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(item.inferredType.name, color = theme.accent, fontSize = 9.5.sp)
                    }
                    Text(
                        "₹ ${String.format("%,.0f", item.extractedAmount)}",
                        color = if (item.inferredType == PocketType.LIABILITY_LOAN || item.inferredType == PocketType.CREDIT_CARD) theme.mildRed else theme.mildGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onConfirmBatch(itemsList.filter { it.isSelectedForCommit }) },
                modifier = Modifier
                    .weight(1.3f)
                    .height(42.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                enabled = itemsList.any { it.isSelectedForCommit }
            ) {
                Text("Import Selected", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onDismissStaging,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)
            ) {
                Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun EditAccountDialog(
    pocket: LedgerPocket,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (String, Double, Int, Double, Long) -> Unit
) {
    var name by remember { mutableStateOf(pocket.name) }
    var limit by remember { mutableStateOf(if (pocket.creditLimit > 0.0) String.format("%.0f", pocket.creditLimit) else "") }
    var dueDay by remember { mutableStateOf(if (pocket.billDueDay > 0) pocket.billDueDay.toString() else "") }
    var targetAmt by remember { mutableStateOf(if (pocket.targetGoalAmount > 0.0) String.format("%.0f", pocket.targetGoalAmount) else "") }
    var targetDateEpoch by remember { mutableStateOf(if (pocket.goalTargetDate > 0) pocket.goalTargetDate else System.currentTimeMillis() + (30L * 24 * 3600 * 1000L)) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(targetDateEpoch))

    if (showDatePicker) {
        CustomCalendarDialog(
            initialDateMillis = targetDateEpoch,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                targetDateEpoch = it
                showDatePicker = false
            }
        )
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
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 400.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(16.dp))
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "EDIT ${pocket.name.uppercase(Locale.getDefault())}",
                        color = theme.textBright,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Account Name", color = theme.textMuted, fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = theme.surfaceAlt,
                            unfocusedContainerColor = theme.surfaceAlt,
                            focusedBorderColor = theme.accent,
                            unfocusedBorderColor = theme.borderLight,
                            focusedTextColor = theme.textBright,
                            unfocusedTextColor = theme.textBright
                        )
                    )

                    if (pocket.type == PocketType.CREDIT_CARD) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = limit,
                                onValueChange = { input ->
                                    if (input.all { c -> c.isDigit() || c == '.' }) limit = input
                                },
                                placeholder = { Text("Limit ₹", color = theme.textMuted, fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = theme.surfaceAlt,
                                    unfocusedContainerColor = theme.surfaceAlt,
                                    focusedBorderColor = theme.accent,
                                    unfocusedBorderColor = theme.borderLight,
                                    focusedTextColor = theme.textBright,
                                    unfocusedTextColor = theme.textBright
                                )
                            )
                            OutlinedTextField(
                                value = dueDay,
                                onValueChange = { input ->
                                    if (input.all { c -> c.isDigit() } && (input.toIntOrNull() ?: 0) <= 31) dueDay = input
                                },
                                placeholder = { Text("Due Day (1-31)", color = theme.textMuted, fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(46.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = theme.surfaceAlt,
                                    unfocusedContainerColor = theme.surfaceAlt,
                                    focusedBorderColor = theme.accent,
                                    unfocusedBorderColor = theme.borderLight,
                                    focusedTextColor = theme.textBright,
                                    unfocusedTextColor = theme.textBright
                                )
                            )
                        }
                    }

                    if (pocket.type == PocketType.GOAL_POT) {
                        OutlinedTextField(
                            value = targetAmt,
                            onValueChange = { input ->
                                if (input.all { c -> c.isDigit() || c == '.' }) targetAmt = input
                            },
                            placeholder = { Text("Target Goal Amount ₹", color = theme.textMuted, fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = theme.surfaceAlt,
                                unfocusedContainerColor = theme.surfaceAlt,
                                focusedBorderColor = theme.accent,
                                unfocusedBorderColor = theme.borderLight,
                                focusedTextColor = theme.textBright,
                                unfocusedTextColor = theme.textBright
                            )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (name.isNotBlank()) onSave(
                                    name,
                                    limit.toDoubleOrNull() ?: pocket.creditLimit,
                                    dueDay.toIntOrNull() ?: pocket.billDueDay,
                                    targetAmt.toDoubleOrNull() ?: pocket.targetGoalAmount,
                                    targetDateEpoch
                                )
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                            enabled = name.isNotBlank()
                        ) {
                            Text("Save Changes", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)
                        ) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
