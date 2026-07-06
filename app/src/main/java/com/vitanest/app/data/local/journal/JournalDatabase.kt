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
    version = 6,
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
                    // v5 -> v6: added audioSynced to voice_notes (separate from
                    // `synced`, which tracks metadata only — audio upload is a
                    // distinct step). Destructive fallback again — still test
                    // data. NOTE: v4->v5's comment already said that bump was
                    // "the last one" before a real Migration. That line was
                    // wrong. Stop deferring: write the real Migration before
                    // the next schema change, not after.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}