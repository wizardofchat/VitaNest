package com.vitanest.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitanest.app.data.repository.VitaClawRepository
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar // Use TopAppBar, not SmallTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseDetailScreen(
    repository: VitaClawRepository,
    onBack: () -> Unit
) {
    var metrics by remember { mutableStateOf(PulseMetrics()) }

    LaunchedEffect(Unit) {
        // Fix: Use 'askQuestion' as per your VitaClawRepository.kt
        repository.askQuestion("strain today").let { result ->
            if (result.isSuccess) {
                // Fix: Extract .answer from the AskResponse object
                val rawText = result.getOrNull()?.answer ?: ""
                metrics = parsePulseResponse(rawText)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar( // CenterAlignedTopAppBar also works for 'Sundar-level' polish
                title = { Text("Pulse • Today", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Section 1: Recovery Ring & Primary Metrics
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RecoveryRing(percentage = metrics.recovery)
                    Spacer(modifier = Modifier.width(32.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricRow("HRV", "${metrics.hrv} ms")
                        MetricRow("RHR", "${metrics.rhr.toInt()} bpm")
                        MetricRow("SpO2", "${metrics.spo2}%")
                        MetricRow("Skin temp", "${metrics.skinTemp}°C")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Activity & Sleep
            Text("ACTIVITY & SLEEP", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            StrainGauge(currentStrain = metrics.strain)

            Spacer(modifier = Modifier.height(20.dp))

            PerformanceBar("Sleep Performance", metrics.sleepPerformance, Color(0xFF534AB7))
            PerformanceBar("Sleep Efficiency", metrics.sleepEfficiency, Color(0xFF639922))

            Spacer(modifier = Modifier.height(24.dp))

            // Sleep Detail Chips (Fix #1: Disturbances)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepChip("${metrics.remMin.toInt()}", "REM min", Modifier.weight(1f))
                SleepChip("${metrics.deepMin.toInt()}", "Deep min", Modifier.weight(1f))
                SleepChip("${metrics.disturbances}", "Disturbances", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Last Workout (Fix #2: Date format)
            WorkoutCard(metrics.lastWorkout)
        }
    }
}

// ... Custom UI components (RecoveryRing, StrainGauge, etc.) stay the same ...
@Composable
fun RecoveryRing(percentage: Float) {
    val color = when {
        percentage >= 67f -> Color(0xFF639922)
        percentage >= 34f -> Color(0xFFEF9F27)
        else -> Color(0xFFE24B4A)
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        Canvas(modifier = Modifier.size(100.dp)) {
            drawCircle(color = color.copy(alpha = 0.1f), style = Stroke(width = 10.dp.toPx()))
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = (percentage / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${percentage.toInt()}%", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Recovery", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
fun StrainGauge(currentStrain: Float) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Day strain", fontSize = 12.sp, color = Color.Gray)
            Text("$currentStrain / 21", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFF0F0F0), CircleShape)) {
            Box(Modifier.fillMaxWidth(currentStrain / 21f).fillMaxHeight().background(Color(0xFF534AB7), CircleShape))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "1", "10", "15", "21").forEach { Text(it, fontSize = 9.sp, color = Color.LightGray) }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Color(0xFF639922), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(70.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PerformanceBar(label: String, pct: Float, color: Color) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text("${pct.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).background(Color(0xFFF0F0F0), CircleShape)) {
            Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(color, CircleShape))
        }
    }
}

@Composable
fun SleepChip(value: String, label: String, modifier: Modifier) {
    Surface(modifier = modifier, color = Color(0xFFF1F3F4), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 9.sp, color = Color.Gray)
        }
    }
}

@Composable
fun WorkoutCard(workoutInfo: String) {
    val dateRegex = Regex("""\((\d{4})-(\d{2})-(\d{2})\)""")
    val match = dateRegex.find(workoutInfo)

    val displayDate = if (match != null) {
        val (_, month, day) = match.destructured
        val monthName = when(month) {
            "04" -> "Apr"
            else -> month
        }
        "$day $monthName"
    } else ""

    val sportName = workoutInfo.substringBefore(" (").replaceFirstChar { it.uppercase() }
    val strainValue = workoutInfo.substringAfter("strain ").trim()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF1F3F4),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Last workout — $displayDate", fontSize = 10.sp, color = Color.Gray)
                Text(sportName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = "$strainValue strain", color = Color(0xFFBA7517), fontWeight = FontWeight.Bold)
        }
    }
}