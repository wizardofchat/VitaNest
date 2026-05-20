package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// GrowthScreen — three sub-tabs: Portfolio · Health · Energy
// Fixed header, table/chart toggle, dynamic column picker, CSV + text share ☘️
// Dynamic columns: new fields in GrowthSeries appear automatically in picker.
// No VitaNest changes needed when VitaClaw adds new growth metrics.
// seriesFieldMap() is the only place to update when GrowthSeries data class grows.

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vitanest.app.data.remote.GrowthResponse
import com.vitanest.app.data.remote.GrowthSeries
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

// ── Palette ───────────────────────────────────────────────────

private val Cream        = Color(0xFFF2EFE8)
private val NearBlack    = Color(0xFF111111)
private val MidGrey      = Color(0xFF888888)
private val LightRule    = Color(0xFFC8C4BB)
private val GreenDark    = Color(0xFF2D6A4F)
private val GreenDeep    = Color(0xFF1B4332)
private val AmberWarm    = Color(0xFFD4A017)
private val ErrorRed     = Color(0xFFC0392B)
private val White        = Color(0xFFFFFFFF)
private val GreenPillBg  = Color(0xFFEAF3DE)
private val GreenPillFg  = Color(0xFF3B6D11)
private val AmberPillBg  = Color(0xFFFAEEDA)
private val AmberPillFg  = Color(0xFF854F0B)
private val RedPillBg    = Color(0xFFFCEBEB)
private val RedPillFg    = Color(0xFFA32D2D)

// ── Column metadata ───────────────────────────────────────────
// Maps GrowthSeries field name → (display label, description).
// Any field NOT in this map auto-formats: snake_case → "Snake case".
// Add new entries here only if you want a custom label or description.

private val COLUMN_META = mapOf(
    "equity_gbp"               to Pair("Equity £",       "portfolio value"),
    "pnl_gbp"                  to Pair("P&L £",          "unrealised gain/loss"),
    "deposits_mtd"             to Pair("Deposits £",     "month-to-date"),
    "recovery_score"           to Pair("Recovery",       "score 0–100"),
    "recovery_zone"            to Pair("Zone",           "green/amber/red"),
    "hrv_ms"                   to Pair("HRV ms",         "rmssd"),
    "rhr_bpm"                  to Pair("RHR bpm",        "resting HR"),
    "spo2_pct"                 to Pair("SpO2 %",         "threshold 92%"),
    "solar_kwh"                to Pair("Solar kWh",      "generated"),
    "self_sufficiency_pct"     to Pair("Self-suff %",    "solar vs total"),
    "energy_savings_gbp"       to Pair("Savings £",      "vs grid cost"),
    "ev_solar_kwh"             to Pair("EV solar kWh",   "solar charge"),
    "ev_grid_kwh"              to Pair("EV grid kWh",    "grid charge"),
    "eddi_solar_kwh"           to Pair("Eddi kWh",       "hot water solar"),
    "grid_imported_kwh"        to Pair("Grid kWh",       "imported"),
    "solar_exported_kwh"       to Pair("Export kWh",     "exported"),
    "export_earnings_gbp"      to Pair("Export £",       "earnings"),
    "income_mtd_gbp"           to Pair("Income MTD £",   "month-to-date"),
    "income_30d_gbp"           to Pair("Income 30d £",   "rolling 30 days"),
    "income_gap_to_target_gbp" to Pair("Gap £",          "to target"),
    "ghost_actions_total"      to Pair("Ghost actions",  "total count"),
    "llm_cost_usd"             to Pair("LLM cost \$",    "daily spend"),
    "calibrated_domains_count" to Pair("Calibrated",     "domain count")
)

// Default columns shown checked on first open per tab.
private val DEFAULT_COLUMNS = mapOf(
    "portfolio" to listOf("equity_gbp", "pnl_gbp", "deposits_mtd"),
    "health"    to listOf("spo2_pct", "recovery_score", "recovery_zone"),
    "energy"    to listOf("solar_kwh", "self_sufficiency_pct", "energy_savings_gbp")
)

