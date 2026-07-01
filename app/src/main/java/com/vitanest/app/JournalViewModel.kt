package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// JournalViewModel — Voice Notes + Trip Log, fully local (Room), no network
// calls. Sync to VitaClaw is a separate, later action — not wired here.

import android.content.Context
import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitanest.app.data.local.journal.JournalDatabase
import com.vitanest.app.data.local.journal.TripEntity
import com.vitanest.app.data.local.journal.TripNoteEntity
import com.vitanest.app.data.local.journal.VoiceNoteEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class JournalUiState(
    val activeTrip:       TripEntity?          = null,
    val completedTrips:   List<TripEntity>     = emptyList(),
    val recentVoiceNotes: List<VoiceNoteEntity> = emptyList(),
    val activeTripStopCount: Int                = 0,
    val isRecording:      Boolean               = false
)

class JournalViewModel(private val appContext: Context) : ViewModel() {

    private val db          = JournalDatabase.getInstance(appContext)
    private val tripDao      = db.tripDao()
    private val tripNoteDao  = db.tripNoteDao()
    private val voiceNoteDao = db.voiceNoteDao()

    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var currentRecordingPath: String? = null
    private var currentRecordingStartMs: Long = 0L

    private var initialised = false

    fun initialise() {
        if (initialised) return
        initialised = true

        viewModelScope.launch {
            combine(
                tripDao.observeTrips(),
                voiceNoteDao.observeRecentUntagged()
            ) { trips, recentVoice -> trips to recentVoice }
                .collect { (trips, recentVoice) ->
                    val active    = trips.firstOrNull { it.status == "active" }
                    val completed = trips.filter { it.status == "completed" }
                    val stopCount = active?.let { tripNoteDao.countForTrip(it.tripId) } ?: 0

                    _uiState.value = _uiState.value.copy(
                        activeTrip          = active,
                        completedTrips       = completed,
                        recentVoiceNotes      = recentVoice,
                        activeTripStopCount   = stopCount
                    )
                }
        }
    }

    fun observeTrip(tripId: String) = tripDao.observeTrip(tripId)

    fun observeNotesForTrip(tripId: String) = tripNoteDao.observeNotesForTrip(tripId)

    // ── Trips ────────────────────────────────────────────────

    fun startTrip(name: String, vehicleType: String, fuelType: String?, startDate: String) {
        viewModelScope.launch {
            val existing = tripDao.getActiveTrip()
            if (existing != null) return@launch // UI should block this via canStartNewTrip

            val now = System.currentTimeMillis()
            tripDao.upsert(
                TripEntity(
                    tripId      = UUID.randomUUID().toString(),
                    name        = name,
                    vehicleType = vehicleType,
                    fuelType    = fuelType,
                    startDate   = startDate,
                    endDate     = null,
                    status      = "active",
                    createdAt   = now,
                    updatedAt   = now
                )
            )
        }
    }

    fun endTrip(tripId: String, endDate: String) {
        viewModelScope.launch {
            val trip = tripDao.getTrip(tripId) ?: return@launch
            tripDao.update(
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
        durationMinutes: Int?,
        odometerKm: Double?,
        voiceNoteId: String?
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val rate = if (quantity != null && quantity > 0 && localCost != null) {
                localCost / quantity
            } else null

            tripNoteDao.upsert(
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
                    chargeStartTime     = now,
                    durationMinutes     = durationMinutes,
                    odometerKm           = odometerKm,
                    voiceNoteId          = voiceNoteId,
                    source               = if (voiceNoteId != null) "voice_transcribed" else "manual",
                    createdAt            = now,
                    updatedAt            = now
                )
            )
        }
    }

    fun deleteTripStop(entryId: String) {
        viewModelScope.launch {
            tripNoteDao.softDelete(entryId, System.currentTimeMillis())
        }
    }

    // ── Voice notes ──────────────────────────────────────────

    fun startRecording(tripId: String?) {
        if (_uiState.value.isRecording) return

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
            voiceNoteDao.upsert(
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