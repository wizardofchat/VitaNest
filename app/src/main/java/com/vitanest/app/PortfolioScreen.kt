package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// PortfolioScreen — Finance Analytics repurposed as full Portfolio view.
// Adds: AllocationBar (income_type default, tap to cycle), HoldingsList,
//       MarketImpactCard stub. Removes: Raw data collapsible.
// All existing chart/intelligence/alert/synthesis composables untouched.
// Entry point: Home Finance petal → finance_analytics route. ☘️

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vitanest.app.data.remote.FinanceAnalyticsResponse
import com.vitanest.app.data.remote.FinanceAnalyticsSeries
import com.vitanest.app.data.remote.FinanceAnalyticsThresholds
import com.vitanest.app.data.remote.FinanceCorrelations
import com.vitanest.app.data.remote.FinanceHealthState
import com.vitanest.app.data.remote.FinancePatterns
import com.vitanest.app.data.remote.FinanceSynthesisResponse
import com.vitanest.app.data.remote.GrowthResponse
import com.vitanest.app.data.remote.GrowthSeries
import com.vitanest.app.data.remote.WhoopAlert
import com.vitanest.app.data.repository.VitaClawRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.vitanest.app.data.remote.PiesResponse
import com.vitanest.app.data.remote.LensBreakdownItem
import com.vitanest.app.data.remote.PortfolioResponse
import com.vitanest.app.data.remote.Position

// ── Light palette — matches GrowthScreen ─────────────────────

private val Paper        = Color(0xFFF2EFE8)
private val White        = Color(0xFFFFFFFF)
private val NearBlack    = Color(0xFF111111)
private val MidGrey      = Color(0xFF888880)
private val LightRule    = Color(0xFFC8C4BB)
private val GreenDark    = Color(0xFF2D6A4F)
private val AmberWarm    = Color(0xFFBA7517)
private val BlueInk      = Color(0xFF185FA5)
private val RedInk       = Color(0xFFA32D2D)
private val AmberAlertBg = Color(0xFFFDF5E4)
private val AmberAlertBd = Color(0xFFD4A017)
private val AmberAlertTx = Color(0xFF854F0B)
private val AmberBadgeBg = Color(0xFF6B4A00)
private val AmberBadgeTx = Color(0xFFFFCF7A)
private val RedAlertBg   = Color(0xFFFCEBEB)
private val RedAlertBd   = Color(0xFFA32D2D)
private val RedAlertTx   = Color(0xFFA32D2D)
private val RedBadgeBg   = Color(0xFF8B1A1A)
private val RedBadgeTx   = Color(0xFFFF9E9E)

private enum class FinanceChart { EQUITY, PNL, INCOME }

private fun FinanceAnalyticsSeries.equityPoints() =
    dates.zip(equityGbp).mapNotNull { (d, v) -> v?.let { d to it } }
private fun FinanceAnalyticsSeries.pnlPoints() =
    dates.zip(pnlGbp).mapNotNull { (d, v) -> v?.let { d to it } }
private fun FinanceAnalyticsSeries.incomePoints() =
    dates.zip(income30dGbp).mapNotNull { (d, v) -> v?.let { d to it } }

private fun stripMarkdown(text: String): String = text
    .replace(Regex("#{1,6}\\s*"), "")
    .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    .replace(Regex("\\*(.+?)\\*"), "$1")
    .trim()

private fun Color.toHexStr(): String {
    val r = (red * 255).toInt(); val g = (green * 255).toInt(); val b = (blue * 255).toInt()
    return "#%02X%02X%02X".format(r, g, b)
}

private fun financeCorrDisplay(value: Double): Pair<Color, String> {
    val a = abs(value)
    return when {
        a >= 0.7 -> Pair(if (value >= 0) GreenDark else RedInk, "strong")
        a >= 0.4 -> Pair(AmberWarm, "moderate")
        else     -> Pair(MidGrey,   "weak")
    }
}

