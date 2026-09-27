package com.personal.inout.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket

data class QuickBarSuggestion(
    val title: String,
    val template: String,
    val icon: ImageVector
)

object QuickBarSuggester {

    fun evaluate(currentInput: String, activePockets: List<VaultPocket>): List<QuickBarSuggestion> {
        val q = currentInput.trim().lowercase()
        val liquidPockets = activePockets.filter { it.pocketType == PocketType.LIQUID }
        val primaryBankName = liquidPockets.firstOrNull()?.name ?: "BANK"

        // State 0: Empty Input -> Show Primary Quick-Action Shortcuts
        if (q.isEmpty()) {
            return listOf(
                QuickBarSuggestion("Spent", "coffee 120 $primaryBankName", Icons.Filled.ShoppingBag),
                QuickBarSuggestion("Salary In", "salary 50k into $primaryBankName", Icons.Filled.ArrowDownward),
                QuickBarSuggestion("Transfer", "trf 5000 from $primaryBankName to ", Icons.Filled.SwapHoriz),
                QuickBarSuggestion("Card Bill", "pay card 8000 from $primaryBankName", Icons.Filled.CreditCard),
                QuickBarSuggestion("Lent", "lent 1000 to NAME", Icons.Filled.PersonAdd),
                QuickBarSuggestion("Recurring", "rent 15k $primaryBankName monthly from today", Icons.Filled.CalendarMonth)
            )
        }

        // State 1: Recurrence Cadence Triggered -> Provide Smart Starting Date Options
        if (q.contains("monthly") || q.contains("weekly") || q.contains("daily")) {
            val base = currentInput.trim()
            return listOf(
                QuickBarSuggestion("from Today", "$base from today", Icons.Filled.Today),
                QuickBarSuggestion("from 1st", "$base on 1st", Icons.Filled.CalendarMonth),
                QuickBarSuggestion("from 5th", "$base on 5th", Icons.Filled.CalendarMonth),
                QuickBarSuggestion("from 10th", "$base on 10th", Icons.Filled.CalendarMonth)
            )
        }

        // State 2: Account Insertion Pipeline
        // If user typed a number/note but no recognized account yet, show one-tap account chips
        val hasPocketAttached = activePockets.any { q.contains(it.name.lowercase()) }
        if (!hasPocketAttached && q.any { it.isDigit() }) {
            val suggestions = mutableListOf<QuickBarSuggestion>()
            for (pocket in activePockets.take(4)) {
                val icon = when (pocket.pocketType) {
                    PocketType.LIQUID -> Icons.Filled.AccountBalance
                    PocketType.CREDIT_LINE, PocketType.CREDIT -> Icons.Filled.CreditCard
                    PocketType.SAVING_GOAL -> Icons.Filled.Savings
                    PocketType.COUNTERPARTY, PocketType.PEER -> Icons.Filled.Person
                    else -> Icons.Filled.AccountBalanceWallet
                }
                suggestions.add(
                    QuickBarSuggestion(pocket.name, "${currentInput.trim()} ${pocket.name}", icon)
                )
            }
            suggestions.add(QuickBarSuggestion("+ Monthly", "${currentInput.trim()} monthly from today", Icons.Filled.Repeat))
            return suggestions
        }

        // State 3: Account Creation Mode
        if (q.startsWith("new") || q.startsWith("create") || q.startsWith("add")) {
            return listOf(
                QuickBarSuggestion("Bank", "new bank ", Icons.Filled.AccountBalance),
                QuickBarSuggestion("Credit Card", "new card Limit 50k", Icons.Filled.CreditCard),
                QuickBarSuggestion("Goal Pot", "new goal Vacation 60k", Icons.Filled.Savings),
                QuickBarSuggestion("Person", "new person ", Icons.Filled.Person)
            )
        }

        // State 4: Default Fallbacks
        return listOf(
            QuickBarSuggestion("Today", "${currentInput.trim()} today", Icons.Filled.Today),
            QuickBarSuggestion("Monthly", "${currentInput.trim()} monthly", Icons.Filled.Repeat),
            QuickBarSuggestion("Cash", "${currentInput.trim()} Cash", Icons.Filled.Money)
        )
    }
}
