package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// BuddieTradeScreen — dedicated Trade tab
// Sections: Budget → Active Trades (with feedback) → Candidates → Excluded
// Feedback: Bought/Skipped POST /buddie/trades/{id}/feedback?action=executed|skipped
// Status logic: active+window open → buttons · executed/skipped → badge · window closed → muted text ☘️

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.vitanest.app.data.remote.BuddieBudgetMonth
import com.vitanest.app.data.remote.BuddieBudgetResponse
import com.vitanest.app.data.remote.BuddieCandidateItem
import com.vitanest.app.data.remote.BuddieCandidatesResponse
import com.vitanest.app.data.remote.BuddieExcludedItem
import com.vitanest.app.data.remote.BuddieTradeItem
import com.vitanest.app.data.remote.BuddieTradesResponse
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ── Colours ───────────────────────────────────────────────────
private val TradeGreen      = Color(0xFF2D6A4F)
private val TradeGreenBg    = Color(0xFFEAF3DE)
private val TradeGreenFg    = Color(0xFF3B6D11)
private val TradeAmberBg    = Color(0xFFFAEEDA)
private val TradeAmberFg    = Color(0xFF854F0B)
private val TradeRedBg      = Color(0xFFFCEBEB)
private val TradeRedFg      = Color(0xFFA32D2D)
private val TradeMutedBg    = Color(0xFFF2EFE8)

// ── State ─────────────────────────────────────────────────────

data class BuddieTradeState(
    val trades:      BuddieTradesResponse?     = null,
    val budget:      BuddieBudgetResponse?     = null,
    val candidates:  BuddieCandidatesResponse? = null,
    val isLoading:   Boolean                   = true,
    val error:       String?                   = null
)

// ── ViewModel ─────────────────────────────────────────────────

class BuddieTradeViewModel(
    private val repository: VitaClawRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BuddieTradeState())
    val state: StateFlow<BuddieTradeState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val tradesDeferred     = async { repository.getBuddieTrades() }
                val budgetDeferred     = async { repository.getBuddieBudget(months = 3) }
                val candidatesDeferred = async { repository.getBuddieCandidates() }

                val trades     = tradesDeferred.await()
                val budget     = budgetDeferred.await()
                val candidates = candidatesDeferred.await()

                _state.value = BuddieTradeState(
                    trades     = trades.getOrNull(),
                    budget     = budget.getOrNull(),
                    candidates = candidates.getOrNull(),
                    isLoading  = false,
                    error      = if (trades.isFailure && budget.isFailure && candidates.isFailure)
                        "Could not load trade data" else null
                )
            } catch (e: Exception) {
                _state.value = BuddieTradeState(isLoading = false, error = e.message)
            }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────

@Composable
fun BuddieTradeScreen(
    navController: NavController,
    repository:    VitaClawRepository = VitaClawRepository()
) {
    val viewModel = remember { BuddieTradeViewModel(repository) }
    val state     by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 56.dp)   // reserve space for pinned bottom nav
        ) {

            // ── Header ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = T.screenPadding)
            ) {
                Spacer(modifier = Modifier.statusBarsPadding())
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "Trade",
                        fontFamily = T.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 22.sp,
                        color      = T.Ink
                    )
                    Box(
                        modifier = Modifier
                            .background(TradeMutedBg, RoundedCornerShape(4.dp))
                            .border(0.5.dp, T.Rule, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text      = "GHOST MODE",
                            fontSize  = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color     = T.Muted,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(thickness = T.heavyRule, color = T.Ink)
            }

            // ── Body ──────────────────────────────────────────
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(
                            color    = TradeGreen,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                state.error != null && state.trades == null -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text      = state.error ?: "Unknown error",
                                fontSize  = 13.sp,
                                color     = T.Muted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier  = Modifier.padding(horizontal = 32.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.load() },
                                shape   = RoundedCornerShape(4.dp)
                            ) {
                                Text("Retry", fontSize = 12.sp, color = T.Ink)
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier       = Modifier.fillMaxSize()
                    ) {

                        // ── Budget ────────────────────────────
                        state.budget?.let { budget ->
                            item { BudgetSection(budget) }
                        }

                        // ── Active trades ─────────────────────
                        state.trades?.let { tradesResp ->
                            val active = tradesResp.trades.filter { it.status == "active" }
                            if (active.isNotEmpty()) {
                                item {
                                    TradeSectionHeader(
                                        label = "Active trades",
                                        count = active.size
                                    )
                                }
                                items(active) { trade ->
                                    ActiveTradeCard(trade = trade, repository = repository)
                                    HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
                                }
                            }

                            // Past trades (non-active) — collapsed
                            val past = tradesResp.trades.filter { it.status != "active" }
                            if (past.isNotEmpty()) {
                                item { PastTradesSection(past) }
                            }
                        }

                        // ── Candidates ────────────────────────
                        state.candidates?.let { cands ->
                            item { CandidatesSection(cands) }
                        }
                    }
                }
            }
        }

        // ── Bottom nav ────────────────────────────────────────
        InkBottomNav(
            current       = "buddie_trade",
            navController = navController,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

// ── Budget section ────────────────────────────────────────────

@Composable
private fun BudgetSection(budget: BuddieBudgetResponse) {
    // Show current month budget first, then prior months
    val current = budget.budgets.firstOrNull { it.month == budget.currentMonth }
    val prior   = budget.budgets.filter { it.month != budget.currentMonth }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding)
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text      = "BUDGET",
            fontSize  = 10.sp,
            fontWeight = FontWeight.Medium,
            color     = T.Muted,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(8.dp))

        current?.let { BudgetMonthCard(it, isCurrent = true) }
        prior.forEach { month ->
            Spacer(Modifier.height(6.dp))
            BudgetMonthCard(month, isCurrent = false)
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
}

