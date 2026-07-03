package com.vitanest.app.data.local.journal

// © 2026 Sumeet Garg — VitaNest
// JournalExporter — one-way local backup. Dumps all trip data (trips,
// stops, day notes, voice note metadata + paths, photo paths) to a
// timestamped JSON file in the public Downloads folder, shareable via
// WhatsApp/etc through the standard Android share intent. This is NOT a
// restore/import feature — deliberately export-only (see architecture
// decision: VitaClaw becomes the eventual source of truth via /trip/sync;
// this file is insurance, not a self-service restore path).
// Never overwrites a previous export — every export gets its own
// timestamped filename, since this is the only backup mechanism that
// exists before /trip/sync is wired.

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.format.DateTimeFormatter

object JournalExporter {

    /**
     * Entry point — caller (ViewModel/Composable) already has the current
     * Flow-collected lists in hand, so pass them directly rather than
     * re-querying. Avoids adding suspend "getAll" methods to every DAO
     * just for a one-off export.
     */
    suspend fun exportTripWithData(
        context: Context,
        trip: TripEntity,
        stops: List<TripNoteEntity>,
        dayNotes: List<DayNoteEntity>,
        voiceNotes: List<VoiceNoteEntity>,
        photos: List<TripPhotoEntity>
    ): Uri? {
        val json = JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("trip", tripToJson(trip))
            put("stops", JSONArray(stops.map { stopToJson(it) }))
            put("dayNotes", JSONArray(dayNotes.map { dayNoteToJson(it) }))
            put("voiceNotes", JSONArray(voiceNotes.map { voiceNoteToJson(it) }))
            put("photos", JSONArray(photos.map { photoToJson(it) }))
        }
        val fileName = buildFileName(trip.name)
        return writeJsonToDownloads(context, fileName, json)
    }

    fun shareExport(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share trip export"))
    }

    private fun buildFileName(tripName: String): String {
        val safeName = tripName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "trip" }
        val timestamp = java.time.LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        return "vitanest_trip_${safeName}_$timestamp.json"
    }

    private fun writeJsonToDownloads(context: Context, fileName: String, json: JSONObject): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray()) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(downloadsDir, fileName)
                file.writeText(json.toString(2))
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun tripToJson(t: TripEntity) = JSONObject().apply {
        put("tripId", t.tripId)
        put("name", t.name)
        put("vehicleType", t.vehicleType)
        put("fuelType", t.fuelType)
        put("startDate", t.startDate)
        put("endDate", t.endDate)
        put("status", t.status)
        put("flightOrigin", t.flightOrigin)
        put("flightDestination", t.flightDestination)
    }

    private fun stopToJson(s: TripNoteEntity) = JSONObject().apply {
        put("entryId", s.entryId)
        put("locationName", s.locationName)
        put("latitude", s.latitude)
        put("longitude", s.longitude)
        put("quantity", s.quantity)
        put("localCost", s.localCost)
        put("localCurrency", s.localCurrency)
        put("chargeStartTime", s.chargeStartTime)
        put("durationMinutes", s.durationMinutes)
        put("odometerKm", s.odometerKm)
        put("notes", s.notes)
        put("voiceNoteId", s.voiceNoteId)
    }

    private fun dayNoteToJson(d: DayNoteEntity) = JSONObject().apply {
        put("entryId", d.entryId)
        put("date", d.date)
        put("text", d.text)
        put("voiceNoteId", d.voiceNoteId)
    }

    private fun voiceNoteToJson(v: VoiceNoteEntity) = JSONObject().apply {
        put("noteId", v.noteId)
        put("audioPath", v.audioPath)
        put("durationSeconds", v.durationSeconds)
        put("latitude", v.latitude)
        put("longitude", v.longitude)
    }

    private fun photoToJson(p: TripPhotoEntity) = JSONObject().apply {
        put("id", p.id)
        put("uri", p.uri)
        put("createdAt", p.createdAt)
    }
}