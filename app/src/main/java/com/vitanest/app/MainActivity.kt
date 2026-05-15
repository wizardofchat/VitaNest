package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// MainActivity — BuddieViewModel scoped to activity, survives tab switches
// Updated: health route now renders GrowthScreen (Health accessible via turbine petal);
//          dca_detail/{ticker} route retained ☘️

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vitanest.app.data.repository.VitaClawRepository

class MainActivity : ComponentActivity() {

    private val vitaClawRepository = VitaClawRepository()

    // ViewModel scoped to activity — survives tab switches
    private val buddieViewModel: BuddieViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BuddieViewModel(vitaClawRepository) as T
        }
    }

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
                        composable("portfolio_detail") {
                            FinanceScreen(
                                navController = navController,
                                repository    = vitaClawRepository
                            )
                        }
                        composable("dca_detail/{ticker}") { backStackEntry ->
                            val ticker = backStackEntry.arguments
                                ?.getString("ticker") ?: ""
                            DcaDetailScreen(
                                navController = navController,
                                repository    = vitaClawRepository,
                                ticker        = ticker
                            )
                        }
                        composable("ask") {
                            AskScreen(
                                navController = navController,
                                viewModel     = buddieViewModel
                            )
                        }
                        composable("energy") {
                            EnergyDetailScreen(
                                navController = navController,
                                repository    = vitaClawRepository
                            )
                        }
                        // "health" route now renders GrowthScreen.
                        // HealthScreen is still reachable via the turbine Health petal
                        // and centre ring on HomeScreen — both navigate to "health".
                        // The bottom nav tab label is updated in InkBottomNav (HomeScreen.kt).
                        composable("health") {
                            GrowthScreen(
                                navController = navController,
                                repository    = vitaClawRepository
                            )
                        }
                        // Dedicated health route — used when navigating directly to HealthScreen
                        // e.g. from a future deep link or system status tile.
                        composable("health_detail") {
                            HealthScreen(
                                navController = navController,
                                repository    = vitaClawRepository
                            )
                        }
                        composable("council") {
                            ComingSoonScreen(onBack = { navController.popBackStack() })
                        }
                        composable("soul") {
                            ComingSoonScreen(onBack = { navController.popBackStack() })
                        }
                        composable("sky") {
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
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
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