package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// PortfolioDetailScreen — e-ink monochrome · Kindle editorial · locked 2026-04-06 ☘️

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.navigation.NavController
import com.vitanest.app.data.remote.PieItem
import com.vitanest.app.data.remote.PiesResponse
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioDetailScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    var piesData         by remember { mutableStateOf<PiesResponse?>(null) }
    var isLoading        by remember { mutableStateOf(true) }
    var selectedPieIndex by remember { mutableIntStateOf(-1) }
    var showAllPies      by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.getPortfolioPies().let { result ->
            if (result.isSuccess) piesData = result.getOrNull()
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = T.Ink, strokeWidth = 2.dp)
            }
            return@Box
        }

        val pies        = piesData?.pies ?: emptyList()
        val visiblePies = if (showAllPies) pies else pies.take(5)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = T.screenPadding)
        ) {

            // ── Header ────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(52.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { navController.popBackStack() },
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
                            text = "← Back",
                            style = T.meta
                        )
                        Text(
                            text = "PORTFOLIO",
                            style = T.sectionHead
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    piesData?.fetchedAt?.let {
                        Text(text = it, style = T.meta)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(thickness = T.heavyRule, color = T.Ink)
                Spacer(modifier = Modifier.height(T.sectionGap))
            }

            // ── Hero numbers ──────────────────────────────────
            item {
                Text(
                    text = piesData?.totalValueGbp?.let { "£%,.0f".format(it) } ?: "£0",
                    fontFamily = T.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    color = T.Ink
                )
                val pnl    = piesData?.totalPnlGbp ?: 0.0
                val pnlStr = "${if (pnl >= 0) "+" else ""}£%.2f".format(pnl)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = pnlStr, style = T.meta)
                    Text(text = "·", style = T.meta)
                    Text(
                        text = "${if (pnl >= 0) "+" else ""}${"%.1f".format(
                            if ((piesData?.totalValueGbp ?: 0.0) > 0)
                                pnl / (piesData!!.totalValueGbp - pnl) * 100 else 0.0
                        )}%",
                        style = T.meta
                    )
                }
                Spacer(modifier = Modifier.height(T.sectionGap))
            }

            // ── Stats row — ink stamps ─────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = T.sectionGap),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InkStatBlock(
                        label = "PIES",
                        value = pies.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    InkStatBlock(
                        label = "HOLDINGS",
                        value = pies.sumOf { it.holdingsCount }.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    InkStatBlock(
                        label = "DIVIDENDS",
                        value = "£%.0f".format(pies.sumOf { it.dividendsGainedGbp }),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Greyscale donut ───────────────────────────────
            item {
                InkDonutChart(
                    pies = pies,
                    selectedIndex = selectedPieIndex,
                    onSegmentClick = { idx ->
                        selectedPieIndex = if (selectedPieIndex == idx) -1 else idx
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
                Text(
                    text = "tap to filter · ${pies.size} pies",
                    style = T.meta,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .padding(vertical = 8.dp)
                )
                HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Pie list — left ink bars ───────────────────────
            itemsIndexed(visiblePies) { index, pie ->
                val isHighlighted = selectedPieIndex == index || selectedPieIndex == -1
                InkPieRow(
                    pie = pie,
                    inkWeight = T.inkRampColor(index),
                    isHighlighted = isHighlighted,
                    onClick = {
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showAllPies) "Show less" else "+ ${pies.size - 5} more pies",
                            style = T.meta
                        )
                        Icon(
                            imageVector = if (showAllPies) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = T.Muted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
                }
            }

            // ── Cash footer ───────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(T.sectionGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Cash across all pies", style = T.meta)
                    Text(
                        text = "£%.2f".format(piesData?.totalCashGbp ?: 0.0),
                        fontFamily = T.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = T.Ink
                    )
                }
                HorizontalDivider(
                    thickness = T.ruleThickness,
                    color = T.Rule,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

// ── Greyscale donut chart ─────────────────────────────────────
@Composable
fun InkDonutChart(
    pies: List<PieItem>,
    selectedIndex: Int,
    onSegmentClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pies.isEmpty()) return

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = EaseOutCubic),
        label = "donut_anim"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 48.dp.toPx()
            val diameter = minOf(size.width, size.height) - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            var startAngle = -90f

            pies.forEachIndexed { index, pie ->
                val sweep    = (pie.weightPct.toFloat() / 100f) * 360f * animProgress
                val inkColor = T.inkRampColor(index)
                val alpha    = when {
                    selectedIndex == -1   -> 1f
                    selectedIndex == index -> 1f
                    else                  -> 0.25f
                }
                drawArc(
                    color      = inkColor.copy(alpha = alpha),
                    startAngle = startAngle,
                    sweepAngle = sweep - 1f,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(width = strokeWidth)
                )
                startAngle += sweep
            }
        }

        // Centre label
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (selectedIndex >= 0 && selectedIndex < pies.size) {
                val selected = pies[selectedIndex]
                Text(
                    text = selected.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = T.Ink
                )
                Text(
                    text = "£%,.0f".format(selected.valueGbp),
                    fontFamily = T.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = T.Ink
                )
                Text(
                    text = "%.1f%%".format(selected.weightPct),
                    style = T.meta
                )
            } else {
                Text(
                    text = "£%,.0f".format(pies.sumOf { it.valueGbp }),
                    fontFamily = T.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = T.Ink
                )
                Text(
                    text = "${pies.size} pies",
                    style = T.meta
                )
            }
        }
    }
}

// ── Pie row — left ink bar replaces coloured dot ──────────────
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left ink bar — width 3dp, height scales with row
        Box(
            modifier = Modifier
                .width(T.leftBarWidth)
                .height(36.dp)
                .background(inkWeight.copy(alpha = contentAlpha))
        )
        Spacer(modifier = Modifier.width(12.dp))

        // Pie info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pie.name,
                style = T.bodyValue.copy(color = T.Ink.copy(alpha = contentAlpha))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "%.1f%%".format(pie.weightPct),
                    style = T.meta.copy(color = T.Muted.copy(alpha = contentAlpha))
                )
                if (pie.holdingsCount > 0) {
                    Text(
                        text = "· ${pie.holdingsCount} holdings",
                        style = T.meta.copy(color = T.Muted.copy(alpha = contentAlpha))
                    )
                }
                pie.status?.let { status ->
                    Text(
                        text = "· $status",
                        style = T.meta.copy(color = T.Muted.copy(alpha = contentAlpha))
                    )
                }
            }
            if (pie.tickers.isNotEmpty()) {
                Text(
                    text = pie.tickers.joinToString(" · "),
                    style = T.meta.copy(
                        fontSize = 9.sp,
                        color = T.Muted.copy(alpha = contentAlpha)
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Value + P&L — monochrome, sign-only colour distinction removed
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "£%,.0f".format(pie.valueGbp),
                style = T.bodyValue.copy(color = T.Ink.copy(alpha = contentAlpha))
            )
            Text(
                text = "${if (pie.pnlPct >= 0) "+" else ""}%.2f%%".format(pie.pnlPct),
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
            text = value,
            fontFamily = T.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = T.Ink
        )
        HorizontalDivider(
            thickness = T.ruleThickness,
            color = T.Rule,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}