private fun financePatternName(raw: String): String = when (raw) {
    "income_below_target"     -> "Income below target"
    "income_compression"      -> "Income compression"
    "yield_concentration"     -> "Yield concentration"
    "contribution_dependency" -> "Contribution dependency"
    "pnl_volatility"          -> "P&L volatility"
    else -> raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

// ── ViewModel ─────────────────────────────────────────────────

class FinanceAnalyticsViewModel(private val repository: VitaClawRepository) : ViewModel() {
    private val _finance       = MutableStateFlow<FinanceAnalyticsResponse?>(null)
    private val _isLoading     = MutableStateFlow(true)
    private val _error         = MutableStateFlow<String?>(null)
    private val _range         = MutableStateFlow("14d")
    private val _synthesis     = MutableStateFlow<FinanceSynthesisResponse?>(null)
    private val _synLoading    = MutableStateFlow(false)
    private val _synError      = MutableStateFlow<String?>(null)
    private val _growth        = MutableStateFlow<GrowthResponse?>(null)
    private val _growthLoading = MutableStateFlow(false)
    private val _growthLoaded  = MutableStateFlow(false)
    private val synthesisCache = mutableMapOf<String, FinanceSynthesisResponse>()
    // ── New: pies + portfolio ────────────────────────────────────
    private val _pies          = MutableStateFlow<PiesResponse?>(null)
    private val _portfolio     = MutableStateFlow<PortfolioResponse?>(null)

    val finance:       StateFlow<FinanceAnalyticsResponse?> = _finance
    val isLoading:     StateFlow<Boolean>                   = _isLoading
    val error:         StateFlow<String?>                   = _error
    val range:         StateFlow<String>                    = _range
    val synthesis:     StateFlow<FinanceSynthesisResponse?> = _synthesis
    val synLoading:    StateFlow<Boolean>                   = _synLoading
    val synError:      StateFlow<String?>                   = _synError
    val growth:        StateFlow<GrowthResponse?>           = _growth
    val growthLoading: StateFlow<Boolean>                   = _growthLoading
    val growthLoaded:  StateFlow<Boolean>                   = _growthLoaded
    val pies:          StateFlow<PiesResponse?>             = _pies
    val portfolio:     StateFlow<PortfolioResponse?>        = _portfolio

    init { loadAnalytics("14d"); loadPortfolioData() }

    fun loadAnalytics(range: String) {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null; _range.value = range
            _synthesis.value = synthesisCache[range]
            repository.getFinanceAnalytics(range).fold(
                onSuccess = { _finance.value = it },
                onFailure = { _error.value = it.message }
            )
            _isLoading.value = false
        }
    }

    fun loadSynthesis() {
        val r = _range.value
        synthesisCache[r]?.let { _synthesis.value = it; return }
        viewModelScope.launch {
            _synLoading.value = true; _synError.value = null
            repository.getFinanceSynthesis(r).fold(
                onSuccess = { synthesisCache[r] = it; _synthesis.value = it },
                onFailure = { _synError.value = it.message }
            )
            _synLoading.value = false
        }
    }

    fun loadGrowth() {
        if (_growthLoaded.value) return
        viewModelScope.launch {
            _growthLoading.value = true
            repository.getGrowth(days = 30).fold(
                onSuccess = { _growth.value = it; _growthLoaded.value = true },
                onFailure = { }
            )
            _growthLoading.value = false
        }
    }

    private fun loadPortfolioData() {
        viewModelScope.launch { repository.getPortfolioPies().onSuccess { _pies.value = it } }
        viewModelScope.launch { repository.getPortfolio().onSuccess { _portfolio.value = it } }
    }
}

// ── Screen ────────────────────────────────────────────────────

@Composable
fun PortfolioScreen(navController: NavController, repository: VitaClawRepository) {
    val vm: FinanceAnalyticsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FinanceAnalyticsViewModel(repository) as T
        }
    )
    val finance       by vm.finance.collectAsState()
    val isLoading     by vm.isLoading.collectAsState()
    val error         by vm.error.collectAsState()
    val range         by vm.range.collectAsState()
    val synthesis     by vm.synthesis.collectAsState()
    val synLoading    by vm.synLoading.collectAsState()
    val synError      by vm.synError.collectAsState()
    val growth        by vm.growth.collectAsState()
    val pies          by vm.pies.collectAsState()
    val portfolio     by vm.portfolio.collectAsState()
    var activeChart   by remember { mutableStateOf(FinanceChart.EQUITY) }

    Scaffold(
        containerColor = Paper,
        bottomBar = {
            InkBottomNav(
                current       = "health",   // Growth tab is the parent of Portfolio
                navController = navController,
                modifier      = Modifier.navigationBarsPadding()
            )
        },
        topBar = {
            Column(Modifier.fillMaxWidth().background(White).statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                            tint = NearBlack, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Portfolio", fontSize = 20.sp, fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Serif, color = NearBlack)
                        Text(when (range) { "14d" -> "14-day view"; "30d" -> "30-day view"; else -> "Full history" },
                            fontSize = 11.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
                    }
                }
                HorizontalDivider(color = NearBlack, thickness = 1.dp)
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("14d" to "14d", "30d" to "30d", "all" to "All").forEach { (key, label) ->
                        val sel = range == key
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (sel) NearBlack else White)
                                .border(0.5.dp, if (sel) NearBlack else LightRule, RoundedCornerShape(20.dp))
                                .clickable { vm.loadAnalytics(key) }
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                        ) {
                            Text(label, fontSize = 11.sp,
                                color = if (sel) White else MidGrey,
                                fontFamily = FontFamily.SansSerif)
                        }
                    }
                }
                HorizontalDivider(color = LightRule, thickness = 0.5.dp)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState()).padding(bottom = 32.dp)
        ) {
            when {
                isLoading -> Box(Modifier.fillMaxWidth().height(300.dp), Alignment.Center) {
                    CircularProgressIndicator(color = NearBlack, strokeWidth = 1.5.dp)
                }
                error != null -> FinanceErrorCard(error ?: "Unknown error")
                finance != null -> {
                    val data = finance!!
                    Spacer(Modifier.height(8.dp))
                    // ── KEPT: stat strip + chart + intelligence + alerts ──
                    FinanceStatStrip(data, activeChart) { activeChart = it }
                    data.series?.let { FinanceChart(it, activeChart, data.thresholds) }
                    // ── NEW: allocation bar (income_type default, tap to cycle) ──
                    pies?.let { AllocationBar(it) }
                    data.patterns?.let { FinanceIntelligenceTile(it); Spacer(Modifier.height(6.dp)) }
                    val warnings = data.alerts.filter { it.severity != "ok" }
                    if (warnings.isNotEmpty()) {
                        SectionLabel("Alerts")
                        warnings.forEach { FinanceAlertCard(it); Spacer(Modifier.height(6.dp)) }
                    }
                    Spacer(Modifier.height(4.dp))
                    // ── NEW: holdings list (replaces raw data drawer) ──
                    HoldingsList(
                        portfolio  = portfolio,
                        onDetailClick = { navController.navigate("portfolio_detail") }
                    )
                    // ── NEW: market impact entry card ──
                    MarketImpactCard()
                    Spacer(Modifier.height(8.dp))
                    // ── KEPT: Buddie insight ──
                    val ctx = LocalContext.current
                    FinanceInsightSection(synthesis, synLoading, synError, ctx) { vm.loadSynthesis() }
                }
            }
        }
    }
}

