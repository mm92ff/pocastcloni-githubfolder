package com.example.pocastcloni.data.worker

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

internal data class DownloadResumeMetadata(
    val url: String,
    val strongETag: String?,
    val lastModified: String?,
    val totalBytes: Long?
)

internal class DownloadResumeMetadataStore(
    private val objectMapper: ObjectMapper
) {
    fun read(file: File): DownloadResumeMetadata? =
        runCatching { objectMapper.readValue(file, DownloadResumeMetadata::class.java) }.getOrNull()

    fun write(
        file: File,
        metadata: DownloadResumeMetadata
    ) {
        file.parentFile?.mkdirs()
        val temporaryFile = File(file.parentFile, "${file.name}.tmp")
        objectMapper.writeValue(temporaryFile, metadata)
        if (file.exists() && !file.delete()) {
            temporaryFile.delete()
            throw IOException("Could not replace download resume metadata")
        }
        if (!temporaryFile.renameTo(file)) {
            temporaryFile.delete()
            throw IOException("Could not commit download resume metadata")
        }
    }
}

internal data class ResumableDownloadResult(
    val partFile: File,
    val totalBytes: Long
)

/**
 * Owns resumable staging and each HTTP response for the duration of a transfer.
 *
 * [executeCall] keeps `Response.use` ownership after a successful handoff and cancels the active
 * call when its coroutine ends. Resume metadata is committed before response bytes are appended,
 * and cancellation leaves staging decisions to the worker's retry ownership policy.
 */
internal class ResumableDownload(
    private val client: OkHttpClient,
    private val metadataStore: DownloadResumeMetadataStore,
    private val maxBytes: Long,
    private val storageReserveBytes: Long,
    private val storageRecheckIntervalBytes: Long,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun download(
        url: String,
        stagingFiles: DownloadStagingFiles,
        availableBytes: () -> Long,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): ResumableDownloadResult {
        stagingFiles.partFile.parentFile?.mkdirs()
        val transferContext = TransferContext(url, stagingFiles, availableBytes, onProgress)
        return downloadLoop(transferContext)
    }

    private suspend fun downloadLoop(context: TransferContext): ResumableDownloadResult {
        var cleanRestartUsed = false
        while (true) {
            currentCoroutineContext().ensureActive()
            val resume = resumeCandidate(context.url, context.stagingFiles)
            val offset = resume?.offset ?: 0L
            val decision = executeCall(createRequest(context.url, resume)) { response ->
                responseDecision(response, offset, resume?.metadata, context)
            }
            if (decision is TransferDecision.Complete) return decision.result
            if (cleanRestartUsed) {
                throw DownloadProtocolException("Server returned inconsistent resume responses")
            }
            cleanRestartUsed = true
            context.stagingFiles.delete()
        }
    }

    private fun resumeCandidate(
        url: String,
        stagingFiles: DownloadStagingFiles
    ): ResumeCandidate? {
        val partFile = stagingFiles.partFile
        val metadata = metadataStore.read(stagingFiles.metadataFile)
        val validator = metadata?.resumeValidator()
        val partLength = partFile.takeIf { it.isFile }?.length() ?: 0L
        if (partLength > maxBytes) throw DownloadSizeLimitException()
        val declaredTotalIsValid =
            metadata?.totalBytes?.let { it > partLength && it <= maxBytes } ?: true
        val canResume =
            partLength > 0L &&
                metadata?.url == url &&
                validator != null &&
                declaredTotalIsValid
        return if (canResume) {
            ResumeCandidate(partLength, requireNotNull(metadata), requireNotNull(validator))
        } else {
            stagingFiles.delete()
            null
        }
    }

    private fun createRequest(
        url: String,
        resume: ResumeCandidate?
    ): Request =
        Request.Builder()
            .url(url)
            .header(HEADER_ACCEPT_ENCODING, IDENTITY_ENCODING)
            .apply {
                if (resume != null) {
                    header(HEADER_RANGE, "bytes=${resume.offset}-")
                    header(HEADER_IF_RANGE, resume.validator.value)
                }
            }
            .build()

    private suspend fun responseDecision(
        response: Response,
        offset: Long,
        resumeMetadata: DownloadResumeMetadata?,
        context: TransferContext
    ): TransferDecision =
        if (offset > 0L) {
            resumedResponseDecision(response, offset, resumeMetadata, context)
        } else {
            fullResponseDecision(response, context)
        }

    private suspend fun resumedResponseDecision(
        response: Response,
        offset: Long,
        resumeMetadata: DownloadResumeMetadata?,
        context: TransferContext
    ): TransferDecision =
        when (response.code) {
            HTTP_PARTIAL_CONTENT -> partialResponseDecision(response, offset, resumeMetadata, context)
            HTTP_OK ->
                TransferDecision.Complete(
                    copyResponse(
                        response = response,
                        context = context,
                        append = false,
                        startingBytes = 0L,
                        totalBytes = response.body?.contentLength()?.takeIf { it >= 0L }
                    )
                )
            HTTP_RANGE_NOT_SATISFIABLE -> TransferDecision.CleanRestart
            else -> handleNonDownloadResponse(response)
        }

    private suspend fun partialResponseDecision(
        response: Response,
        offset: Long,
        resumeMetadata: DownloadResumeMetadata?,
        context: TransferContext
    ): TransferDecision {
        val range = parseContentRange(response.header(HEADER_CONTENT_RANGE))
        val invalidResponse =
            range == null ||
                range.start != offset ||
                !resumeValidatorMatches(resumeMetadata, response) ||
                !resumeTotalMatches(resumeMetadata, range) ||
                !isExactPartialResponse(range, response)
        return if (invalidResponse) {
            TransferDecision.CleanRestart
        } else {
            val validRange = requireNotNull(range)
            TransferDecision.Complete(
                copyResponse(
                    response = response,
                    context = context,
                    append = true,
                    startingBytes = offset,
                    totalBytes = validRange.total
                )
            )
        }
    }

    private suspend fun fullResponseDecision(
        response: Response,
        context: TransferContext
    ): TransferDecision =
        when (response.code) {
            HTTP_OK ->
                TransferDecision.Complete(
                    copyResponse(
                        response = response,
                        context = context,
                        append = false,
                        startingBytes = 0L,
                        totalBytes = response.body?.contentLength()?.takeIf { it >= 0L }
                    )
                )
            HTTP_PARTIAL_CONTENT,
            HTTP_RANGE_NOT_SATISFIABLE -> TransferDecision.CleanRestart
            else -> handleNonDownloadResponse(response)
        }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "ThrowsCount")
    private suspend fun copyResponse(
        response: Response,
        context: TransferContext,
        append: Boolean,
        startingBytes: Long,
        totalBytes: Long?
    ): ResumableDownloadResult {
        val body = response.body ?: throw IOException("Response body is null")
        if (totalBytes != null && totalBytes > maxBytes) throw DownloadSizeLimitException()
        val requiredBytes = totalBytes?.minus(startingBytes) ?: storageRecheckIntervalBytes
        ensureAvailableStorage(context.availableBytes(), requiredBytes, storageReserveBytes)

        metadataStore.write(
            context.stagingFiles.metadataFile,
            DownloadResumeMetadata(
                url = context.url,
                strongETag = response.header(HEADER_ETAG)?.takeIf(::isStrongETag),
                lastModified = response.header(HEADER_LAST_MODIFIED)?.takeIf(::isValidLastModified),
                totalBytes = totalBytes
            )
        )

        var downloadedBytes = startingBytes
        var nextStorageCheckAt = startingBytes + storageRecheckIntervalBytes
        val expectedResponseBytes = body.contentLength().takeIf { it >= 0L }

        FileOutputStream(context.stagingFiles.partFile, append).use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    ensureDownloadChunkWithinLimit(downloadedBytes, bytesRead, maxBytes)
                    if (downloadedBytes + bytesRead >= nextStorageCheckAt) {
                        val remainingBytes =
                            totalBytes?.minus(downloadedBytes)?.coerceAtLeast(bytesRead.toLong())
                                ?: storageRecheckIntervalBytes
                        ensureAvailableStorage(context.availableBytes(), remainingBytes, storageReserveBytes)
                        nextStorageCheckAt = downloadedBytes + bytesRead + storageRecheckIntervalBytes
                    }
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    context.onProgress(downloadedBytes, totalBytes)
                }
            }
            output.fd.sync()
        }

        if (expectedResponseBytes != null && downloadedBytes - startingBytes != expectedResponseBytes) {
            throw IOException("Download response length did not match Content-Length")
        }
        if (totalBytes != null && downloadedBytes != totalBytes) {
            throw IOException("Download did not reach the declared total size")
        }
        return ResumableDownloadResult(context.stagingFiles.partFile, downloadedBytes)
    }

    private fun isExactPartialResponse(
        range: ContentRange,
        response: Response
    ): Boolean {
        val rangeLength = range.end - range.start + 1L
        val bodyLength = response.body?.contentLength()
        val totalIsValid = range.total == null || (range.total > range.end && range.total <= maxBytes)
        return range.end >= range.start &&
            totalIsValid &&
            bodyLength != null &&
            (bodyLength < 0L || bodyLength == rangeLength)
    }

    private fun handleNonDownloadResponse(response: Response): TransferDecision {
        if (!response.isSuccessful) throw DownloadHttpException(response.code)
        throw DownloadProtocolException("Unexpected successful HTTP status ${response.code}")
    }

    private suspend fun <T> executeCall(
        request: Request,
        block: suspend (Response) -> T
    ): T = coroutineScope {
        val call = client.newCall(request)
        val cancellationWatcher =
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
        try {
            val response = call.awaitResponse()
            response.use { withContext(ioDispatcher) { block(response) } }
        } finally {
            cancellationWatcher.cancelAndJoin()
        }
    }

    private sealed interface TransferDecision {
        data class Complete(val result: ResumableDownloadResult) : TransferDecision

        data object CleanRestart : TransferDecision
    }

    private data class TransferContext(
        val url: String,
        val stagingFiles: DownloadStagingFiles,
        val availableBytes: () -> Long,
        val onProgress: suspend (downloadedBytes: Long, totalBytes: Long?) -> Unit
    )

    private data class ResumeCandidate(
        val offset: Long,
        val metadata: DownloadResumeMetadata,
        val validator: ResumeValidator
    )

    internal data class ResumeValidator(
        val type: ValidatorType,
        val value: String
    )

    internal enum class ValidatorType {
        STRONG_ETAG,
        LAST_MODIFIED
    }

    internal data class ContentRange(
        val start: Long,
        val end: Long,
        val total: Long?
    )

    internal companion object {
        const val HTTP_OK = 200
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val HEADER_ACCEPT_ENCODING = "Accept-Encoding"
        const val HEADER_RANGE = "Range"
        const val HEADER_IF_RANGE = "If-Range"
        const val HEADER_CONTENT_RANGE = "Content-Range"
        const val HEADER_ETAG = "ETag"
        const val HEADER_LAST_MODIFIED = "Last-Modified"
        const val IDENTITY_ENCODING = "identity"
        const val CONTENT_RANGE_START_GROUP = 1
        const val CONTENT_RANGE_END_GROUP = 2
        const val CONTENT_RANGE_TOTAL_GROUP = 3
        const val MIN_STRONG_ETAG_LENGTH = 2
        val CONTENT_RANGE_PATTERN = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)
    }
}

/**
 * Awaits one callback while transferring response ownership cancellation-safely.
 *
 * Cancellation always cancels the call. Once OkHttp produces a response, either the resumed caller
 * owns it and closes it through `Response.use`, or prompt/late cancellation invokes the resume
 * cleanup handler. Those ownership paths are mutually exclusive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun Call.awaitResponse(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    continuation.resume(response) { response.close() }
                }
            }
        )
    }

private fun DownloadResumeMetadata.resumeValidator(): ResumableDownload.ResumeValidator? =
    strongETag?.takeIf(::isStrongETag)?.let {
        ResumableDownload.ResumeValidator(ResumableDownload.ValidatorType.STRONG_ETAG, it)
    } ?: lastModified?.takeIf(::isValidLastModified)?.let {
        ResumableDownload.ResumeValidator(ResumableDownload.ValidatorType.LAST_MODIFIED, it)
    }

private fun resumeValidatorMatches(
    metadata: DownloadResumeMetadata?,
    response: Response
): Boolean {
    val validator = metadata?.resumeValidator() ?: return false
    return when (validator.type) {
        ResumableDownload.ValidatorType.STRONG_ETAG ->
            response.header("ETag")?.takeIf(::isStrongETag) == validator.value
        ResumableDownload.ValidatorType.LAST_MODIFIED ->
            response.header("Last-Modified")?.takeIf(::isValidLastModified) == validator.value
    }
}

private fun resumeTotalMatches(
    metadata: DownloadResumeMetadata?,
    range: ResumableDownload.ContentRange?
): Boolean = metadata?.totalBytes?.let { persistedTotal -> range?.total == persistedTotal } ?: true

private fun parseContentRange(value: String?): ResumableDownload.ContentRange? {
    val match = value?.let { ResumableDownload.CONTENT_RANGE_PATTERN.matchEntire(it.trim()) }
        ?: return null
    val start = match.groupValues[ResumableDownload.CONTENT_RANGE_START_GROUP].toLongOrNull()
    val end = match.groupValues[ResumableDownload.CONTENT_RANGE_END_GROUP].toLongOrNull()
    val totalValue = match.groupValues[ResumableDownload.CONTENT_RANGE_TOTAL_GROUP]
    val total = totalValue.takeUnless { it == "*" }?.toLongOrNull()
    val validTotal = totalValue == "*" || total != null
    return if (start != null && end != null && validTotal) {
        ResumableDownload.ContentRange(start, end, total)
    } else {
        null
    }
}

private fun isStrongETag(value: String): Boolean =
    !value.startsWith("W/", ignoreCase = true) &&
        value.length >= ResumableDownload.MIN_STRONG_ETAG_LENGTH &&
        value.startsWith('"') &&
        value.endsWith('"')

private fun isValidLastModified(value: String): Boolean =
    try {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
        true
    } catch (_: DateTimeParseException) {
        false
    }

internal class DownloadProtocolException(message: String) : IOException(message)
