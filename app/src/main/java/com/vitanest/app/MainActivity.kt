package com.vitanest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vitanest.app.data.repository.MockVitaClawRepository
import com.vitanest.app.data.repository.VitaClawRepository

class MainActivity : ComponentActivity() {

    private val vitaClawRepository = VitaClawRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(navController, vitaClawRepository)
                        }
                        composable("sicksense") { ComingSoonScreen("SickSense") }
                        composable("flow") { ComingSoonScreen("Flow") }
                        composable("soul") { ComingSoonScreen("Soul") }
                        composable("sky") { ComingSoonScreen("Sky") }
                        composable("playnest") { ComingSoonScreen("PlayNest") }
                        composable("council") { ComingSoonScreen("Council") }
                    }
                }
            }
        }
    }
}