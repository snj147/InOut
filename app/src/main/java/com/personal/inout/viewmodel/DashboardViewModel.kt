package com.personal.inout.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.inout.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val rawPockets: List<LedgerPocket> = emptyList(),
    val liquidPockets: List<LedgerPocket> = emptyList(),
    val completedTransactions: List<LedgerTransaction> = emptyList(),
    val unreadNotices: List<SystemNotice> = emptyList(),
    val recurringTemplates: List<LedgerTransaction> = emptyList(),
    val pocketBalances: Map<Long, Double> = emptyMap(),
    val solvencyDeck: SolvencyMetricDeck = SolvencyMetricDeck(0.0, 0.0, 0.0, 500.0, BurnPacingStatus.ON_TRACK, 0L, 0.0, false),
    val totalInflowLifetime: Double = 0.0,
    val totalOutflowLifetime: Double = 0.0
)

class DashboardViewModel(
    private val dao: LedgerDao,
    val prefs: SharedPreferences
) : ViewModel() {

    val ledgerEngine = VaultLedgerEngine(dao, prefs)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    var autoSplitEnabled = MutableStateFlow(prefs.getBoolean("auto_split_debit", false))
    var dailyBurnCeiling = MutableStateFlow(prefs.getFloat("daily_burn_ceiling", 500f).toDouble())

    init {
        // This is the magic block that fixes the "Restart to Refresh" bug.
        // It listens to the database constantly. If anything changes, it recalculates
        // everything and pushes the new state to the UI instantly.
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                dao.getAllActivePockets(),
                dao.observeHistoricalTransactions()
            ) { pockets, txs ->
                val balances = pockets.associate { it.id to dao.computePocketBalance(it.id) }
                val deck = ledgerEngine.computeSolvencyDeck()
                val liquid = pockets.filter { it.type == PocketType.LIQUID || it.type == PocketType.PREPAID_WALLET }
                val recurring = txs.filter { it.isRecurring && it.recurringFrequency != "NONE" }
                val inflow = txs.filter { it.movementNature == MovementNature.OPERATING_INCOME }.sumOf { it.amount }
                val outflow = txs.filter { it.movementNature == MovementNature.OPERATING_EXPENSE }.sumOf { it.amount }

                DashboardUiState(
                    rawPockets = pockets,
                    liquidPockets = liquid,
                    completedTransactions = txs,
                    recurringTemplates = recurring,
                    pocketBalances = balances,
                    solvencyDeck = deck,
                    totalInflowLifetime = inflow,
                    totalOutflowLifetime = outflow
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        // Notices flow
        viewModelScope.launch(Dispatchers.IO) {
            dao.observeUnreadNotices().collect { notices ->
                _uiState.value = _uiState.value.copy(unreadNotices = notices)
            }
        }

        // Catch up recurrings on boot
        viewModelScope.launch(Dispatchers.IO) {
            ledgerEngine.catchUpRecurringRules()
        }
    }

    fun executeMasterEntryTrigger(
        nature: MovementNature, srcId: Long, tgtId: Long?, amt: Double, cat: String, note: String,
        date: Long, isRec: Boolean, freq: String, tax: Boolean, reimb: Boolean,
        onSuccess: (String) -> Unit, onError: (String) -> Unit, onWarning: (String) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = ledgerEngine.recordMovement(nature, srcId, tgtId, amt, cat, note, date, SettlementStatus.CLEARED, autoSplitEnabled.value, tax, reimb, false, isRec, freq)) {
                is VaultExecutionResult.Success -> onSuccess(res.summary)
                is VaultExecutionResult.OverdraftError -> onError(res.message)
                is VaultExecutionResult.DuplicateWarning -> onWarning(res.message)
            }
        }
    }

    fun updateBurnCeiling(newCeiling: Double) {
        dailyBurnCeiling.value = newCeiling
        prefs.edit().putFloat("daily_burn_ceiling", newCeiling.toFloat()).apply()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(solvencyDeck = ledgerEngine.computeSolvencyDeck())
        }
    }

    fun toggleAutoSplit(enabled: Boolean) {
        autoSplitEnabled.value = enabled
        prefs.edit().putBoolean("auto_split_debit", enabled).apply()
    }
}
