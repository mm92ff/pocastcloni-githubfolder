package com.example.pocastcloni.data.repository

import androidx.room.withTransaction
import com.example.pocastcloni.data.local.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupImportTransactionRunner @Inject constructor(
    private val database: AppDatabase
) {
    suspend fun run(block: suspend () -> Unit) {
        database.withTransaction { block() }
    }
}
