package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// DcaDetailScreen — single ticker DCA analysis
// Data: GET /portfolio/dca/{ticker} ☘️
// Updated: rewritten against real API shape 2026-05-12

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitanest.app.data.remote.DcaDetailResponse
import com.vitanest.app.data.remote.DcaMonthlyBuy
import com.vitanest.app.data.remote.DcaPriceBucket
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T

@Composable
fun DcaDetailScreen(
    navController: NavController,
    repository: VitaClawRepository,
    ticker: String
) {
    var dcaData   by remember { mutableStateOf<DcaDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error     by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ticker) {
        isLoading = true
        error = null
        repository.getDcaDetail(ticker).fold(
            onSuccess = { dcaData = it },
            onFailure = { error = it.message }
        )
        isLoading = false
    }

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
                    .padding(horizontal = T.screenPadding)
            ) {
                Spacer(modifier = Modifier.height(52.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick  = { navController.popBackStack() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = T.Ink,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "← Finance", style = T.meta)
                        Text(text = ticker, style = T.sectionHead)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    dcaData?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = it.name.take(20), style = T.meta)
                            Text(
                                text  = "${it.currency} · ${it.overview.totalOrders} orders",
                                style = T.meta
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = T.heavyRule, color = T.Ink)
                Spacer(modifier = Modifier.height(4.dp))
            }

            // ── Body ──────────────────────────────────────────
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = T.Ink, strokeWidth = 1.5.dp)
                    }
                }
                error != null -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text      = error ?: "Unknown error",
                            style     = T.meta,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                dcaData != null -> DcaBody(data = dcaData!!)
            }
        }

        // ── Bottom nav ────────────────────────────────────────
        InkBottomNav(
            current       = "portfolio_detail",
            navController = navController,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

// ── Body ──────────────────────────────────────────────────────

@Composable
private fun DcaBody(data: DcaDetailResponse) {
    val perf = data.performance
    val eff  = data.dcaEffectiveness
    val ov   = data.overview

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = T.screenPadding)
            .padding(bottom = 80.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Performance ───────────────────────────────────────
        DcaSectionLabel("Performance")
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DcaMetricCard(
                label    = "Current value",
                value    = "£%.2f".format(perf.currentValueGbp),
                modifier = Modifier.weight(1f)
            )
            DcaMetricCard(
                label      = "Capital return",
                value      = "%+.2f%%".format(perf.capitalReturnPct),
                valueColor = returnColor(perf.capitalReturnPct),
                modifier   = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DcaMetricCard(
                label    = "Blended avg",
                value    = "£%.4f".format(perf.blendedAvgGbp),
                modifier = Modifier.weight(1f)
            )
            DcaMetricCard(
                label    = "Current price",
                value    = "£%.4f".format(perf.currentPriceGbp),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DcaMetricCard(
                label      = "Capital gain",
                value      = "£%.2f".format(perf.capitalGainGbp),
                valueColor = returnColor(perf.capitalGainGbp),
                modifier   = Modifier.weight(1f)
            )
            DcaMetricCard(
                label    = "Total invested",
                value    = "£%.2f".format(ov.totalInvestedGbp),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(thickness = 0.5.dp, color = T.Ink.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(16.dp))

        // ── Monthly buys ──────────────────────────────────────
        if (data.monthly.isNotEmpty()) {
            DcaSectionLabel("Monthly buys · qty vs price")
            Spacer(modifier = Modifier.height(8.dp))
            DcaCombinedChart(monthly = data.monthly)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 12.dp, height = 8.dp)
                            .background(
                                T.Ink,
                                androidx.compose.foundation.shape.RoundedCornerShape(1.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "above avg", style = T.meta)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 12.dp, height = 8.dp)
                            .background(
                                T.Ink.copy(alpha = 0.22f),
                                androidx.compose.foundation.shape.RoundedCornerShape(1.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "below avg", style = T.meta)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(width = 16.dp, height = 8.dp)) {
                        val y = size.height / 2
                        var x = 0f
                        while (x < size.width) {
                            drawLine(
                                color       = Color(0xFF555555),
                                start       = Offset(x, y),
                                end         = Offset((x + 4f).coerceAtMost(size.width), y),
                                strokeWidth = 3f
                            )
                            x += 7f
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "avg price", style = T.meta)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(thickness = 0.5.dp, color = T.Ink.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Price distribution ────────────────────────────────
        if (data.priceDistribution.isNotEmpty()) {
            DcaSectionLabel("Price distribution · orders per range")
            Spacer(modifier = Modifier.height(8.dp))
            DcaDistributionChart(buckets = data.priceDistribution)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DcaMetricCard(
                    label    = "Min price",
                    value    = "£%.2f".format(data.priceDistribution.first().rangeLow),
                    modifier = Modifier.weight(1f)
                )
                DcaMetricCard(
                    label    = "Max price",
                    value    = "£%.2f".format(data.priceDistribution.last().rangeHigh),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(thickness = 0.5.dp, color = T.Ink.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── DCA effectiveness ─────────────────────────────────
        DcaSectionLabel("DCA effectiveness")
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .background(
                        T.Ink.copy(alpha = 0.12f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(
                            fraction = (eff.buysAbovePct / 100.0).toFloat().coerceIn(0f, 1f)
                        )
                        .background(
                            T.Ink,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        )
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text  = "%.0f%% above avg".format(eff.buysAbovePct),
                style = T.meta
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        DcaRow("Buys above avg", "${eff.buysAboveAvg} orders")
        DcaRow("Buys below avg", "${eff.buysBelowAvg} orders")
        Spacer(modifier = Modifier.height(8.dp))
        if (eff.verdict.isNotBlank()) {
            DcaVerdictBadgeSmall(verdict = eff.verdict)
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(thickness = 0.5.dp, color = T.Ink.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(16.dp))

        // ── Dividends ─────────────────────────────────────────
        data.dividends?.let { div ->
            DcaSectionLabel("Dividends · ${div.frequency}")
            Spacer(modifier = Modifier.height(8.dp))
            DcaRow("Payments recorded", "${div.numPayments}")
            DcaRow("Total received",    "£%.2f".format(div.totalReceivedGbp))
            DcaRow("Dividend return",   "%+.2f%%".format(div.dividendReturnPct))
            if (div.lastDate.isNotBlank()) {
                DcaRow("Last payment",   "£%.2f · ${div.lastDate}".format(div.lastAmountGbp))
                DcaRow("Last per share", "£%.4f".format(div.lastPerShareGbp))
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(thickness = 0.5.dp, color = T.Ink.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Overview ──────────────────────────────────────────
        DcaSectionLabel("Overview")
        Spacer(modifier = Modifier.height(8.dp))
        DcaRow("First order",   ov.firstOrder)
        DcaRow("First price",   "£%.4f".format(ov.firstPriceGbp))
        DcaRow("Last order",    ov.lastOrder)
        DcaRow("Last price",    "£%.4f".format(ov.lastPriceGbp))
        DcaRow("Cash orders",   "${ov.cashOrders}")
        DcaRow("Reinvestments", "${ov.reinvestmentOrders}")
        DcaRow("Total qty",     "%.4f".format(ov.totalQuantity))
        if (ov.reinvestedQuantity > 0) {
            DcaRow("Reinvested qty", "%.4f".format(ov.reinvestedQuantity))
        }

        // ── Verdict ───────────────────────────────────────────
        data.verdict?.let { v ->
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(thickness = 0.5.dp, color = T.Ink.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            DcaSectionLabel("Verdict")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DcaRatingBadge(rating = v.rating)
                Column(horizontalAlignment = Alignment.End) {
                    if (v.capitalReturnPct != 0.0) {
                        Text(
                            text  = "Capital   %+.2f%%".format(v.capitalReturnPct),
                            style = T.meta
                        )
                    }
                    if (v.dividendReturnPct != 0.0) {
                        Text(
                            text  = "Dividend  %+.2f%%".format(v.dividendReturnPct),
                            style = T.meta
                        )
                    }
                    if (v.totalReturnPct != 0.0) {
                        Text(
                            text  = "Total     %+.2f%%".format(v.totalReturnPct),
                            style = T.bodyValue
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ── Combined chart ────────────────────────────────────────────

@Composable
private fun DcaCombinedChart(monthly: List<DcaMonthlyBuy>) {
    if (monthly.isEmpty()) return

    val maxPrice   = monthly.maxOf { it.avgPriceGbp }.takeIf { it > 0.0 } ?: 1.0
    val minPrice   = monthly.minOf { it.avgPriceGbp }
    val priceRange = (maxPrice - minPrice).takeIf { it > 0.0 } ?: 1.0
    val maxOrders  = monthly.maxOf { it.orders }.takeIf { it > 0 } ?: 1

    val barMaxHeight = 64.dp
    val chartHeight  = 96.dp

    Box(modifier = Modifier.fillMaxWidth().height(chartHeight)) {

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment     = Alignment.Bottom
        ) {
            monthly.forEach { entry ->
                val fraction  = (entry.orders.toFloat() / maxOrders).coerceIn(0.01f, 1f)
                val barHeight = barMaxHeight * fraction
                val shortMonth = entry.month.takeLast(2)

                Column(
                    modifier            = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text      = "£%.2f".format(entry.avgPriceGbp),
                        style     = T.meta.copy(
                            fontSize = androidx.compose.ui.unit.TextUnit(
                                7f, androidx.compose.ui.unit.TextUnitType.Sp
                            )
                        ),
                        textAlign = TextAlign.Center,
                        maxLines  = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .background(
                                if (entry.aboveAvgQty) T.Ink else T.Ink.copy(alpha = 0.22f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                    topStart = 2.dp, topEnd = 2.dp
                                )
                            )
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text      = shortMonth,
                        style     = T.meta,
                        textAlign = TextAlign.Center,
                        maxLines  = 1
                    )
                    Text(
                        text      = "${entry.orders}",
                        style     = T.meta.copy(
                            color    = T.Ink.copy(alpha = 0.5f),
                            fontSize = androidx.compose.ui.unit.TextUnit(
                                7f, androidx.compose.ui.unit.TextUnitType.Sp
                            )
                        ),
                        textAlign = TextAlign.Center,
                        maxLines  = 1
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .padding(bottom = 28.dp)
                .padding(top = 12.dp)
        ) {
            val w  = size.width
            val h  = size.height
            val n  = monthly.size
            if (n < 2) return@Canvas

            val stepX  = w / n
            val points = monthly.mapIndexed { i, entry ->
                val normalised = ((entry.avgPriceGbp - minPrice) / priceRange).toFloat()
                val x = stepX * i + stepX / 2
                val y = h - (normalised * h * 0.7f) - h * 0.1f
                Offset(x, y)
            }

            for (i in 0 until points.size - 1) {
                val start    = points[i]
                val end      = points[i + 1]
                val dx       = end.x - start.x
                val dy       = end.y - start.y
                val dist     = kotlin.math.sqrt(dx * dx + dy * dy)
                val dashLen  = 8f
                val gapLen   = 5f
                var progress = 0f
                while (progress < dist) {
                    val t1 = progress / dist
                    val t2 = ((progress + dashLen) / dist).coerceAtMost(1f)
                    drawLine(
                        color       = Color(0xFF555555),
                        start       = Offset(start.x + dx * t1, start.y + dy * t1),
                        end         = Offset(start.x + dx * t2, start.y + dy * t2),
                        strokeWidth = 2.5f
                    )
                    progress += dashLen + gapLen
                }
            }

            points.forEach { pt ->
                drawCircle(color = Color(0xFF555555), radius = 4f, center = pt)
                drawCircle(color = Color(0xFFF2EFE8), radius = 2f, center = pt)
            }
        }
    }
}

// ── Distribution chart ────────────────────────────────────────

@Composable
private fun DcaDistributionChart(buckets: List<DcaPriceBucket>) {
    if (buckets.isEmpty()) return
    val maxCount     = buckets.maxOf { it.count }.takeIf { it > 0 } ?: 1
    val barMaxHeight = 52.dp

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.Bottom
    ) {
        buckets.forEach { bucket ->
            val fraction = (bucket.count.toFloat() / maxCount).coerceIn(0.04f, 1f)
            Column(
                modifier            = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text      = "${bucket.count}",
                    style     = T.meta.copy(color = T.Ink),
                    textAlign = TextAlign.Center,
                    maxLines  = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barMaxHeight * fraction)
                        .background(
                            T.Ink.copy(alpha = 0.5f + 0.5f * fraction),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = 2.dp, topEnd = 2.dp
                            )
                        )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text      = "£%.0f".format(bucket.rangeLow),
                    style     = T.meta,
                    textAlign = TextAlign.Center,
                    maxLines  = 1
                )
                Text(
                    text      = "–%.0f".format(bucket.rangeHigh),
                    style     = T.meta,
                    textAlign = TextAlign.Center,
                    maxLines  = 1
                )
            }
        }
    }
}

// ── Verdict badges ────────────────────────────────────────────

@Composable
private fun DcaVerdictBadgeSmall(verdict: String) {
    val label = when (verdict) {
        "trending_up"   -> "Trending up ↑"
        "trending_down" -> "Trending down ↓"
        "mixed"         -> "Mixed"
        else            -> verdict
    }
    Box(
        modifier = Modifier
            .background(
                T.Ink.copy(alpha = 0.08f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = label, style = T.meta.copy(color = T.Ink))
    }
}

@Composable
private fun DcaRatingBadge(rating: String) {
    val label = when (rating) {
        "strong"      -> "Strong ★"
        "positive"    -> "Positive"
        "slight_loss" -> "Slight loss"
        "underwater"  -> "Underwater"
        else          -> rating.ifBlank { "—" }
    }
    val filled = rating == "strong" || rating == "positive"
    Box(
        modifier = Modifier
            .background(
                if (filled) T.Ink else T.Ink.copy(alpha = 0.08f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text  = label,
            style = T.bodyValue.copy(color = if (filled) T.Paper else T.Ink)
        )
    }
}

// ── Sub-components ────────────────────────────────────────────

@Composable
private fun DcaSectionLabel(text: String) {
    Text(
        text  = text.uppercase(),
        style = T.meta.copy(
            letterSpacing = androidx.compose.ui.unit.TextUnit(
                0.1f, androidx.compose.ui.unit.TextUnitType.Em
            )
        )
    )
}

@Composable
private fun DcaMetricCard(
    label: String,
    value: String,
    valueColor: Color = T.Ink,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                T.Ink.copy(alpha = 0.06f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text = label, style = T.meta)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = T.bodyValue.copy(color = valueColor))
    }
}

@Composable
private fun DcaRow(label: String, value: String) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = T.meta)
        Text(text = value, style = T.meta.copy(color = T.Ink))
    }
}

// ── Helpers ───────────────────────────────────────────────────

private fun returnColor(pct: Double): Color = when {
    pct >= 5.0  -> Color(0xFF1A5C1A)
    pct >= 0.0  -> Color(0xFF2A7A2A)
    pct >= -5.0 -> Color(0xFFA07000)
    else        -> Color(0xFFA32D2D)
}