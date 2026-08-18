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
import com.securechat.domain.usecase.chat.SendMessageUseCase
import com.securechat.domain.usecase.chat.DeleteMessageUseCase
import com.securechat.domain.usecase.chat.MarkAsReadUseCase
import com.securechat.domain.usecase.media.GetSignedUploadParamsUseCase
import com.securechat.domain.usecase.media.UploadMediaUseCase
import com.securechat.domain.usecase.media.CompleteUploadUseCase
import com.securechat.domain.usecase.media.DownloadMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val markAsReadUseCase: MarkAsReadUseCase,
    private val getSignedUploadParamsUseCase: GetSignedUploadParamsUseCase,
    private val uploadMediaUseCase: UploadMediaUseCase,
    private val completeUploadUseCase: CompleteUploadUseCase,
    private val downloadMediaUseCase: DownloadMediaUseCase,
    private val cloudinaryHelper: CloudinaryUploadHelper
) : ViewModel() {

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

    fun initialize(conversationId: Long, currentUserId: Long) {
        this.conversationId = conversationId
        this.currentUserId = currentUserId
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
                if (cursor == null) {
                    messages.value = page.messages
                } else {
                    messages.value = messages.value + page.messages
                }
                cursor = page.nextCursor
                hasMore.value = page.hasMore
            }.onFailure { e ->
                errorMessage.value = "Failed to load messages"
            }
        }
    }

    fun sendTextMessage(text: String, replyToId: Long? = null) {
        if (text.trim().isEmpty()) return
        
        val tempId = UUID.randomUUID().toString()
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
            tempId = tempId,
            isOptimistic = true
        )
        
        // Add optimistically
        messages.value = messages.value + message
        
        // Send via WebSocket (handled by WebSocket manager)
        // For now, also call use case
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
                    // 3. Complete upload
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
                        sha256 = "" // Would need to compute
                    )
                    
                    completeUploadUseCase(completeRequest)
                }
            }
        }
    }

    fun retryMessage(messageId: Long) {
        viewModelScope.launch {
            deleteMessageUseCase(messageId)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            deleteMessageUseCase(messageId)
            messages.value = messages.value.filter { it.id != messageId }
        }
    }

    fun markAsRead() {
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
        // Check for duplicates
        if (!messages.value.any { it.id == message.id || it.tempId == message.tempId }) {
            messages.value = messages.value + message
        }
    }
}