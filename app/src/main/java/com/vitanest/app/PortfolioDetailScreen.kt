package com.vitanest.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

// Colour palette — one per pie, matches Gemini handoff spec
private val PIE_COLORS = listOf(
    Color(0xFF1D9E75), // ETF
    Color(0xFF534AB7), // Whole
    Color(0xFFBA7517), // Inv Trust
    Color(0xFF185FA5), // SiddhiPie
    Color(0xFF993C1D), // Dhanteras
    Color(0xFF3C3489), // InnovETF
    Color(0xFF0F6E56), // Monthly
    Color(0xFF712B13), // REIT
    Color(0xFF444441), // SectorETF
    Color(0xFFD4537E), // Monv1
    Color(0xFF378ADD), // MonthlyAgg
    Color(0xFF639922), // Renewables
    Color(0xFF888780), // VitaWatch
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioDetailScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    var piesData by remember { mutableStateOf<PiesResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPieIndex by remember { mutableIntStateOf(-1) }
    var showAllPies by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.getPortfolioPies().let { result ->
            if (result.isSuccess) piesData = result.getOrNull()
            isLoading = false
        }
    }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Portfolio", color = Color.White, fontWeight = FontWeight.Bold)
                        piesData?.fetchedAt?.let {
                            Text(it, color = Color(0xFF666666), fontSize = 10.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1D9E75))
            }
            return@Scaffold
        }

        val pies = piesData?.pies ?: emptyList()
        val visiblePies = if (showAllPies) pies else pies.take(5)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            // Hero numbers
            item {
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)) {
                    Text("TOTAL VALUE", color = Color(0xFF666666), fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(
                        text = piesData?.totalValueGbp?.let { "£%,.2f".format(it) } ?: "£0.00",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    piesData?.totalPnlGbp?.let { pnl ->
                        Text(
                            text = "${if (pnl >= 0) "+" else ""}£%.2f".format(pnl),
                            color = if (pnl >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Stats row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatMiniCard("Pies", pies.size.toString(), Modifier.weight(1f))
                    StatMiniCard(
                        "Holdings",
                        pies.sumOf { it.holdingsCount }.toString(),
                        Modifier.weight(1f)
                    )
                    StatMiniCard(
                        "Dividends",
                        "£%.0f".format(pies.sumOf { it.dividendsGainedGbp }),
                        Modifier.weight(1f),
                        valueColor = Color(0xFF4CAF50)
                    )
                }
            }

            // Donut chart
            item {
                DonutChart(
                    pies = pies,
                    selectedIndex = selectedPieIndex,
                    onSegmentClick = { idx ->
                        selectedPieIndex = if (selectedPieIndex == idx) -1 else idx
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(bottom = 8.dp)
                )
            }

            item {
                Text(
                    "tap segment to filter · ${pies.size} pies",
                    color = Color(0xFF555555),
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).padding(bottom = 16.dp)
                )
            }

            // Pie list
            itemsIndexed(visiblePies) { index, pie ->
                val isSelected = selectedPieIndex == index || selectedPieIndex == -1
                PieRow(
                    pie = pie,
                    color = PIE_COLORS.getOrElse(index) { Color.Gray },
                    isHighlighted = isSelected,
                    onClick = {
                        selectedPieIndex = if (selectedPieIndex == index) -1 else index
                    }
                )
            }

            // Show more / less toggle
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
                            color = Color(0xFF888780),
                            fontSize = 12.sp
                        )
                        Icon(
                            imageVector = if (showAllPies) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color(0xFF888780),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Cash footer
            item {
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1A1A1A)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cash across all pies", color = Color(0xFF888780), fontSize = 13.sp)
                        Text(
                            text = "£%.2f".format(piesData?.totalCashGbp ?: 0.0),
                            color = Color(0xFFFAC775),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun DonutChart(
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
            val strokeWidth = 52.dp.toPx()
            val diameter = minOf(size.width, size.height) - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            var startAngle = -90f

            pies.forEachIndexed { index, pie ->
                val sweep = (pie.weightPct.toFloat() / 100f) * 360f * animProgress
                val color = PIE_COLORS.getOrElse(index) { Color.Gray }
                val isSelected = selectedIndex == index
                val alpha = when {
                    selectedIndex == -1 -> 1f
                    isSelected -> 1f
                    else -> 0.3f
                }
                drawArc(
                    color = color.copy(alpha = alpha),
                    startAngle = startAngle,
                    sweepAngle = sweep - 1f, // 1dp gap between segments
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
                startAngle += sweep
            }
        }

        // Centre text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (selectedIndex >= 0 && selectedIndex < pies.size) {
                val selected = pies[selectedIndex]
                Text(selected.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("£%,.0f".format(selected.valueGbp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("%.1f%%".format(selected.weightPct), color = Color(0xFF888780), fontSize = 11.sp)
            } else {
                Text("£%,.0f".format(pies.sumOf { it.valueGbp }), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("${pies.size} pies", color = Color(0xFF666666), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun PieRow(
    pie: PieItem,
    color: Color,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (isHighlighted) 1f else 0.4f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    pie.name,
                    color = Color.White.copy(alpha = alpha),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "%.1f%%".format(pie.weightPct),
                        color = Color(0xFF888780).copy(alpha = alpha),
                        fontSize = 10.sp
                    )
                    if (pie.holdingsCount > 0) {
                        Text(
                            "· ${pie.holdingsCount} holdings",
                            color = Color(0xFF888780).copy(alpha = alpha),
                            fontSize = 10.sp
                        )
                    }
                    pie.status?.let {
                        Text(
                            "· $it",
                            color = if (it == "AHEAD") Color(0xFF4CAF50).copy(alpha = alpha)
                            else Color(0xFF888780).copy(alpha = alpha),
                            fontSize = 10.sp
                        )
                    }
                }
                // Tickers sub-row
                if (pie.tickers.isNotEmpty()) {
                    Text(
                        pie.tickers.joinToString(" · "),
                        color = Color(0xFF555555).copy(alpha = alpha),
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "£%,.0f".format(pie.valueGbp),
                color = Color.White.copy(alpha = alpha),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            Text(
                "${if (pie.pnlPct >= 0) "+" else ""}%.2f%%".format(pie.pnlPct),
                color = (if (pie.pnlPct >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)).copy(alpha = alpha),
                fontSize = 11.sp
            )
        }
    }
    Divider(color = Color(0xFF1E1E1E), thickness = 0.5.dp)
}

@Composable
fun StatMiniCard(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.White) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1A1A1A)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = Color(0xFF666666), fontSize = 9.sp, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}