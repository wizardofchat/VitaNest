package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// FinanceScreen — hub composable: Pies | Ask toggle
// e-ink monochrome · Kindle editorial · locked 2026-04-06 ☘️

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vitanest.app.data.remote.PiesResponse
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T

private enum class FinanceSurface { PIES, ASK }

@Composable
fun FinanceScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    var surface      by remember { mutableStateOf(FinanceSurface.PIES) }
    var piesData     by remember { mutableStateOf<PiesResponse?>(null) }
    var isLoading    by remember { mutableStateOf(true) }

    // Data fetched once at hub level — passed down to PiesSurface
    LaunchedEffect(Unit) {
        repository.getPortfolioPies().let { result ->
            if (result.isSuccess) piesData = result.getOrNull()
            isLoading = false
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
                    .padding(horizontal = T.screenPadding)
            ) {
                Spacer(modifier = Modifier.height(52.dp))

                // Back + title row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = T.Ink,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "← Back", style = T.meta)
                        Text(text = "FINANCE", style = T.sectionHead)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (surface == FinanceSurface.PIES) {
                        piesData?.fetchedAt?.let {
                            Text(text = it, style = T.meta)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = T.heavyRule, color = T.Ink)
                Spacer(modifier = Modifier.height(12.dp))

                // ── Pill toggle ───────────────────────────────
                FinancePillToggle(
                    selected = surface,
                    onSelect = { surface = it }
                )

                Spacer(modifier = Modifier.height(4.dp))
            }

            // ── Surface content ───────────────────────────────
            when (surface) {
                FinanceSurface.PIES -> PiesSurface(
                    piesData = piesData,
                    isLoading = isLoading
                )
                FinanceSurface.ASK  -> FinanceAskSurface(
                    repository = repository
                )
            }
        }
    }
}

// ── Two-pill toggle ───────────────────────────────────────────
@Composable
private fun FinancePillToggle(
    selected: FinanceSurface,
    onSelect: (FinanceSurface) -> Unit
) {
    val pillShape = RoundedCornerShape(20.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FinanceSurface.entries.forEach { surface ->
            val isActive = selected == surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(pillShape)
                    .background(if (isActive) T.Ink else Color.White)
                    .border(
                        width = 0.5.dp,
                        color = if (isActive) T.Ink else T.Rule,
                        shape = pillShape
                    )
                    .clickable { onSelect(surface) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = surface.name,            // "PIES" | "ASK"
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp,
                    color = if (isActive) T.Paper else T.Muted
                )
            }
        }
    }
}