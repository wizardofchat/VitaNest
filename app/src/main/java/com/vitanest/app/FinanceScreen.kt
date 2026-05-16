package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// FinanceScreen — scrollable tabs: Pies · Holdings · Portfolio lens · Income stress · Dividend sim ☘️

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitanest.app.data.remote.PiesResponse
import com.vitanest.app.data.remote.PortfolioResponse
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T

private enum class FinanceTab(val label: String) {
    PIES("Pies"),
    HOLDINGS("Holdings"),
    LENS("Portfolio lens"),
    INCOME_STRESS("Income stress"),
    DIVIDEND_SIM("Dividend sim")
}

@Composable
fun FinanceScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    var piesData          by remember { mutableStateOf<PiesResponse?>(null) }
    var portfolioData     by remember { mutableStateOf<PortfolioResponse?>(null) }
    var isLoadingPies     by remember { mutableStateOf(true) }
    var isLoadingHoldings by remember { mutableStateOf(false) }
    var activeTab         by remember { mutableStateOf(FinanceTab.PIES) }
    var searchQuery       by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        repository.getPortfolioPies().let { result ->
            if (result.isSuccess) piesData = result.getOrNull()
            isLoadingPies = false
        }
    }

    LaunchedEffect(activeTab) {
        if (activeTab == FinanceTab.HOLDINGS && portfolioData == null) {
            isLoadingHoldings = true
            repository.getPortfolio().let { result ->
                if (result.isSuccess) portfolioData = result.getOrNull()
                isLoadingHoldings = false
            }
        }
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
                    .background(Color.White)
            ) {
                Spacer(modifier = Modifier.height(52.dp))
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = T.screenPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        Text(text = "← Back", style = T.meta)
                        Text(text = "FINANCE", style = T.sectionHead)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    piesData?.fetchedAt?.let {
                        Text(text = it, style = T.meta)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = T.heavyRule, color = T.Ink)

                // ── Scrollable tab row (Option C) ─────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FinanceTab.entries.forEach { tab ->
                        val active = activeTab == tab
                        Column(
                            modifier            = Modifier
                                .clickable { activeTab = tab }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text  = tab.label,
                                style = T.meta.copy(
                                    color      = if (active) T.Ink else T.Muted,
                                    fontWeight = if (active)
                                        androidx.compose.ui.text.font.FontWeight.Medium
                                    else
                                        androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            )
                            if (active) {
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(2.dp)
                                        .background(T.Ink)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
            }

            // ── Tab content ───────────────────────────────────
            when (activeTab) {
                FinanceTab.PIES -> {
                    PiesSurface(
                        piesData  = piesData,
                        isLoading = isLoadingPies
                    )
                }
                FinanceTab.HOLDINGS -> {
                    HoldingsContent(
                        portfolioData  = portfolioData,
                        isLoading      = isLoadingHoldings,
                        searchQuery    = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onTickerTap    = { ticker ->
                            navController.navigate("dca_detail/$ticker")
                        }
                    )
                }
                FinanceTab.LENS -> {
                    LaunchedEffect(Unit) {
                        navController.navigate("portfolio_lens")
                        activeTab = FinanceTab.PIES
                    }
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = T.Ink, strokeWidth = 1.5.dp)
                    }
                }
                FinanceTab.INCOME_STRESS -> {
                    LaunchedEffect(Unit) {
                        navController.navigate("income_stress")
                        activeTab = FinanceTab.PIES
                    }
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = T.Ink, strokeWidth = 1.5.dp)
                    }
                }
                FinanceTab.DIVIDEND_SIM -> {
                    LaunchedEffect(Unit) {
                        navController.navigate("dividend_simulator")
                        activeTab = FinanceTab.PIES
                    }
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = T.Ink, strokeWidth = 1.5.dp)
                    }
                }
            }
        }

        InkBottomNav(
            current       = "portfolio_detail",
            navController = navController,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

// ── Holdings content ──────────────────────────────────────────

@Composable
private fun HoldingsContent(
    portfolioData: PortfolioResponse?,
    isLoading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onTickerTap: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = T.screenPadding)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value         = searchQuery,
            onValueChange = onSearchChange,
            placeholder   = {
                val count = portfolioData?.positions?.size ?: 0
                Text(
                    text  = if (count > 0) "Search $count holdings…" else "Search holdings…",
                    style = T.meta
                )
            },
            singleLine = true,
            modifier   = Modifier.fillMaxWidth(),
            colors     = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = T.Ink,
                unfocusedBorderColor = T.Ink.copy(alpha = 0.3f),
                cursorColor          = T.Ink
            ),
            textStyle = T.meta
        )
        Spacer(modifier = Modifier.height(8.dp))

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = T.Ink, strokeWidth = 1.5.dp)
                }
            }
            portfolioData == null -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(text = "No holdings data", style = T.meta)
                }
            }
            else -> {
                val filtered = portfolioData.positions.filter { pos ->
                    searchQuery.isBlank() ||
                            pos.ticker.contains(searchQuery, ignoreCase = true) ||
                            pos.name.contains(searchQuery, ignoreCase = true)
                }
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("${filtered.size} holdings", style = T.meta)
                    Text("£%.2f total".format(portfolioData.totalValueGbp), style = T.meta)
                }
                HorizontalDivider(thickness = T.heavyRule, color = T.Ink)
                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filtered, key = { it.ticker }) { position ->
                        HoldingRow(
                            ticker   = position.ticker,
                            name     = position.name,
                            valueGbp = position.marketValue,
                            pnlPct   = position.pnlPct,
                            onClick  = { onTickerTap(position.ticker) }
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color     = T.Ink.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldingRow(
    ticker: String,
    name: String,
    valueGbp: Double,
    pnlPct: Double,
    onClick: () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = ticker, style = T.bodyValue, modifier = Modifier.width(60.dp))
        Text(
            text     = name,
            style    = T.meta,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "£%.2f".format(valueGbp), style = T.bodyValue)
            Text(
                text  = "%+.2f%%".format(pnlPct),
                style = T.meta.copy(
                    color = when {
                        pnlPct > 0  -> Color(0xFF1A5C1A)
                        pnlPct < 0  -> Color(0xFFA32D2D)
                        else        -> T.Ink.copy(alpha = 0.5f)
                    }
                )
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "›", style = T.meta.copy(color = T.Ink.copy(alpha = 0.4f)))
    }
}