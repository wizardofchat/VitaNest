package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// HomeScreen — turbine/shamrock canvas redesign ☘️
// Three blades: Energy (top) · Finance (left) · Health (bottom-right)
// Centre ring: Whoop recovery arc, colour-coded green/amber/red
// Changed: full body replaced with Canvas turbine; LazyColumn removed;
//          Whoop fetch added (parallel); InkBottomNav updated to 5 tabs

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import com.vitanest.app.data.remote.*
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.*

// ── Blade geometry constants ──────────────────────────────────
private const val INNER_RADIUS_DP   = 52f   // gap from ring to blade root
private const val OUTER_RADIUS_DP   = 175f  // blade tip radius
private const val BLADE_ARC_DEG     = 98f   // sweep of each blade sector
private const val GAP_DEG           = 12f   // visual gap between blade tips
private const val BLADE_TAPER_DEG   = 36f   // inner narrowing for tapered root
private const val RING_RADIUS_DP    = 50f
private const val RING_STROKE_DP    = 10f
private const val RING_INNER_DP     = 28f
private val BLADE_FILL              = Color(0xFF2D6A4F)
private val BLADE_STROKE            = Color(0xFF1B4332)
private val BLADE_LABEL_COLOR       = Color(0xFFF2EFE8)
private val BLADE_METRIC_COLOR      = Color(0xFFA8D5BC)

// Blade rotation offsets (clockwise from top = 0°)
// Energy=top(−90°), Finance=left(−90+120=30° → 150°), Health=bottom-right(−90+240=150° → 270°)
// In Canvas coordinates (0° = right, CW positive):
// Energy top     → −90°  (i.e. 270°)
// Finance left   → 150°
// Health br      → 30°
private val BLADE_ANGLES = listOf(
    Triple("Energy",  -90f, 0),   // top
    Triple("Finance", 150f, 1),   // left
    Triple("Health",  30f,  2),   // bottom-right
)

