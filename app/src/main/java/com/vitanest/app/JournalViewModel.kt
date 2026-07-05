package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// JournalViewModel — Voice Notes + Trip Log, fully local (Room via
// JournalRepository), no network calls. Sync to VitaClaw is a separate,
// later action — not wired here. All reads/writes route through
// JournalRepository, not DAOs directly — this is the seam where a
// VitaClaw-backed implementation swaps in later without touching this file.

import android.content.Context
import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitanest.app.data.local.journal.DayNoteEntity
import com.vitanest.app.data.local.journal.JournalDatabase
import com.vitanest.app.data.local.journal.JournalRepository
import com.vitanest.app.data.local.journal.LocalJournalRepository
import com.vitanest.app.data.local.journal.TripEntity
import com.vitanest.app.data.local.journal.TripNoteEntity
import com.vitanest.app.data.local.journal.TripPhotoEntity
import com.vitanest.app.data.local.journal.VoiceNoteEntity
import com.vitanest.app.data.remote.JournalSyncManager
import com.vitanest.app.data.remote.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class JournalUiState(
    val activeTrip:       TripEntity?          = null,
    val completedTrips:   List<TripEntity>     = emptyList(),
    val recentVoiceNotes: List<VoiceNoteEntity> = emptyList(),
    val activeTripStopCount: Int                = 0,
    val isRecording:      Boolean               = false,
    val isSyncing:        Boolean               = false,
    val lastSyncResult:   String?               = null,
    // False until the first combine() emission lands. Recording must be
    // blocked while this is false — otherwise activeTrip reads as null
    // even when a trip IS active, and the voice note saves untagged
    // (root cause of the "voice notes missing from TripDetailScreen" bug).
    val isReady:          Boolean               = false
)