// Ordered field list per tab — drives picker order + dynamic discovery scope.
private val TAB_FIELD_GROUPS = mapOf(
    "portfolio" to listOf("equity_gbp", "pnl_gbp", "deposits_mtd",
        "income_mtd_gbp", "income_30d_gbp", "income_gap_to_target_gbp",
        "ghost_actions_total", "llm_cost_usd", "calibrated_domains_count"),
    "health"    to listOf("spo2_pct", "recovery_score", "recovery_zone",
        "hrv_ms", "rhr_bpm"),
    "energy"    to listOf("solar_kwh", "self_sufficiency_pct", "energy_savings_gbp")
)

private val EXCLUDED_FIELDS = setOf("date")
private const val MAX_COLUMNS = 5

// ── ViewModel ─────────────────────────────────────────────────

class GrowthViewModel(private val repository: VitaClawRepository) : ViewModel() {
    private val _growth    = MutableStateFlow<GrowthResponse?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _error     = MutableStateFlow<String?>(null)
    private val _days      = MutableStateFlow(30)

    val growth:    StateFlow<GrowthResponse?> = _growth
    val isLoading: StateFlow<Boolean>         = _isLoading
    val error:     StateFlow<String?>         = _error
    val days:      StateFlow<Int>             = _days

    init { load(30) }

    fun load(days: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            _days.value      = days
            repository.getGrowth(days = days).fold(
                onSuccess = { _growth.value = it },
                onFailure = { _error.value  = it.message }
            )
            _isLoading.value = false
        }
    }
}

// ── Screen ────────────────────────────────────────────────────

@Composable
fun GrowthScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    val vm: GrowthViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GrowthViewModel(repository) as T
        }
    )

    val growth    by vm.growth.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error     by vm.error.collectAsState()
    val days      by vm.days.collectAsState()
    var activeTab by remember { mutableStateOf("portfolio") }

    // Column selection per tab — initialised to defaults
    val selectedColumns = remember {
        mutableStateMapOf(
            "portfolio" to DEFAULT_COLUMNS["portfolio"]!!.toMutableList(),
            "health"    to DEFAULT_COLUMNS["health"]!!.toMutableList(),
            "energy"    to DEFAULT_COLUMNS["energy"]!!.toMutableList()
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Cream)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Fixed header — title + range chips + sub-tabs
            GrowthHeader(
                growth       = growth,
                days         = days,
                activeTab    = activeTab,
                onDaysChange = { vm.load(it) },
                onTabChange  = { activeTab = it }
            )

            when {
                isLoading -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                        CircularProgressIndicator(color = GreenDark, strokeWidth = 2.dp)
                    }
                }
                error != null -> {
                    Box(Modifier.weight(1f).padding(20.dp)) {
                        GrowthErrorCard(error ?: "Unknown error")
                    }
                }
                growth != null -> {
                    GrowthTabContent(
                        modifier        = Modifier.weight(1f),
                        growth          = growth!!,
                        activeTab       = activeTab,
                        days            = days,
                        selectedColumns = selectedColumns[activeTab] ?: mutableListOf(),
                        onColumnsChange = { selectedColumns[activeTab] = it.toMutableList() },
                        onAnalyticsNav  = { route -> navController.navigate(route) }
                    )
                }
                else -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                        Text("No data yet", style = T.meta, color = T.Muted)
                    }
                }
            }
        }

        InkBottomNav(
            current       = "health",
            navController = navController,
            modifier      = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        )
    }
}

// ── Fixed header ──────────────────────────────────────────────

