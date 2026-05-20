package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// HomeScreen — turbine/shamrock canvas redesign ☘️
// Updated: state moved to HomeViewModel (activity-scoped).
//          No LaunchedEffect — data loads once on app open,
//          survives all tab switches. Refresh icon triggers vm.refresh(). ☘️

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import com.vitanest.app.data.cache.CacheFreshness
import com.vitanest.app.data.remote.*
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlin.math.*

// ── Blade geometry constants ──────────────────────────────────
private const val INNER_RADIUS_DP = 52f
private const val OUTER_RADIUS_DP = 175f
private const val BLADE_ARC_DEG   = 98f
private const val BLADE_TAPER_DEG = 36f
private const val RING_RADIUS_DP  = 50f
private const val RING_STROKE_DP  = 10f
private const val RING_INNER_DP   = 28f
private val BLADE_FILL            = Color(0xFF2D6A4F)
private val BLADE_STROKE_COLOR    = Color(0xFF1B4332)
private val BLADE_LABEL_COLOR     = Color(0xFFF2EFE8)
private val BLADE_METRIC_COLOR    = Color(0xFFA8D5BC)

// ── Freshness colours ─────────────────────────────────────────
private val FreshGreenBg  = Color(0xFFEAF3DE)
private val FreshGreenFg  = Color(0xFF3B6D11)
private val FreshGreenBdr = Color(0xFF3B6D11)
private val FreshAmberBg  = Color(0xFFFAEEDA)
private val FreshAmberFg  = Color(0xFF854F0B)
private val FreshAmberBdr = Color(0xFF854F0B)
private val FreshRedBg    = Color(0xFFFCEBEB)
private val FreshRedFg    = Color(0xFFA32D2D)
private val FreshRedBdr   = Color(0xFFA32D2D)
private val FreshGreyBg   = Color(0xFFF2EFE8)
private val FreshGreyFg   = Color(0xFF888888)
private val FreshGreyBdr  = Color(0xFFC8C4BB)

