package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.Account
import com.personal.inout.data.Transaction

@Composable
fun VaultTabContent(
    accounts: List<Account>,
    transactions: List<Transaction>,
    onAddAccountClick: () -> Unit,
    onAccountClick: (Account) -> Unit
) {
    val liquidAccounts = accounts.filter { it.type != "LOAN" && it.type != "CREDIT" }
    val totalAvailable = liquidAccounts.sumOf { it.balance }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(Color(0xFF1A1F30), Color(0xFF121520))))
                        .border(1.dp, Color(0xFF2B3245), RoundedCornerShape(22.dp))
                        .padding(22.dp)
                ) {
                    Column {
                        Text("TOTAL SPENDABLE CASH", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("₹ ${String.format("%,.2f", totalAvailable)}", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val inSum = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
                            val outSum = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("₹ ${String.format("%,.0f", inSum)}", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = CoralRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("₹ ${String.format("%,.0f", outSum)}", color = CoralRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Wallets & Accounts", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onAddAccountClick) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ElectricIndigo, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Account", color = ElectricIndigo, fontSize = 13.sp)
                }
            }

            if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard).clickable(onClick = onAddAccountClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ Add your first bank account or wallet", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(accounts) { acc ->
                        Card(
                            modifier = Modifier.width(160.dp).height(95.dp).clip(RoundedCornerShape(16.dp)).clickable { onAccountClick(acc) },
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                        ) {
                            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(acc.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                    Text(
                                        acc.type,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (acc.type == "LOAN" || acc.type == "CREDIT") CoralRed else NeonGreen
                                    )
                                }
                                Text("₹ ${String.format("%,.2f", acc.balance)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Recent Transactions", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (transactions.isEmpty()) {
            item {
                Text("No transactions logged yet. Tap '+' to create one.", color = TextMuted, fontSize = 14.sp)
            }
        } else {
            items(transactions) { tx ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceCard).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceCardAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (tx.type == "INCOME") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (tx.type == "INCOME") NeonGreen else CoralRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(tx.note.ifBlank { tx.category }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(tx.category, color = TextMuted, fontSize = 12.sp)
                        }
                    }
                    Text(
                        "${if (tx.type == "INCOME") "+" else "-"} ₹ ${String.format("%.2f", tx.amount)}",
                        color = if (tx.type == "INCOME") NeonGreen else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}
