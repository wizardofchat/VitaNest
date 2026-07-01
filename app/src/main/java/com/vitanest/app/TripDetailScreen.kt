package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// TripDetailScreen — view a trip's stops, add/edit a stop (GPS auto-captured
// + reverse-geocoded via android.location.Geocoder), end the trip. Also
// shows voice notes tagged to this trip specifically — separate from the
// untagged-only list on the Journal landing screen.

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.vitanest.app.data.local.journal.TripEntity
import com.vitanest.app.data.local.journal.TripNoteEntity
import com.vitanest.app.data.local.journal.VoiceNoteEntity
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    navController: NavController,
    viewModel: JournalViewModel,
    tripId: String
) {
    val trip  by viewModel.observeTrip(tripId).collectAsState(initial = null)
    val notes by viewModel.observeNotesForTrip(tripId).collectAsState(initial = emptyList())
    val tripVoiceNotes by viewModel.observeVoiceNotesForTrip(tripId).collectAsState(initial = emptyList())

    var showStopDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<TripNoteEntity?>(null) }
    var showEndConfirm by remember { mutableStateOf(false) }

    var playingNoteId by remember { mutableStateOf<String?>(null) }
    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { try { mediaPlayer.release() } catch (_: Exception) { } } }

    fun playNote(note: VoiceNoteEntity) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(note.audioPath)
            mediaPlayer.prepare()
            mediaPlayer.setOnCompletionListener { playingNoteId = null }
            mediaPlayer.start()
            playingNoteId = note.noteId
        } catch (e: Exception) {
            playingNoteId = null
        }
    }
    fun stopNote() {
        try { if (mediaPlayer.isPlaying) mediaPlayer.stop() } catch (_: Exception) { }
        playingNoteId = null
    }

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
                        text     = currentTrip.vehicleType.replaceFirstChar { it.uppercase() } +
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

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {

                    Text(text = "Stops (${notes.size})", style = T.sectionHead)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (notes.isEmpty()) {
                        Text("No stops logged yet", fontSize = 12.sp, color = T.Muted)
                    } else {
                        notes.forEach { note ->
                            StopRow(
                                note      = note,
                                editable   = currentTrip.status == "active",
                                onEdit     = { editingNote = note; showStopDialog = true },
                                onDelete   = { viewModel.deleteTripStop(note.entryId) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    if (tripVoiceNotes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(text = "Voice notes (${tripVoiceNotes.size})", style = T.sectionHead)
                        Spacer(modifier = Modifier.height(8.dp))
                        tripVoiceNotes.forEach { note ->
                            TripVoiceNoteRow(
                                note        = note,
                                isPlaying    = playingNoteId == note.noteId,
                                onTogglePlay = { if (playingNoteId == note.noteId) stopNote() else playNote(note) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (currentTrip.status == "active") {
                    Button(
                        onClick  = { editingNote = null; showStopDialog = true },
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

    if (showStopDialog && trip != null) {
        AddStopDialog(
            existingNote = editingNote,
            onDismiss    = { showStopDialog = false; editingNote = null },
            onConfirm    = { location, latitude, longitude, quantity, cost, currency, chargeStartTimeMillis, duration, odometer, notes ->
                val note = editingNote
                if (note != null) {
                    viewModel.updateTripStop(
                        entryId          = note.entryId,
                        latitude         = latitude,
                        longitude        = longitude,
                        locationName      = location,
                        quantity          = quantity,
                        localCost         = cost,
                        localCurrency     = currency,
                        chargeStartTime    = chargeStartTimeMillis,
                        durationMinutes   = duration,
                        odometerKm        = odometer,
                        notes              = notes
                    )
                } else {
                    viewModel.addTripStop(
                        tripId          = tripId,
                        latitude        = latitude,
                        longitude       = longitude,
                        locationName     = location,
                        quantity         = quantity,
                        localCost        = cost,
                        localCurrency    = currency,
                        chargeStartTime   = chargeStartTimeMillis,
                        durationMinutes  = duration,
                        odometerKm       = odometer,
                        notes             = notes,
                        voiceNoteId       = null
                    )
                }
                showStopDialog = false
                editingNote = null
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
private fun StopRow(
    note: TripNoteEntity,
    editable: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = T.ruleThickness, color = T.Rule, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = note.locationName ?: "Unnamed stop",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                color      = T.Ink,
                modifier   = Modifier.weight(1f)
            )
            if (editable) {
                Text(
                    text     = "Edit",
                    fontSize = 11.sp,
                    color    = T.Ink,
                    modifier = Modifier.clickable { onEdit() }
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text     = "Delete",
                fontSize = 11.sp,
                color    = T.RecoveryRed,
                modifier = Modifier.clickable { onDelete() }
            )
        }
        val qtyLabel = note.quantity?.let { "$it" } ?: "—"
        val costLabel = when {
            note.localCost != null && note.localCurrency != null -> "${note.localCost} ${note.localCurrency}"
            note.localCost != null                                 -> "${note.localCost}"
            else                                                     -> "—"
        }
        val rateLabel = note.ratePerUnitLocal?.let { "%.2f/unit".format(it) }
        val timeLabel = note.chargeStartTime?.let {
            java.time.Instant.ofEpochMilli(it)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        }
        val gpsLabel = if (note.latitude != 0.0 || note.longitude != 0.0) "GPS ✓" else "no GPS"

        Text(
            text     = listOfNotNull(
                qtyLabel, costLabel, rateLabel, timeLabel, gpsLabel,
                note.odometerKm?.let { "${it}km" }
            ).joinToString(" · "),
            fontSize = 11.sp,
            color    = T.Muted
        )
        if (!note.notes.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text     = note.notes,
                fontSize = 12.sp,
                color    = T.Ink
            )
        }
    }
}

@Composable
private fun TripVoiceNoteRow(note: VoiceNoteEntity, isPlaying: Boolean, onTogglePlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = T.ruleThickness, color = T.Rule, shape = RoundedCornerShape(8.dp))
            .clickable { onTogglePlay() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = if (isPlaying) Icons.Filled.Stop else Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = if (isPlaying) "Stop" else "Play",
            tint               = if (isPlaying) T.RecoveryRed else T.Muted,
            modifier           = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        val secs = note.durationSeconds ?: 0
        Text(
            text     = "${secs / 60}:${(secs % 60).toString().padStart(2, '0')} memo",
            fontSize = 13.sp,
            color    = T.Ink
        )
    }
}

/**
 * Fetches a single current location, best-effort. Returns null if permission
 * isn't granted or the fetch fails/times out — caller must handle null by
 * letting the stop save without coordinates rather than blocking.
 */
private suspend fun fetchCurrentLocation(context: android.content.Context): Pair<Double, Double>? {
    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    if (!hasPermission) return null

    val client = LocationServices.getFusedLocationProviderClient(context)
    val cancellationTokenSource = CancellationTokenSource()

    return try {
        suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (location != null) cont.resume(location.latitude to location.longitude)
                    else cont.resume(null)
                }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { cancellationTokenSource.cancel() }
        }
    } catch (e: SecurityException) {
        null
    }
}

/**
 * Reverse-geocodes lat/long into a short human-readable label (locality +
 * country, falling back to whatever Geocoder gives us). Best-effort — the
 * Geocoder backend can be unavailable (no Play services / no network on
 * some devices), so this must never throw past this function or block save.
 * Uses the blocking Geocoder API (not the API 33+ callback overload) since
 * minSdk is 26 — run off the main thread via Dispatchers.IO.
 */
private suspend fun reverseGeocode(context: android.content.Context, lat: Double, lon: Double): String? {
    return withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocation(lat, lon, 1)
            val address = results?.firstOrNull() ?: return@withContext null

            val locality = address.locality ?: address.subAdminArea
            val country  = address.countryName

            when {
                locality != null && country != null -> "$locality, $country"
                locality != null                       -> locality
                country != null                          -> country
                else                                       -> address.getAddressLine(0)
            }
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
private fun AddStopDialog(
    existingNote: TripNoteEntity?,
    onDismiss: () -> Unit,
    onConfirm: (
        location: String?,
        latitude: Double,
        longitude: Double,
        quantity: Double?,
        cost: Double?,
        currency: String?,
        chargeStartTimeMillis: Long?,
        duration: Int?,
        odometer: Double?,
        notes: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val isEditing = existingNote != null

    val timeFormatter = remember {
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }

    var location by remember { mutableStateOf(existingNote?.locationName ?: "") }
    var locationAutofilled by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf(existingNote?.quantity?.toString() ?: "") }
    var cost by remember { mutableStateOf(existingNote?.localCost?.toString() ?: "") }
    var currency by remember { mutableStateOf(existingNote?.localCurrency ?: "") }
    var duration by remember { mutableStateOf(existingNote?.durationMinutes?.toString() ?: "") }
    var odometer by remember { mutableStateOf(existingNote?.odometerKm?.toString() ?: "") }
    var notes by remember { mutableStateOf(existingNote?.notes ?: "") }
    var chargeTimeText by remember {
        mutableStateOf(
            existingNote?.chargeStartTime?.let {
                java.time.Instant.ofEpochMilli(it)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(timeFormatter)
            } ?: java.time.LocalDateTime.now().format(timeFormatter)
        )
    }

    var gpsCoords by remember {
        mutableStateOf(
            existingNote?.let {
                if (it.latitude != 0.0 || it.longitude != 0.0) it.latitude to it.longitude else null
            }
        )
    }
    var gpsLoading by remember { mutableStateOf(!isEditing) }
    var geocodeLoading by remember { mutableStateOf(false) }

    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }
    var permissionGranted by remember { mutableStateOf(hasLocationPermission) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (!granted) gpsLoading = false
    }

    // Only auto-fetch GPS for a brand-new stop — editing keeps the
    // originally-captured coordinates unless the user wants to redo it.
    LaunchedEffect(Unit) {
        if (isEditing) return@LaunchedEffect
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            gpsLoading = true
            gpsCoords = fetchCurrentLocation(context)
            gpsLoading = false
        }
    }

    // Reverse-geocode whenever we have coordinates and the location field is
    // still empty or was itself auto-filled (never overwrite a hand-typed label).
    LaunchedEffect(gpsCoords) {
        val coords = gpsCoords ?: return@LaunchedEffect
        if (location.isNotBlank() && !locationAutofilled) return@LaunchedEffect
        geocodeLoading = true
        val resolved = reverseGeocode(context, coords.first, coords.second)
        if (resolved != null) {
            location = resolved
            locationAutofilled = true
        }
        geocodeLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit stop" else "Add stop") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                Text(
                    text = when {
                        gpsLoading            -> "Locating…"
                        gpsCoords != null      -> "GPS: ${"%.5f".format(gpsCoords!!.first)}, ${"%.5f".format(gpsCoords!!.second)}"
                        !permissionGranted     -> "GPS unavailable — permission denied"
                        else                    -> "GPS unavailable — saving without coordinates"
                    },
                    fontSize = 11.sp,
                    color    = if (gpsCoords != null) T.Muted else T.RecoveryAmber
                )
                if (geocodeLoading) {
                    Text(text = "Resolving location name…", fontSize = 11.sp, color = T.Muted)
                }
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it; locationAutofilled = false },
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
                    value = chargeTimeText, onValueChange = { chargeTimeText = it },
                    label = { Text("Charge time (yyyy-MM-dd HH:mm)") },
                    modifier = Modifier.fillMaxWidth()
                )
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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val (lat, lon) = gpsCoords ?: (0.0 to 0.0)
                val parsedTimeMillis = try {
                    java.time.LocalDateTime.parse(chargeTimeText, timeFormatter)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                } catch (e: Exception) {
                    null // invalid text — falls back to "now" at the call site rather than blocking save
                }
                onConfirm(
                    location.ifBlank { null },
                    lat,
                    lon,
                    quantity.toDoubleOrNull(),
                    cost.toDoubleOrNull(),
                    currency.ifBlank { null },
                    parsedTimeMillis,
                    duration.toIntOrNull(),
                    odometer.toDoubleOrNull(),
                    notes.ifBlank { null }
                )
            }) { Text(if (isEditing) "Update" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}