private val BLADE_ANGLES = listOf(
    Triple("Energy",  -90f, 0),
    Triple("Finance", 150f, 1),
    Triple("Health",   30f, 2),
)

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel           // ← activity-scoped, never recreated
) {
    // ── Observe ViewModel state — no local state, no LaunchedEffect ──
    val briefData     by viewModel.briefData.collectAsState()
    val portfolioData by viewModel.portfolioData.collectAsState()
    val energyData    by viewModel.energyData.collectAsState()
    val whoopData     by viewModel.whoopData.collectAsState()
    val agenticScore  by viewModel.agenticScore.collectAsState()
    val freshness     by viewModel.freshness.collectAsState()
    val ageLabel      by viewModel.ageLabel.collectAsState()
    val isRefreshing  by viewModel.isRefreshing.collectAsState()
    val isOffline     by viewModel.isOffline.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }

    // ── Animations ────────────────────────────────────────────
    val blade0Alpha by produceState(0f) {
        kotlinx.coroutines.delay(0)
        animate(0f, 1f, animationSpec = tween(300)) { v, _ -> value = v }
    }
    val blade1Alpha by produceState(0f) {
        kotlinx.coroutines.delay(120)
        animate(0f, 1f, animationSpec = tween(300)) { v, _ -> value = v }
    }
    val blade2Alpha by produceState(0f) {
        kotlinx.coroutines.delay(240)
        animate(0f, 1f, animationSpec = tween(300)) { v, _ -> value = v }
    }
    val bladeAlphas = listOf(blade0Alpha, blade1Alpha, blade2Alpha)

    val recoveryScore = whoopData?.recoveryScore ?: 0f
    val arcAnim by animateFloatAsState(
        targetValue   = recoveryScore,
        animationSpec = tween(300, easing = EaseOutCubic),
        label         = "recovery_arc"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label         = "refresh_spin"
    )

    // ── Clear cache dialog ────────────────────────────────────
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text("Clear cached data?", fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, color = T.Ink)
            },
            text = {
                Text(
                    "All locally stored data will be removed. The app will fetch live " +
                            "from VitaClaw on next open. If VitaClaw is unreachable, screens will show empty.",
                    fontSize = 13.sp, color = T.Muted, lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearCache()
                }) {
                    Text("Clear cache", color = Color(0xFFA32D2D), fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = T.Muted)
                }
            },
            containerColor = Color.White,
            shape          = RoundedCornerShape(12.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = T.screenPadding)
        ) {
            // ── Header ────────────────────────────────────────
            Spacer(modifier = Modifier.statusBarsPadding())
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = "VitaNest",
                    fontFamily = T.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 22.sp,
                    color      = T.Ink
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InkStamp(label = "SCORE $agenticScore", isOnline = !isOffline)

                    // Refresh icon — tap = refresh · long press = clear cache
                    val (iconBg, iconFg, iconBdr) = when {
                        isRefreshing                      -> Triple(FreshGreyBg,  FreshGreyFg,  FreshGreyBdr)
                        freshness == CacheFreshness.GREEN -> Triple(FreshGreenBg, FreshGreenFg, FreshGreenBdr)
                        freshness == CacheFreshness.AMBER -> Triple(FreshAmberBg, FreshAmberFg, FreshAmberBdr)
                        freshness == CacheFreshness.RED   -> Triple(FreshRedBg,   FreshRedFg,   FreshRedBdr)
                        else                              -> Triple(FreshGreyBg,  FreshGreyFg,  FreshGreyBdr)
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(iconBg, RoundedCornerShape(6.dp))
                            .border(0.5.dp, iconBdr, RoundedCornerShape(6.dp))
                            .pointerInput(isRefreshing) {
                                detectTapGestures(
                                    onTap       = { viewModel.refresh() },
                                    onLongPress = { showClearDialog = true }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val rotation = if (isRefreshing) spinAngle else 0f
                        Text(
                            text     = "↻",
                            fontSize = 16.sp,
                            color    = iconFg,
                            modifier = Modifier.graphicsLayer { rotationZ = rotation }
                        )
                    }
                }
            }

            // ── Freshness pill ────────────────────────────────
            val (pillBg, pillFg, pillText) = when {
                isRefreshing                      -> Triple(FreshGreyBg,  FreshGreyFg,  "Refreshing…")
                freshness == CacheFreshness.NONE  -> Triple(FreshGreyBg,  FreshGreyFg,  "Connecting…")
                freshness == CacheFreshness.GREEN -> Triple(FreshGreenBg, FreshGreenFg, "Live · $ageLabel")
                freshness == CacheFreshness.AMBER -> Triple(FreshAmberBg, FreshAmberFg, "Cached · $ageLabel")
                freshness == CacheFreshness.RED   -> Triple(FreshRedBg,   FreshRedFg,   "Stale · $ageLabel")
                else                              -> Triple(FreshGreyBg,  FreshGreyFg,  "")
            }
            if (pillText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .background(pillBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(text = pillText, fontSize = 10.sp, color = pillFg)
                }
            }

            HorizontalDivider(thickness = T.heavyRule, color = T.Ink)

            // ── Offline banner ────────────────────────────────
            if (isOffline && !isRefreshing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FreshAmberBg)
                        .padding(vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(FreshAmberFg, RoundedCornerShape(3.dp))
                    )
                    Text(
                        text     = "VitaClaw unreachable · showing cached data",
                        fontSize = 11.sp,
                        color    = FreshAmberFg
                    )
                }
            }

            // ── Quote insight line ────────────────────────────
            val quote       = briefData?.structured?.quote
            val quoteAuthor = briefData?.structured?.quoteAuthor
            val insightText = when {
                quote != null && quoteAuthor != null -> "\"$quote\" — $quoteAuthor"
                quote != null                        -> "\"$quote\""
                else                                 -> "Synthesising daily insights…"
            }
            Text(
                text       = insightText,
                fontFamily = T.Serif,
                fontStyle  = FontStyle.Italic,
                fontSize   = 12.sp,
                color      = T.Ink,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 10.dp)
            )
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)

            // ── Turbine canvas ────────────────────────────────
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                TurbineCanvas(
                    navController = navController,
                    energyData    = energyData,
                    portfolioData = portfolioData,
                    whoopData     = whoopData,
                    bladeAlphas   = bladeAlphas,
                    recoveryArc   = arcAnim,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(16.dp)
                )
            }
        }

        InkBottomNav(
            current       = "home",
            navController = navController,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

// ── Turbine canvas — unchanged ────────────────────────────────
@Composable
private fun TurbineCanvas(
    navController: NavController,
    energyData: EnergyResponse?,
    portfolioData: PortfolioResponse?,
    whoopData: WhoopResponse?,
    bladeAlphas: List<Float>,
    recoveryArc: Float,
    modifier: Modifier = Modifier
) {
    val bladeLabels = remember(energyData, portfolioData, whoopData) {
        listOf(
            Pair("Energy", buildString {
                energyData?.solarGeneratedKwh?.let { append("${"%.1f".format(it)} kWh") }
                    ?: append("—")
                energyData?.chargeMode?.takeIf { it != "None" && it.isNotBlank() }
                    ?.let { append("  $it") }
            }),
            Pair("Finance", buildString {
                val pnl = portfolioData?.dailyPnLGbp
                if (pnl != null) append("${if (pnl >= 0) "+" else ""}£${"%.2f".format(pnl)}")
                else append("—")
            }),
            Pair("Health", buildString {
                whoopData?.let { w ->
                    if (w.hrvRmssdMilli > 0f) append("HRV ${"%.0f".format(w.hrvRmssdMilli)}")
                    if (w.sleepPerformance > 0f) {
                        if (isNotEmpty()) append("  ")
                        append("Sleep ${w.sleepPerformance.toInt()}%")
                    }
                } ?: append("—")
            })
        )
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx         = size.width / 2f
            val cy         = size.height * 0.45f
            val innerR     = INNER_RADIUS_DP.dp.toPx()
            val outerR     = OUTER_RADIUS_DP.dp.toPx()
            val ringR      = RING_RADIUS_DP.dp.toPx()
            val ringStr    = RING_STROKE_DP.dp.toPx()
            val innerCircR = RING_INNER_DP.dp.toPx()
            val centre     = Offset(cx, cy)

            BLADE_ANGLES.forEachIndexed { i, (_, angleDeg, _) ->
                drawBlade(
                    centre   = centre,
                    innerR   = innerR,
                    outerR   = outerR,
                    midAngle = angleDeg,
                    alpha    = bladeAlphas[i]
                )
            }

            val stemW   = 6.dp.toPx()
            val stemH   = 24.dp.toPx()
            val stemTop = cy + ringR + ringStr / 2f
            drawRoundRect(
                color        = Color(0xFFC8C4BB).copy(alpha = 0.45f),
                topLeft      = Offset(cx - stemW / 2f, stemTop),
                size         = androidx.compose.ui.geometry.Size(stemW, stemH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            )
            drawOval(
                color   = Color(0xFFC8C4BB).copy(alpha = 0.25f),
                topLeft = Offset(cx - stemW * 1.5f, stemTop + stemH),
                size    = androidx.compose.ui.geometry.Size(stemW * 3f, 4.dp.toPx())
            )
            drawCircle(
                color  = T.Ink,
                radius = (RING_RADIUS_DP + 4).dp.toPx(),
                center = centre,
                style  = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(color = T.Paper, radius = ringR, center = centre)
            drawCircle(
                color  = Color(0xFFE8E4DC),
                radius = ringR,
                center = centre,
                style  = Stroke(width = ringStr)
            )
            val arcColor = recoveryArcColor(recoveryArc)
            val sweep    = (recoveryArc / 100f) * 360f
            drawArc(
                color      = arcColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter  = false,
                topLeft    = Offset(cx - ringR, cy - ringR),
                size       = androidx.compose.ui.geometry.Size(ringR * 2, ringR * 2),
                style      = Stroke(width = ringStr, cap = StrokeCap.Butt)
            )
            drawCircle(color = T.Paper, radius = innerCircR, center = centre)
        }

        val bladeNavRoutes = listOf("energy", "finance_analytics", "health_detail")
        BLADE_ANGLES.forEachIndexed { i, (_, angleDeg, _) ->
            val radians = Math.toRadians(angleDeg.toDouble())
            val midR    = (INNER_RADIUS_DP + OUTER_RADIUS_DP) / 2f
            val offsetX = (midR * cos(radians)).dp
            val offsetY = (midR * sin(radians)).dp
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = offsetX, y = offsetY)
                    .size(72.dp)
                    .clickable { navController.navigate(bladeNavRoutes[i]) }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size((RING_RADIUS_DP * 2).dp)
                .clickable { navController.navigate("health_detail") }
        )

        Column(
            modifier            = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val score = if ((whoopData?.recoveryScore ?: 0f) > 0f)
                "${whoopData!!.recoveryScore.toInt()}" else "—"
            Text(
                text       = score,
                fontFamily = T.Serif,
                fontWeight = FontWeight.Bold,
                fontSize   = 20.sp,
                color      = T.Ink
            )
            Text(
                text          = "RECOVERY",
                fontSize      = 7.sp,
                fontWeight    = FontWeight.Medium,
                color         = T.Muted,
                letterSpacing = 1.5.sp
            )
        }

        BLADE_ANGLES.forEachIndexed { i, (_, angleDeg, _) ->
            val (domainLabel, metricLabel) = bladeLabels[i]
            val radians = Math.toRadians(angleDeg.toDouble())
            val midR    = (INNER_RADIUS_DP + OUTER_RADIUS_DP) / 2f + 8f
            val offsetX = (midR * cos(radians)).dp
            val offsetY = (midR * sin(radians)).dp
            Column(
                modifier            = Modifier
                    .align(Alignment.Center)
                    .offset(x = offsetX, y = offsetY),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text       = domainLabel,
                    fontFamily = T.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                    color      = BLADE_LABEL_COLOR
                )
                Text(
                    text     = metricLabel,
                    fontSize = 10.sp,
                    color    = BLADE_METRIC_COLOR,
                    maxLines = 1
                )
            }
        }
    }
}

