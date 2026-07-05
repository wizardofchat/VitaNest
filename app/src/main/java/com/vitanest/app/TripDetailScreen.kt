package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// TripDetailScreen — view a trip's stops, add/edit a stop (GPS auto-captured
// + reverse-geocoded via android.location.Geocoder), end the trip. Also
// shows voice notes tagged to this trip specifically — separate from the
// untagged-only list on the Journal landing screen.

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.media.MediaPlayer
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.vitanest.app.data.local.journal.DayNoteEntity
import com.vitanest.app.data.local.journal.TripEntity
import com.vitanest.app.data.local.journal.TripNoteEntity
import com.vitanest.app.data.local.journal.TripPhotoEntity
import com.vitanest.app.data.local.journal.VoiceNoteEntity
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    navController: NavController,
    viewModel: JournalViewModel,
    tripId: String
) {
    val context = LocalContext.current
    val vmUiState by viewModel.uiState.collectAsState()
    val trip  by viewModel.observeTrip(tripId).collectAsState(initial = null)
    val notes by viewModel.observeNotesForTrip(tripId).collectAsState(initial = emptyList())
    val tripVoiceNotes by viewModel.observeVoiceNotesForTrip(tripId).collectAsState(initial = emptyList())
    val dayNotes by viewModel.observeDayNotesForTrip(tripId).collectAsState(initial = emptyList())
    val photos by viewModel.observePhotosForTrip(tripId).collectAsState(initial = emptyList())

    var showStopDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<TripNoteEntity?>(null) }
    var showEndConfirm by remember { mutableStateOf(false) }
    var showEditHeader by remember { mutableStateOf(false) }
    var showDayNoteDialog by remember { mutableStateOf(false) }
    var editingDayNote by remember { mutableStateOf<DayNoteEntity?>(null) }
    var dayNoteTargetDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var fabExpanded by remember { mutableStateOf(false) }
    var showPhotoSourceChoice by remember { mutableStateOf(false) }
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    var pendingPhotoLat by remember { mutableStateOf<Double?>(null) }
    var pendingPhotoLng by remember { mutableStateOf<Double?>(null) }
    var showPhotoCaptionDialog by remember { mutableStateOf(false) }

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

    val photoScope = androidx.compose.runtime.rememberCoroutineScope()

    // Gallery-picker — Android Photo Picker, no permission needed.
    // GPS captured best-effort (same fetchCurrentLocation pattern as stops),
    // then routed to a caption dialog before the photo is actually saved —
    // agreed in session: receipts/landmarks lose context fast, so prompt
    // immediately rather than relying on editing later.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && trip != null) {
            val savedPath = compressAndSavePhoto(context, uri)
            if (savedPath != null) {
                photoScope.launch {
                    val loc = fetchCurrentLocation(context)
                    pendingPhotoPath = savedPath
                    pendingPhotoLat = loc?.first
                    pendingPhotoLng = loc?.second
                    showPhotoCaptionDialog = true
                }
            }
        }
    }

    // Camera capture — writes directly to a FileProvider-shared file, then
    // runs through the same compress/save path as gallery picks so both
    // routes end up identically sized on disk.
    var pendingCaptureUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null && trip != null) {
            val savedPath = compressAndSavePhoto(context, uri)
            if (savedPath != null) {
                photoScope.launch {
                    val loc = fetchCurrentLocation(context)
                    pendingPhotoPath = savedPath
                    pendingPhotoLat = loc?.first
                    pendingPhotoLng = loc?.second
                    showPhotoCaptionDialog = true
                }
            }
        }
        pendingCaptureUri = null
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createCaptureUri(context)
            pendingCaptureUri = uri
            cameraLauncher.launch(uri)
        }
    }
    fun launchCamera() {
        val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            val uri = createCaptureUri(context)
            pendingCaptureUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
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
        containerColor = T.Paper,
        floatingActionButton = {
            if (trip != null) {
                TripDetailFab(
                    expanded         = fabExpanded,
                    onToggle         = { fabExpanded = !fabExpanded },
                    tripActive       = trip!!.status == "active",
                    isRecording      = vmUiState.isRecording,
                    onRecordVoice    = {
                        if (vmUiState.isRecording) {
                            fabExpanded = false
                            viewModel.stopRecording(latitude = null, longitude = null)
                        } else {
                            viewModel.startRecording(tripId = tripId)
                        }
                    },
                    onAddStop        = {
                        fabExpanded = false; editingNote = null; showStopDialog = true
                    },
                    onAddDayNote     = {
                        fabExpanded = false
                        editingDayNote = null
                        dayNoteTargetDate = java.time.LocalDate.now().toString()
                        showDayNoteDialog = true
                    },
                    onAddPhoto       = {
                        fabExpanded = false
                        showPhotoSourceChoice = true
                    },
                    onEndTrip        = { fabExpanded = false; showEndConfirm = true }
                )
            }
        }
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
                TripHeaderCard(trip = currentTrip, onEdit = { showEditHeader = true })

                Spacer(modifier = Modifier.height(8.dp))
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val uri = com.vitanest.app.data.local.journal.JournalExporter.exportTripWithData(
                                context    = context,
                                trip       = currentTrip,
                                stops      = notes,
                                dayNotes   = dayNotes,
                                voiceNotes = tripVoiceNotes,
                                photos     = photos
                            )
                            if (uri != null) {
                                com.vitanest.app.data.local.journal.JournalExporter.shareExport(context, uri)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export / Share backup", fontSize = 13.sp, color = T.Ink)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.syncNow(tripId) },
                    enabled = !vmUiState.isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (vmUiState.isSyncing) "Syncing..." else "Sync now",
                        fontSize = 13.sp,
                        color = T.Ink
                    )
                }

                if (vmUiState.lastSyncResult != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = vmUiState.lastSyncResult ?: "",
                        fontSize = 11.sp,
                        color = T.Muted
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (photos.isNotEmpty()) {
                    TripGalleryRow(photos = photos, photoCount = photos.size, onDeletePhoto = { viewModel.deleteTripPhoto(it) })
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {

                    // Derive the day list from whichever dates actually have
                    // a stop or a day note — grow-as-you-go, nothing
                    // pre-generated for empty future/past days. Grouping key
                    // for stops is chargeStartTime (dynamic — editing a
                    // stop's date moves it to that day's card on next
                    // recompose, no extra logic needed here).
                    val stopsByDate = notes.groupBy { note ->
                        note.chargeStartTime?.let {
                            java.time.Instant.ofEpochMilli(it)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate().toString()
                        } ?: "unknown"
                    }
                    val dayNotesByDate = dayNotes.groupBy { it.date }
                    val allDates = (stopsByDate.keys + dayNotesByDate.keys)
                        .filter { it != "unknown" }
                        .sorted()

                    if (allDates.isEmpty() && (stopsByDate["unknown"]?.isEmpty() != false)) {
                        Text("No stops or notes logged yet", fontSize = 12.sp, color = T.Muted)
                    } else {
                        allDates.forEach { date ->
                            Text(
                                text     = formatDayHeader(date),
                                fontSize = 12.sp,
                                color    = T.Muted,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            stopsByDate[date]?.forEach { note ->
                                StopRow(
                                    note      = note,
                                    editable   = true,
                                    onEdit     = { editingNote = note; showStopDialog = true },
                                    onDelete   = { viewModel.deleteTripStop(note.entryId) }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            dayNotesByDate[date]?.forEach { dn ->
                                val linkedVoiceNote = tripVoiceNotes.firstOrNull { it.noteId == dn.voiceNoteId }
                                DayNoteRow(
                                    dayNote      = dn,
                                    voiceNote     = linkedVoiceNote,
                                    isPlaying     = linkedVoiceNote != null && playingNoteId == linkedVoiceNote.noteId,
                                    onTogglePlay  = { linkedVoiceNote?.let { if (playingNoteId == it.noteId) stopNote() else playNote(it) } },
                                    onEdit        = { editingDayNote = dn; dayNoteTargetDate = dn.date; showDayNoteDialog = true },
                                    onDelete      = { viewModel.deleteDayNote(dn.entryId) }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        // Stops with no parseable chargeStartTime — shouldn't
                        // normally happen (addTripStop defaults to "now"),
                        // but shown rather than silently dropped if it does.
                        stopsByDate["unknown"]?.let { unknownStops ->
                            if (unknownStops.isNotEmpty()) {
                                Text("Undated", fontSize = 12.sp, color = T.Muted, modifier = Modifier.padding(bottom = 8.dp))
                                unknownStops.forEach { note ->
                                    StopRow(
                                        note      = note,
                                        editable   = true,
                                        onEdit     = { editingNote = note; showStopDialog = true },
                                        onDelete   = { viewModel.deleteTripStop(note.entryId) }
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }

                    // Voice notes not linked to any day note — surfaced
                    // separately so a mistagged/unlinked one is never lost.
                    val linkedVoiceNoteIds = dayNotes.mapNotNull { it.voiceNoteId }.toSet()
                    val unlinkedVoiceNotes = tripVoiceNotes.filter { it.noteId !in linkedVoiceNoteIds }
                    if (unlinkedVoiceNotes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Other voice notes (${unlinkedVoiceNotes.size})", style = T.sectionHead)
                        Spacer(modifier = Modifier.height(8.dp))
                        unlinkedVoiceNotes.forEach { note ->
                            TripVoiceNoteRow(
                                note        = note,
                                isPlaying    = playingNoteId == note.noteId,
                                onTogglePlay = { if (playingNoteId == note.noteId) stopNote() else playNote(note) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(80.dp)) // clear the FAB
                }
            }
        }
    }

    if (showPhotoSourceChoice) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceChoice = false },
            title = { Text("Add photo") },
            text  = { Text("Take a new photo or choose one from your gallery.") },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoSourceChoice = false
                    launchCamera()
                }) { Text("Camera") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoSourceChoice = false
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) { Text("Gallery") }
            }
        )
    }

    if (showPhotoCaptionDialog && trip != null && pendingPhotoPath != null) {
        var caption by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                viewModel.addTripPhoto(tripId, pendingPhotoPath!!, pendingPhotoLat, pendingPhotoLng, null)
                showPhotoCaptionDialog = false
                pendingPhotoPath = null
            },
            title = { Text("Add a note? (optional)") },
            text = {
                OutlinedTextField(
                    value = caption, onValueChange = { caption = it },
                    label = { Text("e.g. restaurant, landmark, receipt") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addTripPhoto(tripId, pendingPhotoPath!!, pendingPhotoLat, pendingPhotoLng, caption.ifBlank { null })
                    showPhotoCaptionDialog = false
                    pendingPhotoPath = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.addTripPhoto(tripId, pendingPhotoPath!!, pendingPhotoLat, pendingPhotoLng, null)
                    showPhotoCaptionDialog = false
                    pendingPhotoPath = null
                }) { Text("Skip") }
            }
        )
    }

    if (showStopDialog && trip != null) {
        AddStopDialog(
            existingNote = editingNote,
            vehicleType  = trip!!.vehicleType,
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

    if (showDayNoteDialog && trip != null) {
        DayNoteDialog(
            existingNote = editingDayNote,
            defaultDate   = dayNoteTargetDate,
            availableVoiceNotes = tripVoiceNotes,
            onDismiss    = { showDayNoteDialog = false; editingDayNote = null },
            onConfirm    = { date, text, mood, voiceNoteId ->
                val existing = editingDayNote
                if (existing != null) {
                    viewModel.updateDayNote(existing.entryId, text, mood, voiceNoteId)
                } else {
                    viewModel.addDayNote(tripId, date, text, mood, voiceNoteId)
                }
                showDayNoteDialog = false
                editingDayNote = null
            }
        )
    }

    if (showEditHeader && trip != null) {
        EditTripHeaderDialog(
            trip      = trip!!,
            onDismiss = { showEditHeader = false },
            onConfirm = { name, vehicleType, fuelType, flightOrigin, flightDestination, carRegistration ->
                viewModel.updateTripDetails(tripId, name, vehicleType, fuelType, flightOrigin, flightDestination, carRegistration)
                showEditHeader = false
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
private fun TripHeaderCard(trip: TripEntity, onEdit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = T.ruleThickness, color = T.Rule, shape = RoundedCornerShape(10.dp))
            .clickable { onEdit() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text     = trip.vehicleType.replaceFirstChar { it.uppercase() } +
                        (trip.fuelType?.let { " · $it" } ?: ""),
                fontSize = 12.sp,
                color    = T.Muted
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text     = if (trip.status == "active") "Active" else "Completed",
                    fontSize = 12.sp,
                    color    = T.Muted
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit", fontSize = 11.sp, color = T.Ink)
            }
        }
        if (trip.flightOrigin != null || trip.flightDestination != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text     = "${trip.flightOrigin ?: "—"} → ${trip.flightDestination ?: "—"}",
                fontSize = 12.sp,
                color    = T.Ink
            )
        }
        if (trip.carRegistration != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "Reg: ${trip.carRegistration}", fontSize = 12.sp, color = T.Ink)
        }
    }
}

@Composable
private fun EditTripHeaderDialog(
    trip: TripEntity,
    onDismiss: () -> Unit,
    onConfirm: (name: String, vehicleType: String, fuelType: String?, flightOrigin: String?, flightDestination: String?, carRegistration: String?) -> Unit
) {
    var name by remember { mutableStateOf(trip.name) }
    var vehicleType by remember { mutableStateOf(trip.vehicleType) }
    var fuelType by remember { mutableStateOf(trip.fuelType ?: "") }
    var flightOrigin by remember { mutableStateOf(trip.flightOrigin ?: "") }
    var flightDestination by remember { mutableStateOf(trip.flightDestination ?: "") }
    var carRegistration by remember { mutableStateOf(trip.carRegistration ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit trip") },
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
                    FilterChipRowShared(
                        selected = vehicleType,
                        options  = listOf("electric" to "Electric", "ice" to "ICE", "none" to "None"),
                        onSelect = { vehicleType = it }
                    )
                }
                if (vehicleType == "ice") {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = fuelType, onValueChange = { fuelType = it },
                        label = { Text("Fuel type") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (vehicleType != "none") {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = carRegistration, onValueChange = { carRegistration = it },
                        label = { Text("Car registration") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = flightOrigin, onValueChange = { flightOrigin = it },
                        label = { Text("Flight from") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = flightDestination, onValueChange = { flightDestination = it },
                        label = { Text("Flight to") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(
                        name, vehicleType,
                        if (vehicleType == "ice") fuelType.ifBlank { null } else null,
                        flightOrigin.ifBlank { null },
                        flightDestination.ifBlank { null },
                        if (vehicleType != "none") carRegistration.ifBlank { null } else null
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DayNoteDialog(
    existingNote: DayNoteEntity?,
    defaultDate: String,
    availableVoiceNotes: List<VoiceNoteEntity>,
    onDismiss: () -> Unit,
    onConfirm: (date: String, text: String, mood: String?, voiceNoteId: String?) -> Unit
) {
    val isEditing = existingNote != null
    var date by remember { mutableStateOf(existingNote?.date ?: defaultDate) }
    var text by remember { mutableStateOf(existingNote?.text ?: "") }
    var selectedMood by remember { mutableStateOf(existingNote?.mood) }
    var selectedVoiceNoteId by remember { mutableStateOf(existingNote?.voiceNoteId) }

    // Mood is captured once per day, not per-stop — agreed in session:
    // nobody rates their mood at a charging stop, they rate the day.
    val moods = listOf("😊", "😍", "🤩", "😴", "😢")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit day note" else "Add day note") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = date, onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("Notes") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Mood (optional)", fontSize = 12.sp, color = T.Muted)
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    moods.forEach { emoji ->
                        val isSelected = selectedMood == emoji
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable { selectedMood = if (isSelected) null else emoji }
                                .border(
                                    width = if (isSelected) 2.dp else T.ruleThickness,
                                    color = if (isSelected) T.Ink else T.Rule,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Text(text = emoji, fontSize = 20.sp)
                        }
                    }
                }
                if (availableVoiceNotes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Link a voice note (optional)", fontSize = 12.sp, color = T.Muted)
                    Spacer(modifier = Modifier.height(4.dp))
                    availableVoiceNotes.forEach { vn ->
                        val secs = vn.durationSeconds ?: 0
                        val label = "${secs / 60}:${(secs % 60).toString().padStart(2, '0')} memo"
                        val isSelected = selectedVoiceNoteId == vn.noteId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedVoiceNoteId = if (isSelected) null else vn.noteId }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text     = if (isSelected) "✓ " else "  ",
                                color    = T.Ink
                            )
                            Text(text = label, fontSize = 13.sp, color = T.Ink)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) onConfirm(date, text, selectedMood, selectedVoiceNoteId)
            }) { Text(if (isEditing) "Update" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DayNoteRow(
    dayNote: DayNoteEntity,
    voiceNote: VoiceNoteEntity?,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Notes, contentDescription = null, tint = T.Muted, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Day note", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = T.Ink)
                if (dayNote.mood != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = dayNote.mood, fontSize = 14.sp)
                }
            }
            Row {
                Text("Edit", fontSize = 11.sp, color = T.Ink, modifier = Modifier.clickable { onEdit() })
                Spacer(modifier = Modifier.width(12.dp))
                Text("Delete", fontSize = 11.sp, color = T.RecoveryRed, modifier = Modifier.clickable { onDelete() })
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = dayNote.text, fontSize = 13.sp, color = T.Ink)
        if (voiceNote != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .border(width = T.ruleThickness, color = T.Rule, shape = RoundedCornerShape(6.dp))
                    .clickable { onTogglePlay() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector        = if (isPlaying) Icons.Filled.Stop else Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint               = if (isPlaying) T.RecoveryRed else T.Muted,
                    modifier           = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val secs = voiceNote.durationSeconds ?: 0
                Text(text = "${secs / 60}:${(secs % 60).toString().padStart(2, '0')} memo", fontSize = 12.sp, color = T.Ink)
            }
        }
    }
}

@Composable
private fun TripGalleryRow(photos: List<TripPhotoEntity>, photoCount: Int, onDeletePhoto: (String) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Trip gallery", fontSize = 12.sp, color = T.Muted)
            Text(text = "$photoCount photo${if (photoCount == 1) "" else "s"}", fontSize = 12.sp, color = T.Muted)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LazyVerticalGrid(
            columns  = GridCells.Fixed(4),
            modifier = Modifier.heightIn(max = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement   = Arrangement.spacedBy(6.dp)
        ) {
            items(photos) { photo ->
                PhotoThumbnail(photo = photo, onDelete = { onDeletePhoto(photo.id) })
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(photo: TripPhotoEntity, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val bitmap = remember(photo.uri) {
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(photo.uri, opts)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(T.Rule.copy(alpha = 0.3f))
            .clickable { showDeleteConfirm = true }
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.Photo, contentDescription = null, tint = T.Muted, modifier = Modifier.align(Alignment.Center).size(20.dp))
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete photo?") },
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
private fun TripDetailFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    tripActive: Boolean,
    isRecording: Boolean,
    onRecordVoice: () -> Unit,
    onAddStop: () -> Unit,
    onAddDayNote: () -> Unit,
    onAddPhoto: () -> Unit,
    onEndTrip: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End) {
        if (isRecording) {
            // Recording in progress — surface a dedicated stop control
            // regardless of expanded state, so the user isn't stuck mid-record.
            MiniFabAction(label = "Stop recording", icon = Icons.Filled.Stop, onClick = onRecordVoice)
            Spacer(modifier = Modifier.height(10.dp))
        } else if (expanded) {
            if (tripActive) {
                MiniFabAction(label = "End trip", icon = Icons.Filled.Flag, onClick = onEndTrip)
                Spacer(modifier = Modifier.height(10.dp))
            }
            MiniFabAction(label = "Add photo", icon = Icons.Filled.Photo, onClick = onAddPhoto)
            Spacer(modifier = Modifier.height(10.dp))
            MiniFabAction(label = "Add day note", icon = Icons.Filled.Notes, onClick = onAddDayNote)
            Spacer(modifier = Modifier.height(10.dp))
            MiniFabAction(label = "Record voice note", icon = Icons.Filled.Mic, onClick = onRecordVoice)
            Spacer(modifier = Modifier.height(10.dp))
            if (tripActive) {
                MiniFabAction(label = "Add stop", icon = Icons.Filled.Add, onClick = onAddStop)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        FloatingActionButton(onClick = onToggle, containerColor = if (isRecording) T.RecoveryRed else T.Ink) {
            Icon(
                imageVector        = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = if (expanded) "Close" else "Actions",
                tint               = T.InkInverted
            )
        }
    }
}

@Composable
private fun MiniFabAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text     = label,
            fontSize = 12.sp,
            color    = T.Ink,
            modifier = Modifier
                .background(T.Paper, RoundedCornerShape(6.dp))
                .border(width = T.ruleThickness, color = T.Rule, shape = RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        SmallFloatingActionButton(onClick = onClick, containerColor = T.Rule.copy(alpha = 0.3f)) {
            Icon(icon, contentDescription = label, tint = T.Ink, modifier = Modifier.size(16.dp))
        }
    }
}

/** "2026-07-13" -> "13 Jul 2026". Falls back to the raw string if parsing fails. */
private fun formatDayHeader(isoDate: String): String {
    return try {
        val date = java.time.LocalDate.parse(isoDate)
        date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    } catch (e: Exception) {
        isoDate
    }
}

/**
 * Copies the picked image into app-local storage, downscaling the long edge
 * to ~2000px and re-encoding as JPEG q80 to target the ~2-3MB cap specified
 * in VitaClaw's /trip/media contract. Returns the local file path, or null
 * on failure — caller must not add a TripPhotoEntity if this returns null.
 */
/**
 * Creates a content:// Uri for the camera to write into, backed by a file
 * under the app's external-files directory — matches the existing
 * FileProvider config (file_paths.xml already declares external-files-path,
 * shared with the offline_responses feature). Camera writes JPEG to this
 * Uri; compressAndSavePhoto then re-reads and re-compresses it into
 * trip_photos, same as the gallery path, so both sources end up identical
 * on disk.
 */
private fun createCaptureUri(context: android.content.Context): android.net.Uri {
    val dir = File(context.getExternalFilesDir(null), "camera_captures").apply { mkdirs() }
    val file = File(dir, "capture_${java.util.UUID.randomUUID()}.jpg")
    return androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
}

private fun compressAndSavePhoto(context: android.content.Context, sourceUri: android.net.Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (original == null) return null

        val maxEdge = 2000
        val scale = maxEdge.toFloat() / maxOf(original.width, original.height)
        val resized = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt(),
                (original.height * scale).toInt(),
                true
            )
        } else original

        val dir = File(context.filesDir, "trip_photos").apply { mkdirs() }
        val outFile = File(dir, "photo_${java.util.UUID.randomUUID()}.jpg")
        FileOutputStream(outFile).use { out ->
            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
        }

        // Also save to the phone's camera roll (Pictures/VitaNest) — best
        // effort, never blocks the app-local save. Agreed in session: gives
        // a backup independent of the app during Norway (phone backup apps
        // like Google Photos pick these up automatically), on top of the
        // daily JSON export.
        try {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, outFile.name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VitaNest")
                }
            }
            val rollUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (rollUri != null) {
                resolver.openOutputStream(rollUri)?.use { out ->
                    resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                }
            }
        } catch (e: Exception) {
            // Best-effort only — camera-roll backup failing must never
            // block the trip photo save itself.
        }

        outFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun FilterChipRowShared(
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
            Text(text = label, fontSize = 12.sp, color = if (isSelected) T.InkInverted else T.Ink)
        }
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

private val CURRENCY_OPTIONS = listOf("NOK", "GBP", "EUR", "USD", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStopDialog(
    existingNote: TripNoteEntity?,
    vehicleType: String, // "electric" | "ice" | "none" — controls whether energy/cost fields show
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
    var currency by remember { mutableStateOf(existingNote?.localCurrency ?: "NOK") }
    var currencyDropdownExpanded by remember { mutableStateOf(false) }
    var customCurrency by remember { mutableStateOf("") }
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
                if (vehicleType != "none") {
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
                        ExposedDropdownMenuBox(
                            expanded = currencyDropdownExpanded,
                            onExpandedChange = { currencyDropdownExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = currency,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Currency") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = currencyDropdownExpanded,
                                onDismissRequest = { currencyDropdownExpanded = false }
                            ) {
                                CURRENCY_OPTIONS.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            currency = option
                                            currencyDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (currency == "Other") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customCurrency,
                            onValueChange = { customCurrency = it },
                            label = { Text("Enter currency code") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
            var hasConfirmed by remember { mutableStateOf(false) }
            TextButton(
                enabled = !hasConfirmed,
                onClick = {
                    hasConfirmed = true
                    val (lat, lon) = gpsCoords ?: (0.0 to 0.0)
                    val parsedTimeMillis = try {
                        java.time.LocalDateTime.parse(chargeTimeText, timeFormatter)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    } catch (e: Exception) {
                        null // invalid text — falls back to "now" at the call site rather than blocking save
                    }
                    val resolvedCurrency = when {
                        vehicleType == "none" -> null
                        currency == "Other"    -> customCurrency.ifBlank { null }
                        else                    -> currency
                    }
                    onConfirm(
                        location.ifBlank { null },
                        lat,
                        lon,
                        if (vehicleType == "none") null else quantity.toDoubleOrNull(),
                        if (vehicleType == "none") null else cost.toDoubleOrNull(),
                        resolvedCurrency,
                        parsedTimeMillis,
                        duration.toIntOrNull(),
                        odometer.toDoubleOrNull(),
                        notes.ifBlank { null }
                    )
                }
            ) { Text(if (isEditing) "Update" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}