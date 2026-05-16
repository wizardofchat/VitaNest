package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// PortfolioLensScreen — concentration analysis across 4 slices.
// Shock simulator is pure client-side arithmetic — no LLM on drag.
// Buddie insight: Phase 1 = placeholder; Phase 2 = /buddie/observations. ☘️

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T

// ── Bar palette — index → colour ─────────────────────────────
private val BAR_COLORS = listOf(
    Color(0xFF2D6A4F),   // 0 green dark
    Color(0xFFD4A017),   // 1 amber
    Color(0xFF888888),   // 2 mid grey
    Color(0xFF5B4FCF),   // 3 purple
    Color(0xFFC0392B),   // 4 red
)
private fun barColor(idx: Int) = BAR_COLORS.getOrElse(idx) { Color(0xFF888888) }

private val WarnBg  = Color(0xFFFCEBEB)
private val WarnFg  = Color(0xFFA32D2D)
private val WarnBdr = Color(0xFFF09595)
private val OkBg    = Color(0xFFEAF3DE)
private val OkFg    = Color(0xFF3B6D11)
private val OkBdr   = Color(0xFFC0DD97)
private val BuddieBg  = Color(0xFFEAF3DE)
private val BuddieFg  = Color(0xFF27500A)
private val BuddieBdr = Color(0xFFC0DD97)

@Composable
fun PortfolioLensScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    val vm: RiskViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                RiskViewModel(repository) as T
        }
    )

    val state by vm.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Fixed header ──────────────────────────────────
            LensHeader(
                activeSlice  = state.activeSlice,
                onSliceChange = { vm.setSlice(it) },
                onBack        = { navController.popBackStack() }
            )

            when {
                state.isLoading -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                        CircularProgressIndicator(color = T.Ink, strokeWidth = 2.dp)
                    }
                }
                state.error != null -> {
                    Box(Modifier.weight(1f).padding(20.dp), Alignment.TopStart) {
                        LensErrorCard(state.error!!)
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 80.dp)
                    ) {
                        // ── Shock simulator ───────────────────
                        ShockSimulatorCard(
                            shockPct           = state.shockPct,
                            portfolioBaseGbp   = state.portfolioValueGbp,
                            portfolioShockedGbp= state.portfolioShockedGbp,
                            incomeBaseGbp      = state.incomeBaseGbp,
                            incomeShockedGbp   = state.incomeShockedGbp,
                            resilienceBase     = state.resilienceScore,
                            resilienceShocked  = state.resilienceShocked,
                            onShockChange      = { vm.setShock(it) }
                        )

                        // ── Concentration bars ────────────────
                        ConcentrationCard(
                            bars         = state.bars,
                            thresholdPct = state.thresholdPct,
                            sliceLabel   = state.activeSlice.label,
                            shockPct     = state.shockPct
                        )

                        // ── Buddie insight ────────────────────
                        BuddieInsightCard(
                            observation = state.buddieObservation,
                            slice       = state.activeSlice,
                            shockPct    = state.shockPct,
                            bars        = state.bars,
                            thresholdPct= state.thresholdPct
                        )

                        // ── Actions ───────────────────────────
                        LensActions()

                        Spacer(Modifier.height(12.dp))
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

// ── Fixed header + slice chips ────────────────────────────────

@Composable
private fun LensHeader(
    activeSlice: LensSlice,
    onSliceChange: (LensSlice) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        Row(
            modifier              = Modifier
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
                modifier = Modifier.clickable { onBack() }
            )
            Text(
                text       = "Portfolio lens",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif,
                color      = T.Ink
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

        // Scrollable slice chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = T.screenPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LensSlice.entries.forEach { slice ->
                val active = slice == activeSlice
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (active) T.Ink else Color.White)
                        .border(0.5.dp, if (active) T.Ink else T.Rule, RoundedCornerShape(20.dp))
                        .clickable { onSliceChange(slice) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = slice.label,
                        fontSize   = 12.sp,
                        color      = if (active) T.Paper else T.Muted,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
    }
}

// ── Shock simulator card ──────────────────────────────────────

@Composable
private fun ShockSimulatorCard(
    shockPct: Float,
    portfolioBaseGbp: Double,
    portfolioShockedGbp: Double,
    incomeBaseGbp: Double,
    incomeShockedGbp: Double,
    resilienceBase: Int,
    resilienceShocked: Int,
    onShockChange: (Float) -> Unit
) {
    val portDelta  = portfolioShockedGbp - portfolioBaseGbp
    val incDelta   = incomeShockedGbp    - incomeBaseGbp
    val sevCount   = when {
        shockPct <= -20 -> 5
        shockPct <= -10 -> 3
        shockPct <   0  -> 2
        shockPct == 0f  -> 0
        shockPct <  15  -> 1
        else            -> 0
    }
    val sevColor   = when {
        sevCount >= 4 -> Color(0xFFA32D2D)
        sevCount >= 3 -> Color(0xFFD4A017)
        else          -> Color(0xFF3B6D11)
    }
    val sevLabel   = listOf("None","Low","Low–mod","Moderate","High","Severe").getOrElse(sevCount) { "None" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.dp, T.Ink, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text("Portfolio shock simulator", fontSize = 13.sp,
                fontWeight = FontWeight.Medium, color = T.Ink)
            val shockColor = when {
                shockPct < 0  -> Color(0xFFA32D2D)
                shockPct > 0  -> Color(0xFF3B6D11)
                else          -> T.Muted
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(shockColor.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text       = "${if (shockPct >= 0) "+" else ""}${"%.0f".format(shockPct)}%",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color      = shockColor
                )
            }
        }

        // Slider
        Slider(
            value         = shockPct,
            onValueChange = { onShockChange(it) },
            valueRange    = -30f..30f,
            steps         = 59,
            colors        = SliderDefaults.colors(
                thumbColor           = T.Ink,
                activeTrackColor     = T.Ink,
                inactiveTrackColor   = T.Rule
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("−30% crash", fontSize = 10.sp, color = T.Muted)
            Text("0 neutral",  fontSize = 10.sp, color = T.Muted)
            Text("+30% rally", fontSize = 10.sp, color = T.Muted)
        }

        // Impact grid
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Portfolio value
            ImpactTile(
                modifier  = Modifier.weight(1f),
                label     = "Portfolio value",
                value     = "£${"%.0f".format(portfolioShockedGbp)}",
                delta     = "${if (portDelta >= 0) "+" else "−"}£${"%.0f".format(Math.abs(portDelta))}",
                deltaColor = if (portDelta >= 0) Color(0xFF3B6D11) else Color(0xFFA32D2D)
            )
            // Monthly income
            ImpactTile(
                modifier  = Modifier.weight(1f),
                label     = "Monthly income",
                value     = "£${"%.2f".format(incomeShockedGbp)}",
                delta     = "${if (incDelta >= 0) "+" else "−"}£${"%.2f".format(Math.abs(incDelta))}",
                deltaColor = if (incDelta >= 0) Color(0xFF3B6D11) else Color(0xFFA32D2D)
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Resilience
            ImpactTile(
                modifier  = Modifier.weight(1f),
                label     = "Resilience score",
                value     = "$resilienceShocked / 100",
                delta     = "was $resilienceBase",
                deltaColor = T.Muted
            )
            // Severity
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(T.Paper)
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Risk severity", fontSize = 10.sp, color = T.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (1..5).forEach { i ->
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (i <= sevCount) sevColor else T.Rule)
                        )
                    }
                }
                Text(sevLabel, fontSize = 10.sp, color = sevColor)
            }
        }
    }
}

