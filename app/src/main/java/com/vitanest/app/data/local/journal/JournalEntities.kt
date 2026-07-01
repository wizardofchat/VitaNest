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
    val vehicleType: String,               // "electric" | "ice"
    val fuelType: String? = null,          // nullable, ICE only e.g. petrol/diesel
    val startDate: String,                 // ISO date "YYYY-MM-DD"
    val endDate: String? = null,           // null while active
    val status: String,                    // "active" | "completed"
    val createdAt: Long,                   // epoch millis
    val updatedAt: Long,
    val synced: Boolean = false            // local-only flag, not sent to server
)

/**
 * A single charge/fuel stop within a trip.
 * quantity's unit (kWh vs litres) is inferred from the parent trip's vehicleType —
 * deliberately not duplicated on every row.
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
    val chargeStartTime: Long? = null,     // epoch millis
    val durationMinutes: Int? = null,      // nullable — mainly meaningful for electric
    val odometerKm: Double? = null,
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