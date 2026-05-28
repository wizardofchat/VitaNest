package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// MainActivity — all routes including portfolio_lens + income_stress + health_analytics;
//               buddie_trade replaces energy in bottom nav — energy still reachable via Home petal ☘️

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    private val homeViewModel: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(application, vitaClawRepository) as T
        }
    }

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
                    val cachedBrief   by homeViewModel.briefData.collectAsState()

                    NavHost(
                        navController    = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(navController = navController, viewModel = homeViewModel)
                        }
                        composable("sicksense") {
                            PulseDetailScreen(
                                repository = vitaClawRepository,
                                onBack     = { navController.popBackStack() }
                            )
                        }
                        composable("portfolio_detail") {
                            FinanceScreen(navController = navController, repository = vitaClawRepository)
                        }
                        composable("portfolio_lens") {
                            PortfolioLensScreen(navController = navController, repository = vitaClawRepository)
                        }
                        composable("income_stress") {
                            IncomeStressScreen(navController = navController, repository = vitaClawRepository)
                        }
                        composable("dca_detail/{ticker}") { backStackEntry ->
                            val ticker = backStackEntry.arguments?.getString("ticker") ?: ""
                            DcaDetailScreen(
                                navController = navController,
                                repository    = vitaClawRepository,
                                ticker        = ticker
                            )
                        }
                        composable("ask") {
                            buddieViewModel.initialise(cachedBrief = cachedBrief)
                            AskScreen(navController = navController, viewModel = buddieViewModel, repository = vitaClawRepository)
                        }
                        composable("buddie_trade") {
                            BuddieTradeScreen(navController = navController, repository = vitaClawRepository)
                        }
                        composable("energy") {
                            EnergyDetailScreen(navController = navController, repository = vitaClawRepository)
                        }
                        composable("health") {
                            GrowthScreen(navController = navController, repository = vitaClawRepository)
                        }
                        composable("health_detail") {
                            HealthScreen(navController = navController, repository = vitaClawRepository)
                        }
                        composable("health_analytics") {
                            HealthAnalyticsScreen(
                                navController = navController,
                                repository    = vitaClawRepository
                            )
                        }
                        composable("finance_analytics") {
                            FinanceAnalyticsScreen(
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier         = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) { Text("This feature is coming soon!") }
    }
}