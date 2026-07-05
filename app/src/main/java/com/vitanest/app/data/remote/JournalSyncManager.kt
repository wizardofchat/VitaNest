package com.vitanest.app.data.remote

// © 2026 Sumeet Garg — VitaNest
// JournalSyncManager — coordinates pushing unsynced local Journal data to
// VitaClaw. NOT a JournalRepository implementation — local Room stays the
// source of truth for reads/writes; this is a one-way push on top of it.
// Two phases, sequential: metadata first (trip/sync), then media (trip/media)
// — voice_note media upload requires the metadata row to exist server-side
// first (contract ordering constraint), so media must never run before
// metadata for the same batch.
//
// Trigger policy (agreed 2026-07-05): auto-fire after local writes +
// network-restore, debounced; manual "Sync now" button as fallback for
// when VitaClaw is unreachable and auto-sync silently stalled. Both call
// syncNow() — no separate code paths.

import com.vitanest.app.data.local.journal.JournalRepository
import com.vitanest.app.data.local.journal.TripPhotoEntity
import com.vitanest.app.data.local.journal.VoiceNoteEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

sealed class SyncResult {
    data class Success(
        val metadataSynced: Int,
        val mediaSynced: Int
    ) : SyncResult()

    data class Failure(val reason: String) : SyncResult()
}

class JournalSyncManager(
    private val repository: JournalRepository,
    private val api: VitaClawApiService = RetrofitClient.apiService
) {
    // Prevents overlapping sync runs — auto-trigger and manual button can
    // both fire near-simultaneously; only one network round-trip at a time.
    private val syncMutex = Mutex()

    suspend fun syncNow(tripId: String): SyncResult = syncMutex.withLock {
        try {
            val metadataCount = syncMetadata(tripId)
            val mediaCount = syncMedia(tripId)
            SyncResult.Success(metadataCount, mediaCount)
        } catch (e: Exception) {
            SyncResult.Failure(e.message ?: "Unknown sync error")
        }
    }

    // ── Metadata ─────────────────────────────────────────────
    // Per-record status, not all-or-nothing — a bad row must not block
    // the rest of the batch from being marked synced. Scoped to a single
    // trip — sync must never push every unsynced row across every trip
    // ever created locally (agreed 2026-07-05, fixed after global-sync
    // bug surfaced 15 records / 8 photos on a 2-stop test trip).

    private suspend fun syncMetadata(tripId: String): Int {
        val trips     = repository.getUnsyncedTrip(tripId)
        val stops     = repository.getUnsyncedStopsForTrip(tripId)
        val dayNotes  = repository.getUnsyncedDayNotesForTrip(tripId)
        val voiceNotes = repository.getUnsyncedVoiceNotesForTrip(tripId)

        if (trips.isEmpty() && stops.isEmpty() && dayNotes.isEmpty() && voiceNotes.isEmpty()) {
            return 0
        }

        val request = TripSyncRequest(
            trips       = trips.map { JournalSyncMapper.toPayload(it) },
            tripNotes   = stops.map { JournalSyncMapper.toPayload(it) },
            dayNotes    = dayNotes.map { JournalSyncMapper.toPayload(it) },
            voiceNotes  = voiceNotes.map { JournalSyncMapper.toPayload(it) }
        )

        val response = api.syncTripJournal(request)
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            val errorDetail = response.errorBody()?.string() ?: "no error body"
            throw Exception("trip/sync HTTP ${response.code()}: $errorDetail")
        }

        var syncedCount = 0

        body.trips.forEach { status ->
            if (status.status == "ok") {
                repository.markTripSynced(status.id)
                syncedCount++
            }
            // status == "error" — row stays unsynced, retried next syncNow()
            // call. Not surfaced to the user per-row; aggregate failure is
            // visible via SyncResult if the whole batch fails, but partial
            // per-record errors are silent-retry by design (matches the
            // "one bad row does not fail the batch" contract behavior).
        }
        body.tripNotes.forEach { status ->
            if (status.status == "ok") {
                repository.markStopSynced(status.id)
                syncedCount++
            }
        }
        body.dayNotes.forEach { status ->
            if (status.status == "ok") {
                repository.markDayNoteSynced(status.id)
                syncedCount++
            }
        }
        body.voiceNotes.forEach { status ->
            if (status.status == "ok") {
                repository.markVoiceNoteSynced(status.id)
                syncedCount++
            }
        }

        return syncedCount
    }

    // ── Media ────────────────────────────────────────────────
    // One file per request, per contract. Voice notes only upload if their
    // metadata already synced (ordering constraint) — photos have no such
    // constraint (kind=trip_photo writes file + row in one call).

    private suspend fun syncMedia(tripId: String): Int {
        var syncedCount = 0

        val photos = repository.getUnsyncedPhotosForTrip(tripId)
        for (photo in photos) {
            if (uploadPhoto(photo)) {
                repository.markPhotoSynced(photo.id)
                syncedCount++
            }
        }

        // Voice notes: only attempt upload for notes whose metadata sync
        // already succeeded (synced=true by this point in the run, or a
        // prior run). getUnsyncedVoiceNotes() here would return notes whose
        // audio hasn't uploaded — but that field doesn't distinguish
        // "metadata synced, audio not uploaded" from "nothing synced yet".
        // Flagging: VoiceNoteEntity needs a separate audioSynced flag to
        // do this correctly. Not built — see note below.

        return syncedCount
    }

    private suspend fun uploadPhoto(photo: TripPhotoEntity): Boolean {
        val file = File(photo.uri)
        if (!file.exists()) return false

        val filePart = MultipartBody.Part.createFormData(
            "file", file.name, file.asRequestBody("image/jpeg".toMediaType())
        )

        val response = api.uploadTripMedia(
            kind      = "trip_photo".toPlainRequestBody(),
            id        = photo.id.toPlainRequestBody(),
            tripId    = photo.tripId.toPlainRequestBody(),
            latitude  = photo.latitude?.toString()?.toPlainRequestBody(),
            longitude = photo.longitude?.toString()?.toPlainRequestBody(),
            notes     = photo.notes?.toPlainRequestBody(),
            file      = filePart
        )

        val body = response.body()
        return response.isSuccessful && body?.status == "ok"
    }

    private fun String.toPlainRequestBody(): RequestBody =
        this.toRequestBody("text/plain".toMediaType())
}