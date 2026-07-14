package com.example.pocastcloni.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pocastcloni.util.Constants

@Database(
    entities = [
        PodcastEntity::class,
        EpisodeEntity::class,
        EpisodeFts::class,
        BackupImportJournalEntity::class
    ],
    version = Constants.Database.DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao

    abstract fun backupImportJournalDao(): BackupImportJournalDao

    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        /**
         * Returns the safely published process-wide database instance.
         * The synchronized recheck prevents concurrent first callers from building duplicates.
         */
        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Instance ?: Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    Constants.Database.DATABASE_NAME
                )
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
