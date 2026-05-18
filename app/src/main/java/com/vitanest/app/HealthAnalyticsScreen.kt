package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// HealthAnalyticsScreen — dark theme, pinned header, tap-to-switch chart,
// null-safe all-time series, alert auto-expand (critical open / warning collapsed),
// Buddie insight fully visible. ☘️

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vitanest.app.data.remote.WhoopAlert
import com.vitanest.app.data.remote.WhoopAnalyticsSeries
import com.vitanest.app.data.remote.WhoopBaselines
import com.vitanest.app.data.remote.WhoopResponse
import com.vitanest.app.data.remote.WhoopSynthesisResponse
import com.vitanest.app.data.remote.WhoopThresholds
import com.vitanest.app.data.repository.VitaClawRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ── Dark palette ──────────────────────────────────────────────

private val DarkBg       = Color(0xFF0F1117)
private val DarkCard     = Color(0xFF1A1D26)
private val DarkBorder   = Color(0xFF2A2D36)
private val DarkText     = Color(0xFFE8E4DC)
private val DarkMuted    = Color(0xFF888899)
private val GreenChart   = Color(0xFF2D9E6B)
private val GreenDeepC   = Color(0xFF3B9E1F)
private val AmberChart   = Color(0xFFD4A017)
private val CoralChart   = Color(0xFFD4845A)
private val RedAlert     = Color(0xFFE24B4A)
private val RedBgD       = Color(0xFF2D1515)
private val RedBorderD   = Color(0xFF7A2E2E)
private val RedTextD     = Color(0xFFFF9E9E)
private val RedBadgeBgD  = Color(0xFF8B1A1A)
private val AmberBgD     = Color(0xFF2D2000)
private val AmberBorderD = Color(0xFF7A5A00)
private val AmberTextD   = Color(0xFFFFCF7A)
private val AmberBadgeD  = Color(0xFF6B4A00)

// ── Metric definitions ────────────────────────────────────────

private enum class HealthMetric { SPO2, HRV, RHR, RECOVERY }

private data class Threshold(val value: Float, val color: Color, val label: String)

private data class MetricConfig(
    val metric:     HealthMetric,
    val label:      String,
    val unit:       String,
    val lineColor:  Color,
    val yMin:       Float,
    val yMax:       Float,
    val thresholds: List<Threshold>,
    val dotColor:   (Float) -> Color
)

private val METRIC_CONFIGS = listOf(
    MetricConfig(HealthMetric.SPO2, "SPO₂", "%", GreenChart, 84f, 100f,
        listOf(Threshold(92f, AmberChart, "92% warning"), Threshold(90f, RedAlert, "90% critical")),
        { v -> if (v < 90f) RedAlert else if (v < 92f) AmberChart else GreenChart }
    ),
    MetricConfig(HealthMetric.HRV, "HRV", "ms", GreenChart, 30f, 70f,
        listOf(Threshold(43f, RedAlert, "43ms")),
        { v -> if (v < 43f) RedAlert else GreenChart }
    ),
    MetricConfig(HealthMetric.RHR, "RHR", "bpm", CoralChart, 44f, 66f,
        listOf(Threshold(53f, DarkMuted, "53 avg")),
        { _ -> CoralChart }
    ),
    MetricConfig(HealthMetric.RECOVERY, "Recovery", "%", GreenDeepC, 0f, 100f,
        listOf(Threshold(67f, GreenDeepC, "green"), Threshold(34f, AmberChart, "amber")),
        { v -> if (v >= 67f) GreenDeepC else if (v >= 34f) AmberChart else RedAlert }
    )
)

// ── Null-safe series helpers ──────────────────────────────────

private fun WhoopAnalyticsSeries.spo2Points()     = dates.zip(spo2Pct).mapNotNull     { (d, v) -> v?.let { d to it } }
private fun WhoopAnalyticsSeries.hrvPoints()      = dates.zip(hrvMs).mapNotNull       { (d, v) -> v?.let { d to it } }
private fun WhoopAnalyticsSeries.rhrPoints()      = dates.zip(rhrBpm).mapNotNull      { (d, v) -> v?.let { d to it } }
private fun WhoopAnalyticsSeries.recoveryPoints() = dates.zip(recovery).mapNotNull    { (d, v) -> v?.let { d to it } }

