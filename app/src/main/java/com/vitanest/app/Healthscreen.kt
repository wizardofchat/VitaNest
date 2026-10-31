package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// HealthScreen — Whoop dashboard: recovery ring, metrics, sleep, workout, date nav
// Updated: analytics link row added below vitals ☘️
// Updated 30 Oct 2026: device-neutral — missing shared metrics show "—";
//          Whoop-only tiles (recovery, strain, sleep score/efficiency,
//          skin temp, disturbances) are hidden when null (Garmin days)

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vitanest.app.data.remote.WhoopResponse
import com.vitanest.app.data.remote.presentOrNull
import com.vitanest.app.data.repository.VitaClawRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ── Palette ───────────────────────────────────────────────────

private val Cream        = Color(0xFFF2EFE8)
private val NearBlack    = Color(0xFF111111)
private val MidGrey      = Color(0xFF888888)
private val LightRule    = Color(0xFFC8C4BB)
private val GreenDark    = Color(0xFF2D6A4F)
private val GreenDeep    = Color(0xFF1B4332)
private val AmberWarm    = Color(0xFFD4A017)
private val White        = Color(0xFFFFFFFF)
private val PillGreenBg  = Color(0xFFEAF3DE)
private val PillGreenTxt = Color(0xFF3B6D11)
private val PillAmberBg  = Color(0xFFFAEEDA)
private val PillAmberTxt = Color(0xFF854F0B)
private val PillRedBg    = Color(0xFFFCEBEB)
private val PillRedTxt   = Color(0xFFA32D2D)

// ── ViewModel ─────────────────────────────────────────────────

class HealthViewModel(private val repository: VitaClawRepository) : ViewModel() {

    private val _whoop        = MutableStateFlow<WhoopResponse?>(null)
    private val _isLoading    = MutableStateFlow(false)
    private val _error        = MutableStateFlow<String?>(null)
    private val _selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())

    val whoop:        StateFlow<WhoopResponse?> = _whoop
    val isLoading:    StateFlow<Boolean>        = _isLoading
    val error:        StateFlow<String?>        = _error
    val selectedDate: StateFlow<LocalDate>      = _selectedDate

    init { loadWhoop() }

    fun loadWhoop(date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            _isLoading.value    = true
            _error.value        = null
            _selectedDate.value = date
            val dateParam = if (date == LocalDate.now()) null
            else date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            repository.getWhoop(dateParam).fold(
                onSuccess = { _whoop.value = it },
                onFailure = { _error.value = it.message }
            )
            _isLoading.value = false
        }
    }

    fun goToPreviousDay() {
        val prev = _selectedDate.value.minusDays(1)
        if (!prev.isBefore(LocalDate.now().minusDays(30))) loadWhoop(prev)
    }

    fun goToNextDay() {
        val next = _selectedDate.value.plusDays(1)
        if (!next.isAfter(LocalDate.now())) loadWhoop(next)
    }
}

// ── Screen ────────────────────────────────────────────────────

