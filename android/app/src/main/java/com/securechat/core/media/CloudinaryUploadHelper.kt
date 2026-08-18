package com.securechat.core.media

import android.content.Context
import com.securechat.core.common.Result
import com.securechat.domain.repository.MediaRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class CloudinaryUploadHelper(private val context: Context) {

    private val okHttpClient = OkHttpClient()
    private val client = HttpClient(Android)

    data class CloudinaryUploadResult(
        val publicId: String,
        val secureUrl: String,
        val width: Int?,
        val height: Int?,
        val duration: Double?,
        val format: String,
        val bytes: Long
    )

    suspend fun uploadFile(
        uploadUrl: String,
        publicId: String,
        signature: String,
        timestamp: Long,
        apiKey: String,
        filePath: String,
        progressListener: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<CloudinaryUploadResult> {
        return withContext(Dispatchers.IO) {
            Result.runCatching {
                val file = File(filePath)
                val totalBytes = file.length()

                if (totalBytes == 0L) {
                    throw IllegalArgumentException("File is empty")
                }

                // Create multipart request with progress tracking
                val filePart = createProgressRequestBody(file, progressListener, totalBytes)
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("api_key", apiKey)
                    .addFormDataPart("timestamp", timestamp.toString())
                    .addFormDataPart("signature", signature)
                    .addFormDataPart("public_id", publicId)
                    .addFormDataPart("folder", "securechat")
                    .addPart(filePart)
                    .build()

                val request = Request.Builder()
                    .url(uploadUrl)
                    .post(multipart)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        throw Exception("Upload failed with status ${response.code}: $responseBody")
                    }
                    parseCloudinaryResponse(responseBody)
                }
            }
        }
    }

    private fun createProgressRequestBody(
        file: File,
        progressListener: (bytesTransferred: Long, totalBytes: Long) -> Unit,
        totalBytes: Long
    ): MultipartBody.Part {
        val fileBody = file.asRequestBody(getMimeType(file.absolutePath).toMediaType())

        val body = object : RequestBody() {
            override fun contentType(): okhttp3.MediaType? = fileBody.contentType()

            override fun writeTo(sink: okio.BufferedSink) {
                val buffer = okio.Buffer()
                fileBody.writeTo(buffer)
                val bytesWritten = buffer.size
                sink.write(buffer, bytesWritten)
                sink.flush()
                progressListener(bytesWritten, totalBytes)
            }
        }

        return MultipartBody.Part.createFormData("file", file.name, body)
    }

    private fun parseCloudinaryResponse(json: String): CloudinaryUploadResult {
        // Parse JSON response from Cloudinary
        // Using kotlinx.serialization would be better but keeping simple for now
        val publicId = extractJsonValue(json, "public_id") ?: ""
        val secureUrl = extractJsonValue(json, "secure_url") ?: ""
        val width = extractJsonValue(json, "width")?.toIntOrNull()
        val height = extractJsonValue(json, "height")?.toIntOrNull()
        val duration = extractJsonValue(json, "duration")?.toDoubleOrNull()
        val format = extractJsonValue(json, "format") ?: ""
        val bytes = extractJsonValue(json, "bytes")?.toLongOrNull() ?: 0L

        return CloudinaryUploadResult(
            publicId = publicId,
            secureUrl = secureUrl,
            width = width,
            height = height,
            duration = duration,
            format = format,
            bytes = bytes
        )
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
            ?: "\"$key\"\\s*:\\s*([0-9.]+)".toRegex().find(json)?.groupValues?.get(1)
    }

    private fun getMimeType(filePath: String): String {
        val extension = File(filePath).extension.lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    suspend fun downloadFile(
        url: String,
        destinationPath: String,
        progressCallback: (MediaRepository.UploadProgress) -> Unit
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(destinationPath)
                file.parentFile?.mkdirs()

                val response = client.get(url)
                val totalBytes = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
                val bytes = response.body<ByteArray>()
                file.writeBytes(bytes)

                progressCallback(MediaRepository.UploadProgress(
                    uploadId = "download_${file.name}",
                    bytesTransferred = totalBytes,
                    totalBytes = totalBytes,
                    progressPercent = 100,
                    status = MediaRepository.UploadStatus.COMPLETED
                ))
                Result.success(Unit)
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }
    }

    fun close() {
        client.close()
    }
}