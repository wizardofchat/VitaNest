package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// EnergyDetailScreen — Myenergi solar · EV · Eddi · Grid · Savings
// Five sections mapped from real /energy response 2026-04-24 ☘️
// charge_mode pill: FAST/ECO+ → black · ECO → outline · STOPPED → grey
// plug_state: parked — add when VitaClaw captures PLUG_STATES
// Updated: InkBottomNav added 2026-05-12

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.vitanest.app.data.remote.EnergyResponse
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private const val STALE_HOURS = 26L

@Composable
fun EnergyDetailScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    var energyData by remember { mutableStateOf<EnergyResponse?>(null) }
    var isLoading  by remember { mutableStateOf(true) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        repository.getEnergy().fold(
            onSuccess = { energyData = it },
            onFailure = { errorMsg = "Could not load energy data — check VitaClaw connection" }
        )
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = T.screenPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {

            // ── Header ────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(52.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick  = { navController.popBackStack() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = T.Ink,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "← Back", style = T.meta)
                        Text(text = "ENERGY", style = T.sectionHead)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    energyData?.date?.let { Text(text = it, style = T.meta) }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = T.heavyRule, color = T.Ink)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Loading ───────────────────────────────────────────
            if (isLoading) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color       = T.Ink,
                            strokeWidth = 1.5.dp,
                            modifier    = Modifier.size(20.dp)
                        )
                    }
                }
                return@LazyColumn
            }

            errorMsg?.let { msg ->
                item { Text(text = msg, style = T.meta, color = T.Muted) }
                return@LazyColumn
            }

            val d = energyData ?: return@LazyColumn

            // ── Stale warning ─────────────────────────────────────
            item {
                if (isDataStale(d.lastUpdated)) {
                    StaleWarning(d.lastUpdated)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // ─────────────────────────────────────────────────────
            // 1. SOLAR
            // ─────────────────────────────────────────────────────
            item {
                EnergySectionHead(
                    title = "SOLAR",
                    badge = d.solarSavingsGbp?.let { "£${"%.2f".format(it)} saved" }
                )
                EnergyKwhRow("Generated", d.solarGeneratedKwh)
                EnergyKwhRow("Self-used", d.selfConsumedKwh)
                EnergyKwhRow(
                    label      = "Exported",
                    kwh        = d.solarExportedKwh,
                    annotation = d.exportEarningsGbp?.let { "→ £${"%.2f".format(it)}" }
                )
                Spacer(modifier = Modifier.height(T.sectionGap))
            }

            // ─────────────────────────────────────────────────────
            // 2. EV
            // ─────────────────────────────────────────────────────
            item {
                EnergySectionHead(
                    title = "EV",
                    badge = d.evChargingCostGbp?.let { "£${"%.2f".format(it)} cost" },
                    pill  = d.chargeMode?.takeIf { it != "None" && it.isNotBlank() }
                )
                EnergyKwhRow("Total charged", d.evTotalKwh)
                EnergyKwhRow("From solar",    d.evSolarKwh)
                EnergyKwhRow("From grid",     d.evGridKwh)
                Spacer(modifier = Modifier.height(T.sectionGap))
            }

            // ─────────────────────────────────────────────────────
            // 3. EDDI — WATER HEATING
            // ─────────────────────────────────────────────────────
            item {
                EnergySectionHead(
                    title = "EDDI — WATER HEATING",
                    badge = when {
                        (d.eddiBoostedKwh ?: 0.0) > 0.0 ->
                            "${"%.2f".format(d.eddiBoostedKwh)} kWh boosted"
                        else -> "Solar only"
                    }
                )
                EnergyKwhRow("Solar",   d.eddiSolarKwh)
                EnergyKwhRow("Boosted", d.eddiBoostedKwh ?: 0.0)
                Spacer(modifier = Modifier.height(T.sectionGap))
            }

            // ─────────────────────────────────────────────────────
            // 4. GRID
            // ─────────────────────────────────────────────────────
            item {
                EnergySectionHead(title = "GRID")
                EnergyKwhRow(
                    label      = "Imported",
                    kwh        = d.gridImportedKwh,
                    annotation = d.homeImportCostGbp?.let { "→ £${"%.2f".format(it)}" }
                )
                EnergyKwhRow(
                    label      = "Exported",
                    kwh        = d.solarExportedKwh,
                    annotation = d.exportEarningsGbp?.let { "→ £${"%.2f".format(it)}" }
                )
                Spacer(modifier = Modifier.height(T.sectionGap))
            }

            // ─────────────────────────────────────────────────────
            // 5. SAVINGS
            // ─────────────────────────────────────────────────────
            item {
                EnergySectionHead(title = "SAVINGS")
                EnergyGbpRow("Solar saved",   d.solarSavingsGbp)
                EnergyGbpRow("Export earned", d.exportEarningsGbp)
                HorizontalDivider(
                    thickness = 1.dp,
                    color     = T.Ink,
                    modifier  = Modifier.padding(vertical = 4.dp)
                )
                EnergyGbpRow("Net saved",   d.energyCostSavingsGbp, FontWeight.Bold)
                EnergyGbpRow("Total spent", d.totalCostGbp,          FontWeight.Bold)
                Spacer(modifier = Modifier.height(T.sectionGap))
            }

            // ─────────────────────────────────────────────────────
            // TARIFFS
            // ─────────────────────────────────────────────────────
            item {
                val hasTariffs = d.tariffPeakPence != null ||
                        d.tariffCheapPence != null ||
                        d.tariffExportPence != null
                if (hasTariffs) {
                    Text(
                        text     = "TODAY'S RATES",
                        style    = T.meta,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
                    Spacer(modifier = Modifier.height(4.dp))
                    TariffRow("Peak",   d.tariffPeakPence)
                    TariffRow("Cheap",  d.tariffCheapPence)
                    TariffRow("Export", d.tariffExportPence)
                    Spacer(modifier = Modifier.height(T.sectionGap))
                }
            }

            // ── Footer ────────────────────────────────────────────
            item {
                Text(
                    text  = "Data for ${d.date} · updated ${formatTs(d.lastUpdated)}",
                    style = T.meta,
                    color = T.Muted
                )
            }
        }

        // ── Bottom nav ────────────────────────────────────────────
        InkBottomNav(
            current       = "energy",
            navController = navController,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

// ── Section head — title + optional £ badge + optional pill ──
@Composable
private fun EnergySectionHead(
    title: String,
    badge: String? = null,
    pill: String? = null
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(text = title, style = T.sectionHead)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            badge?.let {
                Text(
                    text       = it,
                    fontFamily = T.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    color      = T.Ink
                )
            }
            pill?.let { ChargeModePill(it) }
        }
    }
    HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
    Spacer(modifier = Modifier.height(8.dp))
}

// ── kWh row ───────────────────────────────────────────────────
@Composable
private fun EnergyKwhRow(
    label: String,
    kwh: Double?,
    annotation: String? = null
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(text = label, style = T.meta, modifier = Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            annotation?.let {
                Text(text = it, style = T.meta, color = T.Muted)
            }
            Text(
                text       = kwh?.let { "${"%.2f".format(it)} kWh" } ?: "—",
                fontFamily = T.Serif,
                fontSize   = 14.sp,
                color      = T.Ink
            )
        }
    }
    HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
}

