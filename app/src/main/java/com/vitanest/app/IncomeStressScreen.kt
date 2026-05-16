package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// IncomeStressScreen — Income stress test against POST /portfolio/income-stress
// Scenario dropdown + slider → Run Scenario → server result rendered.
// Slider change does NOT call endpoint — only Run Scenario button does.
// estimated:true always shows "Estimated" badge — never hidden. ☘️

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vitanest.app.data.remote.IncomeStressRequest
import com.vitanest.app.data.remote.IncomeStressResponse
import com.vitanest.app.data.remote.IncomeStressTypeResult
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ── Scenario definitions ──────────────────────────────────────

private data class ScenarioOption(val key: String, val label: String)

private val SCENARIOS = listOf(
    ScenarioOption("vol_collapse",  "Volatility Collapse"),
    ScenarioOption("market_crash",  "Market Crash"),
    ScenarioOption("rate_cut",      "Rate Cut"),
    ScenarioOption("custom",        "Custom Shock")
)

// ── Colour helpers ────────────────────────────────────────────

private fun incomeChangeColor(pct: Double): Color = when {
    pct >= 0    -> Color(0xFF3B6D11)
    pct >= -10  -> Color(0xFF854F0B)
    else        -> Color(0xFFA32D2D)
}

private fun stabilityColor(score: Int): Color = when {
    score >= 60 -> Color(0xFF3B6D11)
    score >= 30 -> Color(0xFF854F0B)
    else        -> Color(0xFFA32D2D)
}

private fun incomeChangeEmoji(pct: Double): String = when {
    pct >= 0    -> "🟢"
    pct >= -10  -> "🟡"
    else        -> "🔴"
}

private fun stabilityEmoji(score: Int): String = when {
    score >= 60 -> "🟢"
    score >= 30 -> "🟡"
    else        -> "🔴"
}

// ── ViewModel ─────────────────────────────────────────────────

class IncomeStressViewModel(
    private val repository: VitaClawRepository
) : ViewModel() {

    private val _result     = MutableStateFlow<IncomeStressResponse?>(null)
    private val _isLoading  = MutableStateFlow(false)
    private val _error      = MutableStateFlow<String?>(null)

    val result:    StateFlow<IncomeStressResponse?> = _result
    val isLoading: StateFlow<Boolean>               = _isLoading
    val error:     StateFlow<String?>               = _error

    fun runScenario(scenario: String, shockPct: Float) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            repository.runIncomeStress(
                IncomeStressRequest(
                    scenario = scenario,
                    shockPct = shockPct.toDouble()
                )
            ).fold(
                onSuccess = { _result.value = it },
                onFailure = { _error.value  = it.message }
            )
            _isLoading.value = false
        }
    }
}

// ── Screen ────────────────────────────────────────────────────

