package com.vitanest.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitanest.app.data.repository.VitaClawRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable

fun HomeScreen(
    navController: NavController,
    repository: VitaClawRepository          // Removed default for now to avoid unresolved reference
) {
    var healthStatus by remember { mutableStateOf("Ready to connect") }
    var askResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "VitaNest",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Intelligent Personal Assistant",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Test Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    isLoading = true
                    CoroutineScope(Dispatchers.IO).launch {
                        val result = repository.getHealth()
                        withContext(Dispatchers.Main) {
                            healthStatus = if (result.isSuccess) {
                                "✅ Connected | Agentic Score: ${result.getOrNull()?.agenticScore}"
                            } else {
                                "❌ ${result.exceptionOrNull()?.message}"
                            }
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                Text("Test Health")
            }

            Button(
                onClick = {
                    isLoading = true
                    CoroutineScope(Dispatchers.IO).launch {
                        val result = repository.askQuestion("What is your current status?")
                        withContext(Dispatchers.Main) {
                            askResult = if (result.isSuccess) {
                                result.getOrNull()?.answer ?: "No answer"
                            } else {
                                "Error: ${result.exceptionOrNull()?.message}"
                            }
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                Text("Ask VitaClaw")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = healthStatus, style = MaterialTheme.typography.bodyLarge)

        if (askResult.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    text = askResult,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 5 Squares Grid (your preferred style)
        Text(
            text = "Features",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { FeatureCard("Council", "Multi-LLM discussion") { navController.navigate("council") } }
            item { FeatureCard("SickSense", "Health monitoring") { navController.navigate("sicksense") } }
            item { FeatureCard("Flow", "Daily planning") { navController.navigate("flow") } }
            item { FeatureCard("Soul", "Personal growth") { navController.navigate("soul") } }
            item { FeatureCard("Sky", "Market insights") { navController.navigate("sky") } }
            item { FeatureCard("PlayNest", "Entertainment") { navController.navigate("playnest") } }
        }
    }
}

@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}