// ── Allocation bar ───────────────────────────────────────────

private enum class AllocLens { INCOME_TYPE, GEOGRAPHY, ASSET_CLASS, CURRENCY }

private fun AllocLens.label() = when (this) {
    AllocLens.INCOME_TYPE  -> "Income type"
    AllocLens.GEOGRAPHY    -> "Geography"
    AllocLens.ASSET_CLASS  -> "Asset class"
    AllocLens.CURRENCY     -> "Currency"
}

private fun AllocLens.next() = when (this) {
    AllocLens.INCOME_TYPE  -> AllocLens.GEOGRAPHY
    AllocLens.GEOGRAPHY    -> AllocLens.ASSET_CLASS
    AllocLens.ASSET_CLASS  -> AllocLens.CURRENCY
    AllocLens.CURRENCY     -> AllocLens.INCOME_TYPE
}

// Stable colour per key so the bar reads consistently
private fun allocColor(key: String): Color = when (key) {
    "income"        -> GreenDark
    "covered_call"  -> BlueInk
    "growth"        -> AmberWarm
    "commodity"     -> Color(0xFF7B5C2E)
    "mixed"         -> MidGrey
    "us"            -> Color(0xFF185FA5)
    "uk"            -> Color(0xFF2D6A4F)
    "global"        -> Color(0xFF854F0B)
    "em"            -> Color(0xFF6B4A00)
    "etf"           -> BlueInk
    "trust"         -> Color(0xFF2D6A4F)
    "reit"          -> Color(0xFF854F0B)
    "GBP"           -> GreenDark
    "USD"           -> BlueInk
    else            -> MidGrey
}

private fun allocLabel(key: String): String = when (key) {
    "income"       -> "Income"
    "covered_call" -> "Covered call"
    "growth"       -> "Growth"
    "commodity"    -> "Commodity"
    "mixed"        -> "Mixed"
    "us"           -> "US"
    "uk"           -> "UK"
    "global"       -> "Global"
    "em"           -> "EM"
    "etf"          -> "ETF"
    "trust"        -> "Trust"
    "reit"         -> "REIT"
    else           -> key.replaceFirstChar { it.uppercase() }
}