@Composable
fun IncomeStressScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    val vm: IncomeStressViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                IncomeStressViewModel(repository) as T
        }
    )

    val result    by vm.result.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error     by vm.error.collectAsState()

    // Local UI state — not sent to server until Run Scenario tapped
    var selectedScenario by remember { mutableStateOf(SCENARIOS[0]) }
    var shockPct         by remember { mutableStateOf(-10f) }
    var scenarioExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Fixed header ──────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = T.screenPadding, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text     = "←",
                        fontSize = 18.sp,
                        color    = T.Ink,
                        modifier = Modifier.clickable { navController.popBackStack() }
                    )
                    Text(
                        text       = "Income stress test",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Serif,
                        color      = T.Ink
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
            }

            // ── Scrollable content ────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 80.dp)
            ) {
                Spacer(Modifier.height(12.dp))

                // ── Controls card ─────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = T.screenPadding)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(0.5.dp, T.Rule, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "📊 Income Stress Test",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color      = T.Ink
                    )

                    // Scenario dropdown
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Scenario", fontSize = 10.sp, color = T.Muted)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(T.Paper)
                                .border(0.5.dp, T.Rule, RoundedCornerShape(8.dp))
                                .clickable { scenarioExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                Arrangement.SpaceBetween,
                                Alignment.CenterVertically
                            ) {
                                Text(
                                    selectedScenario.label,
                                    fontSize = 13.sp,
                                    color    = T.Ink
                                )
                                Text("▾", fontSize = 12.sp, color = T.Muted)
                            }
                            DropdownMenu(
                                expanded         = scenarioExpanded,
                                onDismissRequest = { scenarioExpanded = false },
                                modifier         = Modifier.background(Color.White)
                            ) {
                                SCENARIOS.forEach { scenario ->
                                    DropdownMenuItem(
                                        text    = {
                                            Text(scenario.label, style = T.meta)
                                        },
                                        onClick = {
                                            selectedScenario = scenario
                                            scenarioExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Shock slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically
                        ) {
                            Text("Shock magnitude", fontSize = 10.sp, color = T.Muted)
                            val shockColor = when {
                                shockPct < 0  -> Color(0xFFA32D2D)
                                shockPct > 0  -> Color(0xFF3B6D11)
                                else          -> T.Muted
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(shockColor.copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "${if (shockPct >= 0) "+" else ""}${"%.0f".format(shockPct)}%",
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color      = shockColor
                                )
                            }
                        }
                        Slider(
                            value         = shockPct,
                            onValueChange = { shockPct = it },
                            valueRange    = -30f..30f,
                            steps         = 59,
                            colors        = SliderDefaults.colors(
                                thumbColor         = T.Ink,
                                activeTrackColor   = T.Ink,
                                inactiveTrackColor = T.Rule
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("−30% crash", fontSize = 10.sp, color = T.Muted)
                            Text("0",          fontSize = 10.sp, color = T.Muted)
                            Text("+30% rally", fontSize = 10.sp, color = T.Muted)
                        }
                    }

                    // Custom scenario warning
                    if (selectedScenario.key == "custom") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFAEEDA))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.Top
                        ) {
                            Text("⚠", fontSize = 12.sp, color = Color(0xFF854F0B))
                            Text(
                                "Custom applies a flat shock to all income types equally. Less realistic than preset scenarios.",
                                fontSize  = 11.sp,
                                color     = Color(0xFF854F0B),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Run Scenario button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isLoading) T.Rule else T.Ink)
                            .clickable(enabled = !isLoading) {
                                vm.runScenario(selectedScenario.key, shockPct)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color       = T.Ink,
                                    strokeWidth = 1.5.dp,
                                    modifier    = Modifier.size(14.dp)
                                )
                                Text("Running…", fontSize = 13.sp, color = T.Ink)
                            }
                        } else {
                            Text(
                                "Run Scenario",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color      = T.Paper
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Error state ───────────────────────────────
                if (error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = T.screenPadding)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFCEBEB))
                            .border(0.5.dp, Color(0xFFF09595), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            "Could not run scenario: $error",
                            fontSize  = 12.sp,
                            color     = Color(0xFFA32D2D),
                            lineHeight = 18.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // ── Results ───────────────────────────────────
                result?.let { r ->
                    // Estimated badge
                    if (r.estimated) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = T.screenPadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFAEEDA))
                                    .border(0.5.dp, Color(0xFFD4A017), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "⚠ Estimated",
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color      = Color(0xFF854F0B)
                                )
                            }
                            Text(
                                "Phase ${r.phase} · sensitivity factors are approximate",
                                fontSize = 10.sp,
                                color    = T.Muted
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Income + value impact
                    ImpactCard(result = r)
                    Spacer(Modifier.height(10.dp))

                    // By income type breakdown
                    ByTypeCard(byType = r.stressed.byType)
                    Spacer(Modifier.height(10.dp))

                    // Stability + floor
                    StabilityCard(
                        stabilityScore = r.stressed.stabilityScore,
                        incomeFloor    = r.stressed.incomeFloorGbp
                    )
                    Spacer(Modifier.height(10.dp))

                    // Key insight
                    InsightCard(insight = r.stressed.keyInsight)
                    Spacer(Modifier.height(12.dp))
                }

                // ── Empty state — before first run ────────────
                if (result == null && !isLoading && error == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = T.screenPadding, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📊", fontSize = 32.sp)
                            Text(
                                "Select a scenario and tap Run Scenario",
                                fontSize  = 13.sp,
                                color     = T.Muted,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
            }
        }

        InkBottomNav(
            current       = "portfolio_detail",
            navController = navController,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

// ── Impact card ───────────────────────────────────────────────

@Composable
private fun ImpactCard(result: IncomeStressResponse) {
    val s              = result.stressed
    val incChangePct   = s.incomeChangePct
    val incColor       = incomeChangeColor(incChangePct)
    val incEmoji       = incomeChangeEmoji(incChangePct)
    val valChangePct   = if (result.current.totalValueGbp > 0)
        ((s.totalValueGbp - result.current.totalValueGbp) /
                result.current.totalValueGbp * 100)
    else 0.0
    val valColor       = incomeChangeColor(valChangePct)
    val valEmoji       = incomeChangeEmoji(valChangePct)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(0.5.dp, T.Rule, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Income impact
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text("Income impact", fontSize = 11.sp, color = T.Muted)
            Text(incEmoji, fontSize = 14.sp)
        }
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text(
                "£${"%.2f".format(result.current.monthlyIncomeGbp)} → £${"%.2f".format(s.monthlyIncomeGbp)}",
                fontSize   = 15.sp,
                fontWeight = FontWeight.Medium,
                color      = T.Ink
            )
            Text(
                "${"%.1f".format(incChangePct)}%",
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                color      = incColor
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

        // Value impact
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text("Value impact", fontSize = 11.sp, color = T.Muted)
            Text(valEmoji, fontSize = 14.sp)
        }
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text(
                "£${"%.0f".format(result.current.totalValueGbp)} → £${"%.0f".format(s.totalValueGbp)}",
                fontSize   = 15.sp,
                fontWeight = FontWeight.Medium,
                color      = T.Ink
            )
            Text(
                "${"%.1f".format(valChangePct)}%",
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                color      = valColor
            )
        }
    }
}

