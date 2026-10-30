package com.securechat.core.media

import android.content.Context
import com.securechat.core.common.Result
import com.securechat.domain.repository.MediaRepository
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Uploads to, and downloads from, Cloudinary directly using the signed upload
 * parameters issued by the backend — the API secret never reaches the client.
 *
 * Both directions stream in fixed-size chunks. The previous implementation
 * buffered the entire file into an okio [okio.Buffer] before its single write
 * and read a download into a [ByteArray], so a video near the configured 500 MB
 * limit allocated its whole size on the heap and died with an OOM. Progress is
 * now emitted as bytes actually leave (or arrive at) the device, which also
 * makes the progress UI mean something: it used to fire once, at 100%.
 *
 * In-flight requests are tracked so [cancel] can abort them for real. Closing
 * the progress channel, as `cancelUpload` did before, only stopped updates and
 * left the transfer running to completion.
 */
class CloudinaryUploadHelper(@Suppress("UNUSED_PARAMETER") private val context: Context) {

    data class CloudinaryUploadResult(
        val publicId: String,
        val secureUrl: String,
        val width: Int?,
        val height: Int?,
        val duration: Double?,
        val format: String,
        val bytes: Long
    )

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    /** Live requests keyed by the caller's upload id, for cancellation. */
    private val activeCalls = Collections.synchronizedMap(mutableMapOf<String, Call>())

    /**
     * Aborts the in-flight request registered under [uploadId].
     * @return true if a transfer was actually cancelled.
     */
    fun cancel(uploadId: String): Boolean {
        val call = activeCalls.remove(uploadId) ?: return false
        call.cancel()
        return true
    }

    /**
     * Streams [filePath] to Cloudinary as a signed multipart upload, reporting
     * progress as bytes are written to the socket.
     */
    suspend fun uploadFile(
        uploadUrl: String,
        publicId: String,
        signature: String,
        timestamp: Long,
        apiKey: String,
        filePath: String,
        uploadId: String? = null,
        progressListener: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<CloudinaryUploadResult> {
        return withContext(Dispatchers.IO) {
            Result.runCatching {
                val file = File(filePath)
                val totalBytes = file.length()
                if (totalBytes == 0L) {
                    throw IllegalArgumentException("File is empty")
                }

                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("api_key", apiKey)
                    .addFormDataPart("timestamp", timestamp.toString())
                    .addFormDataPart("signature", signature)
                    .addFormDataPart("public_id", publicId)
                    // folder must match the folder the backend signed; a
                    // mismatch makes Cloudinary reject the signature.
                    .addFormDataPart("folder", publicId.substringBeforeLast("/", missingDelimiterValue = "securechat"))
                    .addPart(MultipartBody.Part.createFormData("file", file.name, progressRequestBody(file, totalBytes, progressListener)))
                    .build()

                val request = Request.Builder().url(uploadUrl).post(multipart).build()
                val call = okHttpClient.newCall(request)
                if (uploadId != null) activeCalls[uploadId] = call

                try {
                    call.execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            throw IOException("Upload failed with status ${response.code}: $responseBody")
                        }
                        parseCloudinaryResponse(responseBody)
                    }
                } finally {
                    if (uploadId != null) activeCalls.remove(uploadId)
                }
            }
        }
    }

    /**
     * Writes the file to the sink in chunks, emitting progress per chunk rather
     * than once at the end.
     */
    private fun progressRequestBody(
        file: File,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit
    ): RequestBody = object : RequestBody() {
        override fun contentType(): okhttp3.MediaType? =
            getMimeType(file.absolutePath).toMediaType()

        // Declaring the length lets OkHttp stream with a Content-Length header
        // instead of falling back to chunked transfer encoding.
        override fun contentLength(): Long = totalBytes

        override fun writeTo(sink: BufferedSink) {
            file.inputStream().use { input ->
                val buffer = ByteArray(CHUNK_SIZE_BYTES)
                var transferred = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    sink.write(buffer, 0, read)
                    transferred += read
                    onProgress(transferred, totalBytes)
                }
                sink.flush()
            }
        }
    }

    /**
     * Streams [url] to [destinationPath], reporting progress as chunks land on
     * disk rather than after the whole body is in memory.
     */
    suspend fun downloadFile(
        url: String,
        destinationPath: String,
        uploadId: String? = null,
        progressCallback: (MediaRepository.UploadProgress) -> Unit
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            Result.runCatching {
                val call = okHttpClient.newCall(Request.Builder().url(url).get().build())
                if (uploadId != null) activeCalls[uploadId] = call

                try {
                    call.execute().use { response ->
                        val body = response.body ?: throw IOException("Empty download body")
                        if (!response.isSuccessful) {
                            throw IOException("Download failed with status ${response.code}")
                        }

                        val totalBytes = body.contentLength()
                        val file = File(destinationPath)
                        file.parentFile?.mkdirs()

                        var transferred = 0L
                        body.byteStream().use { input ->
                            file.outputStream().use { output ->
                                val buffer = ByteArray(CHUNK_SIZE_BYTES)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                    transferred += read
                                    if (totalBytes > 0) {
                                        progressCallback(
                                            MediaRepository.UploadProgress(
                                                uploadId = uploadId.orEmpty(),
                                                bytesTransferred = transferred,
                                                totalBytes = totalBytes,
                                                progressPercent = ((transferred * 100) / totalBytes).toInt(),
                                                status = MediaRepository.UploadStatus.UPLOADING
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        progressCallback(
                            MediaRepository.UploadProgress(
                                uploadId = uploadId.orEmpty(),
                                bytesTransferred = transferred,
                                totalBytes = if (totalBytes > 0) totalBytes else transferred,
                                progressPercent = 100,
                                status = MediaRepository.UploadStatus.COMPLETED
                            )
                        )
                    }
                } finally {
                    if (uploadId != null) activeCalls.remove(uploadId)
                }
                Unit
            }
        }
    }

    private fun parseCloudinaryResponse(json: String): CloudinaryUploadResult {
        val publicId = extractJsonValue(json, "public_id").orEmpty()
        val secureUrl = extractJsonValue(json, "secure_url").orEmpty()
        if (publicId.isEmpty() || secureUrl.isEmpty()) {
            // A 2xx without these fields is not a usable upload.
            throw IOException("Cloudinary response missing public_id or secure_url")
        }
        return CloudinaryUploadResult(
            publicId = publicId,
            secureUrl = secureUrl,
            width = extractJsonValue(json, "width")?.toIntOrNull(),
            height = extractJsonValue(json, "height")?.toIntOrNull(),
            duration = extractJsonValue(json, "duration")?.toDoubleOrNull(),
            format = extractJsonValue(json, "format").orEmpty(),
            bytes = extractJsonValue(json, "bytes")?.toLongOrNull() ?: 0L
        )
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
            ?: "\"$key\"\\s*:\\s*([0-9.]+)".toRegex().find(json)?.groupValues?.get(1)
    }

    private fun getMimeType(filePath: String): String {
        return when (File(filePath).extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    companion object {
        /** 256 KiB: large enough to keep throughput up, small enough that
         *  progress updates stay responsive and memory use is flat. */
        private const val CHUNK_SIZE_BYTES = 256 * 1024
    }
}