@Composable
private fun AllocationBar(pies: PiesResponse) {
    var lens by remember { mutableStateOf(AllocLens.INCOME_TYPE) }

    val breakdown: Map<String, LensBreakdownItem>? = when (lens) {
        AllocLens.INCOME_TYPE -> pies.incomeTypeBreakdown
        AllocLens.GEOGRAPHY   -> pies.geographyBreakdown
        AllocLens.ASSET_CLASS -> pies.assetClassBreakdown
        AllocLens.CURRENCY    -> pies.currencyBreakdown
    }

    if (breakdown.isNullOrEmpty()) return

    // Sort by weight descending for consistent bar order
    val sorted = breakdown.entries.sortedByDescending { it.value.weightPct }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(White)
            .border(0.5.dp, LightRule, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        // Header — tap to cycle lens
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clickable { lens = lens.next() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = lens.label().uppercase(),
                fontSize   = 9.sp,
                color      = MidGrey,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.5.sp
            )
            Text(
                text       = "tap to change ↻",
                fontSize   = 8.sp,
                color      = MidGrey.copy(alpha = 0.7f),
                fontFamily = FontFamily.SansSerif
            )
        }

        Spacer(Modifier.height(8.dp))

        // Segmented bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        ) {
            sorted.forEach { (key, item) ->
                Box(
                    modifier = Modifier
                        .weight(item.weightPct.toFloat().coerceAtLeast(0.5f))
                        .fillMaxHeight()
                        .background(allocColor(key))
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Legend — two per row
        val chunked = sorted.chunked(2)
        chunked.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { (key, item) ->
                    Row(
                        modifier              = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(allocColor(key))
                        )
                        Text(
                            text       = "${allocLabel(key)} ${"%.0f".format(item.weightPct)}%",
                            fontSize   = 9.sp,
                            color      = MidGrey,
                            fontFamily = FontFamily.SansSerif,
                            maxLines   = 1
                        )
                    }
                }
                // pad if odd number
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(3.dp))
        }
    }
}

// ── Holdings list ─────────────────────────────────────────────

@Composable
private fun HoldingsList(
    portfolio:    PortfolioResponse?,
    onDetailClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(White)
            .border(0.5.dp, LightRule, RoundedCornerShape(8.dp))
    ) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text          = "HOLDINGS",
                fontSize      = 9.sp,
                color         = MidGrey,
                fontFamily    = FontFamily.SansSerif,
                letterSpacing = 0.5.sp
            )
            if (portfolio != null) {
                Text(
                    text       = "${portfolio.positions.size} positions",
                    fontSize   = 9.sp,
                    color      = MidGrey,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        HorizontalDivider(color = LightRule, thickness = 0.5.dp)

        when {
            portfolio == null -> {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color       = NearBlack,
                        strokeWidth = 1.5.dp,
                        modifier    = Modifier.size(16.dp)
                    )
                }
            }
            portfolio.positions.isEmpty() -> {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = "No positions found",
                        fontSize   = 11.sp,
                        color      = MidGrey,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
            else -> {
                // Max value for bar width normalisation
                val maxValue = portfolio.positions.maxOfOrNull { it.marketValue } ?: 1.0

                portfolio.positions
                    .sortedByDescending { it.marketValue }
                    .forEachIndexed { index, position ->
                        HoldingRow(
                            position  = position,
                            maxValue  = maxValue,
                            onClick   = onDetailClick
                        )
                        if (index < portfolio.positions.size - 1) {
                            HorizontalDivider(
                                color     = LightRule,
                                thickness = 0.5.dp,
                                modifier  = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
            }
        }

        // Footer — CSV export
        HorizontalDivider(color = LightRule, thickness = 0.5.dp)
        val ctx = LocalContext.current
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clickable { onDetailClick() }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = "Pies · Holdings · Lens →",
                fontSize   = 10.sp,
                color      = NearBlack,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun HoldingRow(
    position: Position,
    maxValue: Double,
    onClick:  () -> Unit
) {
    val pnlColor = when {
        position.pnlPct > 0  -> GreenDark
        position.pnlPct < 0  -> RedInk
        else                  -> MidGrey
    }
    val barFraction = (position.marketValue / maxValue).toFloat().coerceIn(0.02f, 1f)

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Ticker icon
        Box(
            modifier         = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Paper),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = position.ticker.take(4).replace("l_EQ", "").take(4),
                fontSize   = 8.sp,
                fontWeight = FontWeight.Medium,
                color      = NearBlack,
                fontFamily = FontFamily.SansSerif,
                maxLines   = 1
            )
        }

        // Middle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = position.ticker.replace("l_EQ", "").replace(".L", ""),
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                color      = NearBlack,
                fontFamily = FontFamily.Serif,
                maxLines   = 1
            )
            Text(
                text       = "${"%.0f".format(position.quantity)} shares",
                fontSize   = 10.sp,
                color      = MidGrey,
                fontFamily = FontFamily.SansSerif,
                maxLines   = 1
            )
            // Proportional bar
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(LightRule)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFraction)
                        .fillMaxHeight()
                        .background(GreenDark)
                )
            }
        }

        // Right — value + P&L
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text       = "£${"%.0f".format(position.marketValue)}",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                color      = NearBlack,
                fontFamily = FontFamily.Serif
            )
            Text(
                text       = "${if (position.pnlPct >= 0) "+" else ""}${"%.1f".format(position.pnlPct)}%",
                fontSize   = 10.sp,
                color      = pnlColor,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

// ── Market Impact entry card ──────────────────────────────────

@Composable
private fun MarketImpactCard() {
    // Stub — wires to intelligence/projections/latest when built
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(White)
            .border(0.5.dp, LightRule, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text       = "MARKET IMPACT",
                fontSize   = 9.sp,
                color      = MidGrey,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.5.sp
            )
            Text(
                text       = "Projection screen coming soon",
                fontSize   = 11.sp,
                color      = NearBlack,
                fontFamily = FontFamily.SansSerif
            )
        }
        Text(
            text       = "→",
            fontSize   = 14.sp,
            color      = MidGrey,
            fontFamily = FontFamily.SansSerif
        )
    }
}

