package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// FinanceAnalyticsScreen — dark theme, pinned header, tap-to-switch charts,
// null-safe series, warnings-only alerts, Buddie synthesis cached per range.
// Entry point: PortfolioLensScreen link row → finance_analytics route. ☘️

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.vitanest.app.data.remote.FinanceAnalyticsResponse
import com.vitanest.app.data.remote.FinanceCorrelations
import com.vitanest.app.data.remote.FinanceHealthState
import com.vitanest.app.data.remote.FinancePatterns
import com.vitanest.app.data.remote.FinanceAnalyticsSeries
import com.vitanest.app.data.remote.FinanceAnalyticsThresholds
import com.vitanest.app.data.remote.FinanceSynthesisResponse
import com.vitanest.app.data.remote.WhoopAlert
import com.vitanest.app.data.repository.VitaClawRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

// ── Dark palette (matches HealthAnalyticsScreen) ──────────────

private val DarkBg       = Color(0xFF0F1117)
private val DarkCard     = Color(0xFF1A1D26)
private val DarkBorder   = Color(0xFF2A2D36)
private val DarkText     = Color(0xFFE8E4DC)
private val DarkMuted    = Color(0xFF888899)
private val GreenChart   = Color(0xFF2D9E6B)
private val AmberChart   = Color(0xFFD4A017)
private val BlueChart    = Color(0xFF378ADD)
private val RedAlert     = Color(0xFFE24B4A)
private val AmberBgD     = Color(0xFF2D2000)
private val AmberBorderD = Color(0xFF7A5A00)
private val AmberTextD   = Color(0xFFFFCF7A)
private val AmberBadgeD  = Color(0xFF6B4A00)

// ── Chart types ───────────────────────────────────────────────

private enum class FinanceChart { EQUITY, PNL, INCOME }

// ── Null-safe series helpers ──────────────────────────────────

private fun FinanceAnalyticsSeries.equityPoints() =
    dates.zip(equityGbp).mapNotNull { (d, v) -> v?.let { d to it } }

private fun FinanceAnalyticsSeries.pnlPoints() =
    dates.zip(pnlGbp).mapNotNull { (d, v) -> v?.let { d to it } }

private fun FinanceAnalyticsSeries.incomePoints() =
    dates.zip(income30dGbp).mapNotNull { (d, v) -> v?.let { d to it } }

// ── Markdown stripper ─────────────────────────────────────────

private fun stripMarkdown(text: String): String = text
    .replace(Regex("#{1,6}\\s*"), "")
    .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    .replace(Regex("\\*(.+?)\\*"), "$1")
    .trim()

// ── Color helper ──────────────────────────────────────────────

private fun Color.toHexStr(): String {
    val r = (red   * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue  * 255).toInt()
    return "#%02X%02X%02X".format(r, g, b)
}

// ── ViewModel ─────────────────────────────────────────────────

class FinanceAnalyticsViewModel(private val repository: VitaClawRepository) : ViewModel() {

    private val _finance     = MutableStateFlow<FinanceAnalyticsResponse?>(null)
    private val _isLoading   = MutableStateFlow(true)
    private val _error       = MutableStateFlow<String?>(null)
    private val _range       = MutableStateFlow("14d")
    private val _synthesis   = MutableStateFlow<FinanceSynthesisResponse?>(null)
    private val _synLoading  = MutableStateFlow(false)
    private val _synError    = MutableStateFlow<String?>(null)
    private val synthesisCache = mutableMapOf<String, FinanceSynthesisResponse>()

    val finance:    StateFlow<FinanceAnalyticsResponse?> = _finance
    val isLoading:  StateFlow<Boolean>                   = _isLoading
    val error:      StateFlow<String?>                   = _error
    val range:      StateFlow<String>                    = _range
    val synthesis:  StateFlow<FinanceSynthesisResponse?> = _synthesis
    val synLoading: StateFlow<Boolean>                   = _synLoading
    val synError:   StateFlow<String?>                   = _synError

    init { loadAnalytics("14d") }