@Composable
fun HomeScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    var briefData     by remember { mutableStateOf<BriefResponse?>(null) }
    var portfolioData by remember { mutableStateOf<PortfolioResponse?>(null) }
    var energyData    by remember { mutableStateOf<EnergyResponse?>(null) }
    var whoopData     by remember { mutableStateOf<WhoopResponse?>(null) }
    var agenticScore  by remember { mutableIntStateOf(0) }
    var isOnline      by remember { mutableStateOf(false) }

    // ── Parallel calls ────────────────────────────────────────
    LaunchedEffect(Unit) {
        coroutineScope {
            val healthDeferred    = async { repository.getHealth() }
            val briefDeferred     = async { repository.getBrief() }
            val portfolioDeferred = async { repository.getPortfolio() }
            val energyDeferred    = async { repository.getEnergy() }
            val whoopDeferred     = async { repository.getWhoop() }

            healthDeferred.await().let { r ->
                isOnline     = r.isSuccess
                agenticScore = r.getOrNull()?.agenticScore ?: 0
            }
            briefDeferred.await().let { r ->
                if (r.isSuccess) briefData = r.getOrNull()
            }
            portfolioDeferred.await().let { r ->
                if (r.isSuccess) portfolioData = r.getOrNull()
            }
            energyDeferred.await().let { r ->
                if (r.isSuccess) energyData = r.getOrNull()
            }
            whoopDeferred.await().let { r ->
                if (r.isSuccess) whoopData = r.getOrNull()
            }
        }
    }

    // ── Blade fade-in animation (120ms stagger) ───────────────
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

    // ── Recovery arc animation ────────────────────────────────
    val recoveryScore = whoopData?.recoveryScore ?: 0f
    val arcAnim by animateFloatAsState(
        targetValue  = recoveryScore,
        animationSpec = tween(300, easing = EaseOutCubic),
        label        = "recovery_arc"
    )

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
            Spacer(modifier = Modifier.height(36.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
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
                InkStamp(label = "SCORE $agenticScore", isOnline = isOnline)
            }
            HorizontalDivider(thickness = T.heavyRule, color = T.Ink)

            // ── Insight line ──────────────────────────────────
            val insightText = briefData?.summary
                ?.split(".")
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.let { "$it." }
                ?: "Synthesising daily insights…"

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
                modifier = Modifier
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

        // ── Bottom nav ────────────────────────────────────────
        InkBottomNav(
            current       = "home",
            navController = navController,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

// ── Turbine canvas ────────────────────────────────────────────
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
    // Pre-build blade label strings
    val bladeLabels = remember(energyData, portfolioData, whoopData) {
        listOf(
            // Energy blade
            Pair(
                "Energy",
                buildString {
                    energyData?.solarGeneratedKwh?.let { append("${"%.1f".format(it)} kWh") }
                        ?: append("—")
                    energyData?.chargeMode?.takeIf { it != "None" && it.isNotBlank() }
                        ?.let { append("  $it") }
                }
            ),
            // Finance blade
            Pair(
                "Finance",
                buildString {
                    val pnl = portfolioData?.dailyPnLGbp
                    if (pnl != null) {
                        append("${if (pnl >= 0) "+" else ""}£${"%.2f".format(pnl)}")
                    } else {
                        append("—")
                    }
                }
            ),
            // Health blade
            Pair(
                "Health",
                buildString {
                    whoopData?.let { w ->
                        if (w.hrvRmssdMilli > 0f) append("HRV ${"%.0f".format(w.hrvRmssdMilli)}")
                        if (w.sleepPerformance > 0f) {
                            if (isNotEmpty()) append("  ")
                            append("Sleep ${w.sleepPerformance.toInt()}%")
                        }
                    } ?: append("—")
                }
            )
        )
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx      = size.width / 2f
            val cy      = size.height * 0.45f  // bias upward — prevents bottom clip
            val innerR  = INNER_RADIUS_DP.dp.toPx()
            val outerR  = OUTER_RADIUS_DP.dp.toPx()
            val ringR   = RING_RADIUS_DP.dp.toPx()
            val ringStr = RING_STROKE_DP.dp.toPx()
            val innerCircR = RING_INNER_DP.dp.toPx()
            val centre  = Offset(cx, cy)

            // Draw blades
            BLADE_ANGLES.forEachIndexed { i, (_, angleDeg, _) ->
                drawBlade(
                    centre   = centre,
                    innerR   = innerR,
                    outerR   = outerR,
                    midAngle = angleDeg,
                    alpha    = bladeAlphas[i]
                )
            }

            // Stem
            val stemW  = 6.dp.toPx()
            val stemH  = 24.dp.toPx()
            val stemTop = cy + ringR + ringStr / 2f
            drawRoundRect(
                color        = Color(0xFFC8C4BB).copy(alpha = 0.45f),
                topLeft      = Offset(cx - stemW / 2f, stemTop),
                size         = androidx.compose.ui.geometry.Size(stemW, stemH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            )
            // Oval shadow below stem
            drawOval(
                color   = Color(0xFFC8C4BB).copy(alpha = 0.25f),
                topLeft = Offset(cx - stemW * 1.5f, stemTop + stemH),
                size    = androidx.compose.ui.geometry.Size(stemW * 3f, 4.dp.toPx())
            )

            // Outer border circle
            drawCircle(
                color  = T.Ink,
                radius = (RING_RADIUS_DP + 4).dp.toPx(),
                center = centre,
                style  = Stroke(width = 1.5.dp.toPx())
            )

            // Ring track
            drawCircle(
                color  = T.Paper,
                radius = ringR,
                center = centre
            )
            drawCircle(
                color  = Color(0xFFE8E4DC),
                radius = ringR,
                center = centre,
                style  = Stroke(width = ringStr)
            )

            // Recovery arc
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

            // Inner cream circle
            drawCircle(
                color  = T.Paper,
                radius = innerCircR,
                center = centre
            )
        }

        // ── Blade click targets (invisible boxes over each blade) ─
        // We place clickable Box overlays at approximate blade positions
        val bladeNavRoutes = listOf("energy", "portfolio_detail", "sicksense")
        BLADE_ANGLES.forEachIndexed { i, (_, angleDeg, _) ->
            val radians = Math.toRadians(angleDeg.toDouble())
            val midR    = (INNER_RADIUS_DP + OUTER_RADIUS_DP) / 2f
            // Offset box centre proportionally — layout in dp
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

        // ── Centre ring click target ──────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size((RING_RADIUS_DP * 2).dp)
                .clickable { navController.navigate("sicksense") }
        )

        // ── Centre text (score + label) ───────────────────────
        Column(
            modifier            = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val score = if ((whoopData?.recoveryScore ?: 0f) > 0f)
                "${whoopData!!.recoveryScore.toInt()}"
            else "—"
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

        // ── Blade labels (drawn via Text composables) ─────────
        // Positioned at each blade's visual centre
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

// ── Blade path drawing ────────────────────────────────────────
// Parametric swept sector: midAngle is the blade's centre axis.
// Inner edges are tapered by BLADE_TAPER_DEG for visual lean.
private fun DrawScope.drawBlade(
    centre: Offset,
    innerR: Float,
    outerR: Float,
    midAngle: Float,    // degrees, 0=right, CW+
    alpha: Float
) {
    val halfArc   = BLADE_ARC_DEG / 2f
    val halfTaper = BLADE_TAPER_DEG / 2f

    // Outer arc: midAngle ± halfArc
    val outerStartDeg = midAngle - halfArc
    val outerEndDeg   = midAngle + halfArc

    // Inner edge: narrower than outer by taper
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
        // Start at inner-start corner
        val p0 = angToOffset(innerStartDeg, innerR)
        moveTo(p0.x, p0.y)

        // Line to outer-start corner
        val p1 = angToOffset(outerStartDeg, outerR)
        lineTo(p1.x, p1.y)

        // Arc along outer edge from outerStart → outerEnd
        val left   = centre.x - outerR
        val top    = centre.y - outerR
        val oSize  = androidx.compose.ui.geometry.Size(outerR * 2, outerR * 2)
        arcTo(
            rect        = androidx.compose.ui.geometry.Rect(left, top, left + oSize.width, top + oSize.height),
            startAngleDegrees = outerStartDeg,
            sweepAngleDegrees = BLADE_ARC_DEG,
            forceMoveTo = false
        )

        // Line back to inner-end corner
        val p3 = angToOffset(innerEndDeg, innerR)
        lineTo(p3.x, p3.y)

        // Small arc along inner edge back to start (concave notch)
        val iLeft  = centre.x - innerR
        val iTop   = centre.y - innerR
        val iSize  = androidx.compose.ui.geometry.Size(innerR * 2, innerR * 2)
        arcTo(
            rect        = androidx.compose.ui.geometry.Rect(iLeft, iTop, iLeft + iSize.width, iTop + iSize.height),
            startAngleDegrees = innerEndDeg,
            sweepAngleDegrees = -(innerEndDeg - innerStartDeg),
            forceMoveTo = false
        )
        close()
    }

    drawPath(path, color = BLADE_FILL.copy(alpha = alpha))
    drawPath(path, color = BLADE_STROKE.copy(alpha = alpha), style = Stroke(width = 2.dp.toPx()))
}

// ── Recovery arc colour ───────────────────────────────────────
private fun recoveryArcColor(score: Float) = when {
    score >= 67f -> Color(0xFF5A9E6F)  // green
    score >= 34f -> Color(0xFFD4A017)  // amber
    else         -> Color(0xFFC0392B)  // red
}

// ── Stamp (unchanged) ─────────────────────────────────────────
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

// ── Section head (unchanged) ──────────────────────────────────
@Composable
fun InkSectionHead(text: String) {
    Text(
        text     = text,
        style    = T.sectionHead,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

// ── Module row (unchanged) ────────────────────────────────────
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

// ── Morning brief (unchanged) ─────────────────────────────────
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
            lineHeight  = 22.sp,
            color      = T.Ink,
            maxLines   = if (isExpanded) Int.MAX_VALUE else 3,
            overflow   = TextOverflow.Ellipsis
        )
    }
}

// ── Bottom nav — updated to 5 tabs ────────────────────────────
@Composable
fun InkBottomNav(
    current: String,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    // route, label, navigate-to
    val items = listOf(
        Triple("home",             "Home ☘️",  "home"),
        Triple("portfolio_detail", "Finance",  "portfolio_detail"),
        Triple("energy",           "Energy",   "energy"),
        Triple("sicksense",        "Health",   "sicksense"),
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
                    modifier = Modifier.clickable {
                        navController.navigate(destination) {
                            launchSingleTop = true
                        }
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