// ── Stat strip ────────────────────────────────────────────────

@Composable
private fun FinanceStatStrip(data: FinanceAnalyticsResponse, activeChart: FinanceChart, onSelect: (FinanceChart) -> Unit) {
    val incomeColor = if (data.income30dGbp >= data.incomeTargetGbp * 0.8) GreenDark else AmberWarm
    val pnlColor    = if (data.pnlGbp >= 900.0) GreenDark else AmberWarm
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FinanceStatCard(Modifier.weight(1f), "Equity",
            "£${"%.0f".format(data.equityGbp)}", GreenDark, activeChart == FinanceChart.EQUITY) { onSelect(FinanceChart.EQUITY) }
        FinanceStatCard(Modifier.weight(1f), "P&L",
            "£${"%.0f".format(data.pnlGbp)}", pnlColor, activeChart == FinanceChart.PNL) { onSelect(FinanceChart.PNL) }
        FinanceStatCard(Modifier.weight(1f), "Income 30d",
            "£${"%.2f".format(data.income30dGbp)}", incomeColor, activeChart == FinanceChart.INCOME) { onSelect(FinanceChart.INCOME) }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FinanceStatCard(Modifier.weight(1f), "Gap to £${"%.0f".format(data.incomeTargetGbp)} target",
            "−£${"%.2f".format(data.incomeGapGbp)}", RedInk, false) {}
        FinanceStatCard(Modifier.weight(1f), "Range", data.range ?: "—", MidGrey, false) {}
    }
}

