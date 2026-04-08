package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// PulseDetailScreen — e-ink monochrome · Kindle editorial · locked 2026-04-06 ☘️

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
                Text(
                    text = "Pulse · Today",
                    fontFamily = T.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = T.Ink
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = T.heavyRule, color = T.Ink)
            Spacer(modifier = Modifier.height(T.sectionGap))

            // ── Recovery ring + primary metrics ───────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Recovery ring — ONE colour exception
                InkRecoveryRing(percentage = metrics.recovery)

                Spacer(modifier = Modifier.width(32.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InkMetricRow("HRV",       "${metrics.hrv} ms")
                    InkMetricRow("RHR",       "${metrics.rhr.toInt()} bpm")
                    InkMetricRow("SpO2",      "${"%.1f".format(metrics.spo2)}%")
                    InkMetricRow("Skin temp", "${metrics.skinTemp}°C")
                }
            }

            Spacer(modifier = Modifier.height(T.sectionGap))
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
            Spacer(modifier = Modifier.height(T.sectionGap))

            // ── Activity & Sleep ──────────────────────────────
            Text(text = "ACTIVITY & SLEEP", style = T.sectionHead)
            Spacer(modifier = Modifier.height(16.dp))

            // Strain gauge — ink bar
            InkBarRow(
                label = "Day strain",
                value = "${metrics.strain} / 21",
                fraction = (metrics.strain / 21f).coerceIn(0f, 1f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            InkBarRow(
                label = "Sleep performance",
                value = "${metrics.sleepPerformance.toInt()}%",
                fraction = (metrics.sleepPerformance / 100f).coerceIn(0f, 1f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            InkBarRow(
                label = "Sleep efficiency",
                value = "${metrics.sleepEfficiency.toInt()}%",
                fraction = (metrics.sleepEfficiency / 100f).coerceIn(0f, 1f)
            )

            Spacer(modifier = Modifier.height(T.sectionGap))
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
            Spacer(modifier = Modifier.height(T.sectionGap))

            // ── Sleep detail — three ink columns ─────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InkSleepStat(
                    value = "${metrics.remMin.toInt()}",
                    label = "REM min",
                    modifier = Modifier.weight(1f)
                )
                // Vertical rule
                Box(
                    modifier = Modifier
                        .width(T.ruleThickness)
                        .height(48.dp)
                        .background(T.Rule)
                        .align(Alignment.CenterVertically)
                )
                InkSleepStat(
                    value = "${metrics.deepMin.toInt()}",
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
                    value = "${metrics.disturbances}",
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
            // Track — light rule
            drawCircle(
                color = Color(0xFFC8C4BB),
                style = Stroke(width = 8.dp.toPx())
            )
            // Arc — recovery colour
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
                text = "${percentage.toInt()}%",
                fontFamily = T.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = T.Ink
            )
            Text(
                text = "Recovery",
                style = T.meta
            )
        }
    }
}

// ── Metric row — label + value, no coloured dots ─────────────
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

// ── Ink bar row — label, value, 4px ink progress bar ─────────
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
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(T.Rule)
        ) {
            // Fill — solid ink
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
            Text(text = sportName, style = T.bodyValue)
        }
        if (strainValue.isNotEmpty()) {
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