    fun loadAnalytics(range: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            _range.value     = range
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
            _synLoading.value = true
            _synError.value   = null
            repository.getFinanceSynthesis(r).fold(
                onSuccess = { synthesisCache[r] = it; _synthesis.value = it },
                onFailure = { _synError.value = it.message }
            )
            _synLoading.value = false
        }
    }
}

// ── Screen ────────────────────────────────────────────────────

@Composable
fun FinanceAnalyticsScreen(
    navController: NavController,
    repository:    VitaClawRepository
) {
    val vm: FinanceAnalyticsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FinanceAnalyticsViewModel(repository) as T
        }
    )

    val finance    by vm.finance.collectAsState()
    val isLoading  by vm.isLoading.collectAsState()
    val error      by vm.error.collectAsState()
    val range      by vm.range.collectAsState()
    val synthesis  by vm.synthesis.collectAsState()
    val synLoading by vm.synLoading.collectAsState()
    val synError   by vm.synError.collectAsState()
    var activeChart by remember { mutableStateOf(FinanceChart.EQUITY) }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBg)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(0.5.dp, DarkBorder, CircleShape)
                            .clickable { navController.popBackStack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint     = DarkText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            "Finance analytics",
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Serif,
                            color      = DarkText
                        )
                        val rangeLabel = when (range) {
                            "14d" -> "14-day view"
                            "30d" -> "30-day view"
                            else  -> "Full history"
                        }
                        Text(
                            rangeLabel,
                            fontSize   = 11.sp,
                            color      = DarkMuted,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
                HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("14d" to "14d", "30d" to "30d", "all" to "All").forEach { (key, label) ->
                        val sel = range == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (sel) GreenChart else DarkCard)
                                .border(0.5.dp, if (sel) GreenChart else DarkBorder, RoundedCornerShape(20.dp))
                                .clickable { vm.loadAnalytics(key) }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                label,
                                fontSize   = 11.sp,
                                color      = if (sel) DarkBg else DarkMuted,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            when {
                isLoading -> Box(
                    Modifier.fillMaxWidth().height(300.dp), Alignment.Center
                ) {
                    CircularProgressIndicator(color = GreenChart, strokeWidth = 2.dp)
                }
                error != null -> DarkFinanceErrorCard(error ?: "Unknown error")
                finance != null -> {
                    val data = finance!!

                    // ── Stat cards (two rows) ─────────────────
                    DarkFinanceStatStrip(
                        data        = data,
                        activeChart = activeChart,
                        onSelect    = { activeChart = it }
                    )

                    // ── Tap-to-switch chart ───────────────────
                    data.series?.let { series ->
                        DarkFinanceChart(
                            series      = series,
                            activeChart = activeChart,
                            thresholds  = data.thresholds
                        )
                    }

                    // ── Warnings-only alerts ──────────────────
                    val warnings = data.alerts.filter { it.severity != "ok" }
                    if (warnings.isNotEmpty()) {
                        DarkSectionLabel("Alerts")
                        warnings.forEach { alert ->
                            DarkFinanceAlertCard(alert)
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // ── Finance intelligence tile ─────────────
                    data.patterns?.let { patterns ->
                        DarkFinanceIntelligenceTile(patterns)
                        Spacer(Modifier.height(6.dp))
                    }

                    // ── Buddie synthesis ──────────────────────
                    Spacer(Modifier.height(8.dp))
                    val ctx = LocalContext.current
                    DarkFinanceInsightSection(
                        synthesis  = synthesis,
                        synLoading = synLoading,
                        synError   = synError,
                        context    = ctx,
                        onRequest  = { vm.loadSynthesis() }
                    )
                }
            }
        }
    }
}

// ── Stat strip ────────────────────────────────────────────────

