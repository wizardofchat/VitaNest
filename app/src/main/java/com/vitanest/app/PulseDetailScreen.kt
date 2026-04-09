package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// PulseDetailScreen — e-ink monochrome · Kindle editorial · locked 2026-04-09 ☘️
// Changed: wired to GET /whoop — live recovery, HRV, RHR, SpO2, skin temp
//          strain/sleep/workout show — pending VitaClaw /whoop extension

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseDetailScreen(
    repository: VitaClawRepository,
    onBack: () -> Unit
) {
    var metrics by remember { mutableStateOf(PulseMetrics()) }
    var lastUpdated by remember { mutableStateOf("") }

    // ── GET /whoop — live data, no /ask, no quota drain ──────
    LaunchedEffect(Unit) {
        repository.getWhoop().let { result ->
            if (result.isSuccess) {
                val w = result.getOrNull()!!
                metrics = metrics.copy(
                    recovery  = w.recoveryScore,
                    hrv       = w.hrvRmssdMilli,
                    rhr       = w.restingHeartRate,
                    spo2      = w.spo2Percentage,
                    skinTemp  = w.skinTempCelsius
                )
                lastUpdated = w.lastUpdated
                    .take(16)
                    .replace("T", " ")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = T.screenPadding)
        ) {
            // ── Header ────────────────────────────────────────
            Spacer(modifier = Modifier.height(52.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = T.Ink,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Pulse · Today",
                        fontFamily = T.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = T.Ink
                    )
                    if (lastUpdated.isNotEmpty()) {
                        Text(text = lastUpdated, style = T.meta)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = T.heavyRule, color = T.Ink)
            Spacer(modifier = Modifier.height(T.sectionGap))

            // ── Recovery ring + primary metrics ───────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InkRecoveryRing(percentage = metrics.recovery)
                Spacer(modifier = Modifier.width(32.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InkMetricRow("HRV",       if (metrics.hrv > 0f) "${"%.1f".format(metrics.hrv)} ms" else "—")
                    InkMetricRow("RHR",       if (metrics.rhr > 0f) "${metrics.rhr.toInt()} bpm" else "—")
                    InkMetricRow("SpO2",      if (metrics.spo2 > 0f) "${"%.1f".format(metrics.spo2)}%" else "—")
                    InkMetricRow("Skin temp", if (metrics.skinTemp > 0f) "${"%.1f".format(metrics.skinTemp)}°C" else "—")
                }
            }

            Spacer(modifier = Modifier.height(T.sectionGap))
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
            Spacer(modifier = Modifier.height(T.sectionGap))

            // ── Activity & Sleep — pending VitaClaw extension ─
            Text(text = "ACTIVITY & SLEEP", style = T.sectionHead)
            Spacer(modifier = Modifier.height(16.dp))

            InkBarRow(
                label = "Day strain",
                value = if (metrics.strain > 0f) "${metrics.strain} / 21" else "—",
                fraction = (metrics.strain / 21f).coerceIn(0f, 1f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            InkBarRow(
                label = "Sleep performance",
                value = if (metrics.sleepPerformance > 0f) "${metrics.sleepPerformance.toInt()}%" else "—",
                fraction = (metrics.sleepPerformance / 100f).coerceIn(0f, 1f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            InkBarRow(
                label = "Sleep efficiency",
                value = if (metrics.sleepEfficiency > 0f) "${metrics.sleepEfficiency.toInt()}%" else "—",
                fraction = (metrics.sleepEfficiency / 100f).coerceIn(0f, 1f)
            )

            Spacer(modifier = Modifier.height(T.sectionGap))
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
            Spacer(modifier = Modifier.height(T.sectionGap))

            // ── Sleep detail ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InkSleepStat(
                    value = if (metrics.remMin > 0f) "${metrics.remMin.toInt()}" else "—",
                    label = "REM min",
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .width(T.ruleThickness)
                        .height(48.dp)
                        .background(T.Rule)
                        .align(Alignment.CenterVertically)
                )
                InkSleepStat(
                    value = if (metrics.deepMin > 0f) "${metrics.deepMin.toInt()}" else "—",
                    label = "Deep min",
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .width(T.ruleThickness)
                        .height(48.dp)
                        .background(T.Rule)
                        .align(Alignment.CenterVertically)
                )
                InkSleepStat(
                    value = if (metrics.disturbances > 0) "${metrics.disturbances}" else "—",
                    label = "Disturbances",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(T.sectionGap))
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
            Spacer(modifier = Modifier.height(T.sectionGap))

            // ── Last workout ──────────────────────────────────
            Text(text = "LAST WORKOUT", style = T.sectionHead)
            Spacer(modifier = Modifier.height(12.dp))
            InkWorkoutRow(metrics.lastWorkout)

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ── Recovery ring — one colour exception ─────────────────────
@Composable
fun InkRecoveryRing(percentage: Float) {
    val ringColor = T.recoveryColor(percentage)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(110.dp)
    ) {
        Canvas(modifier = Modifier.size(100.dp)) {
            drawCircle(
                color = Color(0xFFC8C4BB),
                style = Stroke(width = 8.dp.toPx())
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = (percentage / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (percentage > 0f) "${percentage.toInt()}%" else "—",
                fontFamily = T.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = T.Ink
            )
            Text(text = "Recovery", style = T.meta)
        }
    }
}

// ── Metric row ────────────────────────────────────────────────
@Composable
fun InkMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = T.meta, modifier = Modifier.width(72.dp))
        Text(text = value, style = T.bodyValue)
    }
}

// ── Ink bar row ───────────────────────────────────────────────
@Composable
fun InkBarRow(label: String, value: String, fraction: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = T.meta)
            Text(text = value, style = T.bodyValue)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(T.Rule)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(T.Ink)
            )
        }
    }
}

// ── Sleep stat column ─────────────────────────────────────────
@Composable
fun InkSleepStat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontFamily = T.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = T.Ink
        )
        Text(text = label, style = T.meta)
    }
}

// ── Last workout row ──────────────────────────────────────────
@Composable
fun InkWorkoutRow(workoutInfo: String) {
    val dateRegex = Regex("""\((\d{4})-(\d{2})-(\d{2})\)""")
    val match = dateRegex.find(workoutInfo)
    val displayDate = if (match != null) {
        val (_, month, day) = match.destructured
        val monthName = when (month) {
            "01" -> "Jan"; "02" -> "Feb"; "03" -> "Mar"
            "04" -> "Apr"; "05" -> "May"; "06" -> "Jun"
            "07" -> "Jul"; "08" -> "Aug"; "09" -> "Sep"
            "10" -> "Oct"; "11" -> "Nov"; "12" -> "Dec"
            else -> month
        }
        "$day $monthName"
    } else ""

    val sportName   = workoutInfo.substringBefore(" (").replaceFirstChar { it.uppercase() }
    val strainValue = workoutInfo.substringAfter("strain ").trim()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = if (displayDate.isNotEmpty()) "Last workout — $displayDate" else "Last workout",
                style = T.meta
            )
            Text(
                text = if (sportName.isNotEmpty() && sportName != "No recent workout") sportName else "—",
                style = T.bodyValue
            )
        }
        if (strainValue.isNotEmpty() && strainValue != "No recent workout") {
            Text(
                text = "$strainValue strain",
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = T.Ink
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
}