@Composable
fun HealthScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    val vm: HealthViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HealthViewModel(repository) as T
        }
    )

    val whoop        by vm.whoop.collectAsState()
    val isLoading    by vm.isLoading.collectAsState()
    val error        by vm.error.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    val isToday      = selectedDate == LocalDate.now()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
    ) {
        // ── Scrollable content ────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp)
        ) {
            HealthHeader(selectedDate = selectedDate, isToday = isToday)
            DateNavigator(
                selectedDate = selectedDate,
                isToday      = isToday,
                onPrevious   = { vm.goToPreviousDay() },
                onNext       = { vm.goToNextDay() },
                onTodayTap   = { if (!isToday) vm.loadWhoop(LocalDate.now()) }
            )
            HorizontalDivider(
                color    = LightRule,
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(
                        modifier         = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = GreenDark, strokeWidth = 2.dp)
                    }
                }
                error != null -> {
                    HealthErrorCard(message = error ?: "Unknown error")
                }
                whoop != null -> {
                    val data = whoop!!
                    RecoverySection(data)
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = LightRule, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    Spacer(Modifier.height(16.dp))

                    if (data.lastWorkout.isNotBlank()) {
                        HealthSectionLabel("Last workout")
                        WorkoutCard(data)
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = LightRule, thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(Modifier.height(16.dp))
                    }

                    val hasSleepCards  = data.sleepPerformance.presentOrNull() != null ||
                                         data.sleepEfficiency.presentOrNull() != null
                    val hasSleepStages = data.remMin.presentOrNull() != null ||
                                         data.deepMin.presentOrNull() != null
                    if (hasSleepCards || hasSleepStages) {
                        HealthSectionLabel("Sleep")
                        if (hasSleepCards) {
                            SleepCardsRow(data)
                            Spacer(Modifier.height(10.dp))
                        }
                        if (hasSleepStages) {
                            SleepStagesCard(data)
                            Spacer(Modifier.height(16.dp))
                        }
                        HorizontalDivider(color = LightRule, thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(Modifier.height(16.dp))
                    }

                    HealthSectionLabel("Vitals")
                    VitalsRow(data)
                    Spacer(Modifier.height(16.dp))

                    // ── Analytics link row ────────────────────────────────
                    HorizontalDivider(color = LightRule, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    Spacer(Modifier.height(12.dp))
                    AnalyticsLinkRow(onClick = { navController.navigate("health_analytics") })
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // ── Bottom nav ────────────────────────────────────────
        InkBottomNav(
            current       = "health",
            navController = navController,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

// ── Analytics link row ────────────────────────────────────────

@Composable
private fun AnalyticsLinkRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .border(0.5.dp, LightRule, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Health analytics",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif,
                color      = NearBlack
            )
            Text(
                "SPO₂ trends · HRV · alert events",
                fontSize   = 10.sp,
                color      = MidGrey,
                fontFamily = FontFamily.SansSerif
            )
        }
        Icon(
            imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "View analytics",
            tint               = MidGrey,
            modifier           = Modifier.size(16.dp)
        )
    }
}

// ── Header ────────────────────────────────────────────────────

@Composable
private fun HealthHeader(selectedDate: LocalDate, isToday: Boolean) {
    val dayLabel = when {
        isToday -> "Today"
        selectedDate == LocalDate.now().minusDays(1) -> "Yesterday"
        else -> selectedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK) +
                " ${selectedDate.dayOfMonth} " +
                selectedDate.month.getDisplayName(TextStyle.SHORT, Locale.UK)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text("Health",
                fontSize   = 26.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif,
                color      = NearBlack)
            Text(dayLabel,
                fontSize   = 13.sp,
                color      = MidGrey,
                fontFamily = FontFamily.SansSerif)
        }
    }
}

// ── Date navigator ────────────────────────────────────────────

@Composable
private fun DateNavigator(
    selectedDate: LocalDate,
    isToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTodayTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(0.5.dp, LightRule, CircleShape)
                .clickable { onPrevious() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Previous day",
                tint = NearBlack, modifier = Modifier.size(16.dp))
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isToday) NearBlack else White)
                .border(0.5.dp, LightRule, RoundedCornerShape(20.dp))
                .clickable { onTodayTap() }
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text("Today",
                fontSize   = 13.sp,
                color      = if (isToday) Cream else MidGrey,
                fontFamily = FontFamily.SansSerif)
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(0.5.dp, LightRule, CircleShape)
                .clickable(enabled = !isToday) { onNext() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next day",
                tint = if (isToday) LightRule else NearBlack,
                modifier = Modifier.size(16.dp))
        }
    }
}

// ── Recovery ring + tiles ─────────────────────────────────────

@Composable
private fun RecoverySection(data: WhoopResponse) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Whoop-only: ring and strain tile are hidden when null
        data.recoveryScore.presentOrNull()?.let { RecoveryRing(score = it) }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier            = Modifier.weight(1f)
        ) {
            MetricTile(value = data.hrvRmssdMilli.presentOrNull()?.let { "%.1f ms".format(it) } ?: "—",
                label = "HRV", dotColor = GreenDark)
            data.strain.presentOrNull()?.let {
                MetricTile(value = "%.1f".format(it), label = "Strain", dotColor = AmberWarm)
            }
            MetricTile(value = data.restingHeartRate.presentOrNull()?.let { "%.0f bpm".format(it) } ?: "—",
                label = "Resting HR", dotColor = MidGrey)
        }
    }
}