@Composable
private fun DarkFinanceStatStrip(
    data:        FinanceAnalyticsResponse,
    activeChart: FinanceChart,
    onSelect:    (FinanceChart) -> Unit
) {
    val incomeTarget = data.incomeTargetGbp
    val incomeColor  = if (data.income30dGbp >= incomeTarget * 0.8) GreenChart else AmberChart
    val pnlColor     = if (data.pnlGbp >= 900.0) GreenChart else AmberChart

    // Row 1: Equity · P&L · Income 30d
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DarkFinanceStatCard(
            modifier   = Modifier.weight(1f),
            label      = "Equity",
            value      = "£${"%.0f".format(data.equityGbp)}",
            valueColor = GreenChart,
            selected   = activeChart == FinanceChart.EQUITY,
            onClick    = { onSelect(FinanceChart.EQUITY) }
        )
        DarkFinanceStatCard(
            modifier   = Modifier.weight(1f),
            label      = "P&L",
            value      = "£${"%.0f".format(data.pnlGbp)}",
            valueColor = pnlColor,
            selected   = activeChart == FinanceChart.PNL,
            onClick    = { onSelect(FinanceChart.PNL) }
        )
        DarkFinanceStatCard(
            modifier   = Modifier.weight(1f),
            label      = "Income 30d",
            value      = "£${"%.2f".format(data.income30dGbp)}",
            valueColor = incomeColor,
            selected   = activeChart == FinanceChart.INCOME,
            onClick    = { onSelect(FinanceChart.INCOME) }
        )
    }

    // Row 2: Gap to target · Range label
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DarkFinanceStatCard(
            modifier   = Modifier.weight(1f),
            label      = "Gap to £${"%.0f".format(incomeTarget)} target",
            value      = "−£${"%.2f".format(data.incomeGapGbp)}",
            valueColor = RedAlert,
            selected   = false,
            onClick    = {}
        )
        DarkFinanceStatCard(
            modifier   = Modifier.weight(1f),
            label      = "Range",
            value      = data.range ?: "—",
            valueColor = DarkMuted,
            selected   = false,
            onClick    = {}
        )
    }
}

@Composable
private fun DarkFinanceStatCard(
    modifier:   Modifier,
    label:      String,
    value:      String,
    valueColor: Color,
    selected:   Boolean,
    onClick:    () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkCard)
            .border(
                if (selected) 1.5.dp else 0.5.dp,
                if (selected) GreenChart else DarkBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            value,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Serif,
            color      = valueColor,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
        Text(
            label,
            fontSize   = 8.sp,
            color      = DarkMuted,
            fontFamily = FontFamily.SansSerif,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(5.dp).clip(CircleShape).background(valueColor))
    }
}

// ── Tap-to-switch chart ───────────────────────────────────────

@Composable
private fun DarkFinanceChart(
    series:      FinanceAnalyticsSeries,
    activeChart: FinanceChart,
    thresholds:  FinanceAnalyticsThresholds?
) {
    val incomeTarget = thresholds?.incomeTargetGbp ?: 75.0
    val pnlThreshold = 900.0

    val (title, subtitle, pts) = when (activeChart) {
        FinanceChart.EQUITY -> Triple(
            "Equity GBP",
            "dotted line = invested baseline",
            series.equityPoints().size
        )
        FinanceChart.PNL -> Triple(
            "P&L GBP — daily",
            "green ≥ £${"%.0f".format(pnlThreshold)} · amber below",
            series.pnlPoints().size
        )
        FinanceChart.INCOME -> Triple(
            "Income 30d GBP — rolling",
            "red dotted = £${"%.0f".format(incomeTarget)} target",
            series.incomePoints().size
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(0.5.dp, DarkBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = DarkText,
                fontFamily = FontFamily.Serif
            )
            Text(
                "$pts pts",
                fontSize   = 9.sp,
                color      = DarkMuted,
                fontFamily = FontFamily.SansSerif
            )
        }
        Spacer(Modifier.height(10.dp))

        when (activeChart) {
            FinanceChart.EQUITY -> {
                val points = series.equityPoints()
                if (points.size < 2) DarkChartEmpty() else {
                    val invested = series.depositsMtd.filterNotNull().maxOrNull()
                        ?.let { points.first().second - it } ?: points.first().second
                    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                        drawEquityChart(points, invested)
                    }
                }
            }
            FinanceChart.PNL -> {
                val points = series.pnlPoints()
                if (points.isEmpty()) DarkChartEmpty() else {
                    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                        drawPnlBars(points, pnlThreshold)
                    }
                }
            }
            FinanceChart.INCOME -> {
                val points = series.incomePoints()
                if (points.size < 2) DarkChartEmpty() else {
                    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                        drawIncomeChart(points, incomeTarget)
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            fontSize   = 8.sp,
            color      = DarkMuted.copy(alpha = 0.7f),
            fontFamily = FontFamily.SansSerif
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Tap a stat card above to switch view",
            fontSize   = 8.sp,
            color      = DarkMuted.copy(alpha = 0.5f),
            fontFamily = FontFamily.SansSerif
        )
    }
}

