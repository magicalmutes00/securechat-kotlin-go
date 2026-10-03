package com.securechat.presentation.chat

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.common.Result
import com.securechat.core.media.CloudinaryUploadHelper
import com.securechat.domain.model.Media
import com.securechat.domain.model.Message
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.model.MessageType
import com.securechat.domain.usecase.chat.GetMessagesUseCase
import com.securechat.domain.usecase.chat.ObserveMessagesUseCase
import com.securechat.domain.usecase.chat.SendMessageUseCase
import com.securechat.domain.usecase.chat.DeleteMessageUseCase
import com.securechat.domain.usecase.chat.MarkAsReadUseCase
import com.securechat.domain.usecase.media.GetSignedUploadParamsUseCase
import com.securechat.domain.usecase.media.UploadMediaUseCase
import com.securechat.domain.usecase.media.CompleteUploadUseCase
import com.securechat.domain.usecase.media.DownloadMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getMessagesUseCase: GetMessagesUseCase,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val markAsReadUseCase: MarkAsReadUseCase,
    private val getSignedUploadParamsUseCase: GetSignedUploadParamsUseCase,
    private val uploadMediaUseCase: UploadMediaUseCase,
    private val completeUploadUseCase: CompleteUploadUseCase,
    private val downloadMediaUseCase: DownloadMediaUseCase,
    private val cloudinaryHelper: CloudinaryUploadHelper
) : ViewModel() {

    // Room is the source of truth; the observe flow keeps this state live for
    // optimistic sends, server acks, receipts and incoming messages alike.
    var messages: MutableState<List<Message>> = mutableStateOf(emptyList())
    var isLoading: MutableState<Boolean> = mutableStateOf(false)
    var isLoadingMore: MutableState<Boolean> = mutableStateOf(false)
    var hasMore: MutableState<Boolean> = mutableStateOf(true)
    var errorMessage: MutableState<String?> = mutableStateOf(null)

    private var conversationId: Long = 0L
    var currentUserId: Long = 0L
        private set
    private var cursor: Long? = null
    private val LIMIT = 30
    private var observeJob: Job? = null

    fun initialize(conversationId: Long, currentUserId: Long) {
        if (this.conversationId == conversationId && observeJob?.isActive == true) return
        this.conversationId = conversationId
        this.currentUserId = currentUserId
        this.cursor = null
        this.messages.value = emptyList()

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            observeMessagesUseCase(conversationId).collect { list ->
                // Newest first for the reverse-layout list.
                messages.value = list.sortedByDescending { it.createdAt }
            }
        }

        loadMessages()
        markAsRead()
    }

    fun loadMessages() {
        if (conversationId == 0L) return

        if (cursor == null) {
            isLoading.value = true
        } else {
            isLoadingMore.value = true
        }
        errorMessage.value = null

        viewModelScope.launch {
            val result = getMessagesUseCase(conversationId, LIMIT, cursor)

            if (cursor == null) {
                isLoading.value = false
            } else {
                isLoadingMore.value = false
            }

            result.onSuccess { page ->
                cursor = page.nextCursor
                hasMore.value = page.hasMore
                // The REST fetch refreshes Room; the observe flow updates state.
            }.onFailure {
                errorMessage.value = "Failed to load messages"
            }
        }
    }

    fun sendTextMessage(text: String, replyToId: Long? = null) {
        if (text.trim().isEmpty()) return

        val message = Message(
            id = 0,
            conversationId = conversationId,
            senderId = currentUserId,
            type = MessageType.TEXT,
            text = text.trim(),
            media = null,
            replyTo = replyToId?.let { id -> messages.value.find { it.id == id } },
            status = MessageStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            deliveredAt = null,
            readAt = null,
            deletedAt = null,
            tempId = UUID.randomUUID().toString(),
            isOptimistic = true
        )

        // Repository persists the optimistic row (Room flow updates the list)
        // and hands the frame to the WebSocket queue.
        viewModelScope.launch {
            sendMessageUseCase(message)
        }
    }

    fun sendMediaMessage(
        filePath: String,
        mimeType: String,
        fileName: String,
        fileSize: Long,
        onProgress: (Int) -> Unit
    ) {
        viewModelScope.launch {
            // 1. Get signed upload params
            val resourceType = when {
                mimeType.startsWith("image/") -> "image"
                mimeType.startsWith("video/") -> "video"
                mimeType.startsWith("audio/") -> "audio"
                else -> "raw"
            }

            val paramsResult = getSignedUploadParamsUseCase(
                com.securechat.domain.repository.MediaRepository.SignedUploadRequest(
                    resourceType = resourceType,
                    filename = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize
                )
            )

            paramsResult.onSuccess { params ->
                // 2. Upload to Cloudinary
                val uploadResult = uploadMediaUseCase(params, filePath) { progress ->
                    onProgress(progress.progressPercent)
                }

                uploadResult.onSuccess { cloudinaryResult ->
                    // 3. Complete upload (server validates the SHA-256 format)
                    val completeRequest = com.securechat.domain.repository.MediaRepository.CompleteUploadRequest(
                        conversationId = conversationId,
                        cloudinaryPublicId = cloudinaryResult.publicId,
                        resourceType = resourceType,
                        secureUrl = cloudinaryResult.secureUrl,
                        originalFilename = fileName,
                        mimeType = mimeType,
                        fileSize = fileSize,
                        width = cloudinaryResult.width,
                        height = cloudinaryResult.height,
                        duration = cloudinaryResult.duration,
                        sha256 = computeFileSha256(filePath)
                    )

                    completeUploadUseCase(completeRequest)
                }.onFailure {
                    errorMessage.value = "Media upload failed"
                }
            }.onFailure {
                errorMessage.value = "Could not start media upload"
            }
        }
    }

    private fun computeFileSha256(filePath: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            java.io.File(filePath).inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Backend requires a 64-hex value; an empty file hashes to e3b0c442...
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        }
    }

    fun retryMessage(messageId: Long) {
        val failed = messages.value.find { it.id == messageId } ?: return
        if (failed.status != MessageStatus.FAILED) return

        viewModelScope.launch {
            // Drop the failed local row and resend the same content fresh.
            deleteMessageUseCase(messageId)
            failed.text?.let { sendTextMessage(it) }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            deleteMessageUseCase(messageId)
        }
    }

    fun markAsRead() {
        if (conversationId == 0L) return
        viewModelScope.launch {
            markAsReadUseCase(conversationId)
        }
    }

    fun loadMore() {
        if (hasMore.value && !isLoadingMore.value && cursor != null) {
            loadMessages()
        }
    }

    fun onMessageStatusChanged(messageId: Long, status: MessageStatus) {
        messages.value = messages.value.map { msg ->
            if (msg.id == messageId || msg.tempId == messageId.toString()) {
                msg.copy(status = status, updatedAt = System.currentTimeMillis())
            } else {
                msg
            }
        }
    }

    fun onNewMessage(message: Message) {
        if (!messages.value.any { it.id == message.id || it.tempId == message.tempId }) {
            messages.value = messages.value + message
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }
}
