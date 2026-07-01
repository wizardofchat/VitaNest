package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// MainActivity — all routes including portfolio_lens + income_stress + health_analytics;
//               ask + buddie_trade merged into single "buddie" route/nav item (Chat/Trade/Observations sub-tabs);
//               banking tab added — Finance tab repurposed; Finance Analytics via Home Finance petal ☘️

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

    private val journalViewModel: JournalViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                JournalViewModel(applicationContext) as T
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
                        composable("buddie") {
                            buddieViewModel.initialise(cachedBrief = cachedBrief)
                            BuddieScreen(navController = navController, viewModel = buddieViewModel, repository = vitaClawRepository)
                        }
                        composable("banking") {
                            BankingSummaryScreen(navController = navController, repository = vitaClawRepository)
                        }
                        composable("journal") {
                            JournalScreen(navController = navController, viewModel = journalViewModel)
                        }
                        composable("trip_detail/{tripId}") { backStackEntry ->
                            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
                            TripDetailScreen(
                                navController = navController,
                                viewModel     = journalViewModel,
                                tripId        = tripId
                            )
                        }
                        composable("banking_analytics") {
                            BankingAnalyticsScreen(navController = navController, repository = vitaClawRepository)
                        }
                        // Route: banking_drill/{month}/{category}/{view}/{sort}
                        // Pass "null" string for absent params
                        composable("banking_drill/{month}/{category}/{view}/{sort}") { back ->
                            val month    = back.arguments?.getString("month")?.takeIf { it != "null" }
                            val category = back.arguments?.getString("category")?.takeIf { it != "null" }
                            val view     = back.arguments?.getString("view")?.takeIf { it != "null" }
                            BankingTransactionDrillScreen(
                                navController = navController,
                                repository    = vitaClawRepository,
                                month         = month,
                                category      = category,
                                view          = view
                            )
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
                            PortfolioScreen(
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