class JournalViewModel(
    private val appContext: Context,
    private val repository: JournalRepository =
        LocalJournalRepository(JournalDatabase.getInstance(appContext))
) : ViewModel() {

    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var currentRecordingPath: String? = null
    private var currentRecordingStartMs: Long = 0L

    private var initialised = false

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun initialise() {
        if (initialised) return
        initialised = true

        viewModelScope.launch {
            combine(
                repository.observeTrips(),
                repository.observeRecentUntaggedVoiceNotes()
            ) { trips, recentVoice -> trips to recentVoice }
                .flatMapLatest { (trips, recentVoice) ->
                    val active = trips.firstOrNull { it.status == "active" }
                    val stopCountFlow = active?.let { repository.observeStopCountForTrip(it.tripId) }
                        ?: kotlinx.coroutines.flow.flowOf(0)
                    stopCountFlow.map { count -> Triple(trips, recentVoice, count) }
                }
                .collect { (trips, recentVoice, stopCount) ->
                    val active    = trips.firstOrNull { it.status == "active" }
                    val completed = trips.filter { it.status == "completed" }

                    _uiState.value = _uiState.value.copy(
                        activeTrip          = active,
                        completedTrips       = completed,
                        recentVoiceNotes      = recentVoice,
                        activeTripStopCount   = stopCount,
                        isReady               = true
                    )
                }
        }
    }

    fun observeTrip(tripId: String) = repository.observeTrip(tripId)

    fun observeNotesForTrip(tripId: String) = repository.observeStopsForTrip(tripId)

    fun observeVoiceNotesForTrip(tripId: String) = repository.observeVoiceNotesForTrip(tripId)

    fun observeDayNotesForTrip(tripId: String) = repository.observeDayNotesForTrip(tripId)

    fun observePhotosForTrip(tripId: String) = repository.observePhotosForTrip(tripId)

    // ── Trips ────────────────────────────────────────────────

    /**
     * vehicleType: "electric" | "ice" | "none". fuelType only meaningful for "ice".
     * flightOrigin/flightDestination are both nullable — not every trip flies.
     */
    fun startTrip(
        name: String,
        vehicleType: String,
        fuelType: String?,
        startDate: String,
        flightOrigin: String? = null,
        flightDestination: String? = null,
        carRegistration: String? = null
    ) {
        viewModelScope.launch {
            val existing = repository.getActiveTrip()
            if (existing != null) return@launch // UI should block this via canStartNewTrip

            val now = System.currentTimeMillis()
            repository.upsertTrip(
                TripEntity(
                    tripId             = UUID.randomUUID().toString(),
                    name               = name,
                    vehicleType        = vehicleType,
                    fuelType           = fuelType,
                    startDate          = startDate,
                    endDate            = null,
                    status             = "active",
                    flightOrigin       = flightOrigin,
                    flightDestination  = flightDestination,
                    carRegistration    = carRegistration,
                    createdAt          = now,
                    updatedAt          = now
                )
            )
        }
    }

    /** Edit trip header fields post-creation. Status/dates untouched here — use endTrip() for status. */
    fun updateTripDetails(
        tripId: String,
        name: String,
        vehicleType: String,
        fuelType: String?,
        flightOrigin: String?,
        flightDestination: String?,
        carRegistration: String?
    ) {
        viewModelScope.launch {
            val existing = repository.getTrip(tripId) ?: return@launch
            repository.updateTrip(
                existing.copy(
                    name               = name,
                    vehicleType        = vehicleType,
                    fuelType           = fuelType,
                    flightOrigin       = flightOrigin,
                    flightDestination  = flightDestination,
                    carRegistration    = carRegistration,
                    updatedAt          = System.currentTimeMillis(),
                    synced             = false
                )
            )
        }
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch { repository.softDeleteTrip(tripId) }
    }

    fun endTrip(tripId: String, endDate: String) {
        viewModelScope.launch {
            val trip = repository.getTrip(tripId) ?: return@launch
            repository.updateTrip(
                trip.copy(
                    status    = "completed",
                    endDate   = endDate,
                    updatedAt = System.currentTimeMillis(),
                    synced    = false
                )
            )
        }
    }

    fun canStartNewTrip(): Boolean = _uiState.value.activeTrip == null

    // ── Trip stops ───────────────────────────────────────────
    // Note: when trip.vehicleType == "none", quantity/localCost/localCurrency
    // are expected to arrive null from the UI — this layer doesn't enforce it,
    // the Add Stop form is responsible for hiding those fields in that case.

    fun addTripStop(
        tripId: String,
        latitude: Double,
        longitude: Double,
        locationName: String?,
        quantity: Double?,
        localCost: Double?,
        localCurrency: String?,
        chargeStartTime: Long?,
        durationMinutes: Int?,
        odometerKm: Double?,
        notes: String?,
        voiceNoteId: String?
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val rate = if (quantity != null && quantity > 0 && localCost != null) {
                localCost / quantity
            } else null

            repository.upsertStop(
                TripNoteEntity(
                    entryId            = UUID.randomUUID().toString(),
                    tripId             = tripId,
                    locationName        = locationName,
                    latitude            = latitude,
                    longitude           = longitude,
                    quantity            = quantity,
                    localCost           = localCost,
                    localCurrency       = localCurrency,
                    costGbp             = null, // resolved by VitaClaw at sync
                    ratePerUnitLocal    = rate,
                    chargeStartTime     = chargeStartTime ?: now, // falls back to now only if the entered time text failed to parse
                    durationMinutes     = durationMinutes,
                    odometerKm           = odometerKm,
                    notes                = notes,
                    voiceNoteId          = voiceNoteId,
                    source               = if (voiceNoteId != null) "voice_transcribed" else "manual",
                    createdAt            = now,
                    updatedAt            = now
                )
            )
        }
    }

    fun deleteTripStop(entryId: String) {
        viewModelScope.launch { repository.softDeleteStop(entryId) }
    }

    fun updateTripStop(
        entryId: String,
        latitude: Double,
        longitude: Double,
        locationName: String?,
        quantity: Double?,
        localCost: Double?,
        localCurrency: String?,
        chargeStartTime: Long?,
        durationMinutes: Int?,
        odometerKm: Double?,
        notes: String?
    ) {
        viewModelScope.launch {
            val existing = repository.getStop(entryId) ?: return@launch
            val rate = if (quantity != null && quantity > 0 && localCost != null) {
                localCost / quantity
            } else null

            repository.updateStop(
                existing.copy(
                    latitude          = latitude,
                    longitude         = longitude,
                    locationName       = locationName,
                    quantity           = quantity,
                    localCost          = localCost,
                    localCurrency      = localCurrency,
                    ratePerUnitLocal   = rate,
                    // Dynamic day grouping: editing chargeStartTime to a new
                    // date moves this stop to that day's card on next
                    // recompose — grouping is derived from this field, not
                    // separately stored, so no extra logic is needed here.
                    chargeStartTime    = chargeStartTime ?: existing.chargeStartTime,
                    durationMinutes    = durationMinutes,
                    odometerKm         = odometerKm,
                    notes               = notes,
                    updatedAt          = System.currentTimeMillis(),
                    synced              = false
                )
            )
        }
    }

    // ── Day notes ────────────────────────────────────────────
    // mood: nullable emoji string — captured once per day, not per-stop
    // (agreed in session: nobody rates mood at a charging stop).

    fun addDayNote(tripId: String, date: String, text: String, mood: String?, voiceNoteId: String?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.upsertDayNote(
                DayNoteEntity(
                    entryId     = UUID.randomUUID().toString(),
                    tripId      = tripId,
                    date        = date,
                    text        = text,
                    mood        = mood,
                    voiceNoteId = voiceNoteId,
                    createdAt   = now,
                    updatedAt   = now
                )
            )
        }
    }

    fun updateDayNote(entryId: String, text: String, mood: String?, voiceNoteId: String?) {
        viewModelScope.launch {
            val existing = repository.getDayNote(entryId) ?: return@launch
            repository.updateDayNote(
                existing.copy(
                    text        = text,
                    mood        = mood,
                    voiceNoteId = voiceNoteId,
                    updatedAt   = System.currentTimeMillis(),
                    synced      = false
                )
            )
        }
    }

    fun deleteDayNote(entryId: String) {
        viewModelScope.launch { repository.softDeleteDayNote(entryId) }
    }

    // ── Trip photos ──────────────────────────────────────────
    // latitude/longitude: best-effort GPS captured at add-time (same
    // fetchCurrentLocation pattern as stops) — null if permission denied
    // or fetch fails, never blocks the save.
    // notes: optional caption prompted immediately after capture.

    fun addTripPhoto(tripId: String, uri: String, latitude: Double?, longitude: Double?, notes: String?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.upsertPhoto(
                TripPhotoEntity(
                    id        = UUID.randomUUID().toString(),
                    tripId    = tripId,
                    uri       = uri,
                    latitude  = latitude,
                    longitude = longitude,
                    notes     = notes,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun deleteTripPhoto(id: String) {
        viewModelScope.launch { repository.softDeletePhoto(id) }
    }

    // ── Sync ─────────────────────────────────────────────────
    // Manual trigger only for this pass — debounced auto-sync on
    // local writes / network-restore is separate, not built yet.

    private val syncManager = JournalSyncManager(repository)

    fun syncNow(tripId: String) {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, lastSyncResult = null)
            val result = syncManager.syncNow(tripId)
            val message = when (result) {
                is SyncResult.Success -> "Synced ${result.metadataSynced} records, ${result.mediaSynced} photos"
                is SyncResult.Failure -> "Sync failed: ${result.reason}"
            }
            _uiState.value = _uiState.value.copy(isSyncing = false, lastSyncResult = message)
        }
    }

    // ── Voice notes ──────────────────────────────────────────

    fun startRecording(tripId: String?) {
        if (_uiState.value.isRecording) return
        // Guard against the untagged-voice-note bug: if uiState hasn't
        // emitted yet, activeTrip reads as null even when a trip IS
        // active. Block recording rather than risk a silent mistag.
        if (!_uiState.value.isReady) return

        val dir = File(appContext.filesDir, "voice_notes").apply { mkdirs() }
        val fileName = "note_${UUID.randomUUID()}.m4a"
        val outputFile = File(dir, fileName)

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }

        currentRecordingPath    = outputFile.absolutePath
        currentRecordingStartMs = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(isRecording = true)

        // tripId is consumed in stopRecording via closure capture below
        pendingTripId = tripId
    }

    private var pendingTripId: String? = null

    fun stopRecording(latitude: Double?, longitude: Double?) {
        val path = currentRecordingPath ?: return
        try {
            recorder?.stop()
        } catch (e: Exception) {
            // stop() throws if called too soon after start() with no data —
            // discard the file rather than saving a corrupt/empty note.
            File(path).delete()
            resetRecordingState()
            return
        }
        recorder?.release()
        recorder = null

        val durationSeconds = ((System.currentTimeMillis() - currentRecordingStartMs) / 1000).toInt()
        val tripId = pendingTripId

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.upsertVoiceNote(
                VoiceNoteEntity(
                    noteId          = UUID.randomUUID().toString(),
                    tripId           = tripId,
                    audioPath        = path,
                    durationSeconds  = durationSeconds,
                    latitude         = latitude,
                    longitude        = longitude,
                    createdAt        = now,
                    updatedAt        = now
                )
            )
        }

        resetRecordingState()
    }

    fun cancelRecording() {
        try { recorder?.stop() } catch (_: Exception) { /* no-op — discarding anyway */ }
        recorder?.release()
        recorder = null
        currentRecordingPath?.let { File(it).delete() }
        resetRecordingState()
    }

    private fun resetRecordingState() {
        currentRecordingPath    = null
        currentRecordingStartMs = 0L
        pendingTripId            = null
        _uiState.value = _uiState.value.copy(isRecording = false)
    }

    override fun onCleared() {
        super.onCleared()
        // Safety net — don't leak a MediaRecorder if the ViewModel is torn
        // down mid-recording (e.g. process death).
        try { recorder?.release() } catch (_: Exception) { }
    }
}