// ── ViewModel ─────────────────────────────────────────────────

class HealthAnalyticsViewModel(private val repository: VitaClawRepository) : ViewModel() {

    private val _whoop      = MutableStateFlow<WhoopResponse?>(null)
    private val _isLoading  = MutableStateFlow(true)
    private val _error      = MutableStateFlow<String?>(null)
    private val _range      = MutableStateFlow("14d")
    private val _synthesis  = MutableStateFlow<WhoopSynthesisResponse?>(null)
    private val _synLoading = MutableStateFlow(false)
    private val _synError   = MutableStateFlow<String?>(null)
    private val synthesisCache = mutableMapOf<String, WhoopSynthesisResponse>()

    val whoop:      StateFlow<WhoopResponse?>          = _whoop
    val isLoading:  StateFlow<Boolean>                 = _isLoading
    val error:      StateFlow<String?>                 = _error
    val range:      StateFlow<String>                  = _range
    val synthesis:  StateFlow<WhoopSynthesisResponse?> = _synthesis
    val synLoading: StateFlow<Boolean>                 = _synLoading
    val synError:   StateFlow<String?>                 = _synError

    init { loadAnalytics("14d") }

    fun loadAnalytics(range: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            _range.value     = range
            _synthesis.value = synthesisCache[range]
            repository.getWhoopAnalytics(range).fold(
                onSuccess = { _whoop.value = it },
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
            repository.getWhoopSynthesis(r).fold(
                onSuccess = { synthesisCache[r] = it; _synthesis.value = it },
                onFailure = { _synError.value = it.message }
            )
            _synLoading.value = false
        }
    }
}

// ── Screen ────────────────────────────────────────────────────

@Composable
fun HealthAnalyticsScreen(
    navController: NavController,
    repository:    VitaClawRepository
) {
    val vm: HealthAnalyticsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HealthAnalyticsViewModel(repository) as T
        }
    )

    val whoop      by vm.whoop.collectAsState()
    val isLoading  by vm.isLoading.collectAsState()
    val error      by vm.error.collectAsState()
    val range      by vm.range.collectAsState()
    val synthesis  by vm.synthesis.collectAsState()
    val synLoading by vm.synLoading.collectAsState()
    val synError   by vm.synError.collectAsState()
    var activeMetric by remember { mutableStateOf(HealthMetric.SPO2) }

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
                    verticalAlignment = Alignment.CenterVertically,
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkText, modifier = Modifier.size(16.dp))
                    }
                    Column {
                        Text("Health analytics",
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Serif,
                            color      = DarkText)
                        val rangeLabel = if (range == "14d") "14-day view" else "Full history"
                        val syncLabel  = whoop?.lastUpdated?.take(10) ?: ""
                        Text("$rangeLabel${if (syncLabel.isNotBlank()) " · $syncLabel" else ""}",
                            fontSize = 11.sp, color = DarkMuted, fontFamily = FontFamily.SansSerif)
                    }
                }
                HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("14d" to "14 days", "all" to "All time").forEach { (key, label) ->
                        val sel = range == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (sel) GreenChart else DarkCard)
                                .border(0.5.dp, if (sel) GreenChart else DarkBorder, RoundedCornerShape(20.dp))
                                .clickable { vm.loadAnalytics(key) }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(label,
                                fontSize = 11.sp,
                                color    = if (sel) DarkBg else DarkMuted,
                                fontFamily = FontFamily.SansSerif)
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
                isLoading -> Box(Modifier.fillMaxWidth().height(300.dp), Alignment.Center) {
                    CircularProgressIndicator(color = GreenChart, strokeWidth = 2.dp)
                }
                error != null -> DarkErrorCard(error ?: "Unknown error")
                whoop != null -> {
                    val data = whoop!!
                    data.series?.let { series ->
                        DarkStatStrip(series, data.thresholds, activeMetric) { activeMetric = it }
                        DarkMergedChart(series, activeMetric, data.thresholds)
                    }
                    if (data.alerts.isNotEmpty()) {
                        DarkSectionLabel("Alert events")
                        data.alerts.forEach { alert ->
                            DarkAlertCard(alert)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    data.baselines?.let { bl ->
                        DarkSectionLabel("30-day baselines")
                        DarkBaselinesCard(bl)
                    }
                    Spacer(Modifier.height(16.dp))
                    DarkInsightSection(synthesis, synLoading, synError) { vm.loadSynthesis() }
                }
            }
        }
    }
}

