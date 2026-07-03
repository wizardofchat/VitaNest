package com.vitanest.app.data.local.journal

// © 2026 Sumeet Garg — VitaNest
// JournalRepository — sits between JournalViewModel and data sources.
// Today: Room only. Post-Norway: this is the seam where a VitaClaw-backed
// (or local+remote) implementation swaps in without touching the ViewModel
// or any Composable. Do not let ViewModel call DAOs directly going forward —
// route all reads/writes through here.

import kotlinx.coroutines.flow.Flow

interface JournalRepository {

    // Trips
    fun observeTrips(): Flow<List<TripEntity>>
    fun observeTrip(tripId: String): Flow<TripEntity?>
    suspend fun getActiveTrip(): TripEntity?
    suspend fun getTrip(tripId: String): TripEntity?
    suspend fun upsertTrip(trip: TripEntity)
    suspend fun updateTrip(trip: TripEntity)
    suspend fun softDeleteTrip(tripId: String)

    // Stops
    fun observeStopsForTrip(tripId: String): Flow<List<TripNoteEntity>>
    suspend fun getStop(entryId: String): TripNoteEntity?
    suspend fun upsertStop(note: TripNoteEntity)
    suspend fun updateStop(note: TripNoteEntity)
    suspend fun softDeleteStop(entryId: String)
    suspend fun countStopsForTrip(tripId: String): Int
    fun observeStopCountForTrip(tripId: String): Flow<Int>

    // Voice notes
    fun observeAllVoiceNotes(): Flow<List<VoiceNoteEntity>>
    fun observeVoiceNotesForTrip(tripId: String): Flow<List<VoiceNoteEntity>>
    fun observeRecentUntaggedVoiceNotes(): Flow<List<VoiceNoteEntity>>
    suspend fun upsertVoiceNote(note: VoiceNoteEntity)

    // Day notes
    fun observeDayNotesForTrip(tripId: String): Flow<List<DayNoteEntity>>
    suspend fun getDayNote(entryId: String): DayNoteEntity?
    suspend fun upsertDayNote(note: DayNoteEntity)
    suspend fun updateDayNote(note: DayNoteEntity)
    suspend fun softDeleteDayNote(entryId: String)

    // Trip photos
    fun observePhotosForTrip(tripId: String): Flow<List<TripPhotoEntity>>
    suspend fun upsertPhoto(photo: TripPhotoEntity)
    suspend fun softDeletePhoto(id: String)
    suspend fun countPhotosForTrip(tripId: String): Int
}

/**
 * Room-backed implementation — the only implementation today. Sync-aware
 * remote implementation is future work, deferred until /trip/sync wiring
 * is actually built (see VITANEST_ARCHITECTURE.md open items).
 */
class LocalJournalRepository(private val db: JournalDatabase) : JournalRepository {

    private val tripDao      = db.tripDao()
    private val tripNoteDao  = db.tripNoteDao()
    private val voiceNoteDao = db.voiceNoteDao()
    private val dayNoteDao   = db.dayNoteDao()
    private val tripPhotoDao = db.tripPhotoDao()

    override fun observeTrips() = tripDao.observeTrips()
    override fun observeTrip(tripId: String) = tripDao.observeTrip(tripId)
    override suspend fun getActiveTrip() = tripDao.getActiveTrip()
    override suspend fun getTrip(tripId: String) = tripDao.getTrip(tripId)
    override suspend fun upsertTrip(trip: TripEntity) = tripDao.upsert(trip)
    override suspend fun updateTrip(trip: TripEntity) = tripDao.update(trip)
    override suspend fun softDeleteTrip(tripId: String) =
        tripDao.softDelete(tripId, System.currentTimeMillis())

    override fun observeStopsForTrip(tripId: String) = tripNoteDao.observeNotesForTrip(tripId)
    override suspend fun getStop(entryId: String) = tripNoteDao.getNote(entryId)
    override suspend fun upsertStop(note: TripNoteEntity) = tripNoteDao.upsert(note)
    override suspend fun updateStop(note: TripNoteEntity) = tripNoteDao.update(note)
    override suspend fun softDeleteStop(entryId: String) =
        tripNoteDao.softDelete(entryId, System.currentTimeMillis())
    override suspend fun countStopsForTrip(tripId: String) = tripNoteDao.countForTrip(tripId)
    override fun observeStopCountForTrip(tripId: String) = tripNoteDao.observeCountForTrip(tripId)

    override fun observeAllVoiceNotes() = voiceNoteDao.observeAll()
    override fun observeVoiceNotesForTrip(tripId: String) = voiceNoteDao.observeForTrip(tripId)
    override fun observeRecentUntaggedVoiceNotes() = voiceNoteDao.observeRecentUntagged()
    override suspend fun upsertVoiceNote(note: VoiceNoteEntity) = voiceNoteDao.upsert(note)

    override fun observeDayNotesForTrip(tripId: String) = dayNoteDao.observeForTrip(tripId)
    override suspend fun getDayNote(entryId: String) = dayNoteDao.getNote(entryId)
    override suspend fun upsertDayNote(note: DayNoteEntity) = dayNoteDao.upsert(note)
    override suspend fun updateDayNote(note: DayNoteEntity) = dayNoteDao.update(note)
    override suspend fun softDeleteDayNote(entryId: String) =
        dayNoteDao.softDelete(entryId, System.currentTimeMillis())

    override fun observePhotosForTrip(tripId: String) = tripPhotoDao.observeForTrip(tripId)
    override suspend fun upsertPhoto(photo: TripPhotoEntity) = tripPhotoDao.upsert(photo)
    override suspend fun softDeletePhoto(id: String) =
        tripPhotoDao.softDelete(id, System.currentTimeMillis())
    override suspend fun countPhotosForTrip(tripId: String) = tripPhotoDao.countForTrip(tripId)
}