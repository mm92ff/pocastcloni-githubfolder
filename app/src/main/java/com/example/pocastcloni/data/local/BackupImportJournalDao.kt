package com.example.pocastcloni.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BackupImportJournalDao {
    @Query("SELECT * FROM backup_import_journal WHERE id = 1")
    suspend fun getPendingImport(): BackupImportJournalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePendingImport(entry: BackupImportJournalEntity)

    @Query("DELETE FROM backup_import_journal")
    suspend fun clearPendingImport()
}
