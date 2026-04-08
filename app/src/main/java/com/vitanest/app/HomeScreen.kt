package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// HomeScreen — e-ink monochrome · Kindle editorial · locked 2026-04-08 ☘️
// Changed: parallel coroutines — fixes socket cascade on sequential calls

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vitanest.app.data.remote.*
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
fun HomeScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    var briefData     by remember { mutableStateOf<BriefResponse?>(null) }
    var portfolioData by remember { mutableStateOf<PortfolioResponse?>(null) }
    var agenticScore  by remember { mutableIntStateOf(0) }
    var isOnline      by remember { mutableStateOf(false) }

    // ── Parallel calls — each gets its own connection, no cascade ──
    LaunchedEffect(Unit) {
        coroutineScope {
            val healthDeferred    = async { repository.getHealth() }
            val briefDeferred     = async { repository.getBrief() }
            val portfolioDeferred = async { repository.getPortfolio() }

            healthDeferred.await().let { result ->
                isOnline     = result.isSuccess
                agenticScore = result.getOrNull()?.agenticScore ?: 0
            }
            briefDeferred.await().let { result ->
                if (result.isSuccess) briefData = result.getOrNull()
            }
            portfolioDeferred.await().let { result ->
                if (result.isSuccess) portfolioData = result.getOrNull()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = T.screenPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 52.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VitaNest",
                        fontFamily = T.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = T.Ink
                    )
                    InkStamp(label = "SCORE $agenticScore", isOnline = isOnline)
                }
                HorizontalDivider(thickness = T.heavyRule, color = T.Ink)
                Spacer(modifier = Modifier.height(T.sectionGap))
            }

            item {
                MorningBriefInk(briefData)
                Spacer(modifier = Modifier.height(T.sectionGap))
            }

            item {
                InkSectionHead("BODY")
                HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
                Spacer(modifier = Modifier.height(12.dp))
                InkModuleRow(
                    label = "Recovery",
                    value = "Pulse →",
                    meta = "Tap to view today's metrics",
                    onClick = { navController.navigate("sicksense") }
                )
                Spacer(modifier = Modifier.height(T.sectionGap))
            }

            item {
                InkSectionHead("MONEY")
                HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
                Spacer(modifier = Modifier.height(12.dp))

                val total  = portfolioData?.totalValueGbp?.let { "£%,.0f".format(it) } ?: "—"
                val pnl    = portfolioData?.dailyPnLGbp ?: 0.0
                val pnlStr = "${if (pnl >= 0) "+" else ""}£%.2f".format(pnl)

                Text(
                    text = total,
                    fontFamily = T.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    color = T.Ink
                )
                Text(
                    text = pnlStr,
                    style = T.meta,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                InkModuleRow(
                    label = "Finance",
                    value = "13 pies →",
                    meta = "Portfolio detail",
                    onClick = { navController.navigate("portfolio_detail") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                InkModuleRow(
                    label = "Dividends",
                    value = "£14.17 Apr",
                    meta = "£61 to target",
                    onClick = { navController.navigate("income_detail") }
                )
                Spacer(modifier = Modifier.height(T.sectionGap))
            }

            item {
                InkSectionHead("MODULES")
                HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
                Spacer(modifier = Modifier.height(12.dp))
                InkModuleRow(
                    label = "Council",
                    value = "5 minds →",
                    onClick = { navController.navigate("council") }
                )
                Spacer(modifier = Modifier.height(4.dp))
                InkModuleRow(
                    label = "Soul",
                    value = "Growth →",
                    onClick = { navController.navigate("soul") }
                )
                Spacer(modifier = Modifier.height(4.dp))
                InkModuleRow(
                    label = "Sky",
                    value = "Markets →",
                    onClick = { navController.navigate("sky") }
                )
                Spacer(modifier = Modifier.height(T.sectionGap))
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        InkBottomNav(
            current = "brief",
            navController = navController,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

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

@Composable
fun InkSectionHead(text: String) {
    Text(
        text = text,
        style = T.sectionHead,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, style = T.bodyValue)
            if (meta != null) {
                Text(text = meta, style = T.meta)
            }
        }
        Text(
            text = value,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = valueColor
        )
    }
    HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
}

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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "MORNING BRIEF", style = T.sectionHead)
            Text(text = if (isExpanded) "▲" else "▼", style = T.meta)
        }
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = brief?.summary ?: "Synthesising your daily insights…",
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = T.Ink,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
fun InkBottomNav(
    current: String,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        "brief"   to "Brief",
        "finance" to "Finance",
        "pulse"   to "Pulse",
        "more"    to "More"
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
            items.forEach { (route, label) ->
                val isActive = current == route
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        when (route) {
                            "finance" -> navController.navigate("portfolio_detail")
                            "pulse"   -> navController.navigate("sicksense")
                            else      -> { /* coming soon */ }
                        }
                    }
                ) {
                    Text(
                        text = label,
                        fontFamily = FontFamily.Default,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                        color = if (isActive) T.Ink else T.Muted
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