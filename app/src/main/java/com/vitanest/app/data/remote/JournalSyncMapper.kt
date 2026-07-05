package com.vitanest.app.data.remote

// © 2026 Sumeet Garg — VitaNest
// JournalSyncMapper — pure functions, local Room entity -> wire shape.
// No business logic, no derived fields beyond format conversion
// (epoch millis -> ISO8601). Anything requiring computation belongs
// on VitaClaw, not here.

import com.vitanest.app.data.local.journal.DayNoteEntity
import com.vitanest.app.data.local.journal.TripEntity
import com.vitanest.app.data.local.journal.TripNoteEntity
import com.vitanest.app.data.local.journal.VoiceNoteEntity
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object JournalSyncMapper {

    private val iso = DateTimeFormatter.ISO_INSTANT

    private fun epochMillisToIso(millis: Long?): String? =
        millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).format(iso) }

    fun toPayload(t: TripEntity) = SyncTripPayload(
        tripId            = t.tripId,
        name              = t.name,
        vehicleType       = t.vehicleType,
        fuelType          = t.fuelType,
        startDate         = t.startDate,
        endDate           = t.endDate,
        status            = t.status,
        flightOrigin      = t.flightOrigin,
        flightDestination = t.flightDestination,
        deleted           = t.deleted
    )

    fun toPayload(s: TripNoteEntity) = SyncTripNotePayload(
        entryId           = s.entryId,
        tripId            = s.tripId,
        locationName      = s.locationName,
        latitude          = s.latitude,
        longitude         = s.longitude,
        quantity          = s.quantity,
        localCost         = s.localCost,
        localCurrency     = s.localCurrency,
        ratePerUnitLocal  = s.ratePerUnitLocal,
        chargeStartTime   = epochMillisToIso(s.chargeStartTime),
        durationMinutes   = s.durationMinutes,
        odometerKm        = s.odometerKm,
        voiceNoteId       = s.voiceNoteId,
        deleted           = s.deleted,
        source            = s.source,
        notes             = s.notes
    )

    fun toPayload(d: DayNoteEntity) = SyncDayNotePayload(
        entryId     = d.entryId,
        tripId      = d.tripId,
        date        = d.date,
        text        = d.text,
        mood        = d.mood,
        voiceNoteId = d.voiceNoteId,
        deleted     = d.deleted
    )

    fun toPayload(v: VoiceNoteEntity) = SyncVoiceNotePayload(
        noteId          = v.noteId,
        tripId          = v.tripId,
        durationSeconds = v.durationSeconds,
        latitude        = v.latitude,
        longitude       = v.longitude,
        deleted         = false // VoiceNoteEntity has no deleted field today — flagged separately
    )
}