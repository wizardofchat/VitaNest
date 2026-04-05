package com.vitanest.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi // ADD THIS
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vitanest.app.data.remote.BriefResponse
import com.vitanest.app.data.remote.HealthResponse
import com.vitanest.app.data.remote.PortfolioResponse
import com.vitanest.app.data.repository.VitaClawRepository
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.text.style.TextOverflow

// CHANGE: Use the string name or ensure the import above is exact
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    val scope = rememberCoroutineScope()

    // 1. STATE DECLARATIONS
    var briefData by remember { mutableStateOf<BriefResponse?>(null) }
    var portfolioData by remember { mutableStateOf<PortfolioResponse?>(null) }
    var agenticScore by remember { mutableIntStateOf(0) }
    var isOnline by remember { mutableStateOf(false) }

    // 2. LOGIC BLOCK
    LaunchedEffect(Unit) {
        // Fetch Health
        repository.getHealth().let { result ->
            isOnline = result.isSuccess
            agenticScore = result.getOrNull()?.agenticScore ?: 0
        }

        // Fetch Brief
        repository.getBrief().let { result ->
            if (result.isSuccess) briefData = result.getOrNull()
        }

        // Fetch Portfolio
        repository.getPortfolio().let { result ->
            if (result.isSuccess) portfolioData = result.getOrNull()
        }
    }

    // 3. UI BLOCK
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("VitaNest", fontWeight = FontWeight.Bold) },
                actions = { ConnectivityPulse(isOnline, agenticScore) }
            )
        }
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalItemSpacing = 16.dp,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Card
            item(span = StaggeredGridItemSpan.FullLine) {
                MorningBriefHero(briefData)
            }

            // Portfolio Card
            item(span = StaggeredGridItemSpan.FullLine) {
                PortfolioCard(portfolioData)
            }

            // Grid Items
            item { FeatureTile("Council", "AI Logic", Icons.Default.Groups) { navController.navigate("council") } }
            item { FeatureTile("SickSense", "Health", Icons.Default.MedicalServices) { navController.navigate("sicksense") } }
            item { FeatureTile("Flow", "Autonomy", Icons.Default.Refresh) { navController.navigate("flow") } }
            item { FeatureTile("Soul", "Growth", Icons.Default.Favorite) { navController.navigate("soul") } }
            item { FeatureTile("Sky", "Markets", Icons.Default.WbSunny) { navController.navigate("sky") } }
            item { FeatureTile("Play", "Media", Icons.Default.PlayArrow) { navController.navigate("playnest") } }
        }
    }
}

@Composable
fun MorningBriefHero(brief: BriefResponse?) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(), // Smoothly animates the resize
        onClick = { isExpanded = !isExpanded },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MORNING BRIEF",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Show less" else "Show more",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = brief?.summary ?: "Analysing your family's vitals...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
                // Condense logic: Show all lines if expanded, otherwise cap at 3
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )

            if (!isExpanded && (brief?.summary?.length ?: 0) > 100) {
                Text(
                    text = "Tap to read more...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun FeatureTile(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ConnectivityPulse(isOnline: Boolean, score: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 16.dp)
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text("SCORE", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
            Text(score.toString(), fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = (if (isOnline) Color(0xFF00C853) else Color.Red).copy(alpha = if (isOnline) alpha else 1f),
                    shape = CircleShape
                )
        )
    }
}

@Composable
fun PortfolioCard(portfolio: PortfolioResponse?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("NET WORTH", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            val totalText = portfolio?.totalValueGbp?.let { "£%,.2f".format(it) } ?: "£0.00"
            Text(
                text = totalText,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            portfolio?.dailyPnLGbp?.let { pnl ->
                val pnlText = "${if (pnl >= 0.0) "+" else ""}£%.2f".format(pnl)
                Text(
                    text = pnlText,
                    color = if (pnl >= 0.0) Color.Green else Color.Red,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}