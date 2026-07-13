package com.example.pocastcloni.data.worker

import com.example.pocastcloni.util.Constants
import java.io.File

internal data class DownloadStagingFiles(
    val partFile: File,
    val metadataFile: File
) {
    fun delete() {
        partFile.delete()
        metadataFile.delete()
    }
}

internal fun downloadStagingFiles(
    filesDir: File,
    episodeId: Long
): DownloadStagingFiles {
    require(episodeId > 0L) { "episodeId must be positive" }
    val stagingDirectory = File(filesDir, "${Constants.DOWNLOADS_DIR}/.staging")
    return DownloadStagingFiles(
        partFile = File(stagingDirectory, "$episodeId.part"),
        metadataFile = File(stagingDirectory, "$episodeId.meta")
    )
}