@Composable
private fun DarkChartEmpty() {
    Box(
        Modifier.fillMaxWidth().height(130.dp).padding(vertical = 24.dp),
        Alignment.Center
    ) {
        Text("Not enough data", fontSize = 11.sp, color = DarkMuted)
    }
}

// ── Canvas drawing ────────────────────────────────────────────

private fun DrawScope.drawEquityChart(
    points:   List<Pair<String, Double>>,
    invested: Double
) {
    val n     = points.size
    val padL  = 32.dp.toPx(); val padR = 6.dp.toPx()
    val padT  = 8.dp.toPx();  val padB = 14.dp.toPx()
    val iw    = size.width - padL - padR
    val ih    = size.height - padT - padB
    val xStep = if (n > 1) iw / (n - 1).toFloat() else iw

    val values = points.map { it.second }
    val yMin   = minOf(values.min(), invested) * 0.998
    val yMax   = values.max() * 1.002
    val yRange = (yMax - yMin).coerceAtLeast(1.0)

    fun xp(i: Int)    = padL + i * xStep
    fun yp(v: Double) = (padT + ih - ((v - yMin) / yRange * ih)).toFloat()
        .coerceIn(padT, padT + ih)

    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            textSize  = 7.dp.toPx()
            color     = android.graphics.Color.parseColor("#888899")
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        canvas.nativeCanvas.drawText("£${"%.0f".format(yMax)}", padL - 4.dp.toPx(), padT + 6.dp.toPx(), paint)
        canvas.nativeCanvas.drawText("£${"%.0f".format(yMin)}", padL - 4.dp.toPx(), padT + ih + 4.dp.toPx(), paint)
    }

    val baseY = yp(invested)
    drawIntoCanvas { canvas ->
        val p = android.graphics.Paint().apply {
            color       = android.graphics.Color.parseColor(DarkBorder.toHexStr())
            strokeWidth = 1.dp.toPx()
            style       = android.graphics.Paint.Style.STROKE
            pathEffect  = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f)
        }
        canvas.nativeCanvas.drawLine(padL, baseY, size.width - padR, baseY, p)
    }

    val path = Path()
    points.forEachIndexed { i, (_, v) ->
        val x = xp(i); val y = yp(v)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, GreenChart, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

    points.forEachIndexed { i, (_, v) ->
        drawCircle(GreenChart, 2.5.dp.toPx(), Offset(xp(i), yp(v)))
    }
}