@Composable
private fun GrowthHeader(
    growth: GrowthResponse?,
    days: Int,
    activeTab: String,
    onDaysChange: (Int) -> Unit,
    onTabChange: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().background(White).statusBarsPadding()) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text("Growth", fontSize = 22.sp, fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Serif, color = NearBlack)
                if (growth != null) {
                    Text(
                        text       = "${shortDate(growth.from)} → ${shortDate(growth.to)}  ·  ${growth.days}d",
                        fontSize   = 11.sp, color = MidGrey, fontFamily = FontFamily.SansSerif
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7, 30, 90).forEach { d ->
                    val active = days == d
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (active) NearBlack else White)
                            .border(0.5.dp, if (active) NearBlack else LightRule, RoundedCornerShape(20.dp))
                            .clickable { onDaysChange(d) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${d}d", fontSize = 11.sp,
                            color = if (active) Cream else MidGrey,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
        }
        HorizontalDivider(color = LightRule, thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth()) {
            listOf("portfolio" to "Portfolio", "health" to "Health", "energy" to "Energy")
                .forEach { (key, label) ->
                    val active = activeTab == key
                    Column(
                        modifier            = Modifier.weight(1f).clickable { onTabChange(key) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(label, fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            color      = if (active) NearBlack else MidGrey)
                        if (active) {
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.width(24.dp).height(2.dp).background(NearBlack))
                        }
                    }
                }
        }
        HorizontalDivider(color = LightRule, thickness = 0.5.dp)
    }
}

// ── Tab content ───────────────────────────────────────────────

