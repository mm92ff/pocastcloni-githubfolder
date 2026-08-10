package com.example.pocastcloni.data.cover

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Path.Companion.toOkioPath
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Coil data that identifies a published cover revision without exposing a local absolute path. */
data class PodcastCoverRequestData(
    val podcastRssUrl: String,
    val sourceUrl: String,
    val thumbnailFileName: String?,
    val thumbnailRevision: Long
)

@Singleton
class PodcastCoverFetcherFactory
@Inject
constructor(
    private val materializer: PodcastCoverMaterializer
) : Fetcher.Factory<PodcastCoverRequestData> {
    override fun create(
        data: PodcastCoverRequestData,
        options: Options,
        imageLoader: ImageLoader
    ): Fetcher = PodcastCoverFetcher(data, materializer)
}

private class PodcastCoverFetcher(
    private val data: PodcastCoverRequestData,
    private val materializer: PodcastCoverMaterializer
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val result =
            materializer.resolveForDisplay(
                podcastRssUrl = data.podcastRssUrl,
                latestSourceUrl = data.sourceUrl,
                claimedFileName = data.thumbnailFileName
            )
        val file =
            when (result) {
                is PodcastCoverMaterializationResult.Available -> result.file
                is PodcastCoverMaterializationResult.Waiting -> result.file
                is PodcastCoverMaterializationResult.RetryableFailure -> throw result.error
                is PodcastCoverMaterializationResult.PermanentFailure -> throw result.error
                PodcastCoverMaterializationResult.NoSource -> null
            } ?: throw IOException("No valid podcast cover is available")
        return SourceResult(
            source = ImageSource(file.toOkioPath()),
            mimeType = "image/webp",
            dataSource = DataSource.DISK
        )
    }
}
