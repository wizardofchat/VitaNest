package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// PiesSurface — pies + donut display surface (child of FinanceScreen)
// Previously: PortfolioDetailScreen — data fetch + wrapper moved to FinanceScreen
// Donut gap fix: gap applied between segments only, not after the last one ☘️

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitanest.app.data.remote.PieItem
import com.vitanest.app.data.remote.PiesResponse
import com.vitanest.app.ui.theme.VitaNestTheme as T

// ── Public entry point called by FinanceScreen ────────────────
@Composable
fun PiesSurface(
    piesData: PiesResponse?,
    isLoading: Boolean
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = T.Ink, strokeWidth = 2.dp)
        }
        return
    }

    var selectedPieIndex by remember { mutableIntStateOf(-1) }
    var showAllPies      by remember { mutableStateOf(false) }

    val pies        = piesData?.pies ?: emptyList()
    val visiblePies = if (showAllPies) pies else pies.take(5)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = T.screenPadding),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {

        // ── Hero numbers ──────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(T.sectionGap))
            Text(
                text       = piesData?.totalValueGbp?.let { "£%,.0f".format(it) } ?: "£0",
                fontFamily = T.Serif,
                fontWeight = FontWeight.Bold,
                fontSize   = 40.sp,
                color      = T.Ink
            )
            val pnl    = piesData?.totalPnlGbp ?: 0.0
            val pnlStr = "${if (pnl >= 0) "+" else ""}£%.2f".format(pnl)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = pnlStr, style = T.meta)
                Text(text = "·", style = T.meta)
                Text(
                    text  = "${if (pnl >= 0) "+" else ""}${"%.1f".format(
                        if ((piesData?.totalValueGbp ?: 0.0) > 0)
                            pnl / (piesData!!.totalValueGbp - pnl) * 100 else 0.0
                    )}%",
                    style = T.meta
                )
            }
            Spacer(modifier = Modifier.height(T.sectionGap))
        }

        // ── Stats row ────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = T.sectionGap),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InkStatBlock(
                    label    = "PIES",
                    value    = pies.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                InkStatBlock(
                    label    = "HOLDINGS",
                    value    = pies.sumOf { it.holdingsCount }.toString(),
                    modifier = Modifier.weight(1f)
                )
                InkStatBlock(
                    label    = "DIVIDENDS",
                    value    = "£%.0f".format(pies.sumOf { it.dividendsGainedGbp }),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Donut ─────────────────────────────────────────
        item {
            InkDonutChart(
                pies          = pies,
                selectedIndex = selectedPieIndex,
                onSegmentClick = { idx ->
                    selectedPieIndex = if (selectedPieIndex == idx) -1 else idx
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Text(
                text     = "tap to filter · ${pies.size} pies",
                style    = T.meta,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
            )
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Pie list ──────────────────────────────────────
        itemsIndexed(visiblePies) { index, pie ->
            val isHighlighted = selectedPieIndex == index || selectedPieIndex == -1
            InkPieRow(
                pie           = pie,
                inkWeight     = T.inkRampColor(index),
                isHighlighted = isHighlighted,
                onClick       = {
                    selectedPieIndex = if (selectedPieIndex == index) -1 else index
                }
            )
        }

        // ── Show more / less ──────────────────────────────
        if (pies.size > 5) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAllPies = !showAllPies }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text  = if (showAllPies) "Show less" else "+ ${pies.size - 5} more pies",
                        style = T.meta
                    )
                    Icon(
                        imageVector     = if (showAllPies) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint            = T.Muted,
                        modifier        = Modifier.size(14.dp)
                    )
                }
                HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
            }
        }

        // ── Cash footer ───────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(T.sectionGap))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(text = "Cash across all pies", style = T.meta)
                Text(
                    text       = "£%.2f".format(piesData?.totalCashGbp ?: 0.0),
                    fontFamily = T.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                    color      = T.Ink
                )
            }
            HorizontalDivider(
                thickness = T.ruleThickness,
                color     = T.Rule,
                modifier  = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ── Donut chart — gap fix ─────────────────────────────────────
// FIX: previously `sweepAngle = sweep - 1f` was applied to every segment
// including the last, leaving a visible notch at 12 o'clock.
// Now: gap is applied only between segments (not after the last one).
@Composable
fun InkDonutChart(
    pies: List<PieItem>,
    selectedIndex: Int,
    onSegmentClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pies.isEmpty()) return

    val animProgress by animateFloatAsState(
        targetValue  = 1f,
        animationSpec = tween(800, easing = EaseOutCubic),
        label        = "donut_anim"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 48.dp.toPx()
            val diameter    = minOf(size.width, size.height) - strokeWidth
            val topLeft     = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize     = Size(diameter, diameter)
            var startAngle  = -90f
            val gapDegrees  = 1f        // gap between segments only
            val lastIndex   = pies.lastIndex

            pies.forEachIndexed { index, pie ->
                val sweep    = (pie.weightPct.toFloat() / 100f) * 360f * animProgress
                // Apply gap only between segments — not after the last one
                val drawSweep = if (index < lastIndex) sweep - gapDegrees else sweep
                val inkColor  = T.inkRampColor(index)
                val alpha     = when {
                    selectedIndex == -1    -> 1f
                    selectedIndex == index -> 1f
                    else                   -> 0.25f
                }
                drawArc(
                    color      = inkColor.copy(alpha = alpha),
                    startAngle = startAngle,
                    sweepAngle = drawSweep,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(width = strokeWidth)
                )
                startAngle += sweep     // always advance by full sweep
            }
        }

        // Centre label
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (selectedIndex >= 0 && selectedIndex < pies.size) {
                val selected = pies[selectedIndex]
                Text(
                    text       = selected.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 12.sp,
                    color      = T.Ink
                )
                Text(
                    text       = "£%,.0f".format(selected.valueGbp),
                    fontFamily = T.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 20.sp,
                    color      = T.Ink
                )
                Text(
                    text  = "%.1f%%".format(selected.weightPct),
                    style = T.meta
                )
            } else {
                Text(
                    text       = "£%,.0f".format(pies.sumOf { it.valueGbp }),
                    fontFamily = T.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 20.sp,
                    color      = T.Ink
                )
                Text(
                    text  = "${pies.size} pies",
                    style = T.meta
                )
            }
        }
    }
}

