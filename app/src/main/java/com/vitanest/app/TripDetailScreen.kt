package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// TripDetailScreen — view a trip's stops, add a new stop, end the trip.
// NOTE: Add-stop entry here is manual (location typed, not GPS-derived) —
// GPS auto-fill via FusedLocationProviderClient is a follow-up piece, not
// yet wired. Built now so "tap a trip → crash" is fixed and the core
// add-stop / end-trip loop is testable before the trip.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vitanest.app.data.local.journal.TripEntity
import com.vitanest.app.data.local.journal.TripNoteEntity
import com.vitanest.app.ui.theme.VitaNestTheme as T

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    navController: NavController,
    viewModel: JournalViewModel,
    tripId: String
) {
    val trip  by viewModel.observeTrip(tripId).collectAsState(initial = null)
    val notes by viewModel.observeNotesForTrip(tripId).collectAsState(initial = emptyList())

    var showAddStop by remember { mutableStateOf(false) }
    var showEndConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.name ?: "Trip", fontFamily = T.Serif, color = T.Ink) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = T.Ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = T.Paper)
            )
        },
        containerColor = T.Paper
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(T.screenPadding)
        ) {
            val currentTrip = trip

            if (currentTrip == null) {
                Text("Loading…", color = T.Muted, fontSize = 13.sp)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text     = "${currentTrip.vehicleType.replaceFirstChar { it.uppercase() }}" +
                                (currentTrip.fuelType?.let { " · $it" } ?: ""),
                        fontSize = 12.sp,
                        color    = T.Muted
                    )
                    Text(
                        text     = if (currentTrip.status == "active") "Active" else "Completed",
                        fontSize = 12.sp,
                        color    = T.Muted
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Stops (${notes.size})", style = T.sectionHead)
                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (notes.isEmpty()) {
                        Text("No stops logged yet", fontSize = 12.sp, color = T.Muted)
                    } else {
                        notes.forEach { note ->
                            StopRow(note = note, onDelete = { viewModel.deleteTripStop(note.entryId) })
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (currentTrip.status == "active") {
                    Button(
                        onClick  = { showAddStop = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = T.Ink)
                    ) {
                        Text("Add stop", color = T.InkInverted)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick  = { showEndConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("End trip", color = T.Ink)
                    }
                }
            }
        }
    }

    if (showAddStop && trip != null) {
        AddStopDialog(
            onDismiss = { showAddStop = false },
            onConfirm = { location, quantity, cost, currency, duration, odometer ->
                viewModel.addTripStop(
                    tripId          = tripId,
                    latitude        = 0.0,  // GPS auto-fill — follow-up piece
                    longitude       = 0.0,  // GPS auto-fill — follow-up piece
                    locationName     = location,
                    quantity         = quantity,
                    localCost        = cost,
                    localCurrency    = currency,
                    durationMinutes  = duration,
                    odometerKm       = odometer,
                    voiceNoteId       = null
                )
                showAddStop = false
            }
        )
    }

    if (showEndConfirm && trip != null) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("End ${trip!!.name}?") },
            text  = { Text("${notes.size} stop${if (notes.size == 1) "" else "s"} logged. You can still view and edit this trip after it's ended.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.endTrip(tripId, endDate = java.time.LocalDate.now().toString())
                    showEndConfirm = false
                }) { Text("End trip") }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StopRow(note: TripNoteEntity, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = T.ruleThickness, color = T.Rule, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text       = note.locationName ?: "Unnamed stop",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                color      = T.Ink
            )
            Text(
                text     = "Delete",
                fontSize = 11.sp,
                color    = T.RecoveryRed,
                modifier = Modifier.clickable { onDelete() }
            )
        }
        val qtyLabel = note.quantity?.let { "$it" } ?: "—"
        val costLabel = if (note.localCost != null && note.localCurrency != null) {
            "${note.localCost} ${note.localCurrency}"
        } else "—"
        Text(
            text     = "$qtyLabel · $costLabel" +
                    (note.odometerKm?.let { " · ${it}km" } ?: ""),
            fontSize = 11.sp,
            color    = T.Muted
        )
    }
}

@Composable
private fun AddStopDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        location: String?,
        quantity: Double?,
        cost: Double?,
        currency: String?,
        duration: Int?,
        odometer: Double?
    ) -> Unit
) {
    var location by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add stop") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = location, onValueChange = { location = it },
                    label = { Text("Location") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantity, onValueChange = { quantity = it },
                    label = { Text("Energy/fuel amount") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = cost, onValueChange = { cost = it },
                        label = { Text("Cost") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = currency, onValueChange = { currency = it },
                        label = { Text("Currency") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = duration, onValueChange = { duration = it },
                    label = { Text("Duration (min)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = odometer, onValueChange = { odometer = it },
                    label = { Text("Odometer (km)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    location.ifBlank { null },
                    quantity.toDoubleOrNull(),
                    cost.toDoubleOrNull(),
                    currency.ifBlank { null },
                    duration.toIntOrNull(),
                    odometer.toDoubleOrNull()
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}