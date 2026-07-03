package com.vitanest.app.data.local.journal

// © 2026 Sumeet Garg — VitaNest
// Journal feature — DAO. Flow-based reads for reactive UI, suspend for writes.

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Query("SELECT * FROM trips WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE status = 'active' AND deleted = 0 LIMIT 1")
    suspend fun getActiveTrip(): TripEntity?

    @Query("SELECT * FROM trips WHERE status = 'active' AND deleted = 0 LIMIT 1")
    fun observeActiveTrip(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE tripId = :tripId")
    suspend fun getTrip(tripId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE tripId = :tripId")
    fun observeTrip(tripId: String): Flow<TripEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trip: TripEntity)

    @Update
    suspend fun update(trip: TripEntity)

    @Query("UPDATE trips SET deleted = 1, updatedAt = :now, synced = 0 WHERE tripId = :tripId")
    suspend fun softDelete(tripId: String, now: Long)

    @Query("SELECT * FROM trips WHERE synced = 0")
    suspend fun getUnsyncedTrips(): List<TripEntity>
}

@Dao
interface TripNoteDao {

    @Query("SELECT * FROM trip_notes WHERE tripId = :tripId AND deleted = 0 ORDER BY chargeStartTime DESC")
    fun observeNotesForTrip(tripId: String): Flow<List<TripNoteEntity>>

    @Query("SELECT * FROM trip_notes WHERE entryId = :entryId")
    suspend fun getNote(entryId: String): TripNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: TripNoteEntity)

    @Update
    suspend fun update(note: TripNoteEntity)

    // Soft delete — never a hard DELETE, so sync can propagate the removal.
    @Query("UPDATE trip_notes SET deleted = 1, updatedAt = :now, synced = 0 WHERE entryId = :entryId")
    suspend fun softDelete(entryId: String, now: Long)

    @Query("SELECT * FROM trip_notes WHERE synced = 0")
    suspend fun getUnsyncedNotes(): List<TripNoteEntity>

    @Query("SELECT COUNT(*) FROM trip_notes WHERE tripId = :tripId AND deleted = 0")
    suspend fun countForTrip(tripId: String): Int

    @Query("SELECT COUNT(*) FROM trip_notes WHERE tripId = :tripId AND deleted = 0")
    fun observeCountForTrip(tripId: String): Flow<Int>
}

@Dao
interface VoiceNoteDao {

    @Query("SELECT * FROM voice_notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun observeForTrip(tripId: String): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes WHERE tripId IS NULL ORDER BY createdAt DESC LIMIT 5")
    fun observeRecentUntagged(): Flow<List<VoiceNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: VoiceNoteEntity)

    @Update
    suspend fun update(note: VoiceNoteEntity)

    @Query("SELECT * FROM voice_notes WHERE synced = 0")
    suspend fun getUnsyncedVoiceNotes(): List<VoiceNoteEntity>
}

@Dao
interface DayNoteDao {

    @Query("SELECT * FROM day_notes WHERE tripId = :tripId AND deleted = 0 ORDER BY date ASC")
    fun observeForTrip(tripId: String): Flow<List<DayNoteEntity>>

    @Query("SELECT * FROM day_notes WHERE entryId = :entryId")
    suspend fun getNote(entryId: String): DayNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: DayNoteEntity)

    @Update
    suspend fun update(note: DayNoteEntity)

    @Query("UPDATE day_notes SET deleted = 1, updatedAt = :now, synced = 0 WHERE entryId = :entryId")
    suspend fun softDelete(entryId: String, now: Long)

    @Query("SELECT * FROM day_notes WHERE synced = 0")
    suspend fun getUnsyncedNotes(): List<DayNoteEntity>
}

@Dao
interface TripPhotoDao {

    @Query("SELECT * FROM trip_photos WHERE tripId = :tripId AND deleted = 0 ORDER BY createdAt DESC")
    fun observeForTrip(tripId: String): Flow<List<TripPhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(photo: TripPhotoEntity)

    @Query("UPDATE trip_photos SET deleted = 1, updatedAt = :now, synced = 0 WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM trip_photos WHERE synced = 0")
    suspend fun getUnsyncedPhotos(): List<TripPhotoEntity>

    @Query("SELECT COUNT(*) FROM trip_photos WHERE tripId = :tripId AND deleted = 0")
    suspend fun countForTrip(tripId: String): Int
}