package com.vitanest.app

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitanest.app.data.repository.VitaClawRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import com.vitanest.app.ui.theme.VitaNestTheme as T
import androidx.compose.ui.text.font.FontFamily

// ── Phase 1 constants — replace in Phase 2 ──────────────────────────
private const val JEPQ_PRICE_USD = 54.20f   // TODO Phase 2: yfinance live price
private const val FX_RATE_USD_GBP = 0.79f   // TODO Phase 2: live FX rate
private const val INCOME_GOAL_GBP = 150f    // from goals.yaml
private const val JEPQ_TICKER = "JEPQ"

@Composable
fun DividendSimulatorScreen(
    repository: VitaClawRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // ── State ────────────────────────────────────────────────────────
    var currentShares    by remember { mutableStateOf<Double?>(null) }
    var avgPerShare      by remember { mutableStateOf<Double?>(null) }
    var dataQuality      by remember { mutableStateOf<String?>(null) }
    var daysUntilExDiv   by remember { mutableStateOf<Int?>(null) }
    var exDivDate        by remember { mutableStateOf<String?>(null) }
    var simInvestmentGbp by remember { mutableStateOf("") }
    var isLoading        by remember { mutableStateOf(true) }
    var errorMessage     by remember { mutableStateOf<String?>(null) }

    // ── Load data ────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        scope.launch {
            val portfolioResult = repository.getPortfolio()
            portfolioResult.onSuccess { portfolio ->
                currentShares = portfolio.positions
                    .find { it.ticker == JEPQ_TICKER }?.quantity
            }

            val dividendResult = repository.getDividendData()
            dividendResult.onSuccess { dividendData ->
                val jepq = dividendData.tickers.find { it.ticker == JEPQ_TICKER }
                avgPerShare = jepq?.avgPerShare
                dataQuality = jepq?.dataQuality
                daysUntilExDiv = jepq?.daysUntilExDiv
                exDivDate = jepq?.nextUpcoming?.exDivDateEarliest
            }

            if (portfolioResult.isFailure || dividendResult.isFailure) {
                errorMessage = "Could not load data — check VitaClaw connection"
            }
            isLoading = false
        }
    }

    // ── Derived math ─────────────────────────────────────────────────
    val simGbp     = simInvestmentGbp.toFloatOrNull() ?: 0f
    val simShares  = if (JEPQ_PRICE_USD > 0)
        simGbp / (JEPQ_PRICE_USD * FX_RATE_USD_GBP) else 0f
    val totalShares = (currentShares ?: 0.0) + simShares

    val monthlyCurrentGbp = (currentShares ?: 0.0) * (avgPerShare ?: 0.0) * FX_RATE_USD_GBP
    val monthlySimGbp     = totalShares * (avgPerShare ?: 0.0) * FX_RATE_USD_GBP
    val annualCurrentGbp  = monthlyCurrentGbp * 12
    val annualSimGbp      = monthlySimGbp * 12
    val gapCurrentGbp     = INCOME_GOAL_GBP - monthlyCurrentGbp.toFloat()
    val gapSimGbp         = INCOME_GOAL_GBP - monthlySimGbp.toFloat()

    // ── UI ───────────────────────────────────────────────────────────
    Column(
        modifier = Modifier.Companion
            .fillMaxSize()
            .background(T.Paper)
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {

        // Back
        TextButton(onClick = onBack) {
            Text(
                "← Back",
                color = T.Ink,
                fontSize = 13.sp,
                fontFamily = FontFamily.Default
            )
        }

        Spacer(Modifier.Companion.height(8.dp))

        // Header
        Text(
            "JEPQ Simulator",
            fontSize = 24.sp,
            fontWeight = FontWeight.Companion.Bold,
            color = T.Ink,
            fontFamily = T.Serif
        )
        Text(
            "JPMorgan Nasdaq Equity Premium Income ETF",
            fontSize = 12.sp,
            color = T.Muted,
            fontFamily = FontFamily.Default,
            modifier = Modifier.Companion.padding(top = 4.dp)
        )

        Spacer(Modifier.Companion.height(24.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Companion.Center
                ) {
                    CircularProgressIndicator(
                        color = T.Ink,
                        strokeWidth = 1.5.dp
                    )
                }
            }

            errorMessage != null -> {
                InkCard {
                    Text(
                        "⚠ $errorMessage",
                        color = T.Ink,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Default
                    )
                }
            }

            else -> {

                // ── Current position ─────────────────────────────────
                SectionLabel("CURRENT POSITION")
                Spacer(Modifier.Companion.height(8.dp))
                InkCard {
                    InkRow(
                        "Shares held",
                        "%.4f".format(currentShares ?: 0.0)
                    )
                    InkRow(
                        "Monthly income",
                        "£${"%.2f".format(monthlyCurrentGbp)}"
                    )
                    InkRow(
                        "Annual income",
                        "£${"%.2f".format(annualCurrentGbp)}"
                    )
                    InkRow(
                        "Avg per share",
                        "${"%.5f".format(avgPerShare ?: 0.0)} USD"
                    )
                    Spacer(Modifier.Companion.height(8.dp))
                    DataQualityBadge(dataQuality)
                }

                Spacer(Modifier.Companion.height(20.dp))

                // ── Investment input ─────────────────────────────────
                SectionLabel("ADD INVESTMENT")
                Spacer(Modifier.Companion.height(8.dp))
                InkCard {
                    OutlinedTextField(
                        value = simInvestmentGbp,
                        onValueChange = {
                            simInvestmentGbp = it.filter { c -> c.isDigit() || c == '.' }
                        },
                        prefix = {
                            Text("£", color = T.Ink, fontFamily = FontFamily.Default)
                        },
                        placeholder = {
                            Text("0.00", color = T.Muted, fontFamily = FontFamily.Default)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Companion.Decimal
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = T.Ink,
                            unfocusedBorderColor = T.Muted,
                            focusedTextColor = T.Ink,
                            unfocusedTextColor = T.Ink,
                            cursorColor = T.Ink
                        ),
                        modifier = Modifier.Companion.fillMaxWidth()
                    )

                    if (simGbp > 0f) {
                        Spacer(Modifier.Companion.height(8.dp))
                        Text(
                            "+ ${"%.4f".format(simShares)} shares" +
                                    " at £${"%.2f".format(JEPQ_PRICE_USD * FX_RATE_USD_GBP)}/share (est.)",
                            fontSize = 11.sp,
                            color = T.Muted,
                            fontFamily = FontFamily.Default
                        )
                    }

                    Spacer(Modifier.Companion.height(16.dp))

                    Slider(
                        value = simGbp.coerceIn(0f, 5000f),
                        onValueChange = {
                            simInvestmentGbp = it.roundToInt().toString()
                        },
                        valueRange = 0f..5000f,
                        steps = 49,
                        colors = SliderDefaults.colors(
                            thumbColor = T.Ink,
                            activeTrackColor = T.Ink,
                            inactiveTrackColor = T.Muted
                        )
                    )

                    Row(
                        modifier = Modifier.Companion.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "£0", fontSize = 10.sp,
                            color = T.Muted, fontFamily = FontFamily.Default
                        )
                        Text(
                            "£5,000", fontSize = 10.sp,
                            color = T.Muted, fontFamily = FontFamily.Default
                        )
                    }
                }

                Spacer(Modifier.Companion.height(20.dp))

                // ── Projection table ─────────────────────────────────
                SectionLabel("PROJECTION")
                Spacer(Modifier.Companion.height(8.dp))
                InkCard {
                    // Header row
                    Row(Modifier.Companion.fillMaxWidth()) {
                        Spacer(Modifier.Companion.weight(1f))
                        Text(
                            "Current",
                            fontSize = 11.sp,
                            color = T.Muted,
                            fontFamily = FontFamily.Default,
                            modifier = Modifier.Companion.weight(1f),
                            textAlign = TextAlign.Companion.End
                        )
                        Text(
                            "+ Sim",
                            fontSize = 11.sp,
                            color = T.Ink,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Companion.SemiBold,
                            modifier = Modifier.Companion.weight(1f),
                            textAlign = TextAlign.Companion.End
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.Companion.padding(vertical = 8.dp),
                        color = T.Muted,
                        thickness = 0.5.dp
                    )

                    ProjectionRow(
                        label = "Monthly",
                        current = "£${"%.2f".format(monthlyCurrentGbp)}",
                        sim = "£${"%.2f".format(monthlySimGbp)}"
                    )
                    Spacer(Modifier.Companion.height(6.dp))
                    ProjectionRow(
                        label = "Annual",
                        current = "£${"%.2f".format(annualCurrentGbp)}",
                        sim = "£${"%.2f".format(annualSimGbp)}"
                    )
                    Spacer(Modifier.Companion.height(6.dp))
                    ProjectionRow(
                        label = "Gap to £150/mo",
                        current = "£${"%.2f".format(gapCurrentGbp)}",
                        sim = "£${"%.2f".format(gapSimGbp)}",
                        highlightSim = gapSimGbp < gapCurrentGbp
                    )
                }

                Spacer(Modifier.Companion.height(20.dp))

                // ── Ex-div info ──────────────────────────────────────
                SectionLabel("EX-DIV INFO")
                Spacer(Modifier.Companion.height(8.dp))
                InkCard {
                    if (exDivDate != null && daysUntilExDiv != null) {
                        InkRow(
                            "Next ex-div",
                            "$exDivDate  (${daysUntilExDiv}d)"
                        )
                    }
                    InkRow("Frequency", "Monthly")
                    InkRow("FX rate (est.)", "1 USD = £$FX_RATE_USD_GBP")
                    InkRow("Price (est.)", "$$JEPQ_PRICE_USD USD")
                }

                Spacer(Modifier.Companion.height(20.dp))

                // ── Disclaimer ───────────────────────────────────────
                Text(
                    "⚠  Simulated projection based on historical avg.\n" +
                            "Not financial advice.",
                    fontSize = 11.sp,
                    color = T.Muted,
                    fontFamily = FontFamily.Default,
                    lineHeight = 16.sp,
                    modifier = Modifier.Companion.padding(horizontal = 4.dp)
                )

                Spacer(Modifier.Companion.height(40.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        color = T.Muted,
        fontFamily = FontFamily.Default,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Companion.Medium
    )
}

@Composable
private fun InkCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .border(0.5.dp, T.Muted)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun InkRow(label: String, value: String) {
    Row(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = T.Muted,
            fontFamily = FontFamily.Default
        )
        Text(
            value,
            fontSize = 12.sp,
            color = T.Ink,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Companion.Medium
        )
    }
}

@Composable
private fun ProjectionRow(
    label: String,
    current: String,
    sim: String,
    highlightSim: Boolean = false
) {
    Row(Modifier.Companion.fillMaxWidth()) {
        Text(
            label,
            fontSize = 12.sp,
            color = T.Muted,
            fontFamily = FontFamily.Default,
            modifier = Modifier.Companion.weight(1f)
        )
        Text(
            current,
            fontSize = 12.sp,
            color = T.Muted,
            fontFamily = FontFamily.Default,
            modifier = Modifier.Companion.weight(1f),
            textAlign = TextAlign.Companion.End
        )
        Text(
            sim,
            fontSize = 12.sp,
            color = if (highlightSim) T.Ink else T.Muted,
            fontFamily = FontFamily.Default,
            fontWeight = if (highlightSim) FontWeight.Companion.Bold else FontWeight.Companion.Normal,
            modifier = Modifier.Companion.weight(1f),
            textAlign = TextAlign.Companion.End
        )
    }
}

@Composable
private fun DataQualityBadge(quality: String?) {
    val (icon, label) = when (quality) {
        "full"    -> "✅" to "Full confidence"
        "partial" -> "⚠" to "Partial data"
        "none"    -> "✗" to "No data"
        else      -> "?" to "Unknown"
    }
    Row(verticalAlignment = Alignment.Companion.CenterVertically) {
        Text(
            "$icon  $label",
            fontSize = 11.sp,
            color = T.Muted,
            fontFamily = FontFamily.Default
        )
    }
}