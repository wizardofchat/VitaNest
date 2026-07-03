package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// JournalScreen — Journal tab landing view. Voice Notes + Trip Log, both
// local-only this session. Record button is standalone (no trip required);
// Trip Log shows the active trip card (if any) + completed trips list.
// Detail screens (trip stop entry, full voice notes list, new-trip form)
// are separate composables — this is the landing/hub screen only.

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.vitanest.app.data.local.journal.TripEntity
import com.vitanest.app.data.local.journal.VoiceNoteEntity
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.launch

@Composable
fun JournalScreen(
    navController: NavController,
    viewModel: JournalViewModel
) {
    LaunchedEffect(Unit) { viewModel.initialise() }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var playingNoteId by remember { mutableStateOf<String?>(null) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            try { mediaPlayer.release() } catch (_: Exception) { }
        }
    }

    fun playVoiceNote(note: VoiceNoteEntity) {
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

    fun stopPlayback() {
        try { if (mediaPlayer.isPlaying) mediaPlayer.stop() } catch (_: Exception) { }
        playingNoteId = null
    }

    var showNewTripSheet by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }

    val hasAudioPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }
    var audioPermissionGranted by remember { mutableStateOf(hasAudioPermission) }

    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> audioPermissionGranted = granted }

    Box(modifier = Modifier.fillMaxSize().background(T.Paper)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(T.screenPadding)
        ) {

            Text(
                text       = "Journal",
                fontFamily = T.Serif,
                fontWeight = FontWeight.Bold,
                fontSize   = 24.sp,
                color      = T.Ink
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Record button ────────────────────────────────
            RecordButton(
                isRecording          = uiState.isRecording,
                audioPermissionGranted = audioPermissionGranted,
                onRequestPermission   = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onStart               = {
                    // Guard: don't start recording until the first uiState
                    // emission has landed. Tapping before then reads
                    // activeTrip as null even when a trip IS active, which
                    // silently untags the note (root cause of voice notes
                    // missing from TripDetailScreen).
                    if (uiState.isReady) {
                        viewModel.startRecording(tripId = uiState.activeTrip?.tripId)
                    }
                },
                onStop                = { viewModel.stopRecording(latitude = null, longitude = null) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Voice notes section ──────────────────────────
            SectionHeader(title = "Voice notes", onSeeAll = { /* TODO: full list screen */ })
            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.recentVoiceNotes.isEmpty()) {
                Text(
                    text     = "No voice notes yet",
                    fontSize = 12.sp,
                    color    = T.Muted
                )
            } else {
                uiState.recentVoiceNotes.forEach { note ->
                    VoiceNoteRow(
                        note        = note,
                        isPlaying    = playingNoteId == note.noteId,
                        onTogglePlay = {
                            if (playingNoteId == note.noteId) stopPlayback() else playVoiceNote(note)
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Trip log section ─────────────────────────────
            SectionHeader(title = "Trip log", onSeeAll = { /* TODO: full trips list */ })
            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.activeTrip != null) {
                TripCard(
                    trip      = uiState.activeTrip!!,
                    stopCount = uiState.activeTripStopCount,
                    onClick   = { navController.navigate("trip_detail/${uiState.activeTrip!!.tripId}") },
                    onDelete  = { viewModel.deleteTrip(uiState.activeTrip!!.tripId) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            uiState.completedTrips.forEach { trip ->
                TripCard(
                    trip      = trip,
                    stopCount = null, // not loaded for completed list — detail screen shows count
                    onClick   = { navController.navigate("trip_detail/${trip.tripId}") },
                    onDelete  = { viewModel.deleteTrip(trip.tripId) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (uiState.activeTrip == null && uiState.completedTrips.isEmpty()) {
                Text(
                    text     = "No trips yet",
                    fontSize = 12.sp,
                    color    = T.Muted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    if (viewModel.canStartNewTrip()) {
                        showNewTripSheet = true
                    } else {
                        showBlockedDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New trip", fontSize = 13.sp)
            }

            // Reserve space so scrollable content never sits under the
            // overlaid bottom nav — matches nav's own height + padding.
            Spacer(modifier = Modifier.height(72.dp))
        }

        InkBottomNav(
            current       = "journal",
            navController = navController,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }

    if (showNewTripSheet) {
        NewTripDialog(
            onDismiss = { showNewTripSheet = false },
            onConfirm = { name, vehicleType, fuelType, startDate, flightOrigin, flightDestination, carRegistration ->
                viewModel.startTrip(name, vehicleType, fuelType, startDate, flightOrigin, flightDestination, carRegistration)
                showNewTripSheet = false
            }
        )
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title   = { Text("Trip already active") },
            text    = { Text("End \"${uiState.activeTrip?.name}\" before starting a new trip.") },
            confirmButton = {
                TextButton(onClick = { showBlockedDialog = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    audioPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isRecording) T.RecoveryRed.copy(alpha = 0.12f) else T.Rule.copy(alpha = 0.4f))
            .clickable {
                when {
                    !audioPermissionGranted -> onRequestPermission()
                    isRecording              -> onStop()
                    else                      -> onStart()
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) T.RecoveryRed else T.Ink),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (isRecording) "Stop recording" else "Record voice note",
                    tint               = T.InkInverted,
                    modifier           = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text       = when {
                    !audioPermissionGranted -> "Tap to allow microphone access"
                    isRecording              -> "Recording… tap to stop"
                    else                      -> "Record voice note"
                },
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                color      = T.Ink
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(text = title, style = T.sectionHead)
        Text(
            text     = "See all",
            fontSize = 12.sp,
            color    = T.Ink,
            modifier = Modifier.clickable { onSeeAll() }
        )
    }
}

@Composable
private fun VoiceNoteRow(note: VoiceNoteEntity, isPlaying: Boolean, onTogglePlay: () -> Unit) {
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
        Column(modifier = Modifier.weight(1f)) {
            val secs = note.durationSeconds ?: 0
            Text(
                text       = "${secs / 60}:${(secs % 60).toString().padStart(2, '0')} memo",
                fontSize   = 13.sp,
                color      = T.Ink
            )
            Text(
                text     = "Untagged",
                fontSize = 11.sp,
                color    = T.Muted
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TripCard(trip: TripEntity, stopCount: Int?, onClick: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = T.ruleThickness, color = T.Rule, shape = RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick     = onClick,
                onLongClick = { showDeleteConfirm = true }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = trip.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = T.Ink)
            Text(
                text     = if (trip.status == "active") "Active" else "Completed",
                fontSize = 11.sp,
                color    = T.Muted
            )
        }
        if (stopCount != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text     = "$stopCount stop${if (stopCount == 1) "" else "s"} logged",
                fontSize = 11.sp,
                color    = T.Muted
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${trip.name}\"?") },
            text  = { Text("This removes the trip and all its stops, notes, and photos. This can't be undone locally.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = T.RecoveryRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NewTripDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        vehicleType: String,
        fuelType: String?,
        startDate: String,
        flightOrigin: String?,
        flightDestination: String?,
        carRegistration: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("electric") }
    var fuelType by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var flightOrigin by remember { mutableStateOf("") }
    var flightDestination by remember { mutableStateOf("") }
    var carRegistration by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New trip") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Trip name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text("Vehicle type", fontSize = 12.sp, color = T.Muted)
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChipRow(
                        selected = vehicleType,
                        options  = listOf("electric" to "Electric", "ice" to "ICE", "none" to "None"),
                        onSelect = { vehicleType = it }
                    )
                }

                if (vehicleType == "ice") {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = fuelType, onValueChange = { fuelType = it },
                        label = { Text("Fuel type (petrol/diesel)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (vehicleType != "none") {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = carRegistration, onValueChange = { carRegistration = it },
                        label = { Text("Car registration (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = startDate, onValueChange = { startDate = it },
                    label = { Text("Start date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Flight (optional)", fontSize = 12.sp, color = T.Muted)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = flightOrigin, onValueChange = { flightOrigin = it },
                        label = { Text("From (e.g. BFS)") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = flightDestination, onValueChange = { flightDestination = it },
                        label = { Text("To (e.g. OSL)") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name,
                            vehicleType,
                            if (vehicleType == "ice") fuelType.ifBlank { null } else null,
                            startDate,
                            flightOrigin.ifBlank { null },
                            flightDestination.ifBlank { null },
                            if (vehicleType != "none") carRegistration.ifBlank { null } else null
                        )
                    }
                }
            ) { Text("Start trip") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FilterChipRow(
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    options.forEach { (value, label) ->
        val isSelected = value == selected
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) T.Ink else T.Rule.copy(alpha = 0.3f))
                .clickable { onSelect(value) }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text     = label,
                fontSize = 12.sp,
                color    = if (isSelected) T.InkInverted else T.Ink
            )
        }
    }
}