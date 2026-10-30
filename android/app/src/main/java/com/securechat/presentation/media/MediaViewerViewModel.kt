package com.securechat.presentation.media

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.data.local.dao.MediaDao
import com.securechat.data.local.entity.toDomain
import com.securechat.domain.model.Media
import com.securechat.domain.model.MediaResourceType
import com.securechat.domain.usecase.media.DownloadMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Backs the full-screen image/video/document viewers.
 *
 * The viewers are reachable by media id rather than by passing an object through
 * the navigation graph, so they survive process death and deep links, and read the
 * attachment straight from the Room cache the message list already populated.
 */
@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    private val mediaDao: MediaDao,
    private val downloadMediaUseCase: DownloadMediaUseCase,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mediaId: Long = savedStateHandle.get<Long>("mediaId") ?: 0L

    val media: StateFlow<Media?> = mediaDao.getByServerId(mediaId)
        .map { it?.toDomain() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Progress percentage while downloading, null when idle. */
    var downloadProgress: MutableState<Int?> = mutableStateOf(null)
        private set

    /** A one-shot line of feedback for the viewer to show ("Saved", failure...). */
    var statusMessage: MutableState<String?> = mutableStateOf(null)
        private set

    /**
     * The downloaded copy, cached on disk. Anything that hands the file to another
     * app needs a real path and a content Uri, not the remote URL.
     */
    var downloadedFile: MutableState<File?> = mutableStateOf(null)
        private set

    /** True once a local copy exists, which is what enables Share / Open. */
    val hasLocalCopy: Boolean get() = downloadedFile.value?.exists() == true

    fun clearStatus() {
        statusMessage.value = null
    }

    /**
     * Downloads the asset into app-private storage.
     *
     * Deliberately not the public Downloads folder: that needs a MediaStore insert
     * (or a storage permission) and only matters for "keep this", whereas every
     * action in these screens — share, open in another app — works from the cache
     * copy.
     */
    fun download(onFinished: (Boolean) -> Unit = {}) {
        val current = media.value ?: return
        if (downloadProgress.value != null) return

        viewModelScope.launch {
            downloadProgress.value = 0
            statusMessage.value = null

            val target = cacheFileFor(current)
            val result = downloadMediaUseCase(current, target.absolutePath) { progress ->
                downloadProgress.value = progress.progressPercent
            }

            downloadProgress.value = null
            when (result) {
                is com.securechat.core.common.Result.Success -> {
                    downloadedFile.value = target
                    statusMessage.value = "Saved ${target.name}"
                    onFinished(true)
                }
                is com.securechat.core.common.Result.Failure -> {
                    statusMessage.value = result.exception.message ?: "Download failed"
                    onFinished(false)
                }
            }
        }
    }

    /** Shares the local copy, downloading it first when necessary. */
    fun share() {
        val current = media.value ?: return
        val existing = downloadedFile.value?.takeIf { it.exists() }
        if (existing != null) {
            launchShare(existing, current)
            return
        }
        download { ok ->
            if (ok) downloadedFile.value?.let { launchShare(it, current) }
        }
    }

    /** Hands the file to whichever installed app claims its MIME type. */
    fun openExternally() {
        val current = media.value ?: return
        val existing = downloadedFile.value?.takeIf { it.exists() }
        if (existing != null) {
            launchView(existing, current)
            return
        }
        download { ok ->
            if (ok) downloadedFile.value?.let { launchView(it, current) }
        }
    }

    private fun launchShare(file: File, media: Media) {
        val uri = uriFor(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = media.mimeType.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startChooser(intent, "Share ${media.originalFilename}")
    }

    private fun launchView(file: File, media: Media) {
        val uri = uriFor(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, media.mimeType.ifBlank { "application/octet-stream" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            // No handler installed for this type — say so instead of throwing.
            statusMessage.value = "No app can open ${media.mimeType.ifBlank { "this file" }}"
            return
        }
        startChooser(intent, "Open with")
    }

    private fun startChooser(intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
            .onFailure { statusMessage.value = it.message ?: "Couldn't open the file" }
    }

    private fun uriFor(file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private suspend fun cacheFileFor(media: Media): File =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "downloads").apply { mkdirs() }
            val name = sanitize(media.originalFilename)
            File(directory, "${media.id}_$name")
        }

    /** A media name is remote content; it must not choose the destination path. */
    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_')
            .takeIf { it.isNotBlank() }
            ?: defaultNameFor(media.value?.resourceType)

    private fun defaultNameFor(type: MediaResourceType?): String = when (type) {
        MediaResourceType.IMAGE -> "image.jpg"
        MediaResourceType.VIDEO -> "video.mp4"
        MediaResourceType.AUDIO -> "audio.m4a"
        else -> "document.bin"
    }
}
