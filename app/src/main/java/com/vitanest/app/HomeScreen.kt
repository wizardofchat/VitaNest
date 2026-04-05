package com.vitanest.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vitanest.app.data.remote.*
import com.vitanest.app.data.repository.VitaClawRepository
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    val scope = rememberCoroutineScope()
    var briefData by remember { mutableStateOf<BriefResponse?>(null) }
    var portfolioData by remember { mutableStateOf<PortfolioResponse?>(null) }
    var agenticScore by remember { mutableIntStateOf(0) }
    var isOnline by remember { mutableStateOf(false) }

    // Pulse State for Health Tile
    var pulseMetrics by remember { mutableStateOf(PulseMetrics()) }

    LaunchedEffect(Unit) {
        // 1. Fetch health status and agentic score
        repository.getHealth().let { result ->
            isOnline = result.isSuccess
            agenticScore = result.getOrNull()?.agenticScore ?: 0
            if (result.isFailure) {
                android.util.Log.e("VitaNetDebug", "Health Fetch Failed: ${result.exceptionOrNull()?.message}")
            }
        }

        // 2. Fetch Morning Brief
        repository.getBrief().let { result ->
            if (result.isSuccess) briefData = result.getOrNull()
        }

        // 3. Fetch Portfolio snapshot
        repository.getPortfolio().let { result ->
            if (result.isSuccess) portfolioData = result.getOrNull()
        }

        // 4. FIX: Use 'askQuestion' and handle the AskResponse object
        repository.askQuestion("strain today").let { result -> // Changed from askAgent
            if (result.isSuccess) {
                val rawResponse = result.getOrNull()?.answer ?: "" // Extract .answer
                android.util.Log.d("PulseRawData", "RAW TEXT: $rawResponse") // ADD THIS
                pulseMetrics = parsePulseResponse(rawResponse)
            }
        }
    }

    // Professional background gradient
    Box(modifier = Modifier
        .fillMaxSize()
        .background(
            Brush.verticalGradient(
                colors = listOf(Color(0xFFFBFBFE), Color(0xFFF3F4F9))
            )
        )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    title = { Text("VitaNest", fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp) },
                    actions = { ConnectivityPulse(isOnline, agenticScore) }
                )
            }
        ) { padding ->
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                verticalItemSpacing = 20.dp,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    MorningBriefHero(briefData)
                }

                item(span = StaggeredGridItemSpan.FullLine) {
                    PortfolioCard(portfolioData)
                }

                // High-polish feature grid
                item {
                    FeatureTile("Council", "Ask 5 minds", Icons.Default.Groups) {
                        navController.navigate("council")
                    }
                }

                item {
                    PulseHomeTile(metrics = pulseMetrics) {
                        navController.navigate("sicksense")
                    }
                }

                item { FeatureTile("Flow", "Autonomy", Icons.Default.Refresh) { navController.navigate("flow") } }
                item { FeatureTile("Soul", "Growth", Icons.Default.Favorite) { navController.navigate("soul") } }
                item { FeatureTile("Sky", "Markets", Icons.Default.WbSunny) { navController.navigate("sky") } }
                item { FeatureTile("Play", "Media", Icons.Default.PlayArrow) { navController.navigate("playnest") } }
            }
        }
    }
}

@Composable
fun MorningBriefHero(brief: BriefResponse?) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { isExpanded = !isExpanded },
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFFEBEBFF), // Lavender per brief
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MORNING BRIEF",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = Color(0xFF5C59BB)
                    )
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF5C59BB)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = brief?.summary ?: "Synthesizing your daily insights...",
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2D2B55)
                ),
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PortfolioCard(portfolio: PortfolioResponse?) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF1A1A1E), // Obsidian
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("NET WORTH", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 1.sp)
            val totalText = portfolio?.totalValueGbp?.let { "£%,.2f".format(it) } ?: "£0.00"
            Text(
                text = totalText,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = Color.White
            )
            portfolio?.dailyPnLGbp?.let { pnl ->
                Text(
                    text = "${if (pnl >= 0.0) "+" else ""}£%.2f".format(pnl),
                    color = if (pnl >= 0.0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun PulseHomeTile(metrics: PulseMetrics, onClick: () -> Unit) {
    val recoveryColor = when {
        metrics.recovery >= 67f -> Color(0xFF639922) // Green
        metrics.recovery >= 34f -> Color(0xFFEF9F27) // Amber
        else -> Color(0xFFE24B4A) // Red alert
    }

    FeatureTile(
        title = "Pulse",
        subtitle = "Recovery ${metrics.recovery.toInt()}%",
        subtitleColor = recoveryColor,
        secondarySubtitle = "Strain ${metrics.strain} • Sleep ${metrics.sleepPerformance.toInt()}%",
        icon = Icons.Default.MedicalServices,
        onClick = onClick
    )
}

@Composable
fun FeatureTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    subtitleColor: Color = Color.Gray,
    secondarySubtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(160.dp),
        shape = RoundedCornerShape(32.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                modifier = Modifier.size(48.dp).background(Color(0xFFF5F5F9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF5C59BB), modifier = Modifier.size(24.dp))
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = subtitleColor)
                if (secondarySubtitle != null) {
                    Text(text = secondarySubtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF888780))
                }
            }
        }
    }
}

@Composable
fun ConnectivityPulse(isOnline: Boolean, score: Int) {
    val alpha by rememberInfiniteTransition().animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse)
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
        Column(horizontalAlignment = Alignment.End) {
            Text("AGENT SCORE", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(score.toString(), fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.size(12.dp).background(
            color = (if (isOnline) Color(0xFF00C853) else Color.Red).copy(alpha = if (isOnline) alpha else 1f),
            shape = CircleShape
        ))
    }
}