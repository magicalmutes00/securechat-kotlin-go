package com.securechat.core.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun Long.formatTimestamp(): String {
    val date = Date(this)
    val now = Date()
    val diff = now.time - this
    
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${diff / TimeUnit.MINUTES.toMillis(1)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            hourFormat.format(date)
        }
        diff < TimeUnit.DAYS.toMillis(7) -> {
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            dayFormat.format(date)
        }
        else -> {
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            dateFormat.format(date)
        }
    }
}

fun Long.formatFullTimestamp(): String {
    val date = Date(this)
    val now = Date()
    val diff = now.time - this
    
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${diff / TimeUnit.MINUTES.toMillis(1)} minutes ago"
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val format = SimpleDateFormat("'Today at' HH:mm", Locale.getDefault())
            format.format(date)
        }
        diff < TimeUnit.DAYS.toMillis(2) -> {
            val format = SimpleDateFormat("'Yesterday at' HH:mm", Locale.getDefault())
            format.format(date)
        }
        diff < TimeUnit.DAYS.toMillis(7) -> {
            val format = SimpleDateFormat("EEEE 'at' HH:mm", Locale.getDefault())
            format.format(date)
        }
        else -> {
            val format = SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault())
            format.format(date)
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb / 1024
    if (mb < 1024) return "$mb MB"
    val gb = mb / 1024
    return "$gb GB"
}

fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes % 60, seconds % 60)
        minutes > 0 -> String.format(Locale.getDefault(), "%d:%02d", minutes, seconds % 60)
        else -> String.format(Locale.getDefault(), "0:%02d", seconds)
    }
}

fun getMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast(".", "").lowercase()
    return when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
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
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "zip" -> "application/zip"
        "rar" -> "application/x-rar-compressed"
        "7z" -> "application/x-7z-compressed"
        else -> "application/octet-stream"
    }
}

fun getMediaTypeFromMime(mimeType: String): String {
    return when {
        mimeType.startsWith("image/") -> "image"
        mimeType.startsWith("video/") -> "video"
        mimeType.startsWith("audio/") -> "audio"
        else -> "raw"
    }
}

fun FormatOtp.format(otp: String): String {
    if (otp.length <= 3) return otp
    return "${otp.substring(0, 3)} ${otp.substring(3)}"
}

object FormatOtp {
    fun format(otp: String): String {
        if (otp.length <= 3) return otp
        return "${otp.substring(0, 3)} ${otp.substring(3)}"
    }
}