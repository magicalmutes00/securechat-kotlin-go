package com.securechat.data.sync

import com.securechat.core.common.Result
import com.securechat.domain.model.Media
import com.securechat.domain.model.Message
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.model.MessageType
import com.securechat.domain.repository.MediaRepository
import com.securechat.domain.repository.MessageRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a [PendingMediaSend] into a message: sign, upload, complete, send.
 *
 * The four steps and their ordering live in exactly one place because two callers
 * need them — the chat screen when the user attaches a file, and the sync pass
 * when a previous attempt failed. Duplicating the sequence is how the original
 * code ended up doing steps one to three in the UI and never doing step four, so
 * the asset reached Cloudinary and no message ever existed.
 */
@Singleton
class MediaMessageSender @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val messageRepository: MessageRepository
) {
    suspend fun send(
        payload: PendingMediaSend,
        onProgress: (Int) -> Unit = {}
    ): Result<Message> {
        val resourceType = resourceTypeFor(payload.mimeType)
        val messageType = messageTypeFor(payload.mimeType)

        val paramsResult = mediaRepository.getSignedUploadParams(
            MediaRepository.SignedUploadRequest(
                resourceType = resourceType,
                filename = payload.fileName,
                mimeType = payload.mimeType,
                fileSize = payload.fileSize
            )
        )
        val params = when (paramsResult) {
            is Result.Failure -> return paramsResult
            is Result.Success -> paramsResult.data
        }

        // The caller-supplied id keeps the transfer addressable for cancel/retry
        // and makes the queue row and the in-flight transfer the same thing.
        val uploadResult = mediaRepository.uploadMedia(
            params = params,
            filePath = payload.filePath,
            uploadId = payload.uploadId,
            progressCallback = { onProgress(it.progressPercent) }
        )
        val uploaded = when (uploadResult) {
            is Result.Failure -> return uploadResult
            is Result.Success -> uploadResult.data
        }

        val completeResult = mediaRepository.completeUpload(
            MediaRepository.CompleteUploadRequest(
                conversationId = payload.conversationId,
                cloudinaryPublicId = uploaded.publicId,
                resourceType = resourceType,
                secureUrl = uploaded.secureUrl,
                originalFilename = payload.fileName,
                mimeType = payload.mimeType,
                fileSize = if (uploaded.bytes > 0) uploaded.bytes else payload.fileSize,
                width = uploaded.width,
                height = uploaded.height,
                duration = uploaded.duration,
                sha256 = payload.sha256
            )
        )
        val stored = when (completeResult) {
            is Result.Failure -> return completeResult
            is Result.Success -> completeResult.data
        }

        // The message references the *server* media id, which is what the socket
        // frame carries and what the local media row mirrors in `serverId`.
        val media = Media(
            id = stored.id,
            messageId = 0,
            cloudinaryPublicId = stored.cloudinaryPublicId,
            resourceType = stored.resourceType,
            secureUrl = stored.secureUrl,
            originalFilename = payload.fileName,
            mimeType = payload.mimeType,
            fileSize = if (stored.fileSize > 0) stored.fileSize else payload.fileSize,
            width = stored.width,
            height = stored.height,
            duration = stored.duration,
            sha256 = payload.sha256,
            thumbnailUrl = stored.thumbnailUrl,
            createdAt = stored.createdAt
        )

        val now = System.currentTimeMillis()
        val message = Message(
            id = 0,
            conversationId = payload.conversationId,
            senderId = payload.senderId,
            type = messageType,
            text = null,
            media = media,
            replyTo = null,
            status = MessageStatus.PENDING,
            createdAt = now,
            updatedAt = now,
            deliveredAt = null,
            readAt = null,
            deletedAt = null,
            tempId = payload.uploadId,
            isOptimistic = true
        )

        return messageRepository.sendMessage(message)
    }

    /** Cloudinary's upload endpoint is addressed by one of these four. */
    private fun resourceTypeFor(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "image"
        mimeType.startsWith("video/") -> "video"
        mimeType.startsWith("audio/") -> "audio"
        else -> "raw"
    }

    /**
     * The message kind, which is a different vocabulary from Cloudinary's: a PDF
     * is `raw` to Cloudinary and DOCUMENT in a chat, and a `.zip` is `raw` in both
     * but must not render as an image.
     */
    private fun messageTypeFor(mimeType: String): MessageType = when {
        mimeType.startsWith("image/") -> MessageType.IMAGE
        mimeType.startsWith("video/") -> MessageType.VIDEO
        mimeType.startsWith("audio/") -> MessageType.AUDIO
        else -> MessageType.DOCUMENT
    }
}