@Composable
private fun FinanceStatCard(modifier: Modifier, label: String, value: String, valueColor: Color, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(White)
            .border(if (selected) 1.5.dp else 0.5.dp, if (selected) NearBlack else LightRule, RoundedCornerShape(8.dp))
            .clickable { onClick() }.padding(horizontal = 9.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Serif,
            color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 8.sp, color = MidGrey, fontFamily = FontFamily.SansSerif,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Chart ─────────────────────────────────────────────────────

@Composable
private fun FinanceChart(series: FinanceAnalyticsSeries, activeChart: FinanceChart, thresholds: FinanceAnalyticsThresholds?) {
    val incomeTarget = thresholds?.incomeTargetGbp ?: 75.0
    val pnlThreshold = 900.0
    val (title, subtitle, pts) = when (activeChart) {
        FinanceChart.EQUITY -> Triple("Equity GBP", "dotted line = invested baseline", series.equityPoints().size)
        FinanceChart.PNL    -> Triple("P&L GBP — daily", "green ≥ £${"%.0f".format(pnlThreshold)} · amber below", series.pnlPoints().size)
        FinanceChart.INCOME -> Triple("Income 30d GBP — rolling", "red dotted = £${"%.0f".format(incomeTarget)} target", series.incomePoints().size)
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 10.dp)
            .clip(RoundedCornerShape(8.dp)).background(White)
            .border(0.5.dp, LightRule, RoundedCornerShape(8.dp)).padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = NearBlack, fontFamily = FontFamily.Serif)
            Text("$pts pts", fontSize = 9.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
        }
        Spacer(Modifier.height(10.dp))
        when (activeChart) {
            FinanceChart.EQUITY -> {
                val points = series.equityPoints()
                if (points.size < 2) FinanceChartEmpty() else {
                    val invested = series.depositsMtd.filterNotNull().maxOrNull()
                        ?.let { points.first().second - it } ?: points.first().second
                    Canvas(Modifier.fillMaxWidth().height(130.dp)) { drawEquityChart(points, invested) }
                }
            }
            FinanceChart.PNL -> {
                val points = series.pnlPoints()
                if (points.isEmpty()) FinanceChartEmpty() else
                    Canvas(Modifier.fillMaxWidth().height(130.dp)) { drawPnlBars(points, pnlThreshold) }
            }
            FinanceChart.INCOME -> {
                val points = series.incomePoints()
                if (points.size < 2) FinanceChartEmpty() else
                    Canvas(Modifier.fillMaxWidth().height(130.dp)) { drawIncomeChart(points, incomeTarget) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 8.sp, color = MidGrey.copy(alpha = 0.8f), fontFamily = FontFamily.SansSerif)
        Spacer(Modifier.height(2.dp))
        Text("Tap a stat card above to switch view", fontSize = 8.sp, color = MidGrey.copy(alpha = 0.5f), fontFamily = FontFamily.SansSerif)
    }
}

@Composable
private fun FinanceChartEmpty() {
    Box(Modifier.fillMaxWidth().height(130.dp), Alignment.Center) {
        Text("Not enough data", fontSize = 11.sp, color = MidGrey)
    }
}

private fun DrawScope.drawEquityChart(points: List<Pair<String, Double>>, invested: Double) {
    val n = points.size; val padL = 36.dp.toPx(); val padR = 6.dp.toPx()
    val padT = 8.dp.toPx(); val padB = 14.dp.toPx()
    val iw = size.width - padL - padR; val ih = size.height - padT - padB
    val xStep = if (n > 1) iw / (n - 1).toFloat() else iw
    val values = points.map { it.second }
    val yMin = minOf(values.min(), invested) * 0.998; val yMax = values.max() * 1.002
    val yRange = (yMax - yMin).coerceAtLeast(1.0)
    fun xp(i: Int) = padL + i * xStep
    fun yp(v: Double) = (padT + ih - ((v - yMin) / yRange * ih)).toFloat().coerceIn(padT, padT + ih)
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply { textSize = 8.dp.toPx(); color = android.graphics.Color.parseColor(MidGrey.toHexStr()); textAlign = android.graphics.Paint.Align.RIGHT }
        canvas.nativeCanvas.drawText("£${"%.0f".format(yMax)}", padL - 4.dp.toPx(), padT + 7.dp.toPx(), paint)
        canvas.nativeCanvas.drawText("£${"%.0f".format(yMin)}", padL - 4.dp.toPx(), padT + ih + 5.dp.toPx(), paint)
    }
    drawIntoCanvas { canvas ->
        val p = android.graphics.Paint().apply { color = android.graphics.Color.parseColor(LightRule.toHexStr()); strokeWidth = 1.dp.toPx(); style = android.graphics.Paint.Style.STROKE; pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f) }
        canvas.nativeCanvas.drawLine(padL, yp(invested), size.width - padR, yp(invested), p)
    }
    val path = Path()
    points.forEachIndexed { i, (_, v) -> val x = xp(i); val y = yp(v); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
    drawPath(path, GreenDark, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    points.forEachIndexed { i, (_, v) -> drawCircle(GreenDark, 3.dp.toPx(), Offset(xp(i), yp(v))) }
}

private fun DrawScope.drawPnlBars(points: List<Pair<String, Double>>, pnlThreshold: Double) {
    val n = points.size; val padL = 36.dp.toPx(); val padR = 6.dp.toPx()
    val padT = 8.dp.toPx(); val padB = 14.dp.toPx()
    val iw = size.width - padL - padR; val ih = size.height - padT - padB
    val values = points.map { it.second }
    val yMin = 0.0; val yMax = values.max() * 1.05; val yRange = (yMax - yMin).coerceAtLeast(1.0)
    val slotW = iw / n; val barW = slotW * 0.7f; val gapW = slotW * 0.15f
    fun xp(i: Int) = padL + i * slotW + gapW
    fun yp(v: Double) = (padT + ih - ((v - yMin) / yRange * ih)).toFloat().coerceIn(padT, padT + ih)
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply { textSize = 8.dp.toPx(); color = android.graphics.Color.parseColor(MidGrey.toHexStr()); textAlign = android.graphics.Paint.Align.RIGHT }
        canvas.nativeCanvas.drawText("£${"%.0f".format(yMax)}", padL - 4.dp.toPx(), padT + 7.dp.toPx(), paint)
        canvas.nativeCanvas.drawText("£0", padL - 4.dp.toPx(), padT + ih + 5.dp.toPx(), paint)
    }
    drawIntoCanvas { canvas ->
        val p = android.graphics.Paint().apply { color = android.graphics.Color.parseColor(AmberWarm.toHexStr()); strokeWidth = 1.2.dp.toPx(); style = android.graphics.Paint.Style.STROKE; pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f) }
        canvas.nativeCanvas.drawLine(padL, yp(pnlThreshold), size.width - padR, yp(pnlThreshold), p)
    }
    points.forEachIndexed { i, (_, v) ->
        val barColor = if (v >= pnlThreshold) GreenDark else AmberWarm
        drawRect(color = barColor, topLeft = Offset(xp(i), yp(v)), size = Size(barW, padT + ih - yp(v)))
    }
}