@Composable
private fun GrowthTabContent(
    modifier: Modifier,
    growth: GrowthResponse,
    activeTab: String,
    days: Int,
    selectedColumns: List<String>,
    onColumnsChange: (List<String>) -> Unit,
    onAnalyticsNav: (String) -> Unit = {}
) {
    val context   = LocalContext.current
    val allSeries = growth.series.sortedBy { it.date }

// Only show fields belonging to this tab — no cross-domain leakage
    val knownTabFields   = TAB_FIELD_GROUPS[activeTab] ?: emptyList()
// Dynamic discovery: any new field VitaClaw adds to this tab's group
// appears automatically without VitaNest changes
    val liveDataKeys     = allSeries.flatMap { seriesFieldMap(it).keys }.distinct()
        .filter { it !in EXCLUDED_FIELDS }
        .filter { it in knownTabFields }   // ← scoped to this tab only
    val availableColumns = (knownTabFields + liveDataKeys).distinct()
        .filter { key -> allSeries.any { seriesFieldMap(it).containsKey(key) } }

    var showTable   by remember(activeTab) { mutableStateOf(true) }
    var pickerOpen  by remember(activeTab) { mutableStateOf(false) }
    var pickerDraft by remember(activeTab) { mutableStateOf(selectedColumns.toMutableList()) }

    Column(modifier = modifier.fillMaxWidth().padding(bottom = 80.dp)) {
        // Controls row
        Row(
            modifier              = Modifier.fillMaxWidth().background(White)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            listOf(true to "Table", false to "Chart").forEach { (isTable, label) ->
                val active = showTable == isTable
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) NearBlack else White)
                        .border(0.5.dp, if (active) NearBlack else LightRule, RoundedCornerShape(6.dp))
                        .clickable { showTable = isTable; pickerOpen = false }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(label, fontSize = 11.sp, color = if (active) Cream else MidGrey)
                }
            }
            Spacer(Modifier.weight(1f))
            val extraCount = selectedColumns.count { it !in (DEFAULT_COLUMNS[activeTab] ?: emptyList()) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (pickerOpen) NearBlack else White)
                    .border(0.5.dp, if (pickerOpen) NearBlack else LightRule, RoundedCornerShape(6.dp))
                    .clickable {
                        pickerOpen  = !pickerOpen
                        pickerDraft = selectedColumns.toMutableList()
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text     = if (extraCount > 0) "Columns +$extraCount" else "Columns",
                    fontSize = 11.sp,
                    color    = if (pickerOpen) Cream else MidGrey
                )
            }
        }
        HorizontalDivider(color = LightRule, thickness = 0.5.dp)

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (pickerOpen) {
                ColumnPickerPanel(
                    activeTab        = activeTab,
                    availableColumns = availableColumns,
                    selectedColumns  = selectedColumns,
                    draft            = pickerDraft,
                    onDraftChange    = { pickerDraft = it.toMutableList() },
                    onApply          = { onColumnsChange(pickerDraft); pickerOpen = false },
                    onReset          = { pickerDraft = (DEFAULT_COLUMNS[activeTab] ?: emptyList()).toMutableList() }
                )
                HorizontalDivider(color = LightRule, thickness = 0.5.dp)
            }

            // ── Analytics deep-dive link — top of tab ──────────
            if (activeTab == "portfolio") {
                Spacer(Modifier.height(12.dp))
                GrowthAnalyticsLinkRow(
                    label    = "Finance analytics",
                    subtitle = "Equity · P&L · Income trends",
                    onClick  = { onAnalyticsNav("finance_analytics") }
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = LightRule, thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (activeTab == "energy") {
                Spacer(Modifier.height(12.dp))
                GrowthAnalyticsLinkRow(
                    label    = "Energy analytics",
                    subtitle = "Solar · Self-sufficiency · EV trends",
                    onClick  = { onAnalyticsNav("energy_analytics") }
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = LightRule, thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }

            SummaryCards(growth = growth, activeTab = activeTab)
            HorizontalDivider(color = LightRule, thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(14.dp))

            if (showTable) {
                GrowthTable(allSeries, selectedColumns, activeTab)
            } else {
                GrowthCharts(allSeries, selectedColumns)
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = LightRule, thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))

            ExportRow(
                context         = context,
                series          = allSeries,
                selectedColumns = listOf("date") + selectedColumns,
                activeTab       = activeTab,
                days            = days,
                growthFrom      = growth.from,
                growthTo        = growth.to
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Column picker ─────────────────────────────────────────────

@Composable
private fun ColumnPickerPanel(
    activeTab: String,
    availableColumns: List<String>,
    selectedColumns: List<String>,
    draft: List<String>,
    onDraftChange: (List<String>) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit
) {
    val defaults     = DEFAULT_COLUMNS[activeTab] ?: emptyList()
    val defaultCols  = availableColumns.filter { it in defaults }
    val optionalCols = availableColumns.filter { it !in defaults }

    Column(
        modifier            = Modifier.fillMaxWidth().background(White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Pick columns", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NearBlack)
            Text("Reset defaults", fontSize = 11.sp, color = MidGrey,
                modifier = Modifier.clickable { onReset() })
        }
        if (defaultCols.isNotEmpty()) {
            Text("Default", fontSize = 10.sp, color = MidGrey, letterSpacing = 0.05.sp)
            PickerGrid(defaultCols, draft, onDraftChange)
        }
        if (optionalCols.isNotEmpty()) {
            Text("Add more", fontSize = 10.sp, color = MidGrey, letterSpacing = 0.05.sp)
            PickerGrid(optionalCols, draft, onDraftChange)
        }
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(NearBlack).clickable { onApply() }.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Apply · show ${draft.size} columns",
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Cream)
        }
        Text("Max $MAX_COLUMNS columns for readability",
            fontSize = 10.sp, color = MidGrey, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PickerGrid(
    columns: List<String>,
    draft: List<String>,
    onDraftChange: (List<String>) -> Unit
) {
    val rows = columns.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { key ->
                    val isChecked  = key in draft
                    val isDisabled = !isChecked && draft.size >= MAX_COLUMNS
                    val (label, sub) = COLUMN_META[key]
                        ?: Pair(key.replace('_', ' ').replaceFirstChar { it.uppercase() }, "")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isChecked) GreenPillBg else Cream)
                            .border(0.5.dp, if (isChecked) GreenDark else LightRule, RoundedCornerShape(8.dp))
                            .then(if (!isDisabled) Modifier.clickable {
                                val nd = draft.toMutableList()
                                if (isChecked) nd.remove(key) else nd.add(key)
                                onDraftChange(nd)
                            } else Modifier)
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))
                                    .background(when { isDisabled -> LightRule; isChecked -> GreenDark; else -> White })
                                    .border(0.5.dp, if (isChecked) GreenDark else LightRule, RoundedCornerShape(3.dp))
                            )
                            Column {
                                Text(label, fontSize = 11.sp,
                                    color = when { isDisabled -> MidGrey; isChecked -> GreenDeep; else -> NearBlack },
                                    fontWeight = if (isChecked) FontWeight.Medium else FontWeight.Normal)
                                if (sub.isNotEmpty()) Text(sub, fontSize = 10.sp, color = MidGrey)
                            }
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── Summary cards ─────────────────────────────────────────────

@Composable
private fun SummaryCards(growth: GrowthResponse, activeTab: String) {
    val s = growth.summary
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (activeTab) {
            "portfolio" -> {
                SummaryCard(Modifier.weight(1f), "Portfolio",
                    value = s.portfolioChangeGbp?.let { "${if (it >= 0) "+" else ""}£${"%.0f".format(it)}" } ?: "—",
                    sub   = s.portfolioChangePct?.let { "${"%.1f".format(it)}%" } ?: "",
                    valueColor = when { s.portfolioChangePct == null -> MidGrey; (s.portfolioChangePct ?: 0.0) >= 0 -> GreenDark; else -> ErrorRed })
                SummaryCard(Modifier.weight(1f), "Income 30d",
                    value = s.income30dGbp?.let { "£${"%.2f".format(it)}" } ?: "—",
                    sub   = s.incomeGapToTargetGbp?.let { "£${"%.0f".format(it)} to target" } ?: "",
                    valueColor = NearBlack)
            }
            "health" -> {
                SummaryCard(Modifier.weight(1f), "Avg recovery",
                    value = s.avgRecovery?.let { "${"%.0f".format(it)}%" } ?: "—",
                    sub   = "",
                    valueColor = when { s.avgRecovery == null -> MidGrey; (s.avgRecovery ?: 0.0) >= 67 -> GreenDark; (s.avgRecovery ?: 0.0) >= 34 -> AmberWarm; else -> ErrorRed })
                SummaryCard(Modifier.weight(1f), "Avg SpO2",
                    value = s.avgSpo2Pct?.let { "${"%.1f".format(it)}%" } ?: "—",
                    sub   = if ((s.spo2BelowThreshold ?: 0) > 0) "${s.spo2BelowThreshold} days below 92%" else "all above 92%",
                    valueColor = if ((s.spo2BelowThreshold ?: 0) > 0) AmberWarm else GreenDark)
            }
            "energy" -> {
                val latest = growth.series.sortedBy { it.date }.lastOrNull()
                SummaryCard(Modifier.weight(1f), "Latest solar",
                    value = latest?.solarKwh?.let { "${"%.1f".format(it)} kWh" } ?: "—",
                    sub   = latest?.selfSufficiencyPct?.let { "${"%.0f".format(it)}% self-suff" } ?: "",
                    valueColor = GreenDark)
                SummaryCard(Modifier.weight(1f), "Savings",
                    value = latest?.energySavingsGbp?.let { "£${"%.2f".format(it)}" } ?: "—",
                    sub   = "latest day",
                    valueColor = NearBlack)
            }
        }
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, label: String, value: String, sub: String, valueColor: Color) {
    Column(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(White)
        .border(0.5.dp, LightRule, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 10.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
        Text(value, fontSize = 17.sp, color = valueColor, fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium)
        if (sub.isNotBlank()) Text(sub, fontSize = 10.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
    }
}

// ── Table ─────────────────────────────────────────────────────

@Composable
private fun GrowthTable(series: List<GrowthSeries>, selectedColumns: List<String>, activeTab: String) {
    if (series.isEmpty()) { GrowthEmptyCard("No data for this period"); return }
    val visibleCols = listOf("date") + selectedColumns

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(10.dp)).background(White)
        .border(0.5.dp, LightRule, RoundedCornerShape(10.dp))) {
        // Header
        Row(Modifier.fillMaxWidth().background(Cream).padding(horizontal = 10.dp, vertical = 7.dp)) {
            visibleCols.forEach { key ->
                val label = if (key == "date") "Date" else COLUMN_META[key]?.first
                    ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
                Text(label, fontSize = 10.sp, color = MidGrey, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(colWeight(key)))
            }
        }
        HorizontalDivider(color = LightRule, thickness = 0.5.dp)
        // Data rows — newest first
        series.reversed().forEachIndexed { idx, row ->
            val fieldMap = seriesFieldMap(row)
            Row(modifier = Modifier.fillMaxWidth()
                .background(if (idx % 2 == 0) White else Cream.copy(alpha = 0.4f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                visibleCols.forEach { key ->
                    val value = if (key == "date") row.date else fieldMap[key]
                    TableCell(key = key, value = value, modifier = Modifier.weight(colWeight(key)))
                }
            }
            if (idx < series.size - 1) {
                HorizontalDivider(color = LightRule.copy(alpha = 0.5f), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun TableCell(key: String, value: Any?, modifier: Modifier) {
    when (key) {
        "date" -> Text(shortDate(value?.toString() ?: ""), fontSize = 11.sp,
            color = MidGrey, modifier = modifier)
        "recovery_zone" -> {
            val zone = value?.toString()?.lowercase() ?: ""
            val (bg, fg) = when (zone) {
                "green"         -> GreenPillBg to GreenPillFg
                "yellow","amber"-> AmberPillBg to AmberPillFg
                "red"           -> RedPillBg   to RedPillFg
                else            -> Cream        to MidGrey
            }
            Box(modifier) {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(bg)
                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(zone.ifBlank { "—" }, fontSize = 10.sp, color = fg)
                }
            }
        }
        "spo2_pct" -> {
            val d = (value as? Double) ?: value?.toString()?.toDoubleOrNull()
            val warn = d != null && d < 92.0
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                Text(if (d != null) "${"%.1f".format(d)}%" else "—",
                    fontSize   = 12.sp,
                    color      = if (warn) ErrorRed else NearBlack,
                    fontWeight = if (warn) FontWeight.Medium else FontWeight.Normal)
                if (warn) Text(" ⚠", fontSize = 11.sp, color = AmberWarm)
            }
        }
        "recovery_score" -> {
            val d = (value as? Double) ?: value?.toString()?.toDoubleOrNull()
            Text(if (d != null) "${"%.0f".format(d)}" else "—",
                fontSize = 12.sp,
                color    = when { d == null -> MidGrey; d >= 67 -> GreenDark; d >= 34 -> AmberWarm; else -> ErrorRed },
                modifier = modifier)
        }
        else -> {
            val display = when (value) {
                null      -> "—"
                is Double -> formatDouble(key, value)
                is Int    -> value.toString()
                is Boolean-> if (value) "yes" else "no"
                else      -> value.toString().ifBlank { "—" }
            }
            Text(display, fontSize = 12.sp, color = NearBlack, modifier = modifier)
        }
    }
}

// ── Charts ────────────────────────────────────────────────────

@Composable
private fun GrowthCharts(series: List<GrowthSeries>, selectedColumns: List<String>) {
    if (series.isEmpty()) { GrowthEmptyCard("No data for this period"); return }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        selectedColumns.forEach { key ->
            val values = series.mapNotNull { row ->
                val v = seriesFieldMap(row)[key]
                (v as? Double) ?: v?.toString()?.toDoubleOrNull()
            }
            if (values.size >= 2) {
                val (label, _) = COLUMN_META[key]
                    ?: Pair(key.replace('_', ' ').replaceFirstChar { it.uppercase() }, "")
                SparklineCard(label = label, values = values, key = key,
                    dates = series.map { it.date })
            }
        }
    }
}

@Composable
private fun SparklineCard(label: String, values: List<Double>, key: String, dates: List<String>) {
    val minV   = values.min()
    val maxV   = values.max()
    val rangeV = (maxV - minV).coerceAtLeast(0.001)
    val lineColor = if (key.contains("gap") || key.contains("cost")) AmberWarm else GreenDark
    val progress by animateFloatAsState(1f, tween(700), label = "spark_$key")

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(White)
        .border(0.5.dp, LightRule, RoundedCornerShape(10.dp)).padding(12.dp)) {
        Text(label, fontSize = 11.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val w = size.width; val h = size.height; val n = values.size
            val count = (n * progress).toInt().coerceAtLeast(2)
            // Grid line
            drawLine(LightRule.copy(alpha = 0.5f), Offset(0f, h / 2f), Offset(w, h / 2f), 0.5.dp.toPx())
            // SpO2 threshold at 92%
            if (key == "spo2_pct") {
                val ty = h * (1f - ((92.0 - minV) / rangeV).toFloat()).coerceIn(0f, 1f)
                drawLine(AmberWarm.copy(alpha = 0.6f), Offset(0f, ty), Offset(w, ty), 0.8.dp.toPx())
            }
            val path = Path()
            values.take(count).forEachIndexed { idx, v ->
                val x = if (n > 1) (idx.toFloat() / (n - 1)) * w else w / 2f
                val y = h * (1f - ((v - minV) / rangeV).toFloat())
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            val lx = if (n > 1) ((count - 1).toFloat() / (n - 1)) * w else w / 2f
            val ly = h * (1f - ((values[count - 1] - minV) / rangeV).toFloat())
            drawCircle(lineColor, 3.5.dp.toPx(), Offset(lx, ly))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(shortDate(dates.first()), fontSize = 10.sp, color = MidGrey)
            Text(formatDouble(key, values.last()), fontSize = 11.sp,
                fontWeight = FontWeight.Medium, color = NearBlack)
            Text(shortDate(dates.last()), fontSize = 10.sp, color = MidGrey)
        }
    }
}

// ── Export ────────────────────────────────────────────────────

@Composable
private fun ExportRow(
    context: Context,
    series: List<GrowthSeries>,
    selectedColumns: List<String>,
    activeTab: String,
    days: Int,
    growthFrom: String,
    growthTo: String
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // CSV
        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(White)
            .border(0.5.dp, LightRule, RoundedCornerShape(8.dp))
            .clickable {
                shareFile(context, buildCsv(series, selectedColumns),
                    "vitanest_${activeTab}_${days}d_${growthTo}.csv", "text/csv")
            }.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center) {
            Text("↓ CSV  ·  ${series.size} rows", fontSize = 12.sp,
                fontWeight = FontWeight.Medium, color = NearBlack)
        }
        // Formatted text share
        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(NearBlack)
            .clickable {
                shareFile(context,
                    buildFormattedText(series, selectedColumns, activeTab, growthFrom, growthTo),
                    "vitanest_${activeTab}_${days}d_${growthTo}.txt", "text/plain")
            }.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center) {
            Text("↑ Share text", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Cream)
        }
    }
}

// ── File share ────────────────────────────────────────────────

private fun shareFile(context: Context, content: String, filename: String, mimeType: String) {
    try {
        val file = File(context.getExternalFilesDir(null), filename)
        file.writeText(content)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "VitaNest · $filename")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    } catch (e: Exception) { e.printStackTrace() }
}

private fun buildCsv(series: List<GrowthSeries>, columns: List<String>): String {
    val sb = StringBuilder()
    sb.appendLine(columns.joinToString(",") { key ->
        if (key == "date") "Date" else (COLUMN_META[key]?.first ?: key)
    })
    series.reversed().forEach { row ->
        val fm = seriesFieldMap(row)
        sb.appendLine(columns.joinToString(",") { key ->
            csvEscape(if (key == "date") row.date else fm[key]?.toString() ?: "")
        })
    }
    return sb.toString()
}

private fun buildFormattedText(
    series: List<GrowthSeries>,
    columns: List<String>,
    activeTab: String,
    from: String,
    to: String
): String {
    val sb = StringBuilder()
    sb.appendLine("VitaNest — ${activeTab.replaceFirstChar { it.uppercase() }}")
    sb.appendLine("$from → $to  (${series.size} days)")
    sb.appendLine()
    series.reversed().forEach { row ->
        val fm = seriesFieldMap(row)
        sb.append("${shortDate(row.date)}  ")
        columns.filter { it != "date" }.forEach { key ->
            val v = fm[key]
            val (label, _) = COLUMN_META[key] ?: Pair(key, "")
            val display = when {
                v == null -> "—"
                key == "spo2_pct" -> {
                    val d = (v as? Double) ?: v.toString().toDoubleOrNull()
                    val icon = when { d == null -> ""; d < 90.0 -> " 🔴"; d < 92.0 -> " ⚠️"; d >= 95.0 -> " ✅"; else -> "" }
                    "${"%.1f".format(d ?: 0.0)}%$icon"
                }
                key == "recovery_zone" -> v.toString()
                key == "recovery_score" -> {
                    val d = (v as? Double) ?: v.toString().toDoubleOrNull()
                    "${d?.toInt() ?: "—"}"
                }
                v is Double -> formatDouble(key, v)
                else        -> v.toString()
            }
            sb.append("$label: $display  ")
        }
        sb.appendLine()
    }
    return sb.toString()
}

private fun csvEscape(s: String): String =
    if (s.contains(',') || s.contains('"') || s.contains('\n'))
        "\"${s.replace("\"", "\"\"")}\"" else s

