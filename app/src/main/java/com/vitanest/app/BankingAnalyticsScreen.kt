package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// BankingAnalyticsScreen — trend analysis from /banking/summary?month=all
// Sections: Surplus trend · Income vs expenses · Deployment · Net worth
// Jan bonus month labelled — excluded from direction context ☘️

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.vitanest.app.data.remote.BankingAllMonthsResponse
import com.vitanest.app.data.remote.BankingMonthSummary
import com.vitanest.app.data.remote.BankingSummaryResponse
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val AnalGreen   = Color(0xFF2D6A4F)
private val AnalRed     = Color(0xFFA32D2D)
private val AnalAmberFg = Color(0xFF854F0B)

// ── State + ViewModel ─────────────────────────────────────────

data class AnalyticsUiState(
    val allMonths: Map<String, BankingMonthSummary> = emptyMap(),
    val current:   BankingSummaryResponse?           = null,
    val isLoading: Boolean                           = true,
    val error:     String?                           = null
)

// ── ViewModel ─────────────────────────────────────────────────

class BankingAnalyticsViewModel(
    private val repository: VitaClawRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsUiState())
    val state: StateFlow<AnalyticsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val allDeferred     = async { repository.getBankingAllMonths() }
                val currentDeferred = async { repository.getBankingSummary() }
                val allResult       = allDeferred.await()
                val currentResult   = currentDeferred.await()
                _state.value = AnalyticsUiState(
                    allMonths = allResult.getOrNull()?.months ?: emptyMap(),
                    current   = currentResult.getOrNull(),
                    isLoading = false,
                    error     = if (allResult.isFailure && currentResult.isFailure)
                        "Could not load analytics data" else null
                )
            } catch (e: Exception) {
                _state.value = AnalyticsUiState(isLoading = false, error = e.message)
            }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────

