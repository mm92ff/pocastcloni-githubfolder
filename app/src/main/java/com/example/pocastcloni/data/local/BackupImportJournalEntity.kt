package com.example.pocastcloni.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_import_journal")
data class BackupImportJournalEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val previousSettingsJson: String
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
