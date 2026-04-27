package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// AskScreen — standalone chat/query screen, routed from Ask tab
// Hosts FinanceAskSurface only — no pies, no toggle ☘️

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T

@Composable
fun AskScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = T.screenPadding)
            ) {
                Spacer(modifier = Modifier.height(52.dp))
                Text(
                    text       = "Ask",
                    fontFamily = T.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 22.sp,
                    color      = T.Ink,
                    modifier   = Modifier.padding(bottom = 16.dp)
                )
                HorizontalDivider(thickness = T.heavyRule, color = T.Ink)
                Spacer(modifier = Modifier.height(4.dp))
            }

            // ── Ask surface ───────────────────────────────────
            FinanceAskSurface(repository = repository)
        }

        InkBottomNav(
            current       = "ask",
            navController = navController,
            modifier      = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}