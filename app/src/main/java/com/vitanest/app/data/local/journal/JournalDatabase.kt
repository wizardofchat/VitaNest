package com.vitanest.app.data.local.journal

// © 2026 Sumeet Garg — VitaNest
// Journal feature — local Room database.

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TripEntity::class, TripNoteEntity::class, VoiceNoteEntity::class,
        DayNoteEntity::class, TripPhotoEntity::class],
    version = 5,
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
                    // v4 -> v5: added latitude/longitude/notes to trip_photos,
                    // added mood to day_notes. Destructive fallback used again
                    // by explicit decision — still test data, no Norway trip
                    // started. This is the last schema change allowed on
                    // destructive migration: real Migration is owed before
                    // any live Norway trip data exists in these tables.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}