private fun DrawScope.drawPnlBars(
    points:       List<Pair<String, Double>>,
    pnlThreshold: Double
) {
    val n     = points.size
    val padL  = 32.dp.toPx(); val padR = 6.dp.toPx()
    val padT  = 8.dp.toPx();  val padB = 14.dp.toPx()
    val iw    = size.width - padL - padR
    val ih    = size.height - padT - padB

    val values = points.map { it.second }
    val yMin   = 0.0
    val yMax   = values.max() * 1.05
    val yRange = (yMax - yMin).coerceAtLeast(1.0)
    val slotW  = iw / n
    val barW   = slotW * 0.7f
    val gapW   = slotW * 0.15f

    fun xp(i: Int)    = padL + i * slotW + gapW
    fun yp(v: Double) = (padT + ih - ((v - yMin) / yRange * ih)).toFloat()
        .coerceIn(padT, padT + ih)

    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            textSize  = 7.dp.toPx()
            color     = android.graphics.Color.parseColor("#888899")
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        canvas.nativeCanvas.drawText("£${"%.0f".format(yMax)}", padL - 4.dp.toPx(), padT + 6.dp.toPx(), paint)
        canvas.nativeCanvas.drawText("£0", padL - 4.dp.toPx(), padT + ih + 4.dp.toPx(), paint)
    }

    val threshY = yp(pnlThreshold)
    drawIntoCanvas { canvas ->
        val p = android.graphics.Paint().apply {
            color       = android.graphics.Color.parseColor(AmberChart.toHexStr())
            strokeWidth = 1.2.dp.toPx()
            style       = android.graphics.Paint.Style.STROKE
            pathEffect  = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f)
        }
        canvas.nativeCanvas.drawLine(padL, threshY, size.width - padR, threshY, p)
    }

    points.forEachIndexed { i, (_, v) ->
        val barColor = if (v >= pnlThreshold) GreenChart else AmberChart
        val top      = yp(v)
        val bottom   = padT + ih
        drawRect(color = barColor, topLeft = Offset(xp(i), top), size = Size(barW, bottom - top))
    }
}

private fun DrawScope.drawIncomeChart(
    points:       List<Pair<String, Double>>,
    targetIncome: Double
) {
    val n     = points.size
    val padL  = 32.dp.toPx(); val padR = 6.dp.toPx()
    val padT  = 8.dp.toPx();  val padB = 14.dp.toPx()
    val iw    = size.width - padL - padR
    val ih    = size.height - padT - padB
    val xStep = if (n > 1) iw / (n - 1).toFloat() else iw

    val values = points.map { it.second }
    val yMin   = 0.0
    val yMax   = maxOf(values.max(), targetIncome) * 1.1
    val yRange = (yMax - yMin).coerceAtLeast(1.0)

    fun xp(i: Int)    = padL + i * xStep
    fun yp(v: Double) = (padT + ih - ((v - yMin) / yRange * ih)).toFloat()
        .coerceIn(padT, padT + ih)

    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            textSize  = 7.dp.toPx()
            color     = android.graphics.Color.parseColor("#888899")
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        canvas.nativeCanvas.drawText("£${"%.0f".format(yMax)}", padL - 4.dp.toPx(), padT + 6.dp.toPx(), paint)
        canvas.nativeCanvas.drawText("£0", padL - 4.dp.toPx(), padT + ih + 4.dp.toPx(), paint)
    }

    val targetY = yp(targetIncome)
    drawIntoCanvas { canvas ->
        val p = android.graphics.Paint().apply {
            color       = android.graphics.Color.parseColor(RedAlert.toHexStr())
            strokeWidth = 1.2.dp.toPx()
            style       = android.graphics.Paint.Style.STROKE
            pathEffect  = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f)
        }
        canvas.nativeCanvas.drawLine(padL, targetY, size.width - padR, targetY, p)
    }

    val path = Path()
    points.forEachIndexed { i, (_, v) ->
        val x = xp(i); val y = yp(v)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, BlueChart, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

    points.forEachIndexed { i, (_, v) ->
        drawCircle(BlueChart, 2.5.dp.toPx(), Offset(xp(i), yp(v)))
    }
}

// ── Alert card ────────────────────────────────────────────────

@Composable
private fun DarkFinanceAlertCard(alert: WhoopAlert) {
    val isCritical  = alert.severity == "critical"
    val bgColor     = if (isCritical) Color(0xFF2D1515) else AmberBgD
    val borderColor = if (isCritical) Color(0xFF7A2E2E)  else AmberBorderD
    val textColor   = if (isCritical) Color(0xFFFF9E9E)  else AmberTextD
    val badgeBg     = if (isCritical) Color(0xFF8B1A1A)  else AmberBadgeD

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(badgeBg)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                alert.severity,
                fontSize   = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color      = textColor,
                fontFamily = FontFamily.SansSerif
            )
        }
        Text(
            alert.message,
            modifier   = Modifier.weight(1f),
            fontSize   = 10.sp,
            lineHeight  = 14.sp,
            color      = textColor,
            fontFamily = FontFamily.SansSerif
        )
    }
}

// ── Buddie synthesis ──────────────────────────────────────────

@Composable
private fun DarkFinanceInsightSection(
    synthesis:  FinanceSynthesisResponse?,
    synLoading: Boolean,
    synError:   String?,
    context:    Context? = null,
    onRequest:  () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (synthesis == null && !synLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(GreenChart)
                    .clickable { onRequest() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Get insight from Buddie",
                    fontSize   = 12.sp,
                    color      = DarkBg,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
        if (synLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = GreenChart, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text("Buddie is thinking…", fontSize = 11.sp, color = DarkMuted, fontFamily = FontFamily.SansSerif)
            }
        }
        synError?.let {
            Text(
                "Could not load insight: $it",
                fontSize   = 10.sp,
                color      = DarkMuted,
                fontFamily = FontFamily.SansSerif,
                modifier   = Modifier.padding(vertical = 8.dp)
            )
        }
        synthesis?.let { syn ->
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AmberBgD)
                    .border(0.5.dp, AmberBorderD, RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text(
                    "Buddie insight · ${syn.range}",
                    fontSize   = 9.sp,
                    color      = AmberChart,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stripMarkdown(syn.synthesis),
                    fontSize   = 11.sp,
                    lineHeight  = 17.sp,
                    color      = AmberTextD,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${syn.dataPoints} data points",
                    fontSize   = 9.sp,
                    color      = DarkMuted,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(Modifier.height(10.dp))
                if (context != null) {
                    var copied by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(AmberBorderD)
                            .clickable {
                                copyInsightToClipboard(context, syn.synthesis, syn.range, syn.dataPoints)
                                copied = true
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (copied) "✓ Copied" else "⎘ Copy insight",
                            fontSize   = 10.sp,
                            color      = AmberTextD,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    LaunchedEffect(copied) {
                        if (copied) {
                            kotlinx.coroutines.delay(2000)
                            copied = false
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Finance Intelligence tile ─────────────────────────────────
// Compact summary: health state + detected patterns + correlations.
// Consistent with DarkHealthIntelligenceTile in HealthAnalyticsScreen.
// Hidden entirely if patterns == null.

@Composable
private fun DarkFinanceIntelligenceTile(patterns: FinancePatterns) {
    val hasState    = patterns.healthState != null
    val hasPatterns = patterns.detected.isNotEmpty()
    val hasCorr     = patterns.correlations != null
    if (!hasState && !hasPatterns && !hasCorr) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(0.5.dp, DarkBorder, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text(
            text          = "Finance intelligence",
            fontSize      = 10.sp,
            color         = DarkMuted,
            fontFamily    = FontFamily.SansSerif,
            letterSpacing = 0.07.sp
        )

        // ── Health state ──────────────────────────────────────
        patterns.healthState?.let { state ->
            Spacer(Modifier.height(10.dp))
            val stateColor = when (state.zone) {
                "green" -> Color(0xFF4A7C59)
                "amber" -> Color(0xFFBA7517)
                else    -> RedAlert
            }
            Text(
                text       = state.label,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Medium,
                color      = stateColor,
                fontFamily = FontFamily.Serif
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text       = state.reason,
                fontSize   = 10.sp,
                color      = DarkMuted,
                fontFamily = FontFamily.SansSerif,
                maxLines   = 1,
                overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        // ── Detected patterns ─────────────────────────────────
        if (hasPatterns) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text          = "Patterns",
                fontSize      = 9.sp,
                color         = DarkMuted,
                fontFamily    = FontFamily.SansSerif,
                letterSpacing = 0.05.sp
            )
            Spacer(Modifier.height(6.dp))
            patterns.detected.take(3).forEach { pattern ->
                val isCritical = pattern.severity == "critical"
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isCritical) Color(0xFF8B1A1A) else AmberBadgeD)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text       = pattern.severity,
                            fontSize   = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (isCritical) Color(0xFFFF9E9E) else AmberTextD,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Text(
                        text       = financePatternName(pattern.pattern),
                        fontSize   = 10.sp,
                        color      = DarkText,
                        fontFamily = FontFamily.SansSerif,
                        modifier   = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── Correlations ──────────────────────────────────────
        patterns.correlations?.let { corr ->
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text          = "Correlations · ${corr.windowDays}d",
                fontSize      = 9.sp,
                color         = DarkMuted,
                fontFamily    = FontFamily.SansSerif,
                letterSpacing = 0.05.sp
            )
            Spacer(Modifier.height(6.dp))

            // Only render correlations where min_data_points_met = true
            val corrRows = buildList {
                val metDeposits = corr.minDataPointsMet["deposits_vs_income"] == true
                val metIncome   = corr.minDataPointsMet["income_consistency"]  == true
                if (metDeposits && corr.depositsVsIncome != null)
                    add(Triple("Deposits vs Income", corr.depositsVsIncome, financeCorrDisplay(corr.depositsVsIncome)))
                if (metIncome && corr.incomeConsistency != null)
                    add(Triple("Income consistency", corr.incomeConsistency, financeCorrDisplay(corr.incomeConsistency)))
            }

            if (corrRows.isEmpty()) {
                Text(
                    "Insufficient data for correlations",
                    fontSize   = 9.sp,
                    color      = DarkMuted.copy(alpha = 0.6f),
                    fontFamily = FontFamily.SansSerif
                )
            } else {
                corrRows.forEachIndexed { index, (label, value, display) ->
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 10.sp, color = DarkMuted, fontFamily = FontFamily.SansSerif)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                "${"%.2f".format(value)}",
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color      = display.first,
                                fontFamily = FontFamily.Serif
                            )
                            Text(
                                display.second,
                                fontSize   = 8.sp,
                                color      = display.first.copy(alpha = 0.8f),
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                    if (index < corrRows.size - 1)
                        HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ── Finance pattern name mapping ──────────────────────────────

private fun financePatternName(raw: String): String = when (raw) {
    "income_below_target"     -> "Income below target"
    "income_compression"      -> "Income compression"
    "yield_concentration"     -> "Yield concentration"
    "contribution_dependency" -> "Contribution dependency"
    "pnl_volatility"          -> "P&L volatility"
    else -> raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

// ── Finance correlation strength ──────────────────────────────

private fun financeCorrDisplay(value: Double): Pair<Color, String> {
    val a = abs(value)
    return when {
        a >= 0.7 -> Pair(if (value >= 0) GreenChart else RedAlert, "strong")
        a >= 0.4 -> Pair(AmberChart, "moderate")
        else     -> Pair(DarkMuted,  "weak")
    }
}

// ── Share insight as text file ────────────────────────────────

private fun copyInsightToClipboard(
    context:    Context,
    synthesis:  String,
    range:      String,
    dataPoints: Int
) {
    val text = buildString {
        appendLine("Buddie Finance Insight · $range")
        appendLine("$dataPoints data points")
        appendLine()
        appendLine(synthesis
            .replace(Regex("#{1,6}\\s*"), "")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .trim())
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Buddie Finance Insight", text))
}

// ── Helpers ───────────────────────────────────────────────────

@Composable
private fun DarkSectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize      = 10.sp,
        color         = DarkMuted,
        fontFamily    = FontFamily.SansSerif,
        letterSpacing = 0.07.sp,
        modifier      = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun DarkFinanceErrorCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(0.5.dp, DarkBorder, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Text(
            "Could not load analytics\n$message",
            fontSize   = 12.sp,
            color      = DarkMuted,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 18.sp
        )
    }
}