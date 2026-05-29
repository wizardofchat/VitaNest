package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// BankingSummaryScreen — Banking tab (replaces Finance in bottom nav)
// Sections: Summary cards → Spend breakdown → Net worth → Analytics button
// Taps on any stat or category row → BankingTransactionDrillScreen
// Finance Analytics still accessible via Home Finance petal ☘️

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.vitanest.app.data.remote.BankingAnomaly
import com.vitanest.app.data.remote.BankingCurrent
import com.vitanest.app.data.remote.BankingNetWorth
import com.vitanest.app.data.remote.BankingSummaryResponse
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// ── Colours ───────────────────────────────────────────────────
private val BankGreen     = Color(0xFF2D6A4F)
private val BankGreenBg   = Color(0xFFEAF3DE)
private val BankGreenFg   = Color(0xFF3B6D11)
private val BankRed       = Color(0xFFA32D2D)
private val BankRedBg     = Color(0xFFFCEBEB)
private val BankAmberBg   = Color(0xFFFAEEDA)
private val BankAmberFg   = Color(0xFF854F0B)
private val BankAmberBdr  = Color(0xFFEF9F27)

// ── State ─────────────────────────────────────────────────────

data class BankingUiState(
    val summary:   BankingSummaryResponse? = null,
    val isLoading: Boolean                 = true,
    val error:     String?                 = null,
    val month:     String?                 = null   // null = current month
)

// ── ViewModel ─────────────────────────────────────────────────

class BankingViewModel(
    private val repository: VitaClawRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BankingUiState())
    val state: StateFlow<BankingUiState> = _state.asStateFlow()

    // Current month in YYYY-MM format, null = API default (current)
    private var selectedMonth: String? = null

    init { load() }

    fun load(month: String? = null) {
        selectedMonth = month
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, month = month)
            repository.getBankingSummary(month = month)
                .fold(
                    onSuccess = { _state.value = BankingUiState(summary = it, isLoading = false, month = month) },
                    onFailure = { _state.value = BankingUiState(isLoading = false, error = it.message, month = month) }
                )
        }
    }

    fun navigateMonth(forward: Boolean) {
        val current = selectedMonth
            ?: _state.value.summary?.currentMonth?.take(7)
            ?: return
        try {
            val ym = YearMonth.parse(current, DateTimeFormatter.ofPattern("yyyy-MM"))
            val next = if (forward) ym.plusMonths(1) else ym.minusMonths(1)
            load(next.format(DateTimeFormatter.ofPattern("yyyy-MM")))
        } catch (_: Exception) { }
    }

    fun isCurrentMonth(): Boolean {
        val sel = selectedMonth ?: return true
        val cur = _state.value.summary?.currentMonth?.take(7) ?: return true
        return sel == cur
    }
}

// ── Screen ────────────────────────────────────────────────────

