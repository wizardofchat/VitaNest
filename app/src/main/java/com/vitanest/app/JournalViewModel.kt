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
            if (existing != null) return@launch

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
                    entryId          = UUID.randomUUID().toString(),
                    tripId           = tripId,
                    locationName      = locationName,
                    latitude         = latitude,
                    longitude        = longitude,
                    quantity          = quantity,
                    localCost        = localCost,
                    localCurrency     = localCurrency,
                    ratePerUnitLocal = rate,
                    chargeStartTime   = chargeStartTime,
                    durationMinutes  = durationMinutes,
                    odometerKm       = odometerKm,
                    notes             = notes,
                    voiceNoteId       = voiceNoteId,
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

    /** mood: nullable emoji string, e.g. "😊" — captured once per day, not per-stop. */
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
    // or fetch fails/times out, never blocks the save.
    // notes: optional caption prompted immediately after capture (receipt,
    // landmark, restaurant name) — skippable. Edit-later not implemented;
    // DAO has no single-photo getter yet — add one if that's wanted.

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

    // ── Voice notes ──────────────────────────────────────────

    fun startRecording(tripId: String?) {
        if (_uiState.value.isRecording) return
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

        pendingTripId = tripId
    }

    private var pendingTripId: String? = null

    fun stopRecording(latitude: Double?, longitude: Double?) {
        val path = currentRecordingPath ?: return
        try {
            recorder?.stop()
        } catch (e: Exception) {
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
        try { recorder?.stop() } catch (_: Exception) { }
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
        try { recorder?.release() } catch (_: Exception) { }
    }
}