// ── seriesFieldMap — update this when GrowthSeries grows ─────
// This is the single point of truth for field → key mapping.
// New VitaClaw field? Add one line here. Picker updates automatically.

private fun seriesFieldMap(s: GrowthSeries): Map<String, Any> {
    val m = mutableMapOf<String, Any>()
    s.equityGbp?.let              { m["equity_gbp"] = it }
    s.pnlGbp?.let                 { m["pnl_gbp"] = it }
    s.depositsMtd?.let            { m["deposits_mtd"] = it }
    s.recoveryScore?.let          { m["recovery_score"] = it }
    s.recoveryZone?.let           { m["recovery_zone"] = it }
    s.hrvMs?.let                  { m["hrv_ms"] = it }
    s.rhrBpm?.let                 { m["rhr_bpm"] = it }
    s.spo2Pct?.let                { m["spo2_pct"] = it }
    s.solarKwh?.let               { m["solar_kwh"] = it }
    s.selfSufficiencyPct?.let     { m["self_sufficiency_pct"] = it }
    s.energySavingsGbp?.let       { m["energy_savings_gbp"] = it }
    s.incomeMtdGbp?.let           { m["income_mtd_gbp"] = it }
    s.income30dGbp?.let           { m["income_30d_gbp"] = it }
    s.incomeGapToTargetGbp?.let   { m["income_gap_to_target_gbp"] = it }
    s.ghostActionsTotal?.let      { m["ghost_actions_total"] = it }
    s.llmCostUsd?.let             { m["llm_cost_usd"] = it }
    s.calibratedDomainsCount?.let { m["calibrated_domains_count"] = it }
    return m
}

