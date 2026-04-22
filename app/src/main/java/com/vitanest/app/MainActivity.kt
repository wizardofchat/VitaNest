package com.vitanest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vitanest.app.data.repository.VitaClawRepository

class MainActivity : ComponentActivity() {

    private val vitaClawRepository = VitaClawRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController    = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(navController, vitaClawRepository)
                        }
                        composable("sicksense") {
                            PulseDetailScreen(
                                repository = vitaClawRepository,
                                onBack     = { navController.popBackStack() }
                            )
                        }
                        // portfolio_detail now routes to FinanceScreen hub
                        // FinanceScreen owns: Pies surface + Ask surface
                        composable("portfolio_detail") {
                            FinanceScreen(
                                navController = navController,
                                repository    = vitaClawRepository
                            )
                        }
                        composable("council") {
                            ComingSoonScreen(onBack = { navController.popBackStack() })
                        }
                        composable("dividend_simulator") {
                            DividendSimulatorScreen(
                                repository = vitaClawRepository,
                                onBack     = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coming Soon") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector    = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text("This feature is coming soon!")
        }
    }
}