package com.securechat.presentation.chat

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.common.Result
import com.securechat.core.utils.formatTimestamp
import com.securechat.data.sync.MediaMessageSender
import com.securechat.data.sync.MediaSendQueue
import com.securechat.data.sync.PendingMediaSend
import com.securechat.domain.model.Message
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.model.MessageType
import com.securechat.domain.usecase.chat.GetMessagesUseCase
import com.securechat.domain.usecase.chat.ObserveMessagesUseCase
import com.securechat.domain.usecase.chat.SendMessageUseCase
import com.securechat.domain.usecase.chat.DeleteMessageUseCase
import com.securechat.domain.usecase.chat.MarkAsReadUseCase
import com.securechat.domain.usecase.media.CancelUploadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * The attachment currently being sent from this screen, if any.
 *
 * Uploading is a multi-second, failure-prone operation, so it is a first-class
 * piece of state rather than something hidden inside a callback: the composer
 * shows its progress, and a failure offers cancel or retry instead of vanishing.
 */
data class AttachmentSendState(
    val uploadId: String,
    val fileName: String,
    val progress: Int = 0,
    val failed: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getMessagesUseCase: GetMessagesUseCase,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val markAsReadUseCase: MarkAsReadUseCase,
    private val mediaMessageSender: MediaMessageSender,
    private val mediaSendQueue: MediaSendQueue,
    private val cancelUploadUseCase: CancelUploadUseCase,
    private val messageRepository: com.securechat.domain.repository.MessageRepository,
    private val conversationRepository: com.securechat.domain.repository.ConversationRepository,
    private val webSocketEventProcessor: com.securechat.data.sync.WebSocketEventProcessor,
    private val webSocketManager: com.securechat.data.remote.websocket.WebSocketManager
) : ViewModel() {

    // Room is the source of truth; the observe flow keeps this state live for
    // optimistic sends, server acks, receipts and incoming messages alike.
    var messages: MutableState<List<Message>> = mutableStateOf(emptyList())
    var isLoading: MutableState<Boolean> = mutableStateOf(false)
    var isLoadingMore: MutableState<Boolean> = mutableStateOf(false)
    var hasMore: MutableState<Boolean> = mutableStateOf(true)
    var errorMessage: MutableState<String?> = mutableStateOf(null)

    /** Progress/failure of the attachment in flight, if any. */
    var attachmentSend: MutableState<AttachmentSendState?> = mutableStateOf(null)
        private set

    /** Retained so retry can replay the exact same send under the same id. */
    private var pendingAttachment: PendingMediaSend? = null

    /**
     * Whether this chat is a group. Group messages are attributed to their
     * author, so the bubble shows a name above the text — a 1:1 chat has exactly
     * two participants and gains nothing from it.
     */
    var isGroup: MutableState<Boolean> = mutableStateOf(false)
        private set
    var conversationName: MutableState<String?> = mutableStateOf(null)
        private set
    var memberCount: Int = 0
        private set
    var actionError: MutableState<String?> = mutableStateOf(null)
        private set

    /**
     * Header subtitle, derived from live cached presence.
     *
     * A 1:1 chat shows the other participant's state ("Online" / "Last seen ...").
     * A group shows its size. This replaces a hardcoded "Online" that displayed
     * the same string whether the other person was reachable or not.
     */
    var presenceText: MutableState<String> = mutableStateOf("")
        private set

    /** Ids of participants currently typing in this conversation. */
    var typingUserIds: MutableState<Set<Long>> = mutableStateOf(emptySet())
        private set

    /** True while anyone else is typing; the header prefers this over presence. */
    val isAnyoneTyping: Boolean get() = typingUserIds.value.isNotEmpty()

    private var conversationId: Long = 0L
    var currentUserId: Long = 0L
        private set
    private var cursor: String? = null
    private val LIMIT = 30
    private var observeJob: Job? = null
    private var conversationJob: Job? = null
    private var participantsJob: Job? = null
    private var typingJob: Job? = null
    private var typingStopJob: Job? = null
    private val typingTimeoutJobs = mutableMapOf<Long, Job>()
    private var lastTypingSentAt = 0L

    fun initialize(conversationId: Long, currentUserId: Long) {
        if (this.conversationId == conversationId && observeJob?.isActive == true) return
        this.conversationId = conversationId
        this.currentUserId = currentUserId
        this.cursor = null
        this.messages.value = emptyList()

        observeJob?.cancel()
        conversationJob?.cancel()
        participantsJob?.cancel()

        // The conversation row supplies the type (and group title) the message
        // list needs: group messages are attributed, direct ones are not. A
        // separate job from the message collector, so cancelling one on
        // re-initialization cannot silently drop the other.
        conversationJob = viewModelScope.launch {
            conversationRepository.observeConversation(conversationId).collect { conversation ->
                isGroup.value = conversation?.type == com.securechat.domain.model.ConversationType.GROUP
                if (conversation?.name != null) {
                    conversationName.value = conversation.name
                }
            }
        }

        participantsJob = viewModelScope.launch {
            // Active members only: a row with leftAt set has left the group and
            // must not count toward the header or its member list.
            conversationRepository.observeParticipants(conversationId).collect { participants ->
                val active = participants.filter { it.leftAt == null }
                memberCount = active.size

                presenceText.value = if (isGroup.value) {
                    "${active.size} members"
                } else {
                    val other = active.firstOrNull { it.userId != currentUserId }
                    when {
                        other == null -> ""
                        other.isOnline -> "Online"
                        other.lastSeen > 0L -> "Last seen ${formatLastSeen(other.lastSeen)}"
                        else -> ""
                    }
                }
            }
        }

        typingJob = viewModelScope.launch {
            webSocketEventProcessor.typingEvents.collect { event ->
                // The server excludes the sender from its own broadcast, but do
                // not depend on that: showing "you are typing" would be a bug.
                if (event.conversationId != conversationId || event.userId == currentUserId) return@collect

                val current = typingUserIds.value.toMutableSet()
                typingTimeoutJobs.remove(event.userId)?.cancel()
                if (event.isTyping) {
                    current += event.userId
                    // Auto-clear: a lost TYPING_STOP (dropped frame, dead socket)
                    // must not leave "typing…" on screen indefinitely.
                    typingTimeoutJobs[event.userId] = viewModelScope.launch {
                        delay(TYPING_TIMEOUT_MS)
                        typingUserIds.value = typingUserIds.value - event.userId
                    }
                } else {
                    current -= event.userId
                }
                typingUserIds.value = current
            }
        }

        observeJob = viewModelScope.launch {
            observeMessagesUseCase(conversationId).collect { list ->
                // Newest first for the reverse-layout list.
                messages.value = list.sortedByDescending { it.createdAt }
            }
        }

        loadMessages()
        markAsRead()
    }

    /**
     * Retitles the group. The name in the header updates on its own because the
     * conversation collector above re-reads Room, which [rename] refreshes.
     */
    fun renameGroup(newName: String) {
        if (conversationId == 0L) return
        viewModelScope.launch {
            val result = conversationRepository.renameConversation(conversationId, newName)
            if (result.isFailure) {
                val reason = (result as? com.securechat.core.common.Result.Failure)?.exception?.message
                actionError.value = reason ?: "Couldn't rename the group."
            }
        }
    }

    /** Leaves the group, then hands control back to the caller to navigate away. */
    fun leaveGroup(onLeft: () -> Unit) {
        if (conversationId == 0L) return
        viewModelScope.launch {
            val result = conversationRepository.deleteConversation(conversationId)
            if (result.isFailure) {
                val reason = (result as? com.securechat.core.common.Result.Failure)?.exception?.message
                actionError.value = reason ?: "Couldn't leave the group."
                return@launch
            }
            onLeft()
        }
    }

    /**
     * Signals typing while the user composes.
     *
     * TYPING_START is throttled so a burst of keystrokes does not become a burst
     * of frames, and a TYPING_STOP is scheduled for when the user goes quiet —
     * otherwise a participant who stops mid-sentence would appear to type forever.
     */
    fun onUserTyping() {
        if (conversationId == 0L) return
        typingStopJob?.cancel()

        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt > TYPING_SEND_THROTTLE_MS) {
            lastTypingSentAt = now
            viewModelScope.launch { webSocketManager.sendTypingStart(conversationId) }
        }

        typingStopJob = viewModelScope.launch {
            delay(TYPING_IDLE_STOP_MS)
            sendTypingStopped()
        }
    }

    /** Called when the user sends or clears the input. */
    fun onUserStoppedTyping() {
        if (conversationId == 0L) return
        typingStopJob?.cancel()
        if (lastTypingSentAt != 0L) {
            viewModelScope.launch { sendTypingStopped() }
        }
    }

    private suspend fun sendTypingStopped() {
        lastTypingSentAt = 0L
        webSocketManager.sendTypingStop(conversationId)
    }

    fun loadMessages() {
        if (conversationId == 0L) return
        if (cursor == null) {            isLoading.value = true
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
        fileSize: Long
    ) {
        if (conversationId == 0L) return

        val payload = PendingMediaSend(
            uploadId = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = currentUserId,
            filePath = filePath,
            fileName = fileName,
            mimeType = mimeType,
            fileSize = fileSize,
            // Computed once, before any bytes move: the backend rejects a
            // completion without a 64-hex digest, and re-reading the file on a
            // retry (possibly after the cache was evicted) would fail.
            sha256 = computeFileSha256(filePath)
        )

        pendingAttachment = payload
        attachmentSend.value = AttachmentSendState(
            uploadId = payload.uploadId,
            fileName = payload.fileName
        )

        viewModelScope.launch {
            // Persist first: if the app dies mid-upload the sync pass picks it up
            // from pending_uploads instead of the attachment disappearing.
            mediaSendQueue.enqueue(payload)
            deliverAttachment(payload)
        }
    }

    /** Re-runs the same send, reusing the retained file and upload id. */
    fun retryAttachment() {
        val payload = pendingAttachment ?: return
        attachmentSend.value = attachmentSend.value?.copy(failed = false, error = null, progress = 0)
            ?: AttachmentSendState(payload.uploadId, payload.fileName)

        viewModelScope.launch { deliverAttachment(payload) }
    }

    /** Aborts the transfer and forgets the queued send. */
    fun cancelAttachment() {
        val payload = pendingAttachment ?: return
        pendingAttachment = null
        attachmentSend.value = null
        viewModelScope.launch {
            cancelUploadUseCase(payload.uploadId)
            mediaSendQueue.complete(payload.uploadId)
        }
    }

    private suspend fun deliverAttachment(payload: PendingMediaSend) {
        val result = mediaMessageSender.send(payload) { progress ->
            // Ignore late callbacks for an attachment the user already dismissed.
            attachmentSend.value = attachmentSend.value
                ?.takeIf { it.uploadId == payload.uploadId }
                ?.copy(progress = progress)
                ?: attachmentSend.value
        }

        when (result) {
            is Result.Success -> {
                mediaSendQueue.complete(payload.uploadId)
                if (pendingAttachment?.uploadId == payload.uploadId) pendingAttachment = null
                attachmentSend.value = null
            }
            is Result.Failure -> {
                // Keep the row: the sync pass retries it with backoff even if the
                // user navigates away from this screen.
                mediaSendQueue.markAttemptFailed(payload.uploadId)
                if (pendingAttachment?.uploadId == payload.uploadId) {
                    attachmentSend.value = AttachmentSendState(
                        uploadId = payload.uploadId,
                        fileName = payload.fileName,
                        progress = attachmentSend.value?.progress ?: 0,
                        failed = true,
                        error = result.exception.message ?: "Couldn't send the attachment."
                    )
                }
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
            // resendMessage replays the original frame — text, media id, reply
            // reference and all. The old version deleted the row and rebuilt a
            // text-only message, so retrying an image silently did nothing and
            // retrying a reply lost what it was replying to.
            val result = messageRepository.resendMessage(messageId)
            if (result.isFailure) {
                val reason = (result as? Result.Failure)?.exception?.message
                errorMessage.value = reason ?: "Couldn't resend that message."
            }
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
        conversationJob?.cancel()
        participantsJob?.cancel()
        typingJob?.cancel()
        typingStopJob?.cancel()
        typingTimeoutJobs.values.forEach { it.cancel() }
        super.onCleared()
    }

    /**
     * Renders a last-seen timestamp the way the header reads naturally. Reuses
     * the list row's formatter so the two surfaces agree on phrasing.
     */
    private fun formatLastSeen(lastSeen: Long): String = lastSeen.formatTimestamp()

    companion object {
        /** Minimum interval between outgoing TYPING_START frames. */
        private const val TYPING_SEND_THROTTLE_MS = 3_000L
        /** How long after the last keystroke a TYPING_STOP is sent. */
        private const val TYPING_IDLE_STOP_MS = 4_000L
        /** Safety net clearing a locally displayed "typing…" if STOP is lost. */
        private const val TYPING_TIMEOUT_MS = 6_000L
    }
}