@Composable
fun BankingAnalyticsScreen(
    navController: NavController,
    repository:    VitaClawRepository = VitaClawRepository()
) {
    val viewModel = remember { BankingAnalyticsViewModel(repository) }
    val state     by viewModel.state.collectAsState()

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
                    Text(
                        text       = "Analytics",
                        fontFamily = T.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 22.sp,
                        color      = T.Ink
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(thickness = T.heavyRule, color = T.Ink)
            }

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(
                            color       = AnalGreen,
                            modifier    = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                state.error != null && state.allMonths.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error ?: "Error", fontSize = 13.sp, color = T.Muted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp))
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { viewModel.load() },
                                shape = RoundedCornerShape(4.dp)) {
                                Text("Retry", fontSize = 12.sp, color = T.Ink)
                            }
                        }
                    }
                }
                else -> {
                    val months  = state.allMonths
                    val current = state.current
                    val trend   = current?.trend

                    LazyColumn(
                        modifier       = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
                    ) {
                        // Surplus trend
                        if (months.isNotEmpty() || trend != null) {
                            item {
                                SurplusTrendSection(
                                    months    = months,
                                    byMonth   = trend?.surplusByMonth ?: emptyMap(),
                                    direction = trend?.surplusDirection ?: "",
                                    avg3m     = trend?.surplus3mAvg ?: 0.0
                                )
                            }
                        }

                        // Income vs expenses
                        if (months.isNotEmpty()) {
                            item { IncomeVsExpensesSection(months) }
                        }

                        // Deployment
                        if (trend != null) {
                            item { DeploymentSection(trend = trend, current = current) }
                        }

                        // Net worth
                        current?.netWorth?.let { nw ->
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = T.screenPadding, vertical = 12.dp)
                                ) {
                                    Text("NET WORTH", fontSize = 10.sp, fontWeight = FontWeight.Medium,
                                        color = T.Muted, letterSpacing = 0.8.sp)
                                    Spacer(Modifier.height(6.dp))
                                    AnalyticRow("Total",     "£${"%.0f".format(nw.totalGbp)}")
                                    AnalyticRow("Portfolio", "£${"%.0f".format(nw.portfolioGbp)}")
                                    AnalyticRow("Cash",      "£${"%.0f".format(nw.cashGbp)}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Surplus trend section ─────────────────────────────────────

@Composable
private fun SurplusTrendSection(
    months:    Map<String, BankingMonthSummary>,
    byMonth:   Map<String, Double>,
    direction: String,
    avg3m:     Double
) {
    // Merge sources — prefer allMonths, fall back to surplusByMonth from trend
    val surplusData: Map<String, Double> = if (months.isNotEmpty()) {
        months.mapValues { it.value.surplusGbp }
    } else {
        byMonth
    }

    val maxSurplus = surplusData.values.maxOrNull() ?: 1.0
    val isBonus    = maxSurplus > avg3m * 2.5 // heuristic: Jan bonus detection

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 12.dp)
    ) {
        Text("SURPLUS TREND", fontSize = 10.sp, fontWeight = FontWeight.Medium,
            color = T.Muted, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(6.dp))

        if (direction.isNotEmpty()) {
            val (chipBg, chipFg) = when (direction.lowercase()) {
                "declining" -> Pair(Color(0xFFFCEBEB), AnalRed)
                "improving" -> Pair(Color(0xFFEAF3DE), AnalGreen)
                else        -> Pair(Color(0xFFF2EFE8), Color(0xFF888888))
            }
            Box(
                modifier = Modifier
                    .background(chipBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "${direction.replaceFirstChar { it.uppercase() }} · 3m avg £${"%.0f".format(avg3m)}",
                    fontSize = 10.sp, color = chipFg, fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        surplusData.entries.sortedBy { it.key }.forEachIndexed { index, (key, surplus) ->
            val fraction = (surplus / maxSurplus).toFloat().coerceIn(0f, 1f)
            val isLikelyBonus = isBonus && index == 0
            val monthLabel = formatAnalyticsMonth(key)

            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    monthLabel,
                    fontSize = 11.sp,
                    color    = T.Muted,
                    modifier = Modifier.width(28.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .background(Color(0xFFE8E5DC), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(
                                if (isLikelyBonus) Color(0xFF888888) else AnalGreen,
                                RoundedCornerShape(2.dp)
                            )
                    ) {
                        if (isLikelyBonus) {
                            Text(
                                "bonus",
                                fontSize = 9.sp,
                                color    = Color.White,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 4.dp)
                            )
                        }
                    }
                }
                Text(
                    "£${"%.0f".format(surplus)}",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color      = if (isLikelyBonus) Color(0xFF888888) else T.Ink,
                    modifier   = Modifier.width(52.dp),
                    textAlign  = TextAlign.End
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
}

// ── Income vs expenses section ────────────────────────────────

@Composable
private fun IncomeVsExpensesSection(months: Map<String, BankingMonthSummary>) {
    val maxIncome = months.values.maxOfOrNull { it.incomeGbp } ?: 1.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 12.dp)
    ) {
        Text("INCOME VS EXPENSES", fontSize = 10.sp, fontWeight = FontWeight.Medium,
            color = T.Muted, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(6.dp))

        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(color = AnalGreen, label = "Income")
            LegendDot(color = AnalRed,   label = "Expenses")
        }
        Spacer(Modifier.height(8.dp))

        months.entries.sortedBy { it.key }.forEach { (key, data) ->
            val incFrac = (data.incomeGbp / maxIncome).toFloat().coerceIn(0f, 1f)
            val expFrac = (data.expensesGbp / maxIncome).toFloat().coerceIn(0f, 1f)
            val label   = formatAnalyticsMonth(key)

            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(label, fontSize = 11.sp, color = T.Muted, modifier = Modifier.width(28.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DualBarRow(fraction = incFrac, color = AnalGreen, amount = data.incomeGbp)
                    DualBarRow(fraction = expFrac, color = AnalRed,   amount = data.expensesGbp)
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
}

@Composable
private fun DualBarRow(fraction: Float, color: Color, amount: Double) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .background(Color(0xFFE8E5DC), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
        Text(
            "£${"%.0f".format(amount)}",
            fontSize  = 10.sp,
            color     = T.Muted,
            modifier  = Modifier.width(46.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Text(label, fontSize = 10.sp, color = T.Muted)
    }
}

// ── Deployment section ────────────────────────────────────────

@Composable
private fun DeploymentSection(
    trend:   com.vitanest.app.data.remote.BankingTrend,
    current: BankingSummaryResponse
) {
    val deployPct = (trend.deploymentRatePct / 100.0).toFloat().coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 12.dp)
    ) {
        Text("INVESTMENT DEPLOYMENT", fontSize = 10.sp, fontWeight = FontWeight.Medium,
            color = T.Muted, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(8.dp))

        AnalyticRow("YTD deployed",     "£${"%.0f".format(trend.investmentFundingYtd)}")
        AnalyticRow("Monthly average",  "£${"%.0f".format(trend.investmentFundingMonthlyAvg)}")
        AnalyticRow("Rate of income",   "${"%.1f".format(trend.deploymentRatePct)}%")
        AnalyticRow("Direction",
            trend.surplusDirection.replaceFirstChar { it.uppercase() },
            valueColor = when (trend.surplusDirection.lowercase()) {
                "declining" -> AnalRed
                "improving" -> AnalGreen
                else        -> T.Ink
            }
        )

        Spacer(Modifier.height(8.dp))
        // Deployment rate bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0xFFE8E5DC), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(deployPct)
                    .fillMaxHeight()
                    .background(AnalGreen, RoundedCornerShape(2.dp))
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${"%.1f".format(trend.deploymentRatePct)}% of income deployed to investments",
            fontSize = 10.sp,
            color    = T.Muted
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
}

// ── Shared row ────────────────────────────────────────────────

@Composable
private fun AnalyticRow(
    label:      String,
    value:      String,
    valueColor: Color = T.Ink
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = T.Muted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
    HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
}

// ── Helpers ───────────────────────────────────────────────────

private fun formatAnalyticsMonth(key: String): String {
    return try {
        val parts = key.split("-")
        java.time.Month.of(parts[1].toInt()).name.take(3)
            .lowercase().replaceFirstChar { it.uppercase() }
    } catch (_: Exception) { key.take(3) }
}