private fun DrawScope.drawIncomeChart(points: List<Pair<String, Double>>, targetIncome: Double) {
    val n = points.size; val padL = 36.dp.toPx(); val padR = 6.dp.toPx()
    val padT = 8.dp.toPx(); val padB = 14.dp.toPx()
    val iw = size.width - padL - padR; val ih = size.height - padT - padB
    val xStep = if (n > 1) iw / (n - 1).toFloat() else iw
    val values = points.map { it.second }
    val yMin = 0.0; val yMax = maxOf(values.max(), targetIncome) * 1.1; val yRange = (yMax - yMin).coerceAtLeast(1.0)
    fun xp(i: Int) = padL + i * xStep
    fun yp(v: Double) = (padT + ih - ((v - yMin) / yRange * ih)).toFloat().coerceIn(padT, padT + ih)
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply { textSize = 8.dp.toPx(); color = android.graphics.Color.parseColor(MidGrey.toHexStr()); textAlign = android.graphics.Paint.Align.RIGHT }
        canvas.nativeCanvas.drawText("£${"%.0f".format(yMax)}", padL - 4.dp.toPx(), padT + 7.dp.toPx(), paint)
        canvas.nativeCanvas.drawText("£0", padL - 4.dp.toPx(), padT + ih + 5.dp.toPx(), paint)
    }
    drawIntoCanvas { canvas ->
        val p = android.graphics.Paint().apply { color = android.graphics.Color.parseColor(RedInk.toHexStr()); strokeWidth = 1.2.dp.toPx(); style = android.graphics.Paint.Style.STROKE; pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f) }
        canvas.nativeCanvas.drawLine(padL, yp(targetIncome), size.width - padR, yp(targetIncome), p)
    }
    val path = Path()
    points.forEachIndexed { i, (_, v) -> val x = xp(i); val y = yp(v); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
    drawPath(path, BlueInk, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    points.forEachIndexed { i, (_, v) -> drawCircle(BlueInk, 3.dp.toPx(), Offset(xp(i), yp(v))) }
}

// ── Intelligence tile ─────────────────────────────────────────

@Composable
private fun FinanceIntelligenceTile(patterns: FinancePatterns) {
    val hasState = patterns.healthState != null
    val hasPatterns = patterns.detected.isNotEmpty()
    val hasCorr = patterns.correlations != null
    if (!hasState && !hasPatterns && !hasCorr) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(8.dp)).background(White)
            .border(0.5.dp, LightRule, RoundedCornerShape(8.dp)).padding(14.dp)
    ) {
        Text("Finance intelligence", fontSize = 9.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
        patterns.healthState?.let { state ->
            Spacer(Modifier.height(8.dp))
            val stateColor = when (state.zone) { "green" -> GreenDark; "amber" -> AmberWarm; else -> RedInk }
            Text(state.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = stateColor, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(2.dp))
            Text(state.reason, fontSize = 10.sp, color = MidGrey, fontFamily = FontFamily.SansSerif, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (hasPatterns) {
            Spacer(Modifier.height(10.dp)); HorizontalDivider(color = LightRule, thickness = 0.5.dp); Spacer(Modifier.height(8.dp))
            Text("Patterns", fontSize = 9.sp, color = MidGrey, fontFamily = FontFamily.SansSerif); Spacer(Modifier.height(5.dp))
            patterns.detected.take(3).forEach { pattern ->
                val isCritical = pattern.severity == "critical"
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (isCritical) RedBadgeBg else AmberBadgeBg).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(pattern.severity, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = if (isCritical) RedBadgeTx else AmberBadgeTx, fontFamily = FontFamily.SansSerif)
                    }
                    Text(financePatternName(pattern.pattern), fontSize = 10.sp, color = NearBlack, fontFamily = FontFamily.SansSerif, modifier = Modifier.weight(1f))
                }
            }
        }
        patterns.correlations?.let { corr ->
            Spacer(Modifier.height(10.dp)); HorizontalDivider(color = LightRule, thickness = 0.5.dp); Spacer(Modifier.height(8.dp))
            Text("Correlations · ${corr.windowDays}d", fontSize = 9.sp, color = MidGrey, fontFamily = FontFamily.SansSerif); Spacer(Modifier.height(5.dp))
            val corrRows = buildList {
                if (corr.minDataPointsMet["deposits_vs_income"] == true && corr.depositsVsIncome != null)
                    add(Triple("Deposits vs Income", corr.depositsVsIncome, financeCorrDisplay(corr.depositsVsIncome)))
                if (corr.minDataPointsMet["income_consistency"] == true && corr.incomeConsistency != null)
                    add(Triple("Income consistency", corr.incomeConsistency, financeCorrDisplay(corr.incomeConsistency)))
            }
            if (corrRows.isEmpty()) {
                Text("Insufficient data", fontSize = 9.sp, color = MidGrey.copy(alpha = 0.6f), fontFamily = FontFamily.SansSerif)
            } else {
                corrRows.forEachIndexed { index, (label, value, display) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(label, fontSize = 10.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${"%.2f".format(value)}", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = display.first, fontFamily = FontFamily.Serif)
                            Text(display.second, fontSize = 8.sp, color = display.first.copy(alpha = 0.8f), fontFamily = FontFamily.SansSerif)
                        }
                    }
                    if (index < corrRows.size - 1) HorizontalDivider(color = LightRule, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ── Alert card ────────────────────────────────────────────────

@Composable
private fun FinanceAlertCard(alert: WhoopAlert) {
    val isCritical = alert.severity == "critical"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCritical) RedAlertBg else AmberAlertBg)
            .border(0.5.dp, if (isCritical) RedAlertBd else AmberAlertBd, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (isCritical) RedBadgeBg else AmberBadgeBg).padding(horizontal = 7.dp, vertical = 2.dp)) {
            Text(alert.severity, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = if (isCritical) RedBadgeTx else AmberBadgeTx, fontFamily = FontFamily.SansSerif)
        }
        Text(alert.message, modifier = Modifier.weight(1f), fontSize = 10.sp, lineHeight = 14.sp,
            color = if (isCritical) RedAlertTx else AmberAlertTx, fontFamily = FontFamily.SansSerif)
    }
}

