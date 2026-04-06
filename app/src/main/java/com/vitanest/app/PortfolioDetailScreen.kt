package com.vitanest.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vitanest.app.data.remote.PortfolioResponse
import com.vitanest.app.data.remote.Position
import com.vitanest.app.data.repository.VitaClawRepository


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioDetailScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    var portfolio by remember { mutableStateOf<PortfolioResponse?>(null) }

// Inside PortfolioDetailScreen.kt
    LaunchedEffect(Unit) {
        android.util.Log.d("VITA_DEBUG", "PortfolioDetailScreen launched. Fetching data...")
        repository.getPortfolio().let { result ->
            if (result.isSuccess) {
                val data = result.getOrNull()
                android.util.Log.d("VITA_DEBUG", "Data fetch success. Positions found: ${data?.positions?.size ?: 0}")
                portfolio = data
            } else {
                android.util.Log.e("VITA_DEBUG", "Data fetch failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF121212), // Obsidian
        topBar = {
            TopAppBar(
                title = { Text("Portfolio", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(vertical = 24.dp)) {
                    Text("TOTAL VALUE", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = portfolio?.totalValueGbp?.let { "£%,.2f".format(it) } ?: "£0.00",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    portfolio?.dailyPnLGbp?.let { pnl ->
                        Text(
                            text = "${if (pnl >= 0.0) "+" else ""}£%.2f".format(pnl),
                            color = if (pnl >= 0.0) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val positions = portfolio?.positions ?: emptyList()

            item {
                Text("TOP 10 BY VALUE", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 8.dp))
            }

            items(positions.take(10)) { pos ->
                PositionItem(pos, isDetailed = true)
            }

            if (positions.size > 10) {
                item {
                    Text(
                        "REMAINING HOLDINGS (${positions.size - 10})",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                }
                items(positions.drop(10)) { pos ->
                    PositionItem(pos, isDetailed = false)
                }
            }
        }
    }
}

@Composable
fun PositionItem(position: Position, isDetailed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(position.ticker, color = Color.White, fontWeight = FontWeight.Bold)
            if (isDetailed) Text(position.name, color = Color.Gray, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("£${String.format("%,.2f", position.marketValue)}", color = Color.White)
            Text(
                "${if (position.pnlPercent >= 0) "+" else ""}${String.format("%.2f", position.pnlPercent)}%",
                color = if (position.pnlPercent >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                fontSize = 12.sp
            )
        }
    }
}