@Composable
private fun RecoveryRing(score: Float) {
    val animatedSweep by animateFloatAsState(
        targetValue   = (score / 100f) * 300f,
        animationSpec = tween(1000),
        label         = "recovery_ring"
    )
    val ringColor = when {
        score >= 67 -> GreenDark
        score >= 34 -> AmberWarm
        else        -> Color(0xFFE24B4A)
    }
    Box(modifier = Modifier.size(130.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(130.dp)) {
            val stroke  = 14.dp.toPx()
            val padding = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(padding, padding)
            drawArc(color = LightRule, startAngle = 120f, sweepAngle = 300f,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round))
            drawArc(color = ringColor, startAngle = 120f, sweepAngle = animatedSweep,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toInt().toString(),
                fontSize = 30.sp, fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif, color = NearBlack)
            Text("recovery",
                fontSize = 11.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
        }
    }
}

@Composable
private fun MetricTile(value: String, label: String, dotColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .border(0.5.dp, LightRule, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Column {
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif, color = NearBlack, lineHeight = 18.sp)
            Text(label, fontSize = 10.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
        }
    }
}

// ── Workout card ──────────────────────────────────────────────

@Composable
private fun WorkoutCard(data: WhoopResponse) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .border(0.5.dp, LightRule, RoundedCornerShape(10.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Activity", fontSize = 11.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
            Text(data.lastWorkout.replaceFirstChar { it.uppercase() },
                fontSize = 20.sp, fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif, color = NearBlack)
            val subline = listOfNotNull(
                data.strain.presentOrNull()?.let { "Strain %.1f".format(it) },
                data.spo2Percentage.presentOrNull()?.let { "SpO₂ %.1f%%".format(it) }
            ).joinToString("  ·  ")
            if (subline.isNotEmpty()) {
                Text(subline, fontSize = 11.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
            }
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Cream)
                .border(0.5.dp, LightRule, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = workoutIcon(data.lastWorkout),
                contentDescription = data.lastWorkout,
                tint = GreenDark, modifier = Modifier.size(26.dp))
        }
    }
}

private fun workoutIcon(type: String): ImageVector = when (type.lowercase().trim()) {
    "running", "run"           -> Icons.AutoMirrored.Filled.DirectionsRun
    "walking", "walk"          -> Icons.AutoMirrored.Filled.DirectionsWalk
    "cycling", "bike", "cycle" -> Icons.AutoMirrored.Filled.DirectionsBike
    "swimming", "swim"         -> Icons.Default.Water
    "yoga"                     -> Icons.Default.Favorite
    "strength", "weights"      -> Icons.Default.Star
    "hiking", "hike"           -> Icons.Default.Terrain
    else                       -> Icons.Default.Favorite
}

// ── Sleep cards ───────────────────────────────────────────────

@Composable
private fun SleepCardsRow(data: WhoopResponse) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Each card is shown only when its value exists (both are Whoop-only)
        data.sleepPerformance.presentOrNull()?.let { perf ->
            val perfPill = when {
                perf >= 85 -> Triple("Excellent", PillGreenBg, PillGreenTxt)
                perf >= 70 -> Triple("Good",      PillGreenBg, PillGreenTxt)
                perf >= 50 -> Triple("Fair",      PillAmberBg, PillAmberTxt)
                else       -> Triple("Poor",      PillRedBg,   PillRedTxt)
            }
            SleepCard(modifier = Modifier.weight(1f), label = "Sleep score",
                value = "%.0f".format(perf), pill = perfPill)
        }

        data.sleepEfficiency.presentOrNull()?.let { eff ->
            val effPill = when {
                eff >= 90 -> Triple("Efficient",  PillGreenBg, PillGreenTxt)
                eff >= 75 -> Triple("Good",       PillGreenBg, PillGreenTxt)
                else      -> Triple("Below goal", PillAmberBg, PillAmberTxt)
            }
            SleepCard(modifier = Modifier.weight(1f), label = "Efficiency",
                value = "%.1f%%".format(eff), pill = effPill)
        }
    }
}