// ── Blade path ────────────────────────────────────────────────
private fun DrawScope.drawBlade(
    centre: Offset,
    innerR: Float,
    outerR: Float,
    midAngle: Float,
    alpha: Float
) {
    val halfArc   = BLADE_ARC_DEG / 2f
    val halfTaper = BLADE_TAPER_DEG / 2f

    val outerStartDeg = midAngle - halfArc
    val innerStartDeg = midAngle - halfTaper
    val innerEndDeg   = midAngle + halfTaper

    fun angToOffset(angleDeg: Float, r: Float): Offset {
        val rad = Math.toRadians(angleDeg.toDouble())
        return Offset(
            (centre.x + r * cos(rad)).toFloat(),
            (centre.y + r * sin(rad)).toFloat()
        )
    }

    val path = Path().apply {
        val p0 = angToOffset(innerStartDeg, innerR)
        moveTo(p0.x, p0.y)
        val p1 = angToOffset(outerStartDeg, outerR)
        lineTo(p1.x, p1.y)
        val left  = centre.x - outerR
        val top   = centre.y - outerR
        val oSize = androidx.compose.ui.geometry.Size(outerR * 2, outerR * 2)
        arcTo(
            rect              = androidx.compose.ui.geometry.Rect(left, top, left + oSize.width, top + oSize.height),
            startAngleDegrees = outerStartDeg,
            sweepAngleDegrees = BLADE_ARC_DEG,
            forceMoveTo       = false
        )
        val p3    = angToOffset(innerEndDeg, innerR)
        lineTo(p3.x, p3.y)
        val iLeft = centre.x - innerR
        val iTop  = centre.y - innerR
        val iSize = androidx.compose.ui.geometry.Size(innerR * 2, innerR * 2)
        arcTo(
            rect              = androidx.compose.ui.geometry.Rect(iLeft, iTop, iLeft + iSize.width, iTop + iSize.height),
            startAngleDegrees = innerEndDeg,
            sweepAngleDegrees = -(innerEndDeg - innerStartDeg),
            forceMoveTo       = false
        )
        close()
    }

    drawPath(path, color = BLADE_FILL.copy(alpha = alpha))
    drawPath(path, color = BLADE_STROKE_COLOR.copy(alpha = alpha), style = Stroke(width = 2.dp.toPx()))
}