@Composable
private fun BudgetMonthCard(month: BuddieBudgetMonth, isCurrent: Boolean) {
    val spentPct = if (month.openingGbp > 0)
        (month.spentGbp / month.openingGbp).coerceIn(0.0, 1.0).toFloat()
    else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(
                width = if (isCurrent) 1.dp else 0.5.dp,
                color = if (isCurrent) TradeGreen else T.Rule,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text      = month.month,
                fontSize  = 12.sp,
                fontWeight = FontWeight.Medium,
                color     = T.Ink
            )
            if (isCurrent) {
                Text(
                    text     = "current",
                    fontSize = 10.sp,
                    color    = TradeGreenFg
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BudgetStat("Remaining", "£${"%.0f".format(month.remainingGbp)}", TradeGreen)
            BudgetStat("Spent",     "£${"%.2f".format(month.spentGbp)}",    T.Ink)
            BudgetStat("Earned",    "£${"%.2f".format(month.incomeEarnedGbp)}", T.Ink)
            BudgetStat("Target",    "£${"%.0f".format(month.incomeTargetGbp)}", T.Muted)
        }
        Spacer(Modifier.height(8.dp))
        // Spend progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(T.Rule, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(spentPct)
                    .height(3.dp)
                    .background(TradeGreen, RoundedCornerShape(2.dp))
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text     = "${"%.0f".format(spentPct * 100)}% of £${"%.0f".format(month.openingGbp)} budget used",
            fontSize = 10.sp,
            color    = T.Muted
        )
    }
}

@Composable
private fun BudgetStat(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, fontSize = 9.sp, color = T.Muted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

// ── Section header ────────────────────────────────────────────

@Composable
private fun TradeSectionHeader(label: String, count: Int, meta: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding)
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text      = label.uppercase(),
            fontSize  = 10.sp,
            fontWeight = FontWeight.Medium,
            color     = T.Muted,
            letterSpacing = 0.8.sp
        )
        Text(
            text     = meta ?: "$count trade${if (count != 1) "s" else ""}",
            fontSize = 10.sp,
            color    = T.Muted
        )
    }
}

// ── Active trade card ─────────────────────────────────────────


