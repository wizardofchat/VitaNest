package com.vitanest.app.data.local.journal

// © 2026 Sumeet Garg — VitaNest
// Journal feature — local Room database. First local-persistence feature in
// VitaNest; everything else so far has been API-fetched/cached, not stored.
// Kept as its own small database rather than folding into a future app-wide
// one, since nothing else currently needs Room — easy to merge later if
// another feature needs local storage too.

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TripEntity::class, TripNoteEntity::class, VoiceNoteEntity::class,
        DayNoteEntity::class, TripPhotoEntity::class],
    version = 4,
    exportSchema = false
)
abstract class JournalDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun tripNoteDao(): TripNoteDao
    abstract fun voiceNoteDao(): VoiceNoteDao
    abstract fun dayNoteDao(): DayNoteDao
    abstract fun tripPhotoDao(): TripPhotoDao

    companion object {
        @Volatile
        private var INSTANCE: JournalDatabase? = null

        fun getInstance(context: Context): JournalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JournalDatabase::class.java,
                    "vitanest_journal.db"
                )
                    // v2 -> v3: added flightOrigin/flightDestination to trips,
                    // added day_notes + trip_photos tables. Destructive fallback
                    // used deliberately here — explicit call, still test-data-only
                    // ("Testing"/"Testing Norway"), NOT the pattern for any future
                    // schema change once real Norway trip data exists.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}