@Composable
private fun ImpactTile(
    modifier: Modifier,
    label: String,
    value: String,
    delta: String,
    deltaColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(T.Paper)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, fontSize = 10.sp, color = T.Muted)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = T.Ink)
        Text(delta, fontSize = 10.sp, color = deltaColor)
    }
}

// ── Concentration bars ────────────────────────────────────────

@Composable
private fun ConcentrationCard(
    bars: List<ConcentrationBar>,
    thresholdPct: Double,
    sliceLabel: String,
    shockPct: Float
) {
    val anyBreach = bars.any { it.weightPct > thresholdPct }

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
        // Section label
        Text(
            text  = "$sliceLabel · ${if (shockPct == 0f) "live" else "${if (shockPct > 0) "+" else ""}${"%.0f".format(shockPct)}% shocked"}",
            fontSize = 10.sp, color = T.Muted,
            letterSpacing = 0.05.sp
        )

        bars.forEach { bar ->
            ConcentrationBarRow(
                bar          = bar,
                thresholdPct = thresholdPct
            )
        }

        Spacer(Modifier.height(2.dp))
        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        Spacer(Modifier.height(2.dp))

        if (anyBreach) {
            val breached = bars.filter { it.weightPct > thresholdPct }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(WarnBg)
                    .border(0.5.dp, WarnBdr, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.Top
            ) {
                Text("⚠", fontSize = 13.sp, color = WarnFg)
                Text(
                    text      = "${breached.joinToString(", ") { it.label }} " +
                            "above ${"%.0f".format(thresholdPct)}% threshold",
                    fontSize  = 11.sp,
                    color     = WarnFg,
                    lineHeight = 16.sp
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(OkBg)
                    .border(0.5.dp, OkBdr, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("✓", fontSize = 13.sp, color = OkFg)
                Text("All categories within threshold", fontSize = 11.sp, color = OkFg)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ConcentrationBarRow(
    bar: ConcentrationBar,
    thresholdPct: Double
) {
    val fillAnim by animateFloatAsState(
        targetValue   = (bar.weightPct / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(500),
        label         = "bar_${bar.label}"
    )
    val overThreshold = bar.weightPct > thresholdPct
    val barColor = barColor(bar.color)
    val pctColor = if (overThreshold) Color(0xFFD4A017) else T.Ink

    Column {
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text(bar.label, fontSize = 12.sp, color = T.Ink)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "£${"%.0f".format(bar.valueGbp)}",
                    fontSize = 11.sp, color = T.Muted
                )
                Text(
                    "${"%.1f".format(bar.weightPct)}%",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color      = pctColor
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // Bar track with threshold marker
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val trackWidth = maxWidth
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(T.Rule)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fillAnim)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor)
                )
            }
            // Threshold marker line
            val threshX = (thresholdPct / 100.0).toFloat() * trackWidth.value
            Box(
                modifier = Modifier
                    .offset(x = threshX.dp)
                    .width(1.5.dp)
                    .height(13.dp)
                    .offset(y = (-3).dp)
                    .background(Color(0xFFA32D2D).copy(alpha = 0.6f))
            )
        }
    }
}

// ── Buddie insight card ───────────────────────────────────────
// Phase 1: generated client-side from real numbers.
// Phase 2 (after VitaClaw Task 3): reads from /buddie/observations/today
//          domain = "portfolio_lens". Replace body with observation content.

@Composable
private fun BuddieInsightCard(
    observation: String?,
    slice: LensSlice,
    shockPct: Float,
    bars: List<ConcentrationBar>,
    thresholdPct: Double
) {
    val topBar     = bars.firstOrNull()
    val breachBars = bars.filter { it.weightPct > thresholdPct }
    val hasBreath  = breachBars.isNotEmpty()

    // Phase 1 template — replaced by real observation in Phase 2
    val text = observation ?: buildString {
        append("\"")
        if (hasBreath) {
            append("${breachBars.joinToString(", ") { it.label }} ")
            append("${if (breachBars.size == 1) "is" else "are"} above the ")
            append("${"%.0f".format(thresholdPct)}% threshold")
            if (shockPct < -5) {
                append(". A ${"%.0f".format(shockPct)}% shock amplifies concentration risk — ")
                append("diversifying now limits downside exposure.")
            } else if (shockPct > 5) {
                append(". Current rally is boosting concentrated positions — ")
                append("consider rebalancing at peak to reduce future risk.")
            } else {
                append(". Consider rebalancing to reduce single-category dependency.")
            }
        } else {
            append("All ${slice.label.lowercase()} categories are within threshold. ")
            if (topBar != null) {
                append("${topBar.label} leads at ${"%.1f".format(topBar.weightPct)}% — ")
                append("well-distributed portfolio.")
            }
        }
        append("\"")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding)
            .clip(RoundedCornerShape(10.dp))
            .background(BuddieBg)
            .border(0.5.dp, BuddieBdr, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(0.5.dp, BuddieBdr, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("B", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OkFg)
            }
            Column {
                Text("Buddie · lens insight", fontSize = 11.sp,
                    fontWeight = FontWeight.Medium, color = OkFg)
                Text(
                    if (observation != null) "portfolio_lens observation"
                    else "client synthesis · Phase 2 adds nightly LLM",
                    fontSize = 10.sp, color = Color(0xFF3A6A3A)
                )
            }
        }
        Text(
            text       = text,
            fontSize   = 12.sp,
            fontFamily = FontFamily.Serif,
            fontStyle  = FontStyle.Italic,
            color      = BuddieFg,
            lineHeight = 18.sp
        )
    }
    Spacer(Modifier.height(10.dp))
}

// ── Action buttons ────────────────────────────────────────────

@Composable
private fun LensActions() {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Run scenario — Phase 2: POST /risk/scenario
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(T.Ink)
                    .clickable { /* Phase 2: POST /risk/scenario */ }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("▶  Run scenario", fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, color = T.Paper)
            }
            // Compare — opens Ask screen with pre-filled query
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(0.5.dp, T.Rule, RoundedCornerShape(8.dp))
                    .clickable { /* Navigate to Ask with query */ }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("⇄  Compare", fontSize = 12.sp, color = T.Ink)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(0.5.dp, T.Rule, RoundedCornerShape(8.dp))
                .clickable { /* Phase 2: save to memory */ }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("⊞  Save to memory", fontSize = 12.sp, color = T.Ink)
        }
    }
}

// ── Error card ────────────────────────────────────────────────

@Composable
private fun LensErrorCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(0.5.dp, T.Rule, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Text("Could not load portfolio lens\n$message",
            fontSize = 13.sp, color = T.Muted, lineHeight = 20.sp)
    }
}