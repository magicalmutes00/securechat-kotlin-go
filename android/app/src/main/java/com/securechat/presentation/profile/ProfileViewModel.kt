package com.securechat.presentation.profile

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.domain.model.User
import com.securechat.domain.repository.MediaRepository
import com.securechat.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Backs the profile editor. Avatar images are uploaded to Cloudinary through the
 * existing signed-upload flow: sign → upload → complete, which returns a server
 * media id that is then attached to the user via PATCH /users/me.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val user: StateFlow<User?> = userRepository.observeCurrentUser()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var displayName = mutableStateOf("")
        private set
    var username = mutableStateOf("")
        private set
    var avatarUrl = mutableStateOf<String?>(null)
        private set
    var avatarMediaId = mutableStateOf<Long?>(null)
        private set

    var isSaving = mutableStateOf(false)
        private set
    var isUploadingAvatar = mutableStateOf(false)
        private set
    var uploadProgress = mutableStateOf(0)
        private set

    var errorMessage = mutableStateOf<String?>(null)
        private set
    var successMessage = mutableStateOf<String?>(null)
        private set

    private var initialised = false

    init {
        viewModelScope.launch {
            val current = userRepository.observeCurrentUser().firstOrNull()
            if (current != null && !initialised) {
                seedFrom(current)
                initialised = true
            }
        }
    }

    private fun seedFrom(user: User) {
        displayName.value = user.displayName
        username.value = user.username ?: ""
        avatarUrl.value = user.avatarUrl
        avatarMediaId.value = user.profileImageId
    }

    fun onDisplayNameChanged(value: String) {
        displayName.value = value
        errorMessage.value = null
    }

    fun onUsernameChanged(value: String) {
        username.value = value
        errorMessage.value = null
    }

    fun clearError() {
        errorMessage.value = null
    }

    fun clearSuccess() {
        successMessage.value = null
    }

    /** Copies the picked image into cache, uploads it, and stages the media id. */
    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            isUploadingAvatar.value = true
            uploadProgress.value = 0
            errorMessage.value = null

            try {
                val file = copyToCache(uri)
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"

                val params = mediaRepository.getSignedUploadParams(
                    MediaRepository.SignedUploadRequest(
                        resourceType = "image",
                        filename = file.name,
                        mimeType = mimeType,
                        fileSize = file.length()
                    )
                ).getOrThrow()

                val uploaded = mediaRepository.uploadMedia(params, file.absolutePath) { progress ->
                    uploadProgress.value = progress.progressPercent
                }.getOrThrow()

                val completed = mediaRepository.completeUpload(
                    MediaRepository.CompleteUploadRequest(
                        conversationId = 0,
                        cloudinaryPublicId = uploaded.publicId,
                        resourceType = "image",
                        secureUrl = uploaded.secureUrl,
                        originalFilename = file.name,
                        mimeType = mimeType,
                        fileSize = file.length(),
                        width = uploaded.width,
                        height = uploaded.height,
                        duration = uploaded.duration,
                        sha256 = sha256(file)
                    )
                ).getOrThrow()

                val media = completed.media
                    ?: throw IllegalStateException("Upload did not return media")

                avatarMediaId.value = media.id
                avatarUrl.value = media.secureUrl
                successMessage.value = "Photo uploaded. Tap Save to apply."
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Failed to upload photo."
            } finally {
                isUploadingAvatar.value = false
            }
        }
    }

    /** Clears the staged avatar so the next save omits an image id. */
    fun removeAvatar() {
        avatarMediaId.value = null
        avatarUrl.value = null
        successMessage.value = "Photo removed. Tap Save to apply."
    }

    fun save(onSaved: () -> Unit) {
        if (displayName.value.isBlank()) {
            errorMessage.value = "Display name is required."
            return
        }
        if (username.value.isNotBlank() && !username.value.trim().matches("^[a-zA-Z0-9_]{3,30}$".toRegex())) {
            errorMessage.value = "Username must be 3-30 characters (letters, numbers, underscore)."
            return
        }

        isSaving.value = true
        errorMessage.value = null

        viewModelScope.launch {
            val result = userRepository.updateProfile(
                displayName = displayName.value.trim(),
                username = username.value.trim().ifBlank { null },
                profileImageId = avatarMediaId.value,
                avatarUrl = avatarUrl.value
            )
            isSaving.value = false

            result.onSuccess {
                successMessage.value = "Profile updated."
                onSaved()
            }.onFailure { e ->
                errorMessage.value = when {
                    e.message?.contains("taken") == true -> "That username is already taken."
                    else -> e.message ?: "Failed to update profile."
                }
            }
        }
    }

    private fun copyToCache(uri: Uri): File {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to read the selected image.")
        val outFile = File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
        input.use { source -> outFile.outputStream().use { sink -> source.copyTo(sink) } }
        return outFile
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}