// ── Pie row ───────────────────────────────────────────────────
@Composable
fun InkPieRow(
    pie: PieItem,
    inkWeight: Color,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val contentAlpha = if (isHighlighted) 1f else 0.35f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(T.leftBarWidth)
                .height(36.dp)
                .background(inkWeight.copy(alpha = contentAlpha))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = pie.name,
                style = T.bodyValue.copy(color = T.Ink.copy(alpha = contentAlpha))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text  = "%.1f%%".format(pie.weightPct),
                    style = T.meta.copy(color = T.Muted.copy(alpha = contentAlpha))
                )
                if (pie.holdingsCount > 0) {
                    Text(
                        text  = "· ${pie.holdingsCount} holdings",
                        style = T.meta.copy(color = T.Muted.copy(alpha = contentAlpha))
                    )
                }
                pie.status?.let { status ->
                    Text(
                        text  = "· $status",
                        style = T.meta.copy(color = T.Muted.copy(alpha = contentAlpha))
                    )
                }
            }
            if (pie.tickers.isNotEmpty()) {
                Text(
                    text     = pie.tickers.joinToString(" · "),
                    style    = T.meta.copy(
                        fontSize = 9.sp,
                        color    = T.Muted.copy(alpha = contentAlpha)
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text  = "£%,.0f".format(pie.valueGbp),
                style = T.bodyValue.copy(color = T.Ink.copy(alpha = contentAlpha))
            )
            Text(
                text  = "${if (pie.pnlPct >= 0) "+" else ""}%.2f%%".format(pie.pnlPct),
                style = T.meta.copy(color = T.Muted.copy(alpha = contentAlpha))
            )
        }
    }
    HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
}

// ── Ink stat block ────────────────────────────────────────────
@Composable
fun InkStatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = T.sectionHead)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text       = value,
            fontFamily = T.Serif,
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp,
            color      = T.Ink
        )
        HorizontalDivider(
            thickness = T.ruleThickness,
            color     = T.Rule,
            modifier  = Modifier.padding(top = 6.dp)
        )
    }
}