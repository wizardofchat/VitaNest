package com.vitanest.app.data.local.journal

// © 2026 Sumeet Garg — VitaNest
// Journal feature — Room entities, local-only for this session.
// Mirrors the VitaClaw-side schema agreed for trips / trip_notes / voice_notes.
// No sync logic here — sync is added once the VitaClaw endpoint exists.

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A trip container. vehicle_type lives here (not per-entry) — decided in session:
 * a trip uses one vehicle for its full duration, never mixed mid-trip.
 */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val tripId: String,
    val name: String,
    val vehicleType: String,
    val fuelType: String? = null,
    val startDate: String,
    val endDate: String? = null,
    val status: String,
    val flightOrigin: String? = null,
    val flightDestination: String? = null,
    val carRegistration: String? = null,
    val deleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false
)

/**
 * A single charge/fuel stop within a trip.
 * quantity's unit (kWh vs litres) is inferred from the parent trip's vehicleType —
 * deliberately not duplicated on every row. When vehicleType == "none", quantity/
 * localCost/localCurrency are expected to stay null — the UI should not require them.
 */
@Entity(tableName = "trip_notes")
data class TripNoteEntity(
    @PrimaryKey val entryId: String,
    val tripId: String,
    val locationName: String? = null,
    val latitude: Double,
    val longitude: Double,
    val quantity: Double? = null,
    val localCost: Double? = null,
    val localCurrency: String? = null,
    val costGbp: Double? = null,
    val ratePerUnitLocal: Double? = null,
    val chargeStartTime: Long? = null,
    val durationMinutes: Int? = null,
    val odometerKm: Double? = null,
    val notes: String? = null,
    val voiceNoteId: String? = null,
    val deleted: Boolean = false,
    val source: String = "manual",
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false
)

/**
 * A standalone voice memo. Optionally tagged to a trip via tripId, but never owned by one —
 * recording works with no trip context at all.
 */
@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey val noteId: String,
    val tripId: String? = null,
    val audioPath: String,
    val durationSeconds: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val transcript: String? = null,
    val transcribedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false,       // metadata synced via /trip/sync
    val audioSynced: Boolean = false   // NEW — audio file uploaded via /trip/media.
    // Separate from `synced`: metadata row must
    // exist server-side before audio upload is
    // attempted (contract ordering constraint).
)

/**
 * A free-text note for a specific calendar date within a trip. Independent of
 * stops — a day can have a note with zero stops, or stops with no note.
 * Day grouping on the UI side is derived (group stops/notes by date), not
 * stored — this row's `date` field IS the grouping key for itself.
 *
 * mood is captured once per day (not per-stop) — agreed in session: nobody
 * rates their mood at a charging stop, they rate the day.
 */
@Entity(tableName = "day_notes")
data class DayNoteEntity(
    @PrimaryKey val entryId: String,
    val tripId: String,
    val date: String,
    val text: String,
    val mood: String? = null,          // NEW — emoji string, nullable
    val voiceNoteId: String? = null,
    val deleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false
)

/**
 * A photo attached at trip level (not per-day, not per-stop) — deliberately
 * flat. Agreed in session: photos live at trip level only, matched to stops
 * later (if ever) via GPS/timestamp on the VitaClaw side — no client-side
 * distance calculation.
 */
@Entity(tableName = "trip_photos")
data class TripPhotoEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val uri: String,
    val latitude: Double? = null,      // NEW
    val longitude: Double? = null,     // NEW
    val notes: String? = null,         // NEW — e.g. receipt/landmark caption
    val deleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false
)