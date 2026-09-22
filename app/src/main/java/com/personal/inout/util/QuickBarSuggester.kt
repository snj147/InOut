package com.personal.inout.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket

data class LiveSuggestion(
    val title: String,
    val template: String,
    val icon: ImageVector,
    val tag: String
)

object QuickBarSuggester {
    fun evaluate(query: String, activePockets: List<VaultPocket>): List<LiveSuggestion> {
        val q = query.trim().lowercase()

        // 1. Zero-Query / Empty Focus State: Surface high-frequency accelerators
        if (q.isBlank()) {
            val p1 = activePockets.firstOrNull { it.pocketType == PocketType.LIQUID }?.name ?: "SBI"
            val p2 = activePockets.filter { it.pocketType == PocketType.LIQUID }.getOrNull(1)?.name ?: "IDFC"
            return listOf(
                LiveSuggestion("Transfer", "transf 2000 from $p1 to $p2", Icons.Default.SyncAlt, "TRANSFER"),
                LiveSuggestion("+ Bank Account", "new bank HDFC", Icons.Default.AccountBalance, "CREATE"),
                LiveSuggestion("+ Credit Card", "new card Axis limit 50000", Icons.Default.CreditCard, "CREATE"),
                LiveSuggestion("Recurring SIP/Bill", "sip 5000 $p1 monthly from 5th", Icons.Default.Autorenew, "RECURRING"),
                LiveSuggestion("Toggle Auto-Split", "autosplit toggle", Icons.Default.Bolt, "SYSTEM")
            )
        }

        val results = mutableListOf<LiveSuggestion>()

        // 2. Slash command palette
        if (q.startsWith("/")) {
            val sub = q.removePrefix("/")
            val baseCatalog = listOf(
                LiveSuggestion("Create Bank", "new bank SBI", Icons.Default.AccountBalance, "BANK"),
                LiveSuggestion("Create Card", "new card Axis limit 50000", Icons.Default.CreditCard, "CARD"),
                LiveSuggestion("Transfer", "transf 2000 from SBI to IDFC", Icons.Default.SyncAlt, "TRANSFER"),
                LiveSuggestion("Auto-Split", "autosplit toggle", Icons.Default.Bolt, "TOGGLE"),
                LiveSuggestion("Phantom Lock", "phantom lock toggle", Icons.Default.Lock, "SECURITY"),
                LiveSuggestion("Goal Pot", "new goal Laptop 60000 by dec", Icons.Default.Flag, "GOAL"),
                LiveSuggestion("Theme", "theme olive", Icons.Default.Palette, "THEME"),
                LiveSuggestion("Backup", "backup now", Icons.Default.Shield, "BACKUP"),
                LiveSuggestion("Export PDF", "export pdf", Icons.Default.PictureAsPdf, "EXPORT")
            )
            return baseCatalog.filter { it.title.lowercase().contains(sub) || it.template.lowercase().contains(sub) }
        }

        // 3. Account creation prediction
        if (q.startsWith("new") || q.startsWith("cre") || q.startsWith("add") || q.startsWith("ban") || q.startsWith("car")) {
            results.add(LiveSuggestion("New Bank Account", "new bank SBI", Icons.Default.AccountBalance, "CREATE"))
            results.add(LiveSuggestion("New Credit Card", "new card HDFC limit 75000", Icons.Default.CreditCard, "CREATE"))
            results.add(LiveSuggestion("New Goal Pot", "new goal Emergency 100000 by nov", Icons.Default.Flag, "CREATE"))
            results.add(LiveSuggestion("New Peer Profile", "new peer Rahul", Icons.Default.Person, "CREATE"))
        }

        // 4. Transfer prediction
        if (q.startsWith("tr") || q.startsWith("xf") || q.startsWith("mov") || q.startsWith("sh")) {
            val p1 = activePockets.firstOrNull { it.pocketType == PocketType.LIQUID }?.name ?: "SBI"
            val p2 = activePockets.filter { it.pocketType == PocketType.LIQUID }.getOrNull(1)?.name ?: "IDFC"
            results.add(LiveSuggestion("Transfer Between Banks", "transf 2000 from $p1 to $p2", Icons.Default.SyncAlt, "TRANSFER"))
        }

        // 5. Recurring rules prediction
        if (q.startsWith("rec") || q.startsWith("mon") || q.startsWith("sip") || q.startsWith("sal") || q.startsWith("sub")) {
            val p1 = activePockets.firstOrNull { it.pocketType == PocketType.LIQUID }?.name ?: "SBI"
            results.add(LiveSuggestion("Monthly Salary", "salary 50000 $p1 monthly from 1st", Icons.Default.Autorenew, "RECURRING"))
            results.add(LiveSuggestion("Monthly SIP", "sip 5000 $p1 monthly from 10th", Icons.Default.Autorenew, "RECURRING"))
            results.add(LiveSuggestion("Monthly Subscription", "netflix 649 $p1 monthly", Icons.Default.Autorenew, "RECURRING"))
        }

        // 6. Peer borrow/lend prediction
        if (q.startsWith("lo") || q.startsWith("le") || q.startsWith("bo") || q.startsWith("co") || q.startsWith("re")) {
            results.add(LiveSuggestion("Lend to Friend", "loan to Rahul 1500", Icons.Default.ArrowUpward, "PEER"))
            results.add(LiveSuggestion("Borrow from Friend", "loan from Rahul 2000", Icons.Default.ArrowDownward, "PEER"))
            results.add(LiveSuggestion("Collect Peer Debt", "collect 1500 from Rahul", Icons.Default.CheckCircle, "PEER"))
            results.add(LiveSuggestion("Repay Peer Debt", "repay 2000 to Rahul", Icons.Default.AssignmentReturn, "PEER"))
        }

        // 7. System toggles prediction
        if (q.startsWith("au") || q.startsWith("spl")) {
            results.add(LiveSuggestion("Toggle Auto-Split", "autosplit toggle", Icons.Default.Bolt, "SYSTEM"))
        }
        if (q.startsWith("ph") || q.startsWith("loc")) {
            results.add(LiveSuggestion("Toggle Phantom Lock", "phantom lock toggle", Icons.Default.Lock, "SYSTEM"))
        }
        if (q.startsWith("th") || q.startsWith("col")) {
            results.add(LiveSuggestion("Amber Theme", "theme amber", Icons.Default.Palette, "THEME"))
            results.add(LiveSuggestion("Olive Matcha Theme", "theme olive", Icons.Default.Palette, "THEME"))
            results.add(LiveSuggestion("Nordic Slate Theme", "theme nordic", Icons.Default.Palette, "THEME"))
        }

        return results.distinctBy { it.template }
    }
}
