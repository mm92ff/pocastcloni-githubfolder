package com.example.pocastcloni.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppResetMarkerStore
@Inject
constructor(
    @ApplicationContext context: Context
) {
    private val markerFile = context.noBackupFilesDir.resolve(MARKER_FILE_NAME)

    fun markPending() {
        val parent = markerFile.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Failed to create app reset marker directory")
        }
        markerFile.writeText(MARKER_CONTENT)
    }

    fun isPending(): Boolean = markerFile.isFile

    fun clear() {
        if (markerFile.exists() && !markerFile.delete()) {
            throw IOException("Failed to clear app reset marker")
        }
    }

    companion object {
        internal const val MARKER_FILE_NAME = "app_reset_pending"
        private const val MARKER_CONTENT = "pending\n"
    }
}
