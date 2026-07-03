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
    @PrimaryKey val tripId: String,       // client-generated UUID, sync key
    val name: String,
    val vehicleType: String,               // "electric" | "ice" | "none"
    val fuelType: String? = null,          // nullable, ICE only e.g. petrol/diesel
    val startDate: String,                 // ISO date "YYYY-MM-DD"
    val endDate: String? = null,           // null while active
    val status: String,                    // "active" | "completed"
    val flightOrigin: String? = null,      // nullable, e.g. "BFS"
    val flightDestination: String? = null, // nullable, e.g. "OSL"
    val createdAt: Long,                   // epoch millis
    val updatedAt: Long,
    val synced: Boolean = false            // local-only flag, not sent to server
)

/**
 * A single charge/fuel stop within a trip.
 * quantity's unit (kWh vs litres) is inferred from the parent trip's vehicleType —
 * deliberately not duplicated on every row. When vehicleType == "none", quantity/
 * localCost/localCurrency are expected to stay null — the UI should not require them.
 */
@Entity(tableName = "trip_notes")
data class TripNoteEntity(
    @PrimaryKey val entryId: String,       // client-generated UUID, sync key
    val tripId: String,                    // FK -> TripEntity.tripId
    val locationName: String? = null,      // reverse-geocoded, nullable — best effort only
    val latitude: Double,
    val longitude: Double,
    val quantity: Double? = null,          // kWh or litres, per trip.vehicleType
    val localCost: Double? = null,
    val localCurrency: String? = null,     // e.g. "NOK" — entered as-is, no FX lookup on device
    val costGbp: Double? = null,           // left null; VitaClaw backfills via existing FX infra
    val ratePerUnitLocal: Double? = null,  // computed client-side: localCost / quantity
    val chargeStartTime: Long? = null,     // epoch millis — day grouping key, dynamic on edit
    val durationMinutes: Int? = null,      // nullable — mainly meaningful for electric
    val odometerKm: Double? = null,
    val notes: String? = null,             // free-text, e.g. handy day notes when voice note isn't practical
    val voiceNoteId: String? = null,       // soft reference -> VoiceNoteEntity.noteId, no FK constraint
    val deleted: Boolean = false,          // soft delete — carries intent through to sync
    val source: String = "manual",         // "manual" | "voice_transcribed"
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
    @PrimaryKey val noteId: String,        // client-generated UUID, sync key
    val tripId: String? = null,            // nullable — standalone by default
    val audioPath: String,                 // local file path, required at insert
    val durationSeconds: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val transcript: String? = null,        // always null on-device — VitaClaw's Whisper agent fills this
    val transcribedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false
)

/**
 * A free-text note for a specific calendar date within a trip. Independent of
 * stops — a day can have a note with zero stops, or stops with no note.
 * Day grouping on the UI side is derived (group stops/notes by date), not
 * stored — this row's `date` field IS the grouping key for itself.
 */
@Entity(tableName = "day_notes")
data class DayNoteEntity(
    @PrimaryKey val entryId: String,       // client-generated UUID, sync key
    val tripId: String,                    // FK -> TripEntity.tripId
    val date: String,                      // ISO date "YYYY-MM-DD"
    val text: String,
    val voiceNoteId: String? = null,       // soft reference -> VoiceNoteEntity.noteId
    val deleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false
)

/**
 * A photo attached at trip level (not per-day, not per-stop) — deliberately
 * flat. Gallery-picker only for now; camera capture deferred post-trip.
 */
@Entity(tableName = "trip_photos")
data class TripPhotoEntity(
    @PrimaryKey val id: String,            // client-generated UUID, sync key
    val tripId: String,                    // FK -> TripEntity.tripId
    val uri: String,                       // local content:// or file:// URI
    val deleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false
)