// ── Recovery arc colour ───────────────────────────────────────
private fun recoveryArcColor(score: Float) = when {
    score >= 67f -> Color(0xFF5A9E6F)
    score >= 34f -> Color(0xFFD4A017)
    else         -> Color(0xFFC0392B)
}

// ── Stamp ─────────────────────────────────────────────────────
@Composable
fun InkStamp(label: String, isOnline: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .background(T.Ink, RoundedCornerShape(2.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = label, style = T.stampLabel)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = if (isOnline) T.Ink else T.Rule,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
    }
}

// ── Section head ──────────────────────────────────────────────
@Composable
fun InkSectionHead(text: String) {
    Text(
        text     = text,
        style    = T.sectionHead,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

// ── Module row ────────────────────────────────────────────────
@Composable
fun InkModuleRow(
    label: String,
    value: String,
    meta: String? = null,
    valueColor: Color = T.Ink,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, style = T.bodyValue)
            if (meta != null) Text(text = meta, style = T.meta)
        }
        Text(
            text       = value,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 13.sp,
            color      = valueColor
        )
    }
    HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
}

// ── Morning brief (reference, not rendered on screen) ─────────
@Composable
fun MorningBriefInk(brief: BriefResponse?) {
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .animateContentSize()
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(text = "MORNING BRIEF", style = T.sectionHead)
            Text(text = if (isExpanded) "▲" else "▼", style = T.meta)
        }
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text       = brief?.summary ?: "Synthesising your daily insights…",
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize   = 14.sp,
            lineHeight = 22.sp,
            color      = T.Ink,
            maxLines   = if (isExpanded) Int.MAX_VALUE else 3,
            overflow   = TextOverflow.Ellipsis
        )
    }
}

// ── Bottom nav ────────────────────────────────────────────────
@Composable
fun InkBottomNav(
    current: String,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple("home",             "Home ☘️", "home"),
        Triple("portfolio_detail", "Finance",  "portfolio_detail"),
        Triple("energy",           "Energy",   "energy"),
        Triple("health",           "Growth",   "health"),
        Triple("ask",              "Ask",      "ask")
    )

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(T.Paper)
                .padding(horizontal = T.screenPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            items.forEach { (route, label, destination) ->
                val isActive = current == route
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.clickable {
                        navController.navigate(destination) { launchSingleTop = true }
                    }
                ) {
                    Text(
                        text       = label,
                        fontFamily = FontFamily.Default,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        fontSize   = 11.sp,
                        color      = if (isActive) T.Ink else T.Muted
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(2.dp)
                                .background(T.Ink)
                        )
                    }
                }
            }
        }
    }
}