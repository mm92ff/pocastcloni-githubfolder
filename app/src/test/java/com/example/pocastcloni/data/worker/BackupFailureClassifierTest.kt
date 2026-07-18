package com.example.pocastcloni.data.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.example.pocastcloni.domain.backup.BackupFailureReason
import com.example.pocastcloni.util.SizeLimitExceededException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class BackupFailureClassifierTest {
    @Test
    fun `missing or unknown action is an invalid request`() {
        assertEquals(
            BackupFailureReason.INVALID_REQUEST,
            classifyBackupFailure(null, IllegalStateException("missing action"))
        )
        assertEquals(
            BackupFailureReason.INVALID_REQUEST,
            classifyBackupFailure("unsupported", IOException("read failed"))
        )
    }

    @Test
    fun `invalid import content is classified without exposing exception text`() {
        val malformedJsonError =
            requireNotNull(runCatching { ObjectMapper().readTree("{") }.exceptionOrNull())

        assertEquals(
            BackupFailureReason.INVALID_BACKUP,
            classifyBackupFailure(BackupWorker.ACTION_IMPORT, IllegalArgumentException("invalid backup"))
        )
        assertEquals(
            BackupFailureReason.INVALID_BACKUP,
            classifyBackupFailure(BackupWorker.ACTION_IMPORT, SizeLimitExceededException(10L))
        )
        assertEquals(
            BackupFailureReason.INVALID_BACKUP,
            classifyBackupFailure(BackupWorker.ACTION_IMPORT, malformedJsonError)
        )
    }

    @Test
    fun `file access failures are distinct from unknown failures`() {
        assertEquals(
            BackupFailureReason.FILE_ACCESS,
            classifyBackupFailure(BackupWorker.ACTION_EXPORT, IOException("write failed"))
        )
        assertEquals(
            BackupFailureReason.FILE_ACCESS,
            classifyBackupFailure(BackupWorker.ACTION_IMPORT, SecurityException("permission denied"))
        )
        assertEquals(
            BackupFailureReason.UNKNOWN,
            classifyBackupFailure(BackupWorker.ACTION_EXPORT, IllegalArgumentException("unexpected"))
        )
    }
}