@Composable
private fun SleepCard(
    modifier: Modifier,
    label: String,
    value: String,
    pill: Triple<String, Color, Color>
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .border(0.5.dp, LightRule, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(label, fontSize = 11.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Serif, color = NearBlack)
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(pill.second)
            .padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(pill.first, fontSize = 10.sp, color = pill.third,
                fontFamily = FontFamily.SansSerif)
        }
    }
}

// ── Sleep stages ──────────────────────────────────────────────

@Composable
private fun SleepStagesCard(data: WhoopResponse) {
    val rem      = data.remMin  ?: 0f
    val deep     = data.deepMin ?: 0f
    val knownMin = rem + deep
    val lightMin = maxOf(0f, 480f - knownMin - ((data.disturbances ?: 0) * 5f)).coerceAtMost(480f)
    val stages   = listOf(
        Triple("REM",   rem,      GreenDark),
        Triple("Deep",  deep,     GreenDeep),
        Triple("Light", lightMin, MidGrey)
    )
    val total = stages.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(White)
            .border(0.5.dp, LightRule, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Sleep stages", fontSize = 13.sp, color = MidGrey,
                fontFamily = FontFamily.SansSerif)
            data.disturbances?.let {   // Whoop-only; 0 is a real Whoop value
                Text("$it disturbances", fontSize = 11.sp,
                    color = MidGrey, fontFamily = FontFamily.SansSerif)
            }
        }
        stages.forEach { (label, minutes, color) ->
            SleepStageBar(label = label, minutes = minutes,
                fraction = minutes / total, color = color)
        }
    }
}

@Composable
private fun SleepStageBar(label: String, minutes: Float, fraction: Float, color: Color) {
    val hrs     = (minutes / 60).toInt()
    val mins    = (minutes % 60).toInt()
    val display = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
    val animFraction by animateFloatAsState(
        targetValue   = fraction.coerceIn(0f, 1f),
        animationSpec = tween(800),
        label         = "bar_$label"
    )
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 10.sp, color = MidGrey,
            fontFamily = FontFamily.SansSerif, modifier = Modifier.width(36.dp))
        Box(modifier = Modifier.weight(1f).height(10.dp)
            .clip(RoundedCornerShape(5.dp)).background(Cream)) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(animFraction)
                .clip(RoundedCornerShape(5.dp)).background(color))
        }
        Text(display, fontSize = 10.sp, color = NearBlack,
            fontFamily = FontFamily.SansSerif, modifier = Modifier.width(40.dp))
    }
}

// ── Vitals ────────────────────────────────────────────────────

@Composable
private fun VitalsRow(data: WhoopResponse) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VitalCard(modifier = Modifier.weight(1f), label = "SpO₂",
            value = data.spo2Percentage.presentOrNull()?.let { "%.1f%%".format(it) } ?: "—")
        data.skinTempCelsius.presentOrNull()?.let {   // Whoop-only
            VitalCard(modifier = Modifier.weight(1f), label = "Skin temp",
                value = "%.1f°C".format(it))
        }
    }
}

@Composable
private fun VitalCard(modifier: Modifier, label: String, value: String) {
    Column(modifier = modifier
        .clip(RoundedCornerShape(10.dp))
        .background(White)
        .border(0.5.dp, LightRule, RoundedCornerShape(10.dp))
        .padding(12.dp)) {
        Text(label, fontSize = 11.sp, color = MidGrey, fontFamily = FontFamily.SansSerif)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Serif, color = NearBlack)
    }
}

// ── Section label ─────────────────────────────────────────────

@Composable
private fun HealthSectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, color = MidGrey,
        fontFamily = FontFamily.SansSerif, letterSpacing = 0.06.sp,
        modifier = Modifier.padding(horizontal = 20.dp))
    Spacer(Modifier.height(8.dp))
}

// ── Error card ────────────────────────────────────────────────

@Composable
private fun HealthErrorCard(message: String) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(White)
        .border(0.5.dp, LightRule, RoundedCornerShape(10.dp))
        .padding(16.dp)) {
        Text("Could not load health data\n$message",
            fontSize = 13.sp, color = MidGrey,
            fontFamily = FontFamily.SansSerif, lineHeight = 20.sp)
    }
}