// ── By type breakdown ─────────────────────────────────────────

@Composable
private fun ByTypeCard(byType: Map<String, IncomeStressTypeResult>) {
    if (byType.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(0.5.dp, T.Rule, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "By income type",
            fontSize   = 11.sp,
            color      = T.Muted,
            letterSpacing = 0.05.sp
        )
        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

        byType.entries
            .sortedByDescending { it.value.baseIncomeGbp }
            .forEach { (type, data) ->
                val label = type
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
                val changePct = data.incomeChangePct
                val color     = incomeChangeColor(changePct)

                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text(label, fontSize = 12.sp, color = T.Ink,
                        modifier = Modifier.weight(1f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            "£${"%.2f".format(data.baseIncomeGbp)} → £${"%.2f".format(data.stressedIncomeGbp)}",
                            fontSize = 12.sp,
                            color    = T.Ink
                        )
                        Text(
                            "${"%.1f".format(changePct)}%",
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color      = color
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = T.Rule.copy(alpha = 0.5f))
            }
    }
}

// ── Stability card ────────────────────────────────────────────

@Composable
private fun StabilityCard(stabilityScore: Int, incomeFloor: Double) {
    val scoreColor = stabilityColor(stabilityScore)
    val scoreEmoji = stabilityEmoji(stabilityScore)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(0.5.dp, T.Rule, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Stability score
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text("Stability score", fontSize = 12.sp, color = T.Ink)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(scoreEmoji, fontSize = 14.sp)
                Text(
                    "$stabilityScore / 100",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color      = scoreColor
                )
            }
        }

        // Stability bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(T.Rule)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(stabilityScore / 100f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(scoreColor)
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

        // Income floor
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Column {
                Text("Income floor", fontSize = 12.sp, color = T.Ink)
                Text(
                    "Minimum estimated monthly income under stress",
                    fontSize  = 10.sp,
                    color     = T.Muted,
                    lineHeight = 14.sp
                )
            }
            Text(
                "£${"%.2f".format(incomeFloor)}",
                fontSize   = 15.sp,
                fontWeight = FontWeight.Medium,
                color      = if (incomeFloor < 30.0) Color(0xFFA32D2D) else T.Ink
            )
        }
    }
}

// ── Insight card ──────────────────────────────────────────────

@Composable
private fun InsightCard(insight: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEAF3DE))
            .border(0.5.dp, Color(0xFFC0DD97), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White)
                    .border(0.5.dp, Color(0xFFC0DD97), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("B", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF3B6D11))
            }
            Text("Buddie · key insight", fontSize = 11.sp,
                fontWeight = FontWeight.Medium, color = Color(0xFF3B6D11))
        }
        Text(
            text       = insight,
            fontSize   = 12.sp,
            fontFamily = FontFamily.Serif,
            fontStyle  = FontStyle.Italic,
            color      = Color(0xFF27500A),
            lineHeight = 18.sp
        )
    }
}