@Composable
private fun ActiveTradeCard(
    trade:      BuddieTradeItem,
    repository: VitaClawRepository
) {
    var cardStatus by remember(trade.id) { mutableStateOf(trade.status) }
    var isPosting  by remember { mutableStateOf(false) }
    var postError  by remember { mutableStateOf<String?>(null) }
    val scope      = rememberCoroutineScope()

    val windowOpen = remember(trade.expiresAt) {
        try {
            !LocalDate.parse(trade.expiresAt, DateTimeFormatter.ISO_LOCAL_DATE)
                .isBefore(LocalDate.now())
        } catch (_: Exception) { true }
    }

    val daysToExDiv = daysUntilTrade(trade.exDivDate)
    val (exDivBg, exDivFg) = when {
        daysToExDiv <= 3L  -> Pair(TradeRedBg,   TradeRedFg)
        daysToExDiv <= 10L -> Pair(TradeAmberBg, TradeAmberFg)
        else               -> Pair(TradeMutedBg, T.Muted)
    }

    fun postFeedback(action: String) {
        scope.launch {
            isPosting = true
            postError = null
            repository.postTradeFeedback(id = trade.id, action = action)
                .fold(
                    onSuccess = { response -> cardStatus = response.status },
                    onFailure = { err ->
                        postError = when (err.message) {
                            "window_closed"    -> "window_closed"
                            "already_recorded" -> "already_recorded"
                            else               -> err.message
                        }
                    }
                )
            isPosting = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 12.dp)
    ) {
        // Ticker + status badge
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = trade.ticker,
                fontSize   = 17.sp,
                fontWeight = FontWeight.Medium,
                color      = T.Ink
            )
            when (cardStatus) {
                "executed" -> Box(
                    modifier = Modifier
                        .background(TradeGreenBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text       = "Executed \u2713",
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color      = TradeGreenFg
                    )
                }
                "skipped" -> Box(
                    modifier = Modifier
                        .background(TradeMutedBg, RoundedCornerShape(10.dp))
                        .border(0.5.dp, T.Rule, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(text = "Skipped", fontSize = 10.sp, color = T.Muted)
                }
                else -> Box(
                    modifier = Modifier
                        .background(TradeGreenBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text       = "active",
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color      = TradeGreenFg
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Ex-div countdown
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text     = "Buy before ${formatTradeDateShort(trade.exDivDate)}",
                fontSize = 12.sp,
                color    = T.Muted
            )
            Box(
                modifier = Modifier
                    .background(exDivBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text       = "${daysToExDiv}d",
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color      = exDivFg
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${trade.shares.toInt()} shares @ \u00a3${"%.2f".format(trade.priceGbp)}",
                fontSize = 12.sp, color = T.Muted)
            Text("\u00b7", fontSize = 12.sp, color = T.Muted)
            Text("Capital: \u00a3${"%.2f".format(trade.capitalGbp)}",
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = T.Ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Income:", fontSize = 12.sp, color = T.Muted)
            Text("\u00a3${"%.2f".format(trade.projectedIncomeGbp)}",
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TradeGreen)
            Text("\u00b7 pays ${formatTradeDateShort(trade.paymentDate)}",
                fontSize = 12.sp, color = T.Muted)
        }

        Spacer(Modifier.height(10.dp))

        // Bottom row — ghost notice + action controls
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text      = "Paper trade \u2014 not executed",
                fontSize  = 10.sp,
                color     = T.Muted,
                fontStyle = FontStyle.Italic
            )
            when {
                cardStatus == "executed" || cardStatus == "skipped" -> { }
                !windowOpen || postError == "window_closed" -> {
                    Text(
                        text      = "Window closed \u2014 ex-div passed",
                        fontSize  = 10.sp,
                        color     = T.Muted,
                        fontStyle = FontStyle.Italic
                    )
                }
                postError == "already_recorded" -> {
                    Text(
                        text     = "Already recorded",
                        fontSize = 10.sp,
                        color    = T.Muted,
                        fontStyle = FontStyle.Italic
                    )
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick        = { postFeedback("executed") },
                            enabled        = !isPosting,
                            shape          = RoundedCornerShape(4.dp),
                            border         = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier       = Modifier.height(28.dp)
                        ) {
                            if (isPosting) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color       = T.Muted
                                )
                            } else {
                                Text("Bought", fontSize = 10.sp)
                            }
                        }
                        OutlinedButton(
                            onClick        = { postFeedback("skipped") },
                            enabled        = !isPosting,
                            shape          = RoundedCornerShape(4.dp),
                            border         = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier       = Modifier.height(28.dp)
                        ) {
                            Text("Skipped", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}


// ── Past trades (collapsed) ───────────────────────────────────

@Composable
private fun PastTradesSection(trades: List<BuddieTradeItem>) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = T.screenPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text      = "PAST TRADES",
                fontSize  = 10.sp,
                fontWeight = FontWeight.Medium,
                color     = T.Muted,
                letterSpacing = 0.8.sp
            )
            Text(
                text     = "${trades.size} · ${if (expanded) "▲" else "▼"}",
                fontSize = 10.sp,
                color    = T.Muted
            )
        }
        if (expanded) {
            trades.forEach { trade ->
                HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
                PastTradeRow(trade)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
    }
}

@Composable
private fun PastTradeRow(trade: BuddieTradeItem) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(trade.ticker, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = T.Ink)
            Text(trade.month, fontSize = 11.sp, color = T.Muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text     = "£${"%.2f".format(trade.capitalGbp)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color    = T.Ink
            )
            val actualOrProjected = trade.actualIncomeGbp ?: trade.projectedIncomeGbp
            Text(
                text     = "£${"%.2f".format(actualOrProjected)} income",
                fontSize = 11.sp,
                color    = if (trade.actualIncomeGbp != null) TradeGreen else T.Muted
            )
        }
    }
}

// ── Candidates section ────────────────────────────────────────