// ── Stat strip ────────────────────────────────────────────────

@Composable
private fun DarkStatStrip(
    series:       WhoopAnalyticsSeries,
    thresholds:   WhoopThresholds?,
    activeMetric: HealthMetric,
    onSelect:     (HealthMetric) -> Unit
) {
    val avgSpo2 = series.spo2Points().map     { it.second }.let { if (it.isEmpty()) 0.0 else it.average() }
    val avgHrv  = series.hrvPoints().map      { it.second }.let { if (it.isEmpty()) 0.0 else it.average() }
    val avgRhr  = series.rhrPoints().map      { it.second }.let { if (it.isEmpty()) 0.0 else it.average() }
    val avgRec  = series.recoveryPoints().map { it.second }.let { if (it.isEmpty()) 0.0 else it.average() }

    val warnPct   = thresholds?.spo2WarningPct?.toFloat() ?: 92f
    val spo2Color = if (avgSpo2 < warnPct) AmberChart else GreenChart
    val recColor  = if (avgRec >= 67) GreenChart else if (avgRec >= 34) AmberChart else RedAlert

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DarkStatCard(Modifier.weight(1f), "Avg SpO₂",  "${"%.1f".format(avgSpo2)}%",  spo2Color,  activeMetric == HealthMetric.SPO2)     { onSelect(HealthMetric.SPO2) }
        DarkStatCard(Modifier.weight(1f), "Avg HRV",   "${"%.0f".format(avgHrv)}ms",  GreenChart, activeMetric == HealthMetric.HRV)      { onSelect(HealthMetric.HRV) }
        DarkStatCard(Modifier.weight(1f), "Avg RHR",   "${"%.0f".format(avgRhr)}bpm", CoralChart, activeMetric == HealthMetric.RHR)      { onSelect(HealthMetric.RHR) }
        DarkStatCard(Modifier.weight(1f), "Avg Rec",   "${"%.0f".format(avgRec)}%",   recColor,   activeMetric == HealthMetric.RECOVERY) { onSelect(HealthMetric.RECOVERY) }
    }
}

@Composable
private fun DarkStatCard(
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
            .border(if (selected) 1.5.dp else 0.5.dp,
                if (selected) GreenChart else DarkBorder,
                RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 5.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Serif,
            color      = valueColor)
        Text(label,
            fontSize   = 8.sp,
            color      = DarkMuted,
            fontFamily = FontFamily.SansSerif)
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(5.dp).clip(CircleShape).background(valueColor))
    }
}

// ── Merged chart ──────────────────────────────────────────────

@Composable
private fun DarkMergedChart(
    series:       WhoopAnalyticsSeries,
    activeMetric: HealthMetric,
    thresholds:   WhoopThresholds?
) {
    val config = METRIC_CONFIGS.first { it.metric == activeMetric }

    val points: List<Pair<String, Float>> = when (activeMetric) {
        HealthMetric.SPO2     -> series.spo2Points().map     { it.first to it.second.toFloat() }
        HealthMetric.HRV      -> series.hrvPoints().map      { it.first to it.second.toFloat() }
        HealthMetric.RHR      -> series.rhrPoints().map      { it.first to it.second.toFloat() }
        HealthMetric.RECOVERY -> series.recoveryPoints().map { it.first to it.second.toFloat() }
    }

    val effectiveThresholds: List<Threshold> = when (activeMetric) {
        HealthMetric.SPO2 -> listOf(
            Threshold(thresholds?.spo2WarningPct?.toFloat()  ?: 92f, AmberChart, "${thresholds?.spo2WarningPct ?: 92}% warning"),
            Threshold(thresholds?.spo2CriticalPct?.toFloat() ?: 90f, RedAlert,   "${thresholds?.spo2CriticalPct ?: 90}% critical")
        )
        HealthMetric.HRV  -> listOf(
            Threshold(thresholds?.hrvSuppressedMs?.toFloat() ?: 43f, RedAlert, "${thresholds?.hrvSuppressedMs ?: 43}ms")
        )
        else -> config.thresholds
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
            Text("${config.label} — ${config.unit}",
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = DarkText,
                fontFamily = FontFamily.Serif)
            Text("${points.size} pts",
                fontSize = 9.sp, color = DarkMuted, fontFamily = FontFamily.SansSerif)
        }
        Spacer(Modifier.height(10.dp))

        if (points.size < 2) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), Alignment.Center) {
                Text("Not enough data", fontSize = 11.sp, color = DarkMuted)
            }
        } else {
            Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
                drawMetricChart(points, config, effectiveThresholds)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            effectiveThresholds.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Canvas(Modifier.size(width = 14.dp, height = 8.dp)) {
                        val y = size.height / 2f
                        drawLine(t.color, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 3f)))
                    }
                    Text(t.label, fontSize = 8.sp, color = DarkMuted, fontFamily = FontFamily.SansSerif)
                }
            }
            if (activeMetric != HealthMetric.RHR) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(RedAlert))
                    Text("breach", fontSize = 8.sp, color = DarkMuted, fontFamily = FontFamily.SansSerif)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("Tap a metric card above to switch view",
            fontSize = 8.sp, color = DarkMuted.copy(alpha = 0.6f), fontFamily = FontFamily.SansSerif)
    }
}

