package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// BankingTransactionDrillScreen — reusable drill-down for any banking tap
// Route: banking_drill/{month}/{category}/{view}/{sort}
// "null" string used as placeholder for absent params (NavArgs limitation)
// CSV export: writes date,description,amount,category,account rows to clipboard ☘️

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.vitanest.app.data.remote.BankingTransaction
import com.vitanest.app.data.remote.BankingTransactionsResponse
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

private val DrillGreen  = Color(0xFF2D6A4F)
private val DrillRed    = Color(0xFFA32D2D)

// ── State + ViewModel ─────────────────────────────────────────

data class DrillUiState(
    val result:    BankingTransactionsResponse? = null,
    val isLoading: Boolean                      = true,
    val error:     String?                      = null,
    val sortAsc:   Boolean                      = false
)

class BankingDrillViewModel(
    private val repository: VitaClawRepository,
    private val month:      String?,
    private val category:   String?,
    private val view:       String?
) : ViewModel() {

    private val _state = MutableStateFlow(DrillUiState())
    val state: StateFlow<DrillUiState> = _state.asStateFlow()

    init { load(asc = false) }

    fun toggleSort() {
        val newAsc = !_state.value.sortAsc
        _state.value = _state.value.copy(sortAsc = newAsc)
        load(asc = newAsc)
    }

    private fun load(asc: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repository.getBankingTransactions(
                month    = month,
                category = category,
                view     = view,
                sort     = if (asc) "asc" else "desc"
            ).fold(
                onSuccess = { _state.value = DrillUiState(result = it, isLoading = false, sortAsc = asc) },
                onFailure = { _state.value = DrillUiState(isLoading = false, error = it.message, sortAsc = asc) }
            )
        }
    }
}

// ── Screen ────────────────────────────────────────────────────

@Composable
fun BankingTransactionDrillScreen(
    navController: NavController,
    repository:    VitaClawRepository,
    month:         String?,
    category:      String?,
    view:          String?
) {
    val viewModel = remember {
        BankingDrillViewModel(
            repository = repository,
            month      = month,
            category   = category,
            view       = view
        )
    }
    val state     by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var copied    by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }

    val title = when {
        category != null -> category.replace("_", " ").replaceFirstChar { it.uppercase() }
        view == "income"   -> "Income"
        view == "expenses" -> "Expenses"
        view == "surplus"  -> "Surplus"
        else               -> "Transactions"
    }

    val monthLabel = month?.let {
        try {
            val parts = it.split("-")
            val m = java.time.Month.of(parts[1].toInt()).name
                .lowercase().replaceFirstChar { c -> c.uppercase() }
            "$m ${parts[0]}"
        } catch (_: Exception) { it }
    } ?: "Current"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = T.screenPadding)) {
                Spacer(Modifier.statusBarsPadding())
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick  = { navController.popBackStack() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint               = T.Ink,
                                modifier           = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text       = title,
                                fontFamily = T.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 20.sp,
                                color      = T.Ink
                            )
                            Text(monthLabel, fontSize = 11.sp, color = T.Muted)
                        }
                    }
                    // CSV export button
                    state.result?.let { result ->
                        OutlinedButton(
                            onClick = {
                                val csv = buildCsv(result.transactions)
                                clipboard.setText(AnnotatedString(csv))
                                copied = true
                            },
                            shape          = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier       = Modifier.height(30.dp)
                        ) {
                            Text(
                                text     = if (copied) "Copied ✓" else "⬇ CSV",
                                fontSize = 11.sp,
                                color    = if (copied) DrillGreen else T.Ink
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(thickness = T.heavyRule, color = T.Ink)
            }

            // ── Sort row ──────────────────────────────────────
            state.result?.let { result ->
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = T.screenPadding, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "${result.transactionCount} transactions",
                        fontSize = 11.sp, color = T.Muted
                    )
                    OutlinedButton(
                        onClick        = { viewModel.toggleSort() },
                        shape          = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier       = Modifier.height(26.dp)
                    ) {
                        Text(
                            if (state.sortAsc) "Date ↑" else "Date ↓",
                            fontSize = 11.sp,
                            color    = T.Ink
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
            }

            // ── Body ──────────────────────────────────────────
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(
                            color       = DrillGreen,
                            modifier    = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(state.error ?: "Error", fontSize = 13.sp, color = T.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp))
                    }
                }
                else -> {
                    val result = state.result
                    if (result != null) {
                        LazyColumn(
                            modifier       = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(result.transactions) { tx ->
                                TransactionRow(tx)
                                HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
                            }
                        }
                        // Pinned total
                        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = T.screenPadding, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                "Total · ${result.transactionCount} transactions",
                                fontSize = 12.sp, color = T.Muted
                            )
                            val total    = result.totalGbp
                            val isDebit  = total < 0
                            Text(
                                "${if (isDebit) "-" else "+"}£${"%.2f".format(abs(total))}",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color      = if (isDebit) DrillRed else DrillGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Transaction row ───────────────────────────────────────────

@Composable
private fun TransactionRow(tx: BankingTransaction) {
    val isDebit = tx.amountGbp < 0
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                formatTxDate(tx.date),
                fontSize = 10.sp,
                color    = T.Muted
            )
            Text(
                tx.description,
                fontSize  = 12.sp,
                color     = T.Ink,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEEEEEA), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        tx.category.replace("_", " "),
                        fontSize = 9.sp,
                        color    = T.Muted
                    )
                }
                Text("·", fontSize = 9.sp, color = T.Muted)
                Text(
                    tx.accountId.replace("_", " "),
                    fontSize = 9.sp,
                    color    = T.Muted
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${if (isDebit) "-" else "+"}£${"%.2f".format(abs(tx.amountGbp))}",
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
            color      = if (isDebit) DrillRed else DrillGreen
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────

private fun formatTxDate(dateStr: String): String {
    return try {
        val d = java.time.LocalDate.parse(dateStr)
        val month = d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        "${d.dayOfMonth} $month"
    } catch (_: Exception) { dateStr }
}

private fun buildCsv(transactions: List<BankingTransaction>): String {
    val sb = StringBuilder()
    sb.appendLine("date,description,amount_gbp,category,account")
    transactions.forEach { tx ->
        val desc = tx.description.replace(",", " ")
        sb.appendLine("${tx.date},${desc},${tx.amountGbp},${tx.category},${tx.accountId}")
    }
    return sb.toString()
}