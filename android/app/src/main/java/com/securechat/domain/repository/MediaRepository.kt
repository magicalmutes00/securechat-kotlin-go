package com.securechat.domain.repository

import com.securechat.core.common.Result
import com.securechat.domain.model.Media
import com.securechat.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    suspend fun getSignedUploadParams(request: SignedUploadRequest): Result<SignedUploadParams>

    /**
     * Finalises an upload and returns the media record itself.
     *
     * It returns [Media], not [Message]: `/media/complete` registers the asset
     * and nothing else. It used to be declared as returning a Message, which was
     * a fiction the implementation papered over by inventing a message with
     * id = 0 and conversationId = 0 — so the caller had no media id to reference
     * and the attachment could never be sent.
     */
    suspend fun completeUpload(request: CompleteUploadRequest): Result<Media>
    suspend fun uploadMedia(
        params: SignedUploadParams,
        filePath: String,
        /**
         * Caller-chosen id. A generated id is invisible to the caller until the
         * upload has already started, which made [cancelUpload] and
         * [retryUpload] impossible to reach from the UI — there was nothing to
         * pass them. Supplying the id up front makes the transfer addressable.
         *
         * It sits before [progressCallback] so the callback stays the trailing
         * lambda, which every existing call site relies on.
         */
        uploadId: String? = null,
        progressCallback: (UploadProgress) -> Unit
    ): Result<CloudinaryUploadResult>
    suspend fun downloadMedia(media: Media, destinationPath: String, progressCallback: (UploadProgress) -> Unit): Result<Unit>
    suspend fun cancelUpload(uploadId: String): Result<Unit>
    suspend fun retryUpload(uploadId: String): Result<Unit>
    suspend fun deleteMedia(mediaId: Long): Result<Unit>

    fun observeUploadProgress(uploadId: String): Flow<UploadProgress?>

    data class SignedUploadRequest(
        val resourceType: String,
        val filename: String,
        val mimeType: String,
        val fileSize: Long
    )

    data class SignedUploadParams(
        val uploadUrl: String,
        val publicId: String,
        val signature: String,
        val timestamp: Long,
        val apiKey: String,
        val folder: String,
        val resourceType: String,
        val allowedFormats: List<String>,
        val maxFileSize: Long,
        val transformation: String?
    )

    data class CompleteUploadRequest(
        val conversationId: Long,
        val cloudinaryPublicId: String,
        val resourceType: String,
        val secureUrl: String,
        val originalFilename: String,
        val mimeType: String,
        val fileSize: Long,
        val width: Int?,
        val height: Int?,
        val duration: Double?,
        val sha256: String
    )

    data class CloudinaryUploadResult(
        val publicId: String,
        val secureUrl: String,
        val width: Int?,
        val height: Int?,
        val duration: Double?,
        val format: String,
        val bytes: Long
    )

    data class UploadProgress(
        val uploadId: String,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val progressPercent: Int,
        val status: UploadStatus
    ) {
        val isComplete: Boolean
            get() = status == UploadStatus.COMPLETED || status == UploadStatus.FAILED || status == UploadStatus.CANCELLED
    }

    enum class UploadStatus {
        PENDING, UPLOADING, COMPLETED, FAILED, CANCELLED
    }
}