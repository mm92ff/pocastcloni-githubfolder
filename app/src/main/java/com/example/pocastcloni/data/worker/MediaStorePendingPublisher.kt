package com.example.pocastcloni.data.worker

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import timber.log.Timber

/**
 * Publishes a pending download and verifies the durable flag.
 *
 * Android 11+ requires pending rows to be included explicitly for updates. Android 10 uses the
 * legacy include-pending URI. Synthetic `external` paths also retry through their writable
 * primary-volume alias while the database retains its original path.
 */
internal object MediaStorePendingPublisher {
    @RequiresApi(Build.VERSION_CODES.Q)
    fun publish(
        resolver: ContentResolver,
        uri: Uri
    ): Boolean {
        val published =
            publishCandidates(uri).any { candidate ->
                runCatching {
                    resolver.publishCandidate(candidate) && resolver.isPending(candidate) == false
                }.getOrDefault(false)
            }
        if (!published) {
            Timber.w("MediaStore publish could not be confirmed")
        }
        return published
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ContentResolver.publishCandidate(uri: Uri): Boolean {
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        val changed =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                update(
                    uri,
                    values,
                    Bundle().apply {
                        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                update(MediaStore.setIncludePending(uri), values, null, null)
            }
        return changed == 1
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishCandidates(uri: Uri): List<Uri> {
        val primaryAlias =
            if (uri.pathSegments.firstOrNull() == MediaStore.VOLUME_EXTERNAL) {
                runCatching {
                    ContentUris.withAppendedId(
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        ContentUris.parseId(uri)
                    )
                }.getOrNull()
            } else {
                null
            }
        return listOfNotNull(uri, primaryAlias).distinct()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ContentResolver.isPending(uri: Uri): Boolean? =
        query(uri, arrayOf(MediaStore.Downloads.IS_PENDING), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Downloads.IS_PENDING)) != 0
            } else {
                null
            }
        }
}