@Composable
private fun CandidatesSection(cands: BuddieCandidatesResponse) {
    var excludedExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.screenPadding)
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text      = "CANDIDATES · ${cands.month}",
                fontSize  = 10.sp,
                fontWeight = FontWeight.Medium,
                color     = T.Muted,
                letterSpacing = 0.8.sp
            )
            Text(
                text     = "${cands.passedCount} passed · ${cands.totalEvaluated} evaluated",
                fontSize = 10.sp,
                color    = T.Muted
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

        // Selected candidate first — prominent
        val selected  = cands.candidates.firstOrNull { it.selected }
        val remaining = cands.candidates.filter { !it.selected }

        selected?.let {
            CandidateCard(candidate = it, isSelected = true)
            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        }

        remaining.forEach { cand ->
            CandidateCard(candidate = cand, isSelected = false)
            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        }

        // Excluded — collapsible
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { excludedExpanded = !excludedExpanded }
                .padding(horizontal = T.screenPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text      = "EXCLUDED",
                fontSize  = 10.sp,
                fontWeight = FontWeight.Medium,
                color     = T.Muted,
                letterSpacing = 0.8.sp
            )
            Text(
                text     = "${cands.excludedCount} tickers · ${if (excludedExpanded) "▲" else "▼"}",
                fontSize = 10.sp,
                color    = T.Muted
            )
        }

        if (excludedExpanded) {
            cands.excluded.forEach { item ->
                ExcludedRow(item)
                HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        Spacer(Modifier.height(8.dp))
        Text(
            text     = "Generated ${cands.generatedAt}",
            fontSize = 10.sp,
            color    = T.Muted,
            modifier = Modifier.padding(horizontal = T.screenPadding).padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun CandidateCard(candidate: BuddieCandidateItem, isSelected: Boolean) {
    val (exDivBg, exDivFg) = when {
        candidate.daysToExDiv <= 3  -> Pair(TradeRedBg,   TradeRedFg)
        candidate.daysToExDiv <= 10 -> Pair(TradeAmberBg, TradeAmberFg)
        else                        -> Pair(TradeMutedBg, T.Muted)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(Color(0xFFF8FDF5))
                else Modifier
            )
            .padding(horizontal = T.screenPadding, vertical = 10.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text      = candidate.ticker,
                    fontSize  = if (isSelected) 16.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                    color     = T.Ink
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .background(TradeGreenBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text      = "Selected",
                            fontSize  = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color     = TradeGreenFg
                        )
                    }
                }
                if (candidate.confirmed) {
                    Box(
                        modifier = Modifier
                            .background(TradeMutedBg, RoundedCornerShape(10.dp))
                            .border(0.5.dp, T.Rule, RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("confirmed", fontSize = 9.sp, color = T.Muted)
                    }
                }
            }
            // Ex-div countdown badge
            Box(
                modifier = Modifier
                    .background(exDivBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text      = "${candidate.daysToExDiv}d",
                    fontSize  = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color     = exDivFg
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Capital:", fontSize = 11.sp, color = T.Muted)
                Text(
                    "£${"%.2f".format(candidate.capitalGbp)}",
                    fontSize = 11.sp, fontWeight = FontWeight.Medium, color = T.Ink
                )
                Text("·", fontSize = 11.sp, color = T.Muted)
                Text("Income:", fontSize = 11.sp, color = T.Muted)
                Text(
                    "£${"%.2f".format(candidate.projectedIncome)}",
                    fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TradeGreen
                )
            }
            Box(
                modifier = Modifier
                    .background(TradeMutedBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text  = "${"%.1f".format(candidate.annualYield)}% yield",
                    fontSize = 10.sp,
                    color = T.Muted
                )
            }
        }

        Text(
            text     = "Ex-div ${formatTradeDateShort(candidate.exDivDate)}",
            fontSize = 10.sp,
            color    = T.Muted,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ExcludedRow(item: BuddieExcludedItem) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        Text(
            text      = item.ticker,
            fontSize  = 12.sp,
            fontWeight = FontWeight.Medium,
            color     = T.Ink,
            modifier  = Modifier.width(56.dp)
        )
        Text(
            text     = item.reason,
            fontSize = 11.sp,
            color    = T.Muted,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

// ── Date helpers ──────────────────────────────────────────────

private fun daysUntilTrade(dateStr: String): Long {
    return try {
        val target = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        ChronoUnit.DAYS.between(LocalDate.now(), target).coerceAtLeast(0)
    } catch (_: Exception) { 0L }
}

private fun formatTradeDateShort(dateStr: String): String {
    return try {
        val d = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        val month = d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        "${d.dayOfMonth} $month"
    } catch (_: Exception) { dateStr }
}