// ── £ row ─────────────────────────────────────────────────────
@Composable
private fun EnergyGbpRow(
    label: String,
    gbp: Double?,
    weight: FontWeight = FontWeight.Normal
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(text = label, style = T.meta)
        Text(
            text       = gbp?.let { "£${"%.2f".format(it)}" } ?: "—",
            fontFamily = T.Serif,
            fontWeight = weight,
            fontSize   = 14.sp,
            color      = T.Ink
        )
    }
    HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
}

// ── Tariff row ────────────────────────────────────────────────
@Composable
private fun TariffRow(label: String, pence: Double?) {
    if (pence == null) return
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 10.sp, color = T.Muted, letterSpacing = 1.sp)
        Text(
            text       = "${"%.2f".format(pence)}p/kWh",
            fontSize   = 10.sp,
            fontFamily = T.Serif,
            color      = T.Ink
        )
    }
}

// ── Charge mode pill ──────────────────────────────────────────
@Composable
private fun ChargeModePill(mode: String) {
    val pillShape = RoundedCornerShape(4.dp)
    val (bg, fg) = when (mode) {
        "Fast"    -> T.Ink       to T.Paper
        "Eco+"    -> T.Ink       to T.Paper
        "Eco"     -> Color.White to T.Ink
        "Stopped" -> T.Rule      to T.Muted
        else      -> T.Rule      to T.Muted
    }
    Box(
        modifier = Modifier
            .clip(pillShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text          = mode.uppercase(),
            fontSize      = 10.sp,
            fontWeight    = FontWeight.Medium,
            letterSpacing = 1.sp,
            color         = fg
        )
    }
}

// ── Stale warning ─────────────────────────────────────────────
@Composable
private fun StaleWarning(lastUpdated: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(T.Rule.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(text = "DATA MAY BE STALE", style = T.sectionHead, color = T.Muted)
        Text(text = formatTs(lastUpdated),  style = T.meta,        color = T.Muted)
    }
}

// ── Helpers ───────────────────────────────────────────────────
private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.UK).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun parseIso(ts: String): Date? =
    try { ISO_FORMAT.parse(ts) } catch (e: Exception) { null }

private fun isDataStale(lastUpdated: String): Boolean =
    try {
        val updated = parseIso(lastUpdated) ?: return false
        val diffMs  = Date().time - updated.time
        TimeUnit.MILLISECONDS.toHours(diffMs) > STALE_HOURS
    } catch (e: Exception) { false }

private fun formatTs(ts: String): String =
    try {
        val date = parseIso(ts) ?: return ts
        SimpleDateFormat("d MMM, HH:mm", Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("Europe/London")
        }.format(date)
    } catch (e: Exception) { ts }