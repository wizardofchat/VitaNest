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
import com.vitanest.app.ui.theme.VitaNestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VitaNestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") { HomeScreen(navController) }
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