// ── Buddie insight — Option B: white card, amber left border ──

@Composable
private fun FinanceInsightSection(synthesis: FinanceSynthesisResponse?, synLoading: Boolean, synError: String?, context: Context? = null, onRequest: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (synthesis == null && !synLoading) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(NearBlack).clickable { onRequest() }.padding(vertical = 13.dp), Alignment.Center) {
                Text("Get insight from Buddie", fontSize = 12.sp, color = White, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif)
            }
        }
        if (synLoading) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), Arrangement.Center, Alignment.CenterVertically) {
                CircularProgressIndicator(color = NearBlack, strokeWidth = 1.5.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text("Buddie is thinking…", fontSize = 11.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
            }
        }
        synError?.let { Text("Could not load insight: $it", fontSize = 10.sp, color = MidGrey, fontFamily = FontFamily.SansSerif, modifier = Modifier.padding(vertical = 8.dp)) }
        synthesis?.let { syn ->
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(White)
                    .border(0.5.dp, LightRule, RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp))
            ) {
                Box(Modifier.width(3.dp).heightIn(min = 60.dp).background(AmberAlertBd))
                Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Text("Buddie insight · ${syn.range}", fontSize = 9.sp, color = AmberWarm, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif)
                    Spacer(Modifier.height(6.dp))
                    Text(stripMarkdown(syn.synthesis), fontSize = 11.sp, lineHeight = 17.sp, color = NearBlack, fontFamily = FontFamily.SansSerif)
                    Spacer(Modifier.height(8.dp))
                    Text("${syn.dataPoints} data points", fontSize = 9.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
                    Spacer(Modifier.height(10.dp))
                    if (context != null) {
                        var copied by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                .background(Paper).border(0.5.dp, LightRule, RoundedCornerShape(6.dp))
                                .clickable { copyInsightToClipboard(context, syn.synthesis, syn.range, syn.dataPoints); copied = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (copied) "✓ Copied" else "⎘ Copy insight", fontSize = 10.sp,
                                color = if (copied) GreenDark else NearBlack, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif)
                        }
                        LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(2000); copied = false } }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, color = MidGrey, fontFamily = FontFamily.SansSerif, letterSpacing = 0.07.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp))
}

@Composable
private fun FinanceErrorCard(message: String) {
    Box(Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(8.dp)).background(White).border(0.5.dp, LightRule, RoundedCornerShape(8.dp)).padding(16.dp)) {
        Text("Could not load analytics\n$message", fontSize = 12.sp, color = MidGrey, fontFamily = FontFamily.SansSerif, lineHeight = 18.sp)
    }
}

private fun copyInsightToClipboard(context: Context, synthesis: String, range: String, dataPoints: Int) {
    val text = buildString {
        appendLine("Buddie Finance Insight · $range"); appendLine("$dataPoints data points"); appendLine()
        appendLine(synthesis.replace(Regex("#{1,6}\\s*"), "").replace(Regex("\\*\\*(.+?)\\*\\*"), "$1").replace(Regex("\\*(.+?)\\*"), "$1").trim())
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Buddie Finance Insight", text))
}

private fun exportGrowthCsv(context: Context, series: List<GrowthSeries>) {
    try {
        val csv = buildString {
            appendLine("date,equity_gbp,pnl_gbp,deposits_mtd")
            series.forEach { row -> appendLine("${row.date},${row.equityGbp ?: ""},${row.pnlGbp ?: ""},${row.depositsMtd ?: ""}") }
        }
        val file = java.io.File(context.getExternalFilesDir(null), "vitaclaw_portfolio_growth.csv")
        file.writeText(csv)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, "VitaClaw Portfolio Growth"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(Intent.createChooser(intent, "Export CSV"))
    } catch (e: Exception) { e.printStackTrace() }
}