@Composable
fun BankingSummaryScreen(
    navController: NavController,
    repository:    VitaClawRepository = VitaClawRepository()
) {
    val viewModel = remember { BankingViewModel(repository) }
    val state     by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 56.dp)
        ) {
            // ── Header ────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = T.screenPadding)) {
                Spacer(Modifier.statusBarsPadding())
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "Banking",
                        fontFamily = T.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 22.sp,
                        color      = T.Ink
                    )
                    state.summary?.let { s ->
                        val chipBg = when {
                            s.isStale && (s.staleDays ?: 0) > 7 -> BankRedBg
                            s.isStale                            -> BankAmberBg
                            else                                 -> BankGreenBg
                        }
                        val chipFg = when {
                            s.isStale && (s.staleDays ?: 0) > 7 -> BankRed
                            s.isStale                            -> BankAmberFg
                            else                                 -> BankGreenFg
                        }
                        val chipText = when {
                            s.isStale && (s.staleDays ?: 0) > 7 ->
                                "Stale ${s.staleDays}d"
                            s.isStale -> "Cached ${s.staleDays ?: 0}d"
                            else -> "● Live"
                        }
                        Box(
                            modifier = Modifier
                                .background(chipBg, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(chipText, fontSize = 10.sp, color = chipFg, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(thickness = T.heavyRule, color = T.Ink)
            }

            // ── Month nav ─────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = T.screenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick        = { viewModel.navigateMonth(false) },
                    shape          = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier       = Modifier.height(28.dp)
                ) { Text("◀", fontSize = 12.sp) }

                Text(
                    text       = formatMonthLabel(state.month, state.summary?.currentMonth),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color      = T.Ink
                )

                OutlinedButton(
                    onClick        = { viewModel.navigateMonth(true) },
                    enabled        = !viewModel.isCurrentMonth(),
                    shape          = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier       = Modifier.height(28.dp)
                ) { Text("▶", fontSize = 12.sp) }
            }
            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

            // ── Body ──────────────────────────────────────────
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(
                            color       = BankGreen,
                            modifier    = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error ?: "Error", fontSize = 13.sp, color = T.Muted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp))
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { viewModel.load(state.month) },
                                shape = RoundedCornerShape(4.dp)) {
                                Text("Retry", fontSize = 12.sp, color = T.Ink)
                            }
                        }
                    }
                }
                else -> {
                    val summary = state.summary
                    // Resolve current data — works for both current month and specific month
                    val current = summary?.current ?: resolveCurrentFromFlat(summary)
                    val netWorth = summary?.netWorth

                    LazyColumn(
                        modifier       = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {

                        // Incomplete banner
                        if (summary?.expensesIncomplete == true) {
                            item { IncompleteBanner(summary.missingAccounts) }
                        }

                        // Summary cards
                        if (current != null) {
                            item {
                                SummarySection(
                                    current       = current,
                                    navController = navController,
                                    month         = state.month,
                                    avg3m         = summary?.trend?.surplus3mAvg
                                )
                            }
                        }

                        // Spend breakdown
                        if (current != null && current.topCategories.isNotEmpty()) {
                            item {
                                SpendBreakdownSection(
                                    categories = current.topCategories,
                                    anomalies  = current.anomalies,
                                    navController = navController,
                                    month      = state.month
                                )
                            }
                        }

                        // Net worth (current month only)
                        if (netWorth != null && state.month == null) {
                            item { NetWorthSection(netWorth) }
                        }

                        // Analytics button
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = T.screenPadding, vertical = 12.dp)
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .border(0.5.dp, T.Rule, RoundedCornerShape(8.dp))
                                    .clickable { navController.navigate("banking_analytics") }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Text("View trends & analytics", fontSize = 13.sp, color = T.Ink)
                                    Text("→", fontSize = 14.sp, color = T.Muted)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        InkBottomNav(
            current       = "banking",
            navController = navController,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

// ── Incomplete banner ─────────────────────────────────────────

@Composable
private fun IncompleteBanner(missingAccounts: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 6.dp)
            .background(BankAmberBg, RoundedCornerShape(6.dp))
            .border(0.5.dp, BankAmberBdr, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("⚠", fontSize = 12.sp)
        Text(
            text     = "Expenses understated — ${missingAccounts.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }} not connected",
            fontSize = 11.sp,
            color    = BankAmberFg
        )
    }
}

// ── Summary section ───────────────────────────────────────────

@Composable
private fun SummarySection(
    current:       BankingCurrent,
    navController: NavController,
    month:         String?,
    avg3m:         Double? = null
) {
    val monthParam = month ?: "current"

    Column(modifier = Modifier.padding(horizontal = T.screenPadding, vertical = 12.dp)) {
        Text("THIS MONTH", fontSize = 10.sp, fontWeight = FontWeight.Medium,
            color = T.Muted, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                label      = "Income",
                value      = "£${"%.0f".format(current.incomeGbp)}",
                valueColor = BankGreen,
                modifier   = Modifier.weight(1f),
                onClick    = {
                    navController.navigate("banking_drill/$monthParam/null/income/desc")
                }
            )
            StatCard(
                label      = "Expenses",
                value      = "£${"%.0f".format(current.expensesGbp)}",
                valueColor = BankRed,
                modifier   = Modifier.weight(1f),
                onClick    = {
                    navController.navigate("banking_drill/$monthParam/null/expenses/desc")
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                label    = "Surplus",
                value    = "£${"%.0f".format(current.surplusGbp)}",
                subLabel = avg3m?.let { "3m avg £${"%.0f".format(it)}" } ?: "3m —",
                modifier = Modifier.weight(1f),
                onClick  = {
                    navController.navigate("banking_drill/$monthParam/null/surplus/desc")
                }
            )
            StatCard(
                label    = "Deployed",
                value    = "£${"%.0f".format(current.investmentFundingGbp)}",
                subLabel = "${"%.1f".format(
                    if (current.incomeGbp > 0) (current.investmentFundingGbp / current.incomeGbp * 100) else 0.0
                )}% of income",
                modifier = Modifier.weight(1f),
                onClick  = {
                    navController.navigate("banking_drill/$monthParam/null/income/desc")
                }
            )
        }

        // Conditional rows
        if (current.taxProvisionGbp > 0) {
            Spacer(Modifier.height(6.dp))
            BankingMetaRow("Tax provision", "£${"%.2f".format(current.taxProvisionGbp)}")
        }
        if (current.committedSavingsGbp > 0) {
            Spacer(Modifier.height(4.dp))
            BankingMetaRow("Committed savings", "£${"%.2f".format(current.committedSavingsGbp)}")
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
}

@Composable
private fun StatCard(
    label:      String,
    value:      String,
    valueColor: Color   = T.Ink,
    subLabel:   String? = null,
    modifier:   Modifier = Modifier,
    onClick:    (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(0.5.dp, T.Rule, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(label, fontSize = 10.sp, color = T.Muted)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = valueColor)
            if (subLabel != null) {
                Text(subLabel, fontSize = 10.sp, color = T.Muted)
            }
            if (onClick != null) {
                Text("→", fontSize = 10.sp, color = T.Muted,
                    modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun BankingMetaRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = T.Muted)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = T.Ink)
    }
}

// ── Spend breakdown section ───────────────────────────────────

@Composable
private fun SpendBreakdownSection(
    categories:   Map<String, Double>,
    anomalies:    List<BankingAnomaly>,
    navController: NavController,
    month:        String?
) {
    val maxAmount = categories.values.maxOrNull() ?: 1.0
    val monthParam = month ?: "current"

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            "SPEND BREAKDOWN",
            fontSize = 10.sp, fontWeight = FontWeight.Medium,
            color = T.Muted, letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = T.screenPadding)
        )
        Spacer(Modifier.height(8.dp))

        val anomalyMap = anomalies.associateBy { it.category }

        categories.entries.sortedByDescending { it.value }.forEach { (cat, amount) ->
            val anomaly   = anomalyMap[cat]
            val isWarning = anomaly != null && anomaly.deltaPct > 50.0
            val barFraction = (amount / maxAmount).toFloat().coerceIn(0f, 1f)
            val displayName = cat.replace("_", " ").replaceFirstChar { it.uppercase() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate("banking_drill/$monthParam/$cat/null/desc")
                    }
                    .padding(horizontal = T.screenPadding, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text     = if (isWarning) "$displayName ⚠" else displayName,
                    fontSize = 12.sp,
                    color    = if (isWarning) BankRed else T.Ink,
                    modifier = Modifier.width(90.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(T.Rule, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barFraction)
                            .height(3.dp)
                            .background(
                                if (isWarning) BankRed else T.Ink,
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
                Text(
                    "£${"%.0f".format(amount)}",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color      = if (isWarning) BankRed else T.Ink,
                    modifier   = Modifier.width(48.dp),
                    textAlign  = TextAlign.End
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color     = T.Rule,
                modifier  = Modifier.padding(horizontal = T.screenPadding)
            )
        }

        // Anomaly notes
        if (anomalies.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            anomalies.forEach { anomaly ->
                val isSpike = anomaly.deltaPct > 0
                val sign    = if (isSpike) "+" else ""
                val name    = anomaly.category.replace("_", " ").replaceFirstChar { it.uppercase() }
                Text(
                    text     = "${if (isSpike) "⚠" else "↓"} $name $sign${"%.0f".format(anomaly.deltaPct)}% vs 3m avg (£${"%.0f".format(anomaly.avg3m)} → £${"%.0f".format(anomaly.amount)})",
                    fontSize = 11.sp,
                    color    = if (isSpike) BankAmberFg else BankGreenFg,
                    modifier = Modifier.padding(horizontal = T.screenPadding)
                )
                Spacer(Modifier.height(3.dp))
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
}

// ── Net worth section ─────────────────────────────────────────

@Composable
private fun NetWorthSection(netWorth: BankingNetWorth) {
    val portfolioPct = if (netWorth.totalGbp > 0)
        (netWorth.portfolioGbp / netWorth.totalGbp) else 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 12.dp)
    ) {
        Text("NET WORTH", fontSize = 10.sp, fontWeight = FontWeight.Medium,
            color = T.Muted, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "£${"%.0f".format(netWorth.totalGbp)}",
            fontSize = 24.sp, fontWeight = FontWeight.Medium, color = T.Ink
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NwPill(
                label   = "Portfolio",
                value   = "£${"%.0f".format(netWorth.portfolioGbp)}",
                subPct  = "${"%.1f".format(portfolioPct * 100)}%",
                modifier = Modifier.weight(1f)
            )
            NwPill(
                label   = "Cash",
                value   = "£${"%.0f".format(netWorth.cashGbp)}",
                subPct  = "${"%.1f".format((1.0 - portfolioPct) * 100)}%",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        // Portfolio/cash split bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(T.Rule, RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(portfolioPct.toFloat())
                    .fillMaxHeight()
                    .background(BankGreen, RoundedCornerShape(3.dp))
            )
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
}

@Composable
private fun NwPill(label: String, value: String, subPct: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(0.5.dp, T.Rule, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 10.sp, color = T.Muted)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = T.Ink)
        Text(subPct, fontSize = 10.sp, color = T.Muted)
    }
}

// ── Helpers ───────────────────────────────────────────────────

private fun formatMonthLabel(selectedMonth: String?, currentMonth: String?): String {
    val month = selectedMonth ?: currentMonth?.take(7) ?: return "Current"
    return try {
        val ym = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"))
        "${ym.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${ym.year}"
    } catch (_: Exception) { month }
}

// Build a BankingCurrent-equivalent from flat month fields
private fun resolveCurrentFromFlat(summary: BankingSummaryResponse?): BankingCurrent? {
    val s = summary ?: return null
    val income   = s.incomeGbp ?: return null
    val expenses = s.expensesGbp ?: return null
    val surplus  = s.surplusGbp ?: return null
    return BankingCurrent(
        incomeGbp             = income,
        expensesGbp           = expenses,
        investmentFundingGbp  = s.investmentFundingGbp ?: 0.0,
        surplusGbp            = surplus,
        trueDiscretionaryGbp  = s.trueDiscretionaryGbp ?: surplus,
        topCategories         = s.topCategories ?: emptyMap(),
        anomalies             = emptyList(),
        transactionCount      = s.transactionCount ?: 0
    )
}