private fun DrawScope.drawMetricChart(
    points:     List<Pair<String, Float>>,
    config:     MetricConfig,
    thresholds: List<Threshold>
) {
    val n      = points.size
    val padL   = 30.dp.toPx()
    val padR   = 6.dp.toPx()
    val padT   = 8.dp.toPx()
    val padB   = 14.dp.toPx()
    val iw     = size.width - padL - padR
    val ih     = size.height - padT - padB
    val xStep  = if (n > 1) iw / (n - 1).toFloat() else iw
    val yRange = config.yMax - config.yMin

    fun xp(i: Int)   = padL + i * xStep
    fun yp(v: Float) = (padT + ih - ((v - config.yMin) / yRange) * ih).coerceIn(padT, padT + ih)

    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            textSize  = 7.dp.toPx()
            color     = android.graphics.Color.parseColor("#888899")
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        canvas.nativeCanvas.drawText("${config.yMax.toInt()}", padL - 4.dp.toPx(), padT + 6.dp.toPx(), paint)
        canvas.nativeCanvas.drawText("${config.yMin.toInt()}", padL - 4.dp.toPx(), padT + ih + 4.dp.toPx(), paint)
    }

    thresholds.forEach { t ->
        val ty = yp(t.value)
        drawIntoCanvas { canvas ->
            val p = android.graphics.Paint().apply {
                color       = android.graphics.Color.parseColor(t.color.toHex())
                strokeWidth = 1.2.dp.toPx()
                style       = android.graphics.Paint.Style.STROKE
                pathEffect  = android.graphics.DashPathEffect(floatArrayOf(7f, 4f), 0f)
            }
            canvas.nativeCanvas.drawLine(padL, ty, size.width - padR, ty, p)
        }
    }

    val path = Path()
    points.forEachIndexed { i, (_, v) ->
        val x = xp(i); val y = yp(v)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, config.lineColor, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

    points.forEachIndexed { i, (_, v) ->
        val x  = xp(i); val y = yp(v)
        val dc = config.dotColor(v)
        val r  = if (dc != config.lineColor) 4.dp.toPx() else 2.5.dp.toPx()
        drawCircle(dc, r, Offset(x, y))
        if (dc != config.lineColor) drawCircle(DarkCard, 1.8.dp.toPx(), Offset(x, y))
    }
}

private fun Color.toHex(): String {
    val r = (red   * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue  * 255).toInt()
    return "#%02X%02X%02X".format(r, g, b)
}

// ── Alert card ────────────────────────────────────────────────

