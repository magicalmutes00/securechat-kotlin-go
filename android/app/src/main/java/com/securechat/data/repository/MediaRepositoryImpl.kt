package com.securechat.data.repository

import android.net.Uri
import com.securechat.core.common.Result
import com.securechat.core.media.CloudinaryUploadHelper
import com.securechat.data.local.database.SecureChatDatabase
import com.securechat.data.local.dao.MediaDao
import com.securechat.data.local.entity.MediaEntity
import com.securechat.data.remote.api.ApiService
import com.securechat.data.remote.dto.MediaDto
import com.securechat.data.remote.dto.SignUploadResponse
import com.securechat.domain.model.Media
import com.securechat.domain.repository.MediaRepository
import com.securechat.domain.repository.MediaRepository.UploadProgress
import com.securechat.domain.repository.MediaRepository.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val database: SecureChatDatabase,
    private val mediaDao: MediaDao,
    private val cloudinaryHelper: CloudinaryUploadHelper
) : MediaRepository {

    // Concurrent: uploads run in parallel from several coroutines, so an
    // unsynchronized mutableMapOf here was a CME / lost-update risk.
    private val uploadProgressChannels = ConcurrentHashMap<String, Channel<UploadProgress>>()

    /**
     * The request behind an upload that has started but not completed, keyed by
     * upload id. This is what makes [retryUpload] possible at all: the signed
     * target and the local file only exist together here.
     */
    private data class RetainedUpload(
        val params: MediaRepository.SignedUploadParams,
        val filePath: String
    )

    private val retainedUploads = ConcurrentHashMap<String, RetainedUpload>()

    override suspend fun getSignedUploadParams(request: MediaRepository.SignedUploadRequest): Result<MediaRepository.SignedUploadParams> {
        return withContext(Dispatchers.IO) {
            apiService.signUpload(com.securechat.data.remote.dto.SignUploadRequest(
                resource_type = request.resourceType,
                filename = request.filename,
                mime_type = request.mimeType,
                file_size = request.fileSize
            )).map { response ->
                MediaRepository.SignedUploadParams(
                    uploadUrl = response.upload_url,
                    publicId = response.public_id,
                    signature = response.signature,
                    timestamp = response.timestamp,
                    apiKey = response.api_key,
                    folder = response.folder,
                    resourceType = response.resource_type,
                    allowedFormats = response.allowed_formats,
                    maxFileSize = response.max_file_size,
                    transformation = response.transformation
                )
            }
        }
    }

    override suspend fun completeUpload(request: MediaRepository.CompleteUploadRequest): Result<Media> {
        return withContext(Dispatchers.IO) {
            apiService.completeUpload(com.securechat.data.remote.dto.CompleteUploadRequest(
                conversation_id = request.conversationId,
                cloudinary_public_id = request.cloudinaryPublicId,
                resource_type = request.resourceType,
                secure_url = request.secureUrl,
                original_filename = request.originalFilename,
                mime_type = request.mimeType,
                file_size = request.fileSize,
                width = request.width,
                height = request.height,
                duration = request.duration,
                sha256 = request.sha256
            )).map { response ->
                mapToMedia(response)
            }
        }
    }

    override suspend fun uploadMedia(
        params: MediaRepository.SignedUploadParams,
        filePath: String,
        uploadId: String?,
        progressCallback: (UploadProgress) -> Unit
    ): Result<MediaRepository.CloudinaryUploadResult> {
        return withContext(Dispatchers.IO) {
            val id = uploadId ?: UUID.randomUUID().toString()
            // Retained for the whole transfer: a failure leaves the descriptor in
            // place so retryUpload has something to replay, and only a completed
            // or cancelled upload drops it.
            retainedUploads[id] = RetainedUpload(params, filePath)

            val progressChannel = Channel<UploadProgress>(Channel.UNLIMITED)
            uploadProgressChannels[id] = progressChannel

            try {
                coroutineScope {
                    // Structured: the collector is a child of this scope, so it
                    // can never outlive the upload. It previously ran in a
                    // detached CoroutineScope that was only cancelled on the
                    // success path, leaking one scope per upload on failure.
                    val progressJob = launch {
                        progressChannel.receiveAsFlow().collect { progress ->
                            progressCallback(progress)
                        }
                    }

                    try {
                        val result = transfer(id, params, filePath) { progress ->
                            progressChannel.trySend(progress)
                        }
                        if (result.isSuccess) retainedUploads.remove(id)
                        result
                    } finally {
                        progressJob.cancel()
                    }
                }
            } catch (e: Exception) {
                progressCallback(
                    UploadProgress(
                        uploadId = id,
                        bytesTransferred = 0,
                        totalBytes = 0,
                        progressPercent = 0,
                        status = UploadStatus.FAILED
                    )
                )
                Result.failure(e)
            } finally {
                uploadProgressChannels.remove(id)
            }
        }
    }

    /**
     * Retries a retained transfer under the same id, so an observer of
     * [observeUploadProgress] keeps watching the same upload.
     *
     * The retained signed parameters are what get replayed. Cloudinary rejects an
     * expired signature, and re-signing needs the backend, so that case surfaces
     * as a failure the caller answers by re-running the whole send — the transfer
     * itself is the only thing this can restart on its own.
     */
    override suspend fun retryUpload(uploadId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val retained = retainedUploads[uploadId]
                ?: return@withContext Result.failure(
                    IllegalStateException("Nothing retained to retry for upload $uploadId")
                )

            val progressChannel = Channel<UploadProgress>(Channel.UNLIMITED)
            uploadProgressChannels[uploadId] = progressChannel
            try {
                val result = transfer(uploadId, retained.params, retained.filePath) { progress ->
                    progressChannel.trySend(progress)
                }
                if (result.isSuccess) {
                    retainedUploads.remove(uploadId)
                    Result.success(Unit)
                } else {
                    Result.failure(
                        (result as? Result.Failure)?.exception
                            ?: IllegalStateException("Retry failed")
                    )
                }
            } finally {
                uploadProgressChannels.remove(uploadId)
            }
        }
    }

    /**
     * The actual Cloudinary transfer, shared by the first attempt and by retries.
     * Reports progress per chunk as bytes leave the device.
     */
    private suspend fun transfer(
        uploadId: String,
        params: MediaRepository.SignedUploadParams,
        filePath: String,
        onProgress: (UploadProgress) -> Unit
    ): Result<MediaRepository.CloudinaryUploadResult> {
        val result = cloudinaryHelper.uploadFile(
            uploadUrl = params.uploadUrl,
            publicId = params.publicId,
            signature = params.signature,
            timestamp = params.timestamp,
            apiKey = params.apiKey,
            filePath = filePath,
            uploadId = uploadId,
            progressListener = { bytesTransferred, totalBytes ->
                onProgress(
                    UploadProgress(
                        uploadId = uploadId,
                        bytesTransferred = bytesTransferred,
                        totalBytes = totalBytes,
                        progressPercent = if (totalBytes > 0) (bytesTransferred * 100 / totalBytes).toInt() else 0,
                        status = if (bytesTransferred >= totalBytes) UploadStatus.COMPLETED else UploadStatus.UPLOADING
                    )
                )
            }
        )

        return result.map { cloudinaryResult ->
            MediaRepository.CloudinaryUploadResult(
                publicId = cloudinaryResult.publicId,
                secureUrl = cloudinaryResult.secureUrl,
                width = cloudinaryResult.width,
                height = cloudinaryResult.height,
                duration = cloudinaryResult.duration,
                format = cloudinaryResult.format,
                bytes = cloudinaryResult.bytes
            )
        }
    }

    override suspend fun downloadMedia(
        media: Media,
        destinationPath: String,
        progressCallback: (UploadProgress) -> Unit
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Keyed on the media id so a download is cancellable the same way an
            // upload is.
            val downloadId = "download_${media.id}"
            cloudinaryHelper.downloadFile(media.secureUrl, destinationPath, downloadId, progressCallback)
        }
    }

    override suspend fun cancelUpload(uploadId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Closing the channel only stopped progress updates — the transfer
            // kept running to completion. Aborting the HTTP call is what the
            // user expects "cancel" to do.
            val cancelled = cloudinaryHelper.cancel(uploadId)
            uploadProgressChannels.remove(uploadId)?.close()
            // A cancelled transfer must not be retried from a stale descriptor.
            retainedUploads.remove(uploadId)
            // Success either way: cancelling an already-finished transfer is not
            // an error, and the caller only wants the transfer stopped.
            if (!cancelled) {
                // No live transfer under that id (already finished, or unknown).
            }
            Result.success(Unit)
        }
    }

    override suspend fun deleteMedia(mediaId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            apiService.deleteMedia(mediaId)
                .flatMap {
                    database.mediaDao().deleteById(mediaId)
                    Result.success(Unit)
                }
        }
    }

    override fun observeUploadProgress(uploadId: String): kotlinx.coroutines.flow.Flow<UploadProgress?> {
        return uploadProgressChannels[uploadId]?.receiveAsFlow()
            ?: kotlinx.coroutines.flow.flowOf(null)
    }

    private fun mapToMedia(dto: MediaDto): Media {
        return Media(
            id = dto.id,
            messageId = dto.message_id ?: 0,
            cloudinaryPublicId = dto.cloudinary_public_id,
            resourceType = com.securechat.domain.model.MediaResourceType.fromStorage(dto.resource_type),
            secureUrl = dto.secure_url,
            originalFilename = dto.original_filename,
            mimeType = dto.mime_type,
            fileSize = dto.file_size,
            width = dto.width,
            height = dto.height,
            duration = dto.duration,
            sha256 = dto.sha256,
            thumbnailUrl = dto.thumbnail_url,
            createdAt = dto.created_at
        )
    }
}