// ── Helpers ───────────────────────────────────────────────────

private fun shortDate(iso: String): String = try {
    val p = iso.split("-")
    val months = listOf("","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    "${p.getOrNull(2)?.trimStart('0') ?: ""} ${p.getOrNull(1)?.toIntOrNull()?.let { months.getOrElse(it) { "" } } ?: ""}"
} catch (e: Exception) { iso }

private fun colWeight(key: String): Float = when (key) {
    "date" -> 0.8f; "recovery_zone" -> 0.9f; else -> 1f
}

private fun formatDouble(key: String, v: Double): String = when {
    key.contains("gbp")  -> "£${"%.2f".format(v)}"
    key.contains("pct")  -> "${"%.1f".format(v)}%"
    key.contains("kwh")  -> "${"%.1f".format(v)}"
    key.contains("usd")  -> "$${"%.4f".format(v)}"
    key.contains("ms")   -> "${"%.0f".format(v)}"
    key.contains("bpm")  -> "${"%.0f".format(v)}"
    else                 -> "${"%.1f".format(v)}"
}


// ── Growth analytics link row ─────────────────────────────────
// Light theme — matches GrowthScreen palette.
// Tab-conditional — portfolio tab → finance_analytics,
//                   energy tab    → energy_analytics.

@Composable
private fun GrowthAnalyticsLinkRow(
    label:    String,
    subtitle: String,
    onClick:  () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(White)
            .border(0.5.dp, LightRule, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text       = label,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                color      = NearBlack
            )
            Text(
                text     = subtitle,
                fontSize = 10.sp,
                color    = MidGrey
            )
        }
        Text(
            text     = "›",
            fontSize = 18.sp,
            color    = NearBlack.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun GrowthErrorCard(message: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(10.dp)).background(White)
        .border(0.5.dp, LightRule, RoundedCornerShape(10.dp)).padding(16.dp)) {
        Text("Could not load growth data\n$message", fontSize = 13.sp, color = MidGrey,
            fontFamily = FontFamily.SansSerif, lineHeight = 20.sp)
    }
}

@Composable
private fun GrowthEmptyCard(message: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(10.dp)).background(White)
        .border(0.5.dp, LightRule, RoundedCornerShape(10.dp)).padding(16.dp)) {
        Text(message, fontSize = 12.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
    }
}