@Composable
private fun DarkAlertCard(alert: WhoopAlert) {
    val isCritical = alert.severity == "critical"
    var expanded by remember(alert.rule) { mutableStateOf(isCritical) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCritical) RedBgD else AmberBgD)
            .border(0.5.dp, if (isCritical) RedBorderD else AmberBorderD, RoundedCornerShape(8.dp))
            .then(if (!isCritical) Modifier.clickable { expanded = !expanded } else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isCritical) RedBadgeBgD else AmberBadgeD)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(alert.severity,
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isCritical) RedTextD else AmberTextD,
                    fontFamily = FontFamily.SansSerif)
            }
            Text(alert.message,
                modifier  = Modifier.weight(1f),
                fontSize  = 10.sp,
                lineHeight = 14.sp,
                color     = if (isCritical) RedTextD else AmberTextD,
                fontFamily = FontFamily.SansSerif)
            if (!isCritical) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = AmberTextD, modifier = Modifier.size(16.dp)
                )
            }
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            val ctx = buildAlertContext(alert)
            if (ctx.isNotBlank()) {
                Text(ctx,
                    modifier   = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                    fontSize   = 9.sp,
                    lineHeight  = 13.sp,
                    fontStyle  = FontStyle.Italic,
                    color      = if (isCritical) RedTextD.copy(alpha = 0.8f) else AmberTextD.copy(alpha = 0.8f),
                    fontFamily = FontFamily.SansSerif)
            }
        }
    }
}

private fun buildAlertContext(alert: WhoopAlert): String =
    alert.contextList.mapNotNull { ctx ->
        val date = ctx.date ?: return@mapNotNull null
        buildString {
            append(date.takeLast(5))
            ctx.spo2?.let       { append(" SPO₂ ${"%.1f".format(it)}%") }
            ctx.hrv?.let        { append(" HRV ${"%.0f".format(it)}ms") }
            ctx.prevStrain?.let { append(" strain ${"%.1f".format(it)}") }
            ctx.daysCount?.let  { append(" ($it days)") }
        }
    }.joinToString(" · ")

// ── Baselines ─────────────────────────────────────────────────

@Composable
private fun DarkBaselinesCard(baselines: WhoopBaselines) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(0.5.dp, DarkBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        DarkBaselineItem("${baselines.spo230dAvg}%",     "SpO₂ 30d")
        DarkBaselineItem("${baselines.hrv30dAvgMs}ms",   "HRV 30d")
        DarkBaselineItem("${baselines.rhr30dAvgBpm}bpm", "RHR 30d")
        DarkBaselineItem("${baselines.recovery30dAvg}%", "Rec 30d")
    }
}

@Composable
private fun DarkBaselineItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Serif,
            color      = DarkText)
        Text(label, fontSize = 9.sp, color = DarkMuted, fontFamily = FontFamily.SansSerif)
    }
}

// ── Insight ───────────────────────────────────────────────────

@Composable
private fun DarkInsightSection(
    synthesis:  WhoopSynthesisResponse?,
    synLoading: Boolean,
    synError:   String?,
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
                Text("Get insight from Buddie",
                    fontSize   = 12.sp,
                    color      = DarkBg,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif)
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
            Text("Could not load insight: $it", fontSize = 10.sp, color = DarkMuted,
                fontFamily = FontFamily.SansSerif, modifier = Modifier.padding(vertical = 8.dp))
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
                Text("Buddie insight · ${syn.range}",
                    fontSize   = 9.sp,
                    color      = AmberChart,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif)
                Spacer(Modifier.height(8.dp))
                // No maxLines — full Buddie text always shown
                Text(syn.synthesis,
                    fontSize   = 11.sp,
                    lineHeight  = 17.sp,
                    color      = AmberTextD,
                    fontFamily = FontFamily.SansSerif)
                Spacer(Modifier.height(8.dp))
                Text("${syn.dataPoints} data points",
                    fontSize = 9.sp, color = DarkMuted, fontFamily = FontFamily.SansSerif)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────

@Composable
private fun DarkSectionLabel(text: String) {
    Text(text.uppercase(),
        fontSize      = 10.sp,
        color         = DarkMuted,
        fontFamily    = FontFamily.SansSerif,
        letterSpacing = 0.07.sp,
        modifier      = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp))
}

@Composable
private fun DarkErrorCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(0.5.dp, DarkBorder, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Text("Could not load analytics\n$message",
            fontSize = 12.sp, color = DarkMuted,
            fontFamily = FontFamily.SansSerif, lineHeight = 18.sp)
    }
}