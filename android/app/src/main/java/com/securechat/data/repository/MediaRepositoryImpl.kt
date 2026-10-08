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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val database: SecureChatDatabase,
    private val mediaDao: MediaDao,
    private val cloudinaryHelper: CloudinaryUploadHelper
) : MediaRepository {

    private val uploadProgressChannels = mutableMapOf<String, Channel<UploadProgress>>()

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

    override suspend fun completeUpload(request: MediaRepository.CompleteUploadRequest): Result<com.securechat.domain.model.Message> {
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
                mapToMessage(response)
            }
        }
    }

    override suspend fun uploadMedia(
        params: MediaRepository.SignedUploadParams,
        filePath: String,
        progressCallback: (UploadProgress) -> Unit
    ): Result<MediaRepository.CloudinaryUploadResult> {
        return withContext(Dispatchers.IO) {
            val uploadId = UUID.randomUUID().toString()
            val progressChannel = Channel<UploadProgress>(Channel.UNLIMITED)
            uploadProgressChannels[uploadId] = progressChannel
            
            // Start progress listener
            val progressJob = CoroutineScope(Dispatchers.IO).launch {
                progressChannel.receiveAsFlow().collect { progress ->
                    progressCallback(progress)
                }
            }
            
            try {
                val result = cloudinaryHelper.uploadFile(
                    uploadUrl = params.uploadUrl,
                    publicId = params.publicId,
                    signature = params.signature,
                    timestamp = params.timestamp,
                    apiKey = params.apiKey,
                    filePath = filePath,
                    progressListener = { bytesTransferred, totalBytes ->
                        val progress = UploadProgress(
                            uploadId = uploadId,
                            bytesTransferred = bytesTransferred,
                            totalBytes = totalBytes,
                            progressPercent = if (totalBytes > 0) (bytesTransferred * 100 / totalBytes).toInt() else 0,
                            status = if (bytesTransferred >= totalBytes) UploadStatus.COMPLETED else UploadStatus.UPLOADING
                        )
                        progressChannel.trySend(progress)
                    }
                )
                
                progressJob.cancel()
                uploadProgressChannels.remove(uploadId)
                
                result.map { cloudinaryResult ->
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
            } catch (e: Exception) {
                progressJob.cancel()
                uploadProgressChannels.remove(uploadId)
                val failedProgress = UploadProgress(
                    uploadId = uploadId,
                    bytesTransferred = 0,
                    totalBytes = 0,
                    progressPercent = 0,
                    status = UploadStatus.FAILED
                )
                progressCallback(failedProgress)
                Result.failure(e)
            }
        }
    }

    override suspend fun downloadMedia(
        media: Media,
        destinationPath: String,
        progressCallback: (UploadProgress) -> Unit
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Use DownloadManager or custom implementation
            // For now, delegate to cloudinaryHelper
            cloudinaryHelper.downloadFile(media.secureUrl, destinationPath, progressCallback)
        }
    }

    override suspend fun cancelUpload(uploadId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Cancel the upload - would need to track ongoing requests
            uploadProgressChannels[uploadId]?.close()
            uploadProgressChannels.remove(uploadId)
            Result.success(Unit)
        }
    }

    override suspend fun retryUpload(uploadId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Retry logic would be implemented here
            Result.failure(UnsupportedOperationException("Retry not implemented yet"))
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

    private fun mapToMessage(dto: MediaDto): com.securechat.domain.model.Message {
        // This would be a full message mapping
        return com.securechat.domain.model.Message(
            id = 0,
            conversationId = 0,
            senderId = 0,
            type = com.securechat.domain.model.MessageType.valueOf(dto.resource_type.uppercase()),
            text = null,
            media = mapToMedia(dto),
            replyTo = null,
            status = com.securechat.domain.model.MessageStatus.SENT,
            createdAt = dto.created_at,
            updatedAt = dto.created_at,
            deliveredAt = null,
            readAt = null,
            deletedAt = null
        )
    }

    private fun mapToMedia(dto: MediaDto): Media {
        return Media(
            id = dto.id,
            messageId = dto.message_id ?: 0,
            cloudinaryPublicId = dto.cloudinary_public_id,
            resourceType = com.securechat.domain.model.MediaResourceType.valueOf(dto.resource_type.uppercase()),
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