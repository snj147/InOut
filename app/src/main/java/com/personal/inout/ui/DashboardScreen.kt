@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidedActionWizardDialog(
    type: WizardType,
    rawPockets: List<VaultPocket>,
    pocketBalances: List<PocketBalanceEntity>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onRequestNewAccount: (PocketType) -> Unit,
    onCommit: (
        nature: MovementNature,
        sourcePocketId: Long?,
        targetPocketId: Long?,
        amount: Double,
        category: String,
        note: String,
        timestamp: Long,
        isRecurring: Boolean,
        frequency: String
    ) -> Unit
) {
    var rawAmount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("General") }

    val liquidPockets = remember(rawPockets) { rawPockets.filter { it.pocketType == PocketType.LIQUID } }
    val cardPockets = remember(rawPockets) { rawPockets.filter { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE } }
    val peerPockets = remember(rawPockets) { rawPockets.filter { it.pocketType == PocketType.COUNTERPARTY || it.subType == "PEER" || it.pocketType == PocketType.PEER } }

    var selectedSourceId by remember { mutableStateOf(liquidPockets.firstOrNull()?.id) }
    var selectedTargetId by remember {
        mutableStateOf(
            if (type == WizardType.TRANSFER) liquidPockets.getOrNull(1)?.id ?: rawPockets.firstOrNull { it.id != liquidPockets.firstOrNull()?.id }?.id
            else if (type == WizardType.CARD_BILL) cardPockets.firstOrNull()?.id
            else if (type == WizardType.PEER_LEND_BORROW) peerPockets.firstOrNull()?.id
            else null
        )
    }

    var peerModeIsLend by remember { mutableStateOf(true) }
    var isRecurring by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var selectedDateEpoch by remember { mutableStateOf(System.currentTimeMillis()) }

    val computedAmount = remember(rawAmount) { MathEvaluator.evaluate(rawAmount) }

    val categories = when (type) {
        WizardType.EXPENSE -> listOf("Food & Dining", "Groceries", "Transport", "Bills", "Shopping", "Health", "General")
        WizardType.INCOME -> listOf("Salary", "Investment", "Freelance", "Refund", "Income")
        WizardType.TRANSFER -> listOf("Internal Transfer", "Savings Pot")
        WizardType.CARD_BILL -> listOf("Card Payment", "Bill Payment")
        WizardType.PEER_LEND_BORROW -> listOf("Peer Debt", "Personal Loan")
    }

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (type) {
                    WizardType.EXPENSE -> "Record Expense Outflow"
                    WizardType.INCOME -> "Record Income Inflow"
                    WizardType.TRANSFER -> "Zero-Sum Account Transfer"
                    WizardType.CARD_BILL -> "Pay Credit Card Liability"
                    WizardType.PEER_LEND_BORROW -> if (peerModeIsLend) "Lend Money to Contact" else "Borrow Money from Contact"
                },
                color = theme.textBright,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (type == WizardType.PEER_LEND_BORROW) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (peerModeIsLend) theme.accent else theme.surfaceAlt)
                                .clickable { peerModeIsLend = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("I Gave (Lent)", color = if (peerModeIsLend) theme.bg else theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!peerModeIsLend) theme.accent else theme.surfaceAlt)
                                .clickable { peerModeIsLend = false }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("I Received (Borrowed)", color = if (!peerModeIsLend) theme.bg else theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Column {
                    CompactInputField(
                        value = rawAmount,
                        onValueChange = { rawAmount = it },
                        placeholder = "Amount (e.g. 150+40 or 5000)"
                    )
                    if (computedAmount != null && rawAmount.contains("+")) {
                        Text("Evaluated: ₹$computedAmount", color = theme.accent, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }

                if (type in listOf(WizardType.EXPENSE, WizardType.TRANSFER, WizardType.CARD_BILL) || (type == WizardType.PEER_LEND_BORROW && peerModeIsLend)) {
                    Text("Source Account", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(liquidPockets) { p ->
                            val isSel = selectedSourceId == p.id
                            val bal = pocketBalances.firstOrNull { it.pocketId == p.id.toString() }?.computedBalance ?: 0.0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) theme.accent else theme.surfaceAlt)
                                    .clickable { selectedSourceId = p.id }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("${p.name} (₹${bal.toInt()})", color = if (isSel) theme.bg else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (type == WizardType.TRANSFER) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Destination Account", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "+ Add Account",
                            color = theme.accent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onRequestNewAccount(PocketType.LIQUID) }
                        )
                    }
                    val validTargets = rawPockets.filter { it.id != selectedSourceId }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(validTargets) { p ->
                            val isSel = selectedTargetId == p.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) theme.accent else theme.surfaceAlt)
                                    .clickable { selectedTargetId = p.id }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(p.name, color = if (isSel) theme.bg else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (type == WizardType.CARD_BILL) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Credit Card", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "+ Add Card",
                            color = theme.accent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onRequestNewAccount(PocketType.CREDIT_LINE) }
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(cardPockets) { p ->
                            val isSel = selectedTargetId == p.id
                            val bal = pocketBalances.firstOrNull { it.pocketId == p.id.toString() }?.computedBalance ?: 0.0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) theme.accent else theme.surfaceAlt)
                                    .clickable { selectedTargetId = p.id }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("${p.name} (Due: ₹${Math.abs(bal).toInt()})", color = if (isSel) theme.bg else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (type == WizardType.PEER_LEND_BORROW) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Contact", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "+ Add Contact",
                            color = theme.accent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onRequestNewAccount(PocketType.COUNTERPARTY) }
                        )
                    }
                    if (peerPockets.isEmpty()) {
                        Text("No contacts saved. Tap '+ Add Contact' above.", color = theme.mildRed, fontSize = 11.sp)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(peerPockets) { p ->
                                val isSel = selectedTargetId == p.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) theme.accent else theme.surfaceAlt)
                                        .clickable { selectedTargetId = p.id }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(p.name, color = if (isSel) theme.bg else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (type == WizardType.INCOME) {
                    Text("Destination Bank / Wallet", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(liquidPockets) { p ->
                            val isSel = selectedTargetId == p.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) theme.accent else theme.surfaceAlt)
                                    .clickable { selectedTargetId = p.id }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(p.name, color = if (isSel) theme.bg else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Text("Category", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSel = selectedCategoryId == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else theme.surfaceAlt)
                                .clickable { selectedCategoryId = cat }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(cat, color = if (isSel) theme.bg else theme.textBright, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                CompactInputField(value = note, onValueChange = { note = it }, placeholder = "Merchant / Reference Note (Optional)")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Repeat Cadence", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("NONE" to "Once", "DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly").forEach { (fCode, fName) ->
                            val isSel = (if (!isRecurring) "NONE" else frequency) == fCode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) theme.accent else theme.surfaceAlt)
                                    .clickable {
                                        if (fCode == "NONE") {
                                            isRecurring = false
                                        } else {
                                            isRecurring = true
                                            frequency = fCode
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(fName, color = if (isSel) theme.bg else theme.textBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = computedAmount
                    if (amt == null || amt <= 0.0) return@Button

                    val finalNature = when (type) {
                        WizardType.EXPENSE -> MovementNature.OUTFLOW
                        WizardType.INCOME -> MovementNature.INFLOW
                        WizardType.TRANSFER -> MovementNature.TRANSFER
                        WizardType.CARD_BILL -> MovementNature.CARD_PAYMENT
                        WizardType.PEER_LEND_BORROW -> if (peerModeIsLend) MovementNature.PEER_LEND else MovementNature.PEER_BORROW
                    }

                    val src = when (type) {
                        WizardType.INCOME -> null
                        WizardType.EXPENSE, WizardType.TRANSFER, WizardType.CARD_BILL -> selectedSourceId
                        WizardType.PEER_LEND_BORROW -> if (peerModeIsLend) selectedSourceId else selectedTargetId
                    }

                    val tgt = when (type) {
                        WizardType.EXPENSE -> null
                        WizardType.INCOME, WizardType.TRANSFER, WizardType.CARD_BILL -> selectedTargetId
                        WizardType.PEER_LEND_BORROW -> if (peerModeIsLend) selectedTargetId else selectedSourceId
                    }

                    onCommit(
                        finalNature,
                        src,
                        tgt,
                        amt,
                        selectedCategoryId,
                        note.ifBlank { selectedCategoryId },
                        selectedDateEpoch,
                        isRecurring,
                        frequency
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                enabled = computedAmount != null && computedAmount > 0.0
            ) {
                Text("Commit to